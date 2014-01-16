/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#include "libjitsi_webrtc_aec.h"

#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/interface/module_common_types.h"

#include <errno.h>
#include <jni.h>
#include <math.h>
#include <pthread.h>
#include <stdio.h>
#include <sys/time.h>
#include <unistd.h>

using namespace webrtc;

/**
 * Functions to use acoustic echo cancelling with webrtc.
 *
 * @author Vincent Lucas
 */
typedef struct
{
    // 0 = cqpture, 1 = render
    int16_t * data[2];
    int dataLength[2];
    int dataUsed[2];
    int dataProcessed[2];
    int audioProcessingLength[2];
    AudioProcessing * audioProcessing;
    pthread_mutex_t mutex[2];
    struct timeval lastProcess[2];
    int activeStreams[2];
    AudioStreamBasicDescription format;
} libjitsi_webrtc_aec;

static libjitsi_webrtc_aec * aec = NULL;

static JavaVM * libjitsi_webrtc_aec_VM = NULL;

int
libjitsi_webrtc_aec_init(
        void);

void
libjitsi_webrtc_aec_free(
        void);

int16_t *
libjitsi_webrtc_aec_internalGetData(
        int isRenderStream,
        int length);

void
libjitsi_webrtc_aec_log(
        const char * error_format,
        ...);

int
libjitsi_webrtc_aec_getNbSampleForMs(
        int nbMS);

int
libjitsi_webrtc_aec_getNbMsForSample(
        int nbSample);

void
libjitsi_webrtc_aec_nbMsToTimeval(
        int nbMs,
        struct timeval * nbMsTimeval);

int
libjitsi_webrtc_aec_timevalToNbMs(
        struct timeval nbMsTimeval);

int
libjitsi_webrtc_aec_getNbSampleSinceLastProcess(
        int length,
        int isRenderStream);

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *pvt)
{
    libjitsi_webrtc_aec_VM = vm;
    libjitsi_webrtc_aec_init();
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *pvt)
{
    libjitsi_webrtc_aec_free();
    libjitsi_webrtc_aec_VM = NULL;
}


/**
 * Initiates a new webrtc_aec capable instance.
 *
 * @return 0 if everything works fine. -1 otherwise.
 */
int
libjitsi_webrtc_aec_init(
        void)
{
    int err;
    int id = getpid();

    // If AEC is already active, frees it before the reinitialization.
    if(aec != NULL)
    {
        libjitsi_webrtc_aec_free();
    }

    // Starts the initialization.
    if((aec = (libjitsi_webrtc_aec*) malloc(sizeof(libjitsi_webrtc_aec)))
            == NULL)
    {
        libjitsi_webrtc_aec_log(
                "%s: %s\n",
                "libjitsi_webrtc_aec_init (libjitsi_webrtc_aec.c): \
                    \n\tmalloc",
                strerror(errno));
        return -1;
    }

    // Inits the capture and render buffer to default values
    for(int i = 0; i < 2; ++i)
    {
        aec->data[i] = NULL;
        aec->audioProcessingLength[i] = 0;
        aec->dataLength[i] = 0;
        aec->dataUsed[i] = 0;
        aec->dataProcessed[i] = 0;
        if(pthread_mutex_init(&aec->mutex[i], NULL) != 0)
        {
            libjitsi_webrtc_aec_log(
                    "%s: %s\n",
                    "libjitsi_webrtc_aec_init (libjitsi_webrtc_aec.c): \
                    \n\tpthread_mutex_init",
                    strerror(errno));
            libjitsi_webrtc_aec_free();
            return -1;
        }
        timerclear(&aec->lastProcess[i]);
        aec->activeStreams[i] = 0;
    }
    
    // Creates WEBRTC AudioProcessing
    if((aec->audioProcessing = AudioProcessing::Create(id)) == NULL)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
                "libjitsi_webrtc_aec_init (libjitsi_webrtc_aec.c): \
                \n\tAudioProcessing::Create",
                0);
        libjitsi_webrtc_aec_free();
        return -1;
    }

    // Enables high pass filter.
    if((err = aec->audioProcessing->high_pass_filter()->Enable(true))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
                "libjitsi_webrtc_aec_init (libjitsi_webrtc_aec.c): \
                \n\tAudioProcessing::high_pass_filter::Enable",
                err);
        libjitsi_webrtc_aec_free();
        return -1;
    }

    return 0;
}

