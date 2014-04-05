/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#include "LibJitsi_WebRTC_AEC.h"

#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/interface/module_common_types.h"

#include <errno.h>
#include <math.h>
#include <pthread.h>
#include <stdio.h>
#include <sys/time.h>
#include <unistd.h>

/**
 * Functions to use Acoustic Echo Cancellation (AEC) with WebRTC.
 *
 * @author Vincent Lucas
 */

struct _LibJitsi_WebRTC_AEC
{
    // 0 = cqpture, 1 = render
    int16_t * data[2];
    int dataLength[2];
    int dataUsed[2];
    int dataProcessed[2];
    webrtc::AudioProcessing * audioProcessing;
    int audioProcessingLength;
    pthread_mutex_t mutex[2];
    struct timeval lastProcess[2];
    AudioStreamBasicDescription format;
};

int16_t *
LibJitsi_WebRTC_AEC_internalGetData(
        LibJitsi_WebRTC_AEC *aec,
        int isRenderStream,
        int length);

void LibJitsi_WebRTC_AEC_log(const char * error_format, ...);

int LibJitsi_WebRTC_AEC_getNbSampleForMs(LibJitsi_WebRTC_AEC *aec, int nbMS);

int LibJitsi_WebRTC_AEC_getNbMsForSample(
        LibJitsi_WebRTC_AEC *aec,
        int nbSample);

void LibJitsi_WebRTC_AEC_nbMsToTimeval(int nbMs, struct timeval * nbMsTimeval);

int LibJitsi_WebRTC_AEC_timevalToNbMs(struct timeval nbMsTimeval);

int
LibJitsi_WebRTC_AEC_getNbSampleSinceLastProcess(
        LibJitsi_WebRTC_AEC *aec,
        int length,
        int isRenderStream);

/**
 * Initiates a new webrtc_aec capable instance.
 *
 * @return 0 if everything works fine. -1 otherwise.
 */
LibJitsi_WebRTC_AEC *
LibJitsi_WebRTC_AEC_init()
{
    LibJitsi_WebRTC_AEC *aec;
    int err;

    // Starts the initialization.
    aec = (LibJitsi_WebRTC_AEC *) calloc(1, sizeof(LibJitsi_WebRTC_AEC));
    if(aec == NULL)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s (%s:%i): malloc %s\n",
                __func__, __FILE__, (int) __LINE__, strerror(errno));
        return NULL;
    }

    // Inits the capture and render buffer to default values
    for(int i = 0; i < 2; ++i)
    {
        if(pthread_mutex_init(&aec->mutex[i], NULL) != 0)
        {
            LibJitsi_WebRTC_AEC_log(
                    "%s (%s:%i): pthread_mutex_init %s\n",
                    __func__, __FILE__, (int) __LINE__, strerror(errno));
            LibJitsi_WebRTC_AEC_free(aec);
            return NULL;
        }
        timerclear(&aec->lastProcess[i]);
    }
    
    // Creates WebRTC AudioProcessing
    if((aec->audioProcessing = webrtc::AudioProcessing::Create()) == NULL)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s (%s:%i): webrtc::AudioProcessing::Create 0x%x\n",
                __func__, __FILE__, (int) __LINE__, NULL);
        LibJitsi_WebRTC_AEC_free(aec);
        return NULL;
    }

    // Enables high pass filter.
    if((err = aec->audioProcessing->high_pass_filter()->Enable(true))
            != webrtc::AudioProcessing::kNoError)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s (%s:%i): webrtc::AudioProcessing::high_pass_filter::Enable 0x%x\n",
                __func__, __FILE__, (int) __LINE__, err);
        LibJitsi_WebRTC_AEC_free(aec);
        return NULL;
    }

    return aec;
}

/**
 * Frees the webrtc_aec instance.
 */
