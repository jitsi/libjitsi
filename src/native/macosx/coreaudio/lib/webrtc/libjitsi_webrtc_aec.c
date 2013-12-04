/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#include "libjitsi_webrtc_aec.h"

#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/interface/module_common_types.h"


//#include <webrtc/modules/audio_processing/aec/include/echo_cancellation.h>

#include <errno.h>
#include <jni.h>
#include <math.h>
#include <stdio.h>

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
    AudioProcessing * audioProcessing;
} libjitsi_webrtc_aec;

static libjitsi_webrtc_aec * aec = NULL;

static JavaVM * libjitsi_webrtc_aec_VM = NULL;

int libjitsi_webrtc_aec_init(
        void);

void libjitsi_webrtc_aec_free(
        void);

void libjitsi_webrtc_aec_log(
        const char * error_format,
        ...);

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
    int id = 0;

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
        aec->dataLength[i] = 0;
        aec->dataUsed[i] = 0;
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
void libjitsi_webrtc_aec_free(
        void)
{
    if(aec != NULL)
    {
        if(aec->audioProcessing != NULL)
        {
            delete(aec->audioProcessing);
        }

        for(int i = 0; i < 2; ++i)
        {
            if(aec->data[i] != NULL)
            {
                free(aec->data[i]);
            }
        }

        free(aec);
    }
}

/**
 * Analyzes or processes a given stream to removes echo.
 *
 * @param isCaptureStream True if the given buffer comes from the capture
 * device. False if it comes from the render device.
 * @param data The buffer containing the stream data.
 * @param data_length The size of the valid data contained in the buffer.
 * @param sample_rate The sample rate used to get the given buffer.
 * @param nb_cahnnels The number of channels contained in the buffer: 1 = mono,
 * 2 = stereo, etc.
 *
 * @return 0 if everything works fine. -1 otherwise.
 */
int libjitsi_webrtc_aec_process(
        int isCaptureStream,
        int16_t * data,
        int data_length,
        int sample_rate,
        int nb_channels)
{
    int stream_index = 0;
    if(!isCaptureStream)
    {
        stream_index = 1;
    }

    int nb_shift = 0;

    while(nb_shift < data_length)
    {
        int nb_data =
            aec->dataLength[stream_index] - aec->dataUsed[stream_index];
        if(nb_data > data_length)
        {
            nb_data = data_length;
        }
        memcpy(aec->data[stream_index], data, nb_data);
        aec->dataUsed[stream_index] += nb_data;
        nb_shift += nb_data;

        if(aec->dataLength[stream_index] == aec->dataUsed[stream_index])
        {
            int err;
            AudioFrame * frame = new AudioFrame();

            frame->UpdateFrame(
                    -1, // id
                    -1, //j * nb_ms_sample_length, //timestamp
                    aec->data[stream_index],
                    aec->dataLength[stream_index] / nb_channels,
                    sample_rate,
                    AudioFrame::kNormalSpeech,
                    AudioFrame::kVadActive,
                    nb_channels);

            // Process capture stream.
            if(isCaptureStream)
            {
                if((err = aec->audioProcessing->ProcessStream(frame)) != 0)
                {
                    libjitsi_webrtc_aec_log(
                            "%s: 0x%x\n",
                        "libjitsi_webrtc_aec_process (libjitsi_webrtc_aec.c): \
                            \n\tAudioProcessing::ProccessStream",
                            err);
                    return -1;
                }
            }
            // Process render stream.
            // TODO: webrc can resample this render stream : need to set device
            // rate to  echo sampler.
            else
            {
                if((err = aec->audioProcessing->AnalyzeReverseStream(frame))
                        != 0)
                {
                    libjitsi_webrtc_aec_log(
                            "%s: 0x%x\n",
                        "libjitsi_webrtc_aec_process (libjitsi_webrtc_aec.c): \
                            \n\tAudioProcessing::AnalyzeReverseStream",
                            err);
                    return -1;
                }
            }


            aec->dataUsed[stream_index] = 0;
        }
    }


    return 0;
}

/**
 * Logs the corresponding error message.
 *
 * @param error_format The format of the error message.
 * @param ... The list of variable specified in the format argument.
 */
void libjitsi_webrtc_aec_log(
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

int libjitsi_webrtc_aec_initAudioProcessing(
        int sample_rate,
        int nb_capture_channels,
        int nb_render_channels
        )
{
    // TODO: deal with
    // - same input / ouput sample rate
    // - same input / ouput nb channels for capture stream
    int err;

    if((err = aec->audioProcessing->set_sample_rate_hz(sample_rate))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
                \n\tAudioProcessing::set_sample_rate_hz",
                err);
        return -1;
    }

    // Inits the capture and render buffer to default values
    for(int i = 0; i < 2; ++i)
    {
        if(aec->data[i] != NULL)
        {
            free(aec->data[i]);
        }
        aec->dataUsed[i] = 0;
        // capture
        if(i == 0)
        {
            aec->dataLength[i] = sample_rate / 100 * nb_capture_channels;
        }
        // render
        else
        {
            aec->dataLength[i] = sample_rate / 100 * nb_render_channels;
        }
        if((aec->data[i]
                    = (int16_t*) malloc(aec->dataLength[i] * sizeof(int16_t)))
                == NULL)
        {
            libjitsi_webrtc_aec_log(
                    "%s: %s\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
                        \n\tmalloc",
                    strerror(errno));
            return -1;
        }
    }

    // CAPTURE: Mono and stereo render only.
    if((err = aec->audioProcessing->set_num_channels(
                nb_capture_channels,
                nb_capture_channels))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
                \n\tAudioProcessing::set_num_channels",
                err);
        return -1;
    }

    // RENDER
    if((err = aec->audioProcessing->set_num_reverse_channels(
                    nb_render_channels))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
                \n\tAudioProcessing::set_num_reverse_channels",
                err);
        return -1;
    }





    // AEC
    /*if((err = audioProcessing->echo_cancellation()->set_device_sample_rate_hz(
                sample_rate))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
            \n\tAudioProcessing::echo_cancellation::set_device_sample_rate_hz",
                err);
        return -1;
    }*/
    aec->audioProcessing->echo_cancellation()->set_stream_drift_samples(100);
    if((err = aec->audioProcessing->echo_cancellation()
                ->enable_drift_compensation(true))
            != AudioProcessing::kNoError)
    {
        libjitsi_webrtc_aec_log(
                "%s: 0x%x\n",
            "libjitsi_webrtc_aec_initAudioProcessing (libjitsi_webrtc_aec.c): \
            \n\tAudioProcessing::echo_cancellation::enable_drift_compensation",
                err);
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
        return -1;
    }

    return  0;
}

