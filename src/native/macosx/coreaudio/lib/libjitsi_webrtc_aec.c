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
    int currentSide;
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

uint32_t
libjitsi_webrtc_aec_getNbMsSinceLastProcess(
        int isRenderStream,
        struct timeval currentTime);

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
        aec->currentSide = 1;
        aec->lastProcess[i].tv_sec  = 0;
        aec->lastProcess[i].tv_usec = 0;
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

    aec->currentSide = 1;

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

    // Inits the capture and render buffer to default values
    for(int i = 0; i < 2; ++i)
    {
        aec->dataUsed[i] = 0;
        aec->dataProcessed[i] = 0;
        aec->audioProcessingLength[i] = sample_rate / 100 * nb_channels;

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

    memcpy(&aec->format, &format, sizeof(AudioStreamBasicDescription));

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
 * @param isRenderStream True if the given buffer comes from the render
 * device. False if it comes from the capture device.
 * @param sample_rate The sample rate used to get the given buffer.
 * @param nb_channels The number of channels contained in the buffer: 1 = mono,
 * 2 = stereo, etc.
 *
 * @return 0 if everything works fine. -1 otherwise.
 */
int
libjitsi_webrtc_aec_process(
        int isRenderStream,
        int sample_rate,
        int nb_channels)
{
    int err;
    AudioFrame * frame = new AudioFrame();

    int init = 0;
    int start = aec->dataProcessed[isRenderStream];
    int end = start + aec->audioProcessingLength[isRenderStream];

    if(end <= aec->dataUsed[isRenderStream])
    {
        init = 1;

        frame->UpdateFrame(
                -1,
                0,
                aec->data[isRenderStream] + start,
                aec->audioProcessingLength[isRenderStream] / nb_channels,
                sample_rate,
                AudioFrame::kNormalSpeech,
                AudioFrame::kVadActive,
                nb_channels);

        struct timeval currentTime;
        gettimeofday(&currentTime, NULL);
        // For a capture stream: wait that a new render buffer has been
        // processed, limited to 10ms since the last capture buffer processed.
        // And vice versa for a render stream.
        while(aec->activeStreams[!isRenderStream] > 0
                && aec->currentSide == !isRenderStream
                && libjitsi_webrtc_aec_getNbMsSinceLastProcess(
                    isRenderStream,
                    currentTime) < 10)
        {
            usleep(1000);
            gettimeofday(&currentTime, NULL);
        }

        // Process capture stream.
        if(!isRenderStream)
        {
            int32_t nbMs
                = libjitsi_webrtc_aec_getNbMsSinceLastProcess(1, currentTime);
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
                aec->currentSide = 1;

                // If there is an echo detected, then copy the corrected data.
                if(aec->audioProcessing->echo_cancellation()->stream_has_echo())
                {
                    memcpy(
                            aec->data[isRenderStream] + start,
                            frame->data_,
                            aec->audioProcessingLength[isRenderStream]
                                * sizeof(int16_t));
                }
            }
        }
        // Process render stream.
        else
        {
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
                aec->currentSide = 0;
            }
        }
        aec->lastProcess[isRenderStream].tv_sec = currentTime.tv_sec;
        aec->lastProcess[isRenderStream].tv_usec = currentTime.tv_usec;

        start = end;
        end += aec->audioProcessingLength[isRenderStream];
    }
    aec->dataProcessed[isRenderStream] = start;

    delete(frame);

    if(init)
    {
        return start;
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
        jclass clazz = env->FindClass("org/jitsi/impl/neomedia/WebrtcAec");
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
 * Returns the number of ms since last process.
 *
 * @param isRenderStream True to get the number of ms since last process for the
 * render stream. False otherwise.
 * @param currentTime A timeval structure containing the current time. 
 *
 * @return the number of ms since last process.
 */
uint32_t
libjitsi_webrtc_aec_getNbMsSinceLastProcess(
        int isRenderStream,
        struct timeval currentTime)
{
    return (currentTime.tv_sec - aec->lastProcess[isRenderStream].tv_sec) * 1000
        + (currentTime.tv_usec - aec->lastProcess[isRenderStream].tv_usec) / 1000;
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