void
LibJitsi_WebRTC_AEC_free(LibJitsi_WebRTC_AEC *aec)
{
    if(aec != NULL)
    {
        for(int i = 0; i < 2; ++i)
        {
            if(aec->data[i] != NULL)
            {
                aec->dataLength[i] = 0;
                aec->dataUsed[i] = 0;
                aec->dataProcessed[i] = 0;
                timerclear(&aec->lastProcess[i]);
                free(aec->data[i]);
                aec->data[i] = NULL;
                if(pthread_mutex_destroy(&aec->mutex[i]) != 0)
                {
                    LibJitsi_WebRTC_AEC_log(
                            "%s: %s\n",
                            "LibJitsi_WebRTC_AEC_free (LibJitsi_WebRTC_AEC.c): \
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
LibJitsi_WebRTC_AEC_start(LibJitsi_WebRTC_AEC *aec)
{
    for (int i = 0; i < 2; i++)
    {
        LibJitsi_WebRTC_AEC_lock(aec, i);

        timerclear(&aec->lastProcess[i]);
        aec->dataUsed[i] = 0;
        aec->dataProcessed[i] = 0;

        LibJitsi_WebRTC_AEC_unlock(aec, i);
    }
}

/**
 * Unregisters a stopping stream.
 *
 * @param isOutputStream True if the stopping stream is an output stream. False
 * for a capture stream.
 */
void
LibJitsi_WebRTC_AEC_stop(LibJitsi_WebRTC_AEC *aec)
{
    for (int i = 0; i < 2; i++)
    {
        LibJitsi_WebRTC_AEC_lock(aec, i);

        timerclear(&aec->lastProcess[i]);
        aec->dataUsed[i] = 0;
        aec->dataProcessed[i] = 0;

        LibJitsi_WebRTC_AEC_unlock(aec, i);
    }
}

/**
 * Initializes the AEC process to corresponds to the capture specification.
 *
 * @param sample_rate The sample rate used by the capture device.
 * @param nb_channels The number of channels used by the capture device.
 * @param format The stream format description corresponding to the input
 * stream.
 *
 * @return 0 if everything works fine. -1 otherwise.
 */
int
LibJitsi_WebRTC_AEC_initAudioProcessing(
        LibJitsi_WebRTC_AEC *aec,
        int sample_rate,
        int nb_channels,
        AudioStreamBasicDescription format)
{
    LibJitsi_WebRTC_AEC_lock(aec, 0);
    LibJitsi_WebRTC_AEC_lock(aec, 1);

    int err;

    if((err = aec->audioProcessing->set_sample_rate_hz(sample_rate))
            != webrtc::AudioProcessing::kNoError)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s: 0x%x\n",
            "LibJitsi_WebRTC_AEC_initAudioProcessing (LibJitsi_WebRTC_AEC.c): \
                \n\tAudioProcessing::set_sample_rate_hz",
                err);
        LibJitsi_WebRTC_AEC_unlock(aec, 0);
        LibJitsi_WebRTC_AEC_unlock(aec, 1);
        return -1;
    }

    memcpy(&aec->format, &format, sizeof(AudioStreamBasicDescription));

    // Inits the capture and render buffer to default values
    aec->audioProcessingLength = LibJitsi_WebRTC_AEC_getNbSampleForMs(aec, 10);
    for(int i = 0; i < 2; ++i)
    {
        timerclear(&aec->lastProcess[i]);
        aec->dataUsed[i] = 0;
        aec->dataProcessed[i] = 0;

        if(LibJitsi_WebRTC_AEC_internalGetData(aec, i, aec->audioProcessingLength)
                == NULL)
        {
            LibJitsi_WebRTC_AEC_log(
                    "%s\n",
            "LibJitsi_WebRTC_AEC_initAudioProcessing (LibJitsi_WebRTC_AEC.c): \
                    \n\tLibJitsi_WebRTC_AEC_resizeDataBuffer");
            LibJitsi_WebRTC_AEC_unlock(aec, 0);
            LibJitsi_WebRTC_AEC_unlock(aec, 1);
            return -1;
        }
    }

    // CAPTURE: Mono and stereo render only.
    if((err = aec->audioProcessing->set_num_channels(nb_channels, nb_channels))
            != webrtc::AudioProcessing::kNoError)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s: 0x%x\n",
            "LibJitsi_WebRTC_AEC_initAudioProcessing (LibJitsi_WebRTC_AEC.c): \
                \n\tAudioProcessing::set_num_channels",
                err);
        LibJitsi_WebRTC_AEC_unlock(aec, 0);
        LibJitsi_WebRTC_AEC_unlock(aec, 1);
        return -1;
    }

    // RENDER
    if((err = aec->audioProcessing->set_num_reverse_channels(nb_channels))
            != webrtc::AudioProcessing::kNoError)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s: 0x%x\n",
            "LibJitsi_WebRTC_AEC_initAudioProcessing (LibJitsi_WebRTC_AEC.c): \
                \n\tAudioProcessing::set_num_reverse_channels",
                err);
        LibJitsi_WebRTC_AEC_unlock(aec, 0);
        LibJitsi_WebRTC_AEC_unlock(aec, 1);
        return -1;
    }

    // AEC
    if((err = aec->audioProcessing->echo_cancellation()
                ->set_device_sample_rate_hz(sample_rate))
            != webrtc::AudioProcessing::kNoError)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s: 0x%x\n",
            "LibJitsi_WebRTC_AEC_initAudioProcessing (LibJitsi_WebRTC_AEC.c): \
            \n\tAudioProcessing::echo_cancellation::set_device_sample_rate_hz",
                err);
        LibJitsi_WebRTC_AEC_unlock(aec, 0);
        LibJitsi_WebRTC_AEC_unlock(aec, 1);
        return -1;
    }
    if((err = aec->audioProcessing->echo_cancellation()
                ->set_suppression_level(webrtc::EchoCancellation::kHighSuppression))
            != webrtc::AudioProcessing::kNoError)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s: 0x%x\n",
            "LibJitsi_WebRTC_AEC_initAudioProcessing (LibJitsi_WebRTC_AEC.c): \
            \n\tAudioProcessing::echo_cancellation::set_suppression_level",
                err);
        LibJitsi_WebRTC_AEC_unlock(aec, 0);
        LibJitsi_WebRTC_AEC_unlock(aec, 1);
        return -1;
    }
    if((err = aec->audioProcessing->echo_cancellation()->Enable(true))
            != webrtc::AudioProcessing::kNoError)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s: 0x%x\n",
            "LibJitsi_WebRTC_AEC_initAudioProcessing (LibJitsi_WebRTC_AEC.c): \
            \n\tAudioProcessing::echo_cancellation::Enable",
                err);
        LibJitsi_WebRTC_AEC_unlock(aec, 0);
        LibJitsi_WebRTC_AEC_unlock(aec, 1);
        return -1;
    }

    LibJitsi_WebRTC_AEC_unlock(aec, 0);
    LibJitsi_WebRTC_AEC_unlock(aec, 1);

    return  0;
}