/**
 * Frees the webrtc_aec instance.
 */
void
libjitsi_webrtc_aec_free(
        void)
{
    if(aec != NULL)
    {
        for(int i = 0; i < 2; ++i)
        {
            if(aec->data[i] != NULL)
            {
                aec->audioProcessingLength[i] = 0;
                aec->dataLength[i] = 0;
                aec->dataUsed[i] = 0;
                aec->dataProcessed[i] = 0;
                timerclear(&aec->lastProcess[i]);
                free(aec->data[i]);
                aec->data[i] = NULL;
                if(pthread_mutex_destroy(&aec->mutex[i]) != 0)
                {
                    libjitsi_webrtc_aec_log(
                            "%s: %s\n",
                            "libjitsi_webrtc_aec_free (libjitsi_webrtc_aec.c): \
                            \n\tpthread_mutex_destroy",
                            strerror(errno));
                }
            }
        }

        if(aec->audioProcessing != NULL)
        {
            delete(aec->audioProcessing);
            aec->audioProcessing = NULL;
        }


        free(aec);
    }
}

/**
 * Registers a new starting stream.
 *
 * @param isOutputStream True if the starting stream is an output stream. False
 * for a capture stream.
 */
void
libjitsi_webrtc_aec_start(
        unsigned char isOutputStream)
{
    libjitsi_webrtc_aec_lock(isOutputStream);

    timerclear(&aec->lastProcess[isOutputStream]);
    aec->dataUsed[isOutputStream] = 0;
    aec->dataProcessed[isOutputStream] = 0;

    ++aec->activeStreams[isOutputStream];

    libjitsi_webrtc_aec_unlock(isOutputStream);
}

/**
 * Unregisters a stoping stream.
 *
 * @param isOutputStream True if the stoping stream is an output stream. False
 * for a capture stream.
 */
void
libjitsi_webrtc_aec_stop(
        unsigned char isOutputStream)
{
    libjitsi_webrtc_aec_lock(isOutputStream);

    timerclear(&aec->lastProcess[isOutputStream]);
    aec->dataUsed[isOutputStream] = 0;
    aec->dataProcessed[isOutputStream] = 0;

    --aec->activeStreams[isOutputStream];

    libjitsi_webrtc_aec_unlock(isOutputStream);
}

/**
 * Initialyses the AEC process to corresponds to the capture specification.
 *
 * @param sample_rate The sample rate used by the capture device.
 * @param nb_channels The number of channels used by the capture device.
 * @param format The strem format description corresponding to the input
 * stream.
 *
 * @return 0 if everything works fine. -1 otherwise.
 */
int
libjitsi_webrtc_aec_initAudioProcessing(
        int sample_rate,
        int nb_channels,
        AudioStreamBasicDescription format)
{
    libjitsi_webrtc_aec_lock(0);
    libjitsi_webrtc_aec_lock(1);

    int err;

    if((err = aec->audioProcessing->set_sample_rate_hz(sample_rate))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
                \n\tAudioProcessing::set_sample_rate_hz",
                err);
        libjitsi_webrtc_aec_unlock(0);
        libjitsi_webrtc_aec_unlock(1);
        return -1;
    }

    memcpy(&aec->format, &format, sizeof(AudioStreamBasicDescription));

    // Inits the capture and render buffer to default values
    for(int i = 0; i < 2; ++i)
    {
        timerclear(&aec->lastProcess[i]);
        aec->dataUsed[i] = 0;
        aec->dataProcessed[i] = 0;
        aec->audioProcessingLength[i] = libjitsi_webrtc_aec_getNbSampleForMs(10);

        if(libjitsi_webrtc_aec_internalGetData(i, aec->audioProcessingLength[i])
                == NULL)
        {
            libjitsi_webrtc_aec_log(
                    "%s\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
                    \n\tlibjitsi_webrtc_aec_resizeDataBuffer");
            libjitsi_webrtc_aec_unlock(0);
            libjitsi_webrtc_aec_unlock(1);
            return -1;
        }
    }

    // CAPTURE: Mono and stereo render only.
    if((err = aec->audioProcessing->set_num_channels(nb_channels, nb_channels))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
                \n\tAudioProcessing::set_num_channels",
                err);
        libjitsi_webrtc_aec_unlock(0);
        libjitsi_webrtc_aec_unlock(1);
        return -1;
    }

    // RENDER
    if((err = aec->audioProcessing->set_num_reverse_channels(nb_channels))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
                \n\tAudioProcessing::set_num_reverse_channels",
                err);
        libjitsi_webrtc_aec_unlock(0);
        libjitsi_webrtc_aec_unlock(1);
        return -1;
    }

    // AEC
    if((err = aec->audioProcessing->echo_cancellation()
                ->set_device_sample_rate_hz(sample_rate))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
            \n\tAudioProcessing::echo_cancellation::set_device_sample_rate_hz",
                err);
        libjitsi_webrtc_aec_unlock(0);
        libjitsi_webrtc_aec_unlock(1);
        return -1;
    }
    if((err = aec->audioProcessing->echo_cancellation()
                ->set_suppression_level(EchoCancellation::kHighSuppression))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
            \n\tAudioProcessing::echo_cancellation::set_suppression_level",
                err);
        libjitsi_webrtc_aec_unlock(0);
        libjitsi_webrtc_aec_unlock(1);
        return -1;
    }
    if((err = aec->audioProcessing->echo_cancellation()->Enable(true))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
            \n\tAudioProcessing::echo_cancellation::Enable",
                err);
        libjitsi_webrtc_aec_unlock(0);
        libjitsi_webrtc_aec_unlock(1);
        return -1;
    }

    libjitsi_webrtc_aec_unlock(0);
    libjitsi_webrtc_aec_unlock(1);

    return  0;
}