/**
 * Analyzes or processes a given stream to remove echo.
 *
 * @return 0 if everything works fine. -1 otherwise.
 */
int
LibJitsi_WebRTC_AEC_process(LibJitsi_WebRTC_AEC *aec)
{
    int err;
    int nb_channels = aec->format.mChannelsPerFrame;
    int sample_rate = aec->format.mSampleRate;
    webrtc::AudioFrame * frame = new webrtc::AudioFrame();

    int start_capture = aec->dataProcessed[0];
    int end_capture = start_capture + aec->audioProcessingLength;
    int start_render = aec->dataProcessed[1];
    int end_render = start_render + aec->audioProcessingLength;
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
                    aec->audioProcessingLength / nb_channels,
                    sample_rate,
                    webrtc::AudioFrame::kNormalSpeech,
                    webrtc::AudioFrame::kVadActive,
                    nb_channels);

            // Process render buffer.
            if((err = aec->audioProcessing->AnalyzeReverseStream(frame)) != 0)
            {
                LibJitsi_WebRTC_AEC_log(
                        "%s: 0x%x\n",
                        "LibJitsi_WebRTC_AEC_process (LibJitsi_WebRTC_AEC.c): \
                            \n\tAudioProcessing::AnalyzeReverseStream",
                        err);
            }

            // Process capture stream.
            frame->UpdateFrame(
                    -1,
                    0,
                    aec->data[0] + start_capture,
                    aec->audioProcessingLength / nb_channels,
                    sample_rate,
                    webrtc::AudioFrame::kNormalSpeech,
                    webrtc::AudioFrame::kVadActive,
                    nb_channels);

            // Definition from WebRTC library for
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
            int32_t nbMs = LibJitsi_WebRTC_AEC_timevalToNbMs(delay);
            if(nbMs < 0)
            {
                nbMs = 0;
            }
            else if(nbMs > 500)
            {
                nbMs = 500;
            }
            if((err = aec->audioProcessing->set_stream_delay_ms(nbMs))
                    != webrtc::AudioProcessing::kNoError)
            {
                LibJitsi_WebRTC_AEC_log(
                        "%s: 0x%x\n",
                        "LibJitsi_WebRTC_AEC_process (LibJitsi_WebRTC_AEC.c): \
                        \n\tAudioProcessing::set_stream_delay_ms",
                        err);
            }

            // Process capture buffer.
            if((err = aec->audioProcessing->ProcessStream(frame)) != 0)
            {
                LibJitsi_WebRTC_AEC_log(
                        "%s: 0x%x\n",
                        "LibJitsi_WebRTC_AEC_process (LibJitsi_WebRTC_AEC.c): \
                            \n\tAudioProcessing::ProccessStream",
                        err);
            }
            // If there is an echo detected, then copy the corrected data.
            if(aec->audioProcessing->echo_cancellation()->stream_has_echo())
            {
                memcpy(
                        aec->data[0] + start_capture,
                        frame->data_,
                        aec->audioProcessingLength * sizeof(int16_t));
            }

            start_render = end_render;
            end_render += aec->audioProcessingLength;
        }
        start_capture = end_capture;
        end_capture += aec->audioProcessingLength;
    }
    aec->dataProcessed[0] = start_capture;
    aec->dataProcessed[1] = start_render;

    delete(frame);

    return init ? start_capture : 0;
}