/**
 * Analyzes or processes a given stream to removes echo.
 *
 * @return 0 if everything works fine. -1 otherwise.
 */
int
libjitsi_webrtc_aec_process(
        void)
{
    int err;
    int nb_channels = aec->format.mChannelsPerFrame;
    int sample_rate = aec->format.mSampleRate;
    AudioFrame * frame = new AudioFrame();

    int start_capture = aec->dataProcessed[0];
    int end_capture = start_capture + aec->audioProcessingLength[0];
    int start_render = aec->dataProcessed[1];
    int end_render = start_capture + aec->audioProcessingLength[1];
    int init = (end_capture <= aec->dataUsed[0]);

    if(init)
    {
        if(end_render <= aec->dataUsed[1])
        {
            struct timeval currentTime;
            gettimeofday(&currentTime, NULL);

            // Process render stream.
            frame->UpdateFrame(
                    -1,
                    0,
                    aec->data[1] + start_render,
                    aec->audioProcessingLength[1] / nb_channels,
                    sample_rate,
                    AudioFrame::kNormalSpeech,
                    AudioFrame::kVadActive,
                    nb_channels);

            // Process render buffer.
            if(aec->activeStreams[0] > 0)
            {
                if((err = aec->audioProcessing->AnalyzeReverseStream(frame))
                        != 0)
                {
                    libjitsi_webrtc_aec_log(
                            "%s: 0x%x\n",
                        "libjitsi_webrtc_aec_process (libjitsi_webrtc_aec.c): \
                            \n\tAudioProcessing::AnalyzeReverseStream",
                            err);
                }
            }

            // Process capture stream.
            frame->UpdateFrame(
                    -1,
                    0,
                    aec->data[0] + start_capture,
                    aec->audioProcessingLength[0] / nb_channels,
                    sample_rate,
                    AudioFrame::kNormalSpeech,
                    AudioFrame::kVadActive,
                    nb_channels);

            // Definition from Webrtc library for
            // aec->audioProcessing->set_stream_delay_ms :
            //
            // This must be called if and only if echo processing is enabled.
            //
            // Sets the |delay| in ms between AnalyzeReverseStream() receiving a
            // far-end frame and ProcessStream() receiving a near-end frame
            // containing the corresponding echo. On the client-side this can be
            // expressed as
            //   delay = (t_render - t_analyze) + (t_process - t_capture)
            // where,
            //   - t_analyze is the time a frame is passed to
            //   AnalyzeReverseStream() and t_render is the time the first
            //   sample of the same frame is rendered by the audio hardware.
            //   - t_capture is the time the first sample of a frame is captured
            //   by the audio hardware and t_pull is the time the same frame is
            //   passed to ProcessStream().
            struct timeval delay;
            timersub(&currentTime, &aec->lastProcess[0], &delay);
            int32_t nbMs = libjitsi_webrtc_aec_timevalToNbMs(delay);
            if(nbMs < 0)
            {
                nbMs = 0;
            }
            else if(nbMs > 500)
            {
                nbMs = 500;
            }
            if((err = aec->audioProcessing->set_stream_delay_ms(nbMs))
                    != AudioProcessing::kNoError)
            {
                libjitsi_webrtc_aec_log(
                        "%s: 0x%x\n",
                        "libjitsi_webrtc_aec_process (libjitsi_webrtc_aec.c): \
                        \n\tAudioProcessing::set_stream_delay_ms",
                        err);
            }

            // Process capture buffer.
            if(aec->activeStreams[1] > 0)
            {
                if((err = aec->audioProcessing->ProcessStream(frame)) != 0)
                {
                    libjitsi_webrtc_aec_log(
                            "%s: 0x%x\n",
                        "libjitsi_webrtc_aec_process (libjitsi_webrtc_aec.c): \
                            \n\tAudioProcessing::ProccessStream",
                            err);
                }

                // If there is an echo detected, then copy the corrected data.
                if(aec->audioProcessing->echo_cancellation()->stream_has_echo())
                {
                    memcpy(
                            aec->data[0] + start_capture,
                            frame->data_,
                            aec->audioProcessingLength[0] * sizeof(int16_t));
                }
            }
            start_render = end_render;
            end_render += aec->audioProcessingLength[1];
        }
        start_capture = end_capture;
        end_capture += aec->audioProcessingLength[0];
    }
    aec->dataProcessed[0] = start_capture;
    aec->dataProcessed[1] = start_render;

    delete(frame);

    if(init)
    {
        return start_capture;
    }
    return 0;
}

/**
 * Once the stream has been played via the device, or pushed into the Java part,
 * then we can shift the buffer and remove all previously computed data.
 *
 * @param isRenderStream True for the render stream. False otherwise.
 */
void
libjitsi_webrtc_aec_completeProcess(
        int isRenderStream)
{
    if(aec->data[isRenderStream] != NULL)
    {
        int nbLeft
            = aec->dataUsed[isRenderStream] - aec->dataProcessed[isRenderStream];
        if(nbLeft)
        {
            memcpy(
                    aec->data[isRenderStream],
                    aec->data[isRenderStream]
                        + aec->dataProcessed[isRenderStream],
                    nbLeft * sizeof(int16_t));

            int nbMs = libjitsi_webrtc_aec_getNbMsForSample(
                    aec->dataProcessed[isRenderStream]);
            struct timeval nbMsTimeval;
            libjitsi_webrtc_aec_nbMsToTimeval(nbMs, &nbMsTimeval);
            timeradd(
                    &aec->lastProcess[isRenderStream],
                    &nbMsTimeval,
                    &aec->lastProcess[isRenderStream]);
        }
        else
        {
            timerclear(&aec->lastProcess[isRenderStream]);
        }
        aec->dataUsed[isRenderStream] = nbLeft;
    }
    aec->dataProcessed[isRenderStream] = 0;
}

/**
 * Returns a pointer to the data of the stream with a given available free
 * space.
 *
 * @param isRenderStream True for the render stream. False otherwise.
 * @param length The availalbe free space requested by the caller.
 *
 * @return A pointer to the data of the stream. NULL if failed to allocate the
 * requested free space.
 */
int16_t *
libjitsi_webrtc_aec_getData(
        int isRenderStream,
        int length)
{
    int16_t * data = NULL;

    if((data = libjitsi_webrtc_aec_internalGetData(isRenderStream, length))
            == NULL)
    {
        libjitsi_webrtc_aec_log(
                "%s\n",
                "libjitsi_webrtc_aec_getData (libjitsi_webrtc_aec.c): \
                \n\tlibjitsi_webrtc_aec_internalGetData");
        return NULL;
    }
    aec->dataUsed[isRenderStream] += length;

    if(!timerisset(&aec->lastProcess[isRenderStream]))
    {
        gettimeofday(&aec->lastProcess[isRenderStream], NULL);
        if(!isRenderStream)
        {
            int nbMs = libjitsi_webrtc_aec_getNbMsForSample(length);
            struct timeval nbMsTimeval;
            libjitsi_webrtc_aec_nbMsToTimeval(nbMs, &nbMsTimeval);
            timersub(
                    &aec->lastProcess[isRenderStream],
                    &nbMsTimeval,
                    &aec->lastProcess[isRenderStream]);
        }
    }

    return data;
}