/**
 * Once the stream has been played via the device, or pushed into the Java part,
 * then we can shift the buffer and remove all previously computed data.
 *
 * @param isRenderStream True for the render stream. False otherwise.
 */
void
LibJitsi_WebRTC_AEC_completeProcess(
        LibJitsi_WebRTC_AEC *aec,
        int isRenderStream)
{
    if(aec->data[isRenderStream] != NULL)
    {
        int nbLeft
            = aec->dataUsed[isRenderStream]
                - aec->dataProcessed[isRenderStream];
        if(nbLeft)
        {
            memcpy(
                    aec->data[isRenderStream],
                    aec->data[isRenderStream]
                        + aec->dataProcessed[isRenderStream],
                    nbLeft * sizeof(int16_t));

            int nbMs
                = LibJitsi_WebRTC_AEC_getNbMsForSample(
                        aec,
                        aec->dataProcessed[isRenderStream]);
            struct timeval nbMsTimeval;
            LibJitsi_WebRTC_AEC_nbMsToTimeval(nbMs, &nbMsTimeval);
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
 * @param length The available free space requested by the caller.
 *
 * @return A pointer to the data of the stream. NULL if failed to allocate the
 * requested free space.
 */
int16_t *
LibJitsi_WebRTC_AEC_getData(
        LibJitsi_WebRTC_AEC *aec,
        int isRenderStream,
        int length)
{
    int16_t * data = NULL;

    if((data = LibJitsi_WebRTC_AEC_internalGetData(aec, isRenderStream, length))
            == NULL)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s\n",
                "LibJitsi_WebRTC_AEC_getData (LibJitsi_WebRTC_AEC.c): \
                \n\tLibJitsi_WebRTC_AEC_internalGetData");
        return NULL;
    }
    aec->dataUsed[isRenderStream] += length;

    if(!timerisset(&aec->lastProcess[isRenderStream]))
    {
        gettimeofday(&aec->lastProcess[isRenderStream], NULL);
        if(!isRenderStream)
        {
            int nbMs = LibJitsi_WebRTC_AEC_getNbMsForSample(aec, length);
            struct timeval nbMsTimeval;
            LibJitsi_WebRTC_AEC_nbMsToTimeval(nbMs, &nbMsTimeval);
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
 * @param length The available free space requested by the caller.
 *
 * @return A pointer to the data of the stream. NULL if failed to allocate the
 * requested free space.
 */
int16_t *
LibJitsi_WebRTC_AEC_internalGetData(
        LibJitsi_WebRTC_AEC *aec,
        int isRenderStream,
        int length)
{
    int tmpLength;

    // When AEC is activated and no input stream is active, then only allocate a
    // buffer for the current block to process and squeeze previous data.
/*
    if(isRenderStream && aec->activeStreams[0] == 0)
    {
        tmpLength = 0;

        aec->dataUsed[isRenderStream] = 0;
        timerclear(&aec->lastProcess[isRenderStream]);
    }
    else
*/
        tmpLength = length;

    int nbSample
        = LibJitsi_WebRTC_AEC_getNbSampleSinceLastProcess(
                aec,
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
            LibJitsi_WebRTC_AEC_log(
                    "%s: %s\n",
                "LibJitsi_WebRTC_AEC_internalGetData (LibJitsi_WebRTC_AEC.c): \
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
LibJitsi_WebRTC_AEC_getProcessedData(LibJitsi_WebRTC_AEC *aec)
{
    return aec->data[0];
}

/**
 * Logs the corresponding error message.
 *
 * @param format The format of the error message.
 * @param ... The list of variable specified in the format argument.
 */
void
LibJitsi_WebRTC_AEC_log(const char * format, ...)
{
    va_list args;

    va_start(args, format);
    vfprintf(stderr, format, args);
    va_end(args);
}

/**
 * Copies the AEC format (defined by the capture stream) to the format structure
 * provided.
 *
 * @param format The AEC format to be filled in, only if the capture stream is
 * active.
 *
 * @return 1 if the format has been updated. 0 otherwise.
 */
int
LibJitsi_WebRTC_AEC_getCaptureFormat(
        LibJitsi_WebRTC_AEC *aec,
        AudioStreamBasicDescription *format)
{
    memcpy(format, &aec->format, sizeof(AudioStreamBasicDescription));
    return 1;
}

/**
 * Locks the mutex for the capture/render stream.
 *
 * @param isRenderStream True to lock the render stream. False otherwise.
 *
 * @return 0 if everything is OK. Otherwise any other value.
 */
int
LibJitsi_WebRTC_AEC_lock(LibJitsi_WebRTC_AEC *aec, int isRenderStream)
{
    int err;
    if((err = pthread_mutex_lock(&aec->mutex[isRenderStream])) != 0)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s: %s\n",
                "LibJitsi_WebRTC_AEC_lock (LibJitsi_WebRTC_AEC.c): \
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
 * @return 0 if everything is OK. Otherwise any other value.
 */
int
LibJitsi_WebRTC_AEC_unlock(LibJitsi_WebRTC_AEC *aec, int isRenderStream)
{
    int err;
    if((err = pthread_mutex_unlock(&aec->mutex[isRenderStream])) != 0)
    {
        LibJitsi_WebRTC_AEC_log(
                "%s: %s\n",
                "LibJitsi_WebRTC_AEC_unlock (LibJitsi_WebRTC_AEC.c): \
                \n\tpthread_mutex_unlock",
                strerror(errno));
    }
    return err;
}

/**
 * Returns the number of sample necessary for a given time interval.
 *
 * @param nbMs The number of milliseconds required.
 *
 * @return The number of sample necessary for a given time interval.
 */
int
LibJitsi_WebRTC_AEC_getNbSampleForMs(LibJitsi_WebRTC_AEC *aec, int nbMs)
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
LibJitsi_WebRTC_AEC_getNbMsForSample(LibJitsi_WebRTC_AEC *aec, int nbSample)
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
LibJitsi_WebRTC_AEC_nbMsToTimeval(int nbMs, struct timeval * nbMsTimeval)
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
LibJitsi_WebRTC_AEC_timevalToNbMs(struct timeval nbMsTimeval)
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
LibJitsi_WebRTC_AEC_getNbSampleSinceLastProcess(
        LibJitsi_WebRTC_AEC *aec,
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

            int nbMs = LibJitsi_WebRTC_AEC_timevalToNbMs(nbMsTimeval);
            nbSample = LibJitsi_WebRTC_AEC_getNbSampleForMs(aec, nbMs) - length;
            if(nbSample < 0)
                nbSample = 0;
        }
    }
    return nbSample;
}