/**
 * Returns a pointer to the data of the stream with a given available free
 * space.
 *
 * @param isRenderStream True for the render stream. False otherwise.
 * @param length The availalbe free space requested by the caller.
 *
 * @return A pointer to the data of the stream. NULL if failed to allocate the
 * requested free space.
 */
int16_t *
libjitsi_webrtc_aec_internalGetData(
        int isRenderStream,
        int length)
{
    int tmpLength = length;

    // When AEC is activated and no input stream is active, then only allocate a
    // buffer for the current block to process and squeeze previous data.
    if(isRenderStream && aec->activeStreams[!isRenderStream] == 0)
    {
        tmpLength = 0;
        if(aec->activeStreams[!isRenderStream] == 0)
        {
            aec->dataUsed[isRenderStream] = 0;
            timerclear(&aec->lastProcess[isRenderStream]);
        }
    }

    int nbSample = libjitsi_webrtc_aec_getNbSampleSinceLastProcess(
            tmpLength,
            isRenderStream);
    if(nbSample < aec->dataUsed[isRenderStream]
            && (aec->dataUsed[isRenderStream] - nbSample) > (length / 2))
    {
        aec->dataUsed[isRenderStream] = nbSample;
    }

    int newLength = length + aec->dataUsed[isRenderStream];
    if(newLength > aec->dataLength[isRenderStream])
    {
        int16_t * newBuffer;
        if((newBuffer = (int16_t*) malloc(newLength * sizeof(int16_t)))
                == NULL)
        {
            libjitsi_webrtc_aec_log(
                    "%s: %s\n",
                "libjitsi_webrtc_aec_internalGetData (libjitsi_webrtc_aec.c): \
                    \n\tmalloc",
                    strerror(errno));
            return NULL;
        }
        if(aec->data[isRenderStream] != NULL)
        {
            if(aec->dataUsed[isRenderStream])
            {
                memcpy(
                        newBuffer,
                        aec->data[isRenderStream],
                        aec->dataUsed[isRenderStream] * sizeof(int16_t));
            }
            free(aec->data[isRenderStream]);
        }
        aec->data[isRenderStream] = newBuffer;
        aec->dataLength[isRenderStream] = newLength;
    }

    return (aec->data[isRenderStream] + aec->dataUsed[isRenderStream]);
}

/**
 * Returns a pointer to the start of the available processed data.
 *
 * @param isRenderStream True to get the buffer for the render stream. False
 * otherwise.
 *
 * @return A pointer to the start of the available processed data. NULL if
 * unavailable.
 */
int16_t *
libjitsi_webrtc_aec_getProcessedData(
        int isRenderStream)
{
    return aec->data[isRenderStream];
}

/**
 * Logs the corresponding error message.
 *
 * @param error_format The format of the error message.
 * @param ... The list of variable specified in the format argument.
 */
void
libjitsi_webrtc_aec_log(
        const char * error_format,
        ...)
{
    JNIEnv *env = NULL;

    if(libjitsi_webrtc_aec_VM->AttachCurrentThreadAsDaemon(
                (void**) &env,
                NULL)
            == 0)
    {
        jclass clazz = env->FindClass("org/jitsi/impl/neomedia/device/WebrtcAec");
        if (clazz)
        {
            jmethodID methodID = env->GetStaticMethodID(clazz, "log", "([B)V");

            int error_length = 2048;
            char error[error_length];
            va_list arg;
            va_start (arg, error_format);
            vsnprintf(error, error_length, error_format, arg);
            va_end (arg);

            int str_len = strlen(error);
            jbyteArray bufferBytes = env->NewByteArray(str_len);
            env->SetByteArrayRegion(bufferBytes, 0, str_len, (jbyte *) error);
            env->CallStaticVoidMethod(clazz, methodID, bufferBytes);
        }

        libjitsi_webrtc_aec_VM->DetachCurrentThread();
    }
}

/**
 * Copies the AEC format (defined by the capture stream) to the format strcuture
 * provided.
 *
 * @param format The AEC fornat to be filled in, only if the capture stream is
 * active.
 *
 * @return 1 if the format has been updated. 0 otherwise.
 */
int
libjitsi_webrtc_aec_getCaptureFormat(
        AudioStreamBasicDescription * format)
{
    if(aec->activeStreams[0] > 0)
    {
        memcpy(format, &aec->format, sizeof(AudioStreamBasicDescription));
        return 1;
    }
    return 0;
}

/**
 * Locks the mutex for the capture/render stream.
 *
 * @param isRenderStream True to lock the render stream. False otherwise.
 *
 * @return 0 if everything is ok. Otherwise any other value.
 */
int
libjitsi_webrtc_aec_lock(
        int isRenderStream)
{
    int err;
    if((err = pthread_mutex_lock(&aec->mutex[isRenderStream])) != 0)
    {
        libjitsi_webrtc_aec_log(
                "%s: %s\n",
                "libjitsi_webrtc_aec_lock (libjitsi_webrtc_aec.c): \
                \n\tpthread_mutex_lock",
                strerror(errno));
    }
    return err;
}

/**
 * Unlocks the mutex for the capture/render stream.
 *
 * @param isRenderStream True to lock the render stream. False otherwise.
 *
 * @return 0 if everything is ok. Otherwise any other value.
 */
int
libjitsi_webrtc_aec_unlock(
        int isRenderStream)
{
    int err;
    if((err = pthread_mutex_unlock(&aec->mutex[isRenderStream])) != 0)
    {
        libjitsi_webrtc_aec_log(
                "%s: %s\n",
                "libjitsi_webrtc_aec_unlock (libjitsi_webrtc_aec.c): \
                \n\tpthread_mutex_unlock",
                strerror(errno));
    }
    return err;
}

/**
 * Returns the number of sample necessary for a given time interval.
 *
 * @param nbMs The number of ms required.
 *
 * @return The number of sample necessary for a given time interval.
 */
int
libjitsi_webrtc_aec_getNbSampleForMs(
        int nbMs)
{
    int nb_channels = aec->format.mChannelsPerFrame;
    int sample_rate = aec->format.mSampleRate;

    return (nbMs * sample_rate * nb_channels) / 1000;
}

/**
 * Returns the time interval corresponding to a number of sample.
 *
 * @param nbSample The number of sample.
 *
 * @return The time interval corresponding to a number of sample.
 */
int
libjitsi_webrtc_aec_getNbMsForSample(
        int nbSample)
{
    int nb_channels = aec->format.mChannelsPerFrame;
    int sample_rate = aec->format.mSampleRate;

    return (nbSample * 1000) / (nb_channels * sample_rate);
}

/**
 * Converts a time interval in Ms to a structure timeval.
 *
 * @param nbMs The number of milliseconds.
 * @param nbMsTimeval The structure timeval to fill in.
 */
void
libjitsi_webrtc_aec_nbMsToTimeval(
        int nbMs,
        struct timeval * nbMsTimeval)
{
    nbMsTimeval->tv_sec = nbMs / 1000;
    nbMsTimeval->tv_usec = (nbMs % 1000000) * 1000;
}

/**
 * Converts a struct timeval into a time interval in Ms.
 *
 * @param nbMsTimeval The structure timeval.
 *
 * @return The number of milliseconds.
 */
int
libjitsi_webrtc_aec_timevalToNbMs(
        struct timeval nbMsTimeval)
{
    return nbMsTimeval.tv_sec * 1000 + nbMsTimeval.tv_usec / 1000;
}

/**
 * Returns the number of sample necessary since the last process.
 *
 * @param length The number of sample already processed.
 * @param isRenderStream True to compute the number of sample for  the render
 * stream. False otherwise.
 *
 * @return The number of sample necessary since the last process.
 */
int
libjitsi_webrtc_aec_getNbSampleSinceLastProcess(
        int length,
        int isRenderStream)
{
    int nbSample = 0;
    if(timerisset(&aec->lastProcess[isRenderStream]))
    {
        struct timeval currentTime;
        gettimeofday(&currentTime, NULL);
        if(timercmp(&currentTime, &aec->lastProcess[isRenderStream], >))
        {
            struct timeval nbMsTimeval;

            timersub(
                    &currentTime,
                    &aec->lastProcess[isRenderStream],
                    &nbMsTimeval);

            int nbMs = libjitsi_webrtc_aec_timevalToNbMs(nbMsTimeval);
            nbSample = libjitsi_webrtc_aec_getNbSampleForMs(nbMs) - length;
            if(nbSample < 0)
            {
                nbSample = 0;
            }
        }
    }
    return nbSample;
}
