/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_portaudio_Pa.h"

#include "AudioQualityImprovement.h"
#include "ConditionVariable.h"
#include "Mutex.h"

#include <portaudio.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>

#ifdef _WIN32
    #include "WMME_DSound.h"
#endif /* #ifdef _WIN32 */

typedef struct
{
    AudioQualityImprovement *audioQualityImprovement;
    int channels;
    JNIEnv *env;
    jboolean finished;

    /**
     * The value specified as the <tt>framesPerBuffer</tt> argument to the
     * <tt>Pa_OpenStream</tt> function call which has opened #stream.
     */
    unsigned long framesPerBuffer;
    void *input;
    size_t inputCapacity;
    ConditionVariable *inputCondVar;
    long inputFrameSize;

    /** The input latency of #stream. */
    jlong inputLatency;
    size_t inputLength;
    Mutex *inputMutex;
    Mutex *mutex;
    void *output;
    size_t outputCapacity;
    ConditionVariable *outputCondVar;
    long outputFrameSize;

    /** The output latency of #stream. */
    jlong outputLatency;
    size_t outputLength;
    Mutex *outputMutex;

    /**
     * The indicator which determines whether this <tt>PortAudioStream</tt>
     * implements the blocking stream interface on top of the non-blocking
     * stream interface.
     */
    jboolean pseudoBlocking;
    jlong retainCount;
    double sampleRate;
    int sampleSizeInBits;
    PaStream *stream;
    jobject streamCallback;
    jmethodID streamCallbackMethodID;
    jmethodID streamFinishedCallbackMethodID;
    JavaVM *vm;
} PortAudioStream;

static void PortAudio_devicesChangedCallback(void *userData);
static PaStreamParameters *PortAudio_fixInputParametersSuggestedLatency
    (PaStreamParameters *inputParameters,
    jdouble sampleRate, jlong framesPerBuffer,
    PaHostApiTypeId hostApiType);
static PaStreamParameters *PortAudio_fixOutputParametersSuggestedLatency
    (PaStreamParameters *outputParameters,
    jdouble sampleRate, jlong framesPerBuffer,
    PaHostApiTypeId hostApiType);
static PaStreamParameters *PortAudio_fixStreamParametersSuggestedLatency
    (PaStreamParameters *streamParameters,
    jdouble sampleRate, jlong framesPerBuffer,
    PaHostApiTypeId hostApiType);
static long PortAudio_getFrameSize(PaStreamParameters *streamParameters);
static unsigned long PortAudio_getSampleSizeInBits
    (PaStreamParameters *streamParameters);

/**
 * Gets a new <tt>jbyteArray</tt> instance which is initialized with the bytes
 * of a specific C string i.e. <tt>const char *</tt>.
 *
 * @param env
 * @param str the bytes/C string to initialize the new <tt>jbyteArray</tt>
 * instance with
 * @return a new <tt>jbyteArray</tt> instance which is initialized with the
 * bytes of the specified <tt>str</tt>
 */
static jbyteArray PortAudio_getStrBytes(JNIEnv *env, const char *str);
static void PortAudio_throwException(JNIEnv *env, PaError errorCode);

/**
 * Allocates (and initializes) the memory and its associated variables for a
 * specific buffer to be used by the pseudo-blocking stream interface
 * implementation of a <tt>PortAudioStream</tt>.
 *
 * @param capacity the number of bytes to be allocated to the buffer
 * @param bufferPtr a pointer which specifies where the location of the
 * allocated buffer is to be stored
 * @param bufferLengthPtr a pointer which specifies where the initial length
 * (i.e. zero) is to be stored
 * @param bufferCapacityPtr a pointer which specifies where the capacity of the
 * allocated buffer is to be stored
 * @param bufferMutexPtr a pointer which specifies where the <tt>Mute</tt> to
 * synchronize the access to the allocated buffer is to be stored
 * @param bufferCondVarPtr a pointer which specifies where the
 * <tt>ConditionVariable</tt> to synchronize the access to the allocated buffer
 * is to be stored
 * @return the location of the allocated buffer upon success; otherwise,
 * <tt>NULL</tt>
 */
static void *PortAudioStream_allocPseudoBlockingBuffer
    (size_t capacity,
    void **bufferPtr, size_t *bufferLengthPtr, size_t *bufferCapacityPtr,
    Mutex **bufferMutexPtr, ConditionVariable **bufferCondVarPtr);
static void PortAudioStream_free(JNIEnv *env, PortAudioStream *stream);
static int PortAudioStream_javaCallback
    (const void *input,
    void *output,
    unsigned long frameCount,
    const PaStreamCallbackTimeInfo *timeInfo,
    PaStreamCallbackFlags statusFlags,
    void *userData);
static void PortAudioStream_javaFinishedCallback(void *userData);
static PortAudioStream * PortAudioStream_new
    (JNIEnv *env, jobject streamCallback);
static void PortAudioStream_popFromPseudoBlockingBuffer
    (void *buffer, size_t length, size_t *bufferLengthPtr);
static int PortAudioStream_pseudoBlockingCallback
    (const void *input,
    void *output,
    unsigned long frameCount,
    const PaStreamCallbackTimeInfo *timeInfo,
    PaStreamCallbackFlags statusFlags,
    void *userData);
static void PortAudioStream_pseudoBlockingFinishedCallback(void *userData);
static void PortAudioStream_release(PortAudioStream *stream);
static void PortAudioStream_retain(PortAudioStream *stream);

static const char *AUDIO_QUALITY_IMPROVEMENT_STRING_ID = "portaudio";
#define LATENCY_HIGH org_jitsi_impl_neomedia_portaudio_Pa_LATENCY_HIGH
#define LATENCY_LOW org_jitsi_impl_neomedia_portaudio_Pa_LATENCY_LOW
#define LATENCY_UNSPECIFIED org_jitsi_impl_neomedia_portaudio_Pa_LATENCY_UNSPECIFIED

static jclass PortAudio_devicesChangedCallbackClass = 0;
static jmethodID PortAudio_devicesChangedCallbackMethodID = 0;
static JavaVM* PortAudio_vm = 0;

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_AbortStream
    (JNIEnv *env, jclass clazz, jlong stream)
{
    PaError err
        = Pa_AbortStream(((PortAudioStream *) (intptr_t) stream)->stream);

    if (paNoError != err)
        PortAudio_throwException(env, err);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_CloseStream
    (JNIEnv *env, jclass clazz, jlong stream)
{
    PortAudioStream *s = (PortAudioStream *) (intptr_t) stream;
    PaError err = Pa_CloseStream(s->stream);

    if (paNoError != err)
        PortAudio_throwException(env, err);
    else if (s->pseudoBlocking)
        PortAudioStream_release(s);
    else
        PortAudioStream_free(env, s);
}

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getDefaultHighInputLatency
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    return ((PaDeviceInfo *) (intptr_t) deviceInfo)->defaultHighInputLatency;
}

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getDefaultHighOutputLatency
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    return ((PaDeviceInfo *) (intptr_t) deviceInfo)->defaultHighOutputLatency;
}

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getDefaultLowInputLatency
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    return ((PaDeviceInfo *) (intptr_t) deviceInfo)->defaultLowInputLatency;
}

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getDefaultLowOutputLatency
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    return ((PaDeviceInfo *) (intptr_t) deviceInfo)->defaultLowOutputLatency;
}

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getDefaultSampleRate
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    return ((PaDeviceInfo *) (intptr_t) deviceInfo)->defaultSampleRate;
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getDeviceUIDBytes
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    PaDeviceInfo *di = (PaDeviceInfo *) (intptr_t) deviceInfo;

    return
        (di->structVersion >= 3)
            ? PortAudio_getStrBytes(env, di->deviceUID)
            : NULL;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getHostApi
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    return ((PaDeviceInfo *) (intptr_t) deviceInfo)->hostApi;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getMaxInputChannels
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    return ((PaDeviceInfo *) (intptr_t) deviceInfo)->maxInputChannels;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getMaxOutputChannels
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    return ((PaDeviceInfo *) (intptr_t) deviceInfo)->maxOutputChannels;
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getNameBytes
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    PaDeviceInfo *di = (PaDeviceInfo *) (intptr_t) deviceInfo;
    const char *name;

#ifdef _WIN32
    const PaHostApiInfo *hai = Pa_GetHostApiInfo(di->hostApi);

    if (hai && (paMME == hai->type))
    {
        name = WMME_DSound_DeviceInfo_getName(di);
        if (!name)
            name = di->name;
    }
    else
        name = di->name;
#else /* #ifdef _WIN32 */
    name = di->name;
#endif /* #ifdef _WIN32 */
    return PortAudio_getStrBytes(env, name);
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_DeviceInfo_1getTransportTypeBytes
    (JNIEnv *env, jclass clazz, jlong deviceInfo)
{
    PaDeviceInfo *di = (PaDeviceInfo *) (intptr_t) deviceInfo;

    return
        (di->structVersion >= 3)
            ? PortAudio_getStrBytes(env, di->transportType)
            : NULL;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_free
    (JNIEnv *env, jclass clazz, jlong ptr)
{
    free((void *) (intptr_t) ptr);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_GetDefaultInputDevice
    (JNIEnv *env, jclass clazz)
{
    return Pa_GetDefaultInputDevice();
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_GetDefaultOutputDevice
    (JNIEnv *env, jclass clazz)
{
    return Pa_GetDefaultOutputDevice();
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_GetDeviceCount
    (JNIEnv *env, jclass clazz)
{
    PaDeviceIndex deviceCount = Pa_GetDeviceCount();

    if (deviceCount < 0)
        PortAudio_throwException(env, deviceCount);
    return deviceCount;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_GetDeviceInfo
    (JNIEnv *env, jclass clazz, jint deviceIndex)
{
    return (jlong) (intptr_t) Pa_GetDeviceInfo(deviceIndex);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_GetHostApiInfo
    (JNIEnv *env , jclass clazz, jint hostApiIndex)
{
    return (jlong) (intptr_t) Pa_GetHostApiInfo(hostApiIndex);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_GetSampleSize
  (JNIEnv *env, jclass clazz, jlong format)
{
    return Pa_GetSampleSize(format);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_GetStreamReadAvailable
    (JNIEnv *env, jclass clazz, jlong stream)
{
    return
        Pa_GetStreamReadAvailable(
            ((PortAudioStream *) (intptr_t) stream)->stream);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_GetStreamWriteAvailable
    (JNIEnv *env, jclass clazz, jlong stream)
{
    return
        Pa_GetStreamWriteAvailable(
            ((PortAudioStream *) (intptr_t) stream)->stream);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_HostApiInfo_1getDefaultInputDevice
    (JNIEnv *env, jclass clazz, jlong hostApi)
{
    return ((PaHostApiInfo *) (intptr_t) hostApi)->defaultInputDevice;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_HostApiInfo_1getDefaultOutputDevice
    (JNIEnv *env, jclass clazz, jlong hostApi)
{
    return ((PaHostApiInfo *) (intptr_t) hostApi)->defaultOutputDevice;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_HostApiInfo_1getDeviceCount
    (JNIEnv *env, jclass clazz, jlong hostApi)
{
    return ((PaHostApiInfo *) (intptr_t) hostApi)->deviceCount;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_HostApiInfo_1getType
    (JNIEnv *env, jclass clazz, jlong hostApi)
{
    return ((PaHostApiInfo *) (intptr_t) hostApi)->type;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_Initialize
    (JNIEnv *env, jclass clazz)
{
    PaError err = Pa_Initialize();

    if (paNoError == err)
    {
        jclass devicesChangedCallbackClass
            = (*env)->FindClass(env, "org/jitsi/impl/neomedia/portaudio/Pa");

        if (devicesChangedCallbackClass)
        {
            devicesChangedCallbackClass
                = (*env)->NewGlobalRef(env, devicesChangedCallbackClass);
            if (devicesChangedCallbackClass)
            {
                jmethodID devicesChangedCallbackMethodID
                    = (*env)->GetStaticMethodID(
                            env,
                            devicesChangedCallbackClass,
                            "devicesChangedCallback",
                            "()V");

                if (devicesChangedCallbackMethodID)
                {
                    PortAudio_devicesChangedCallbackClass
                        = devicesChangedCallbackClass;
                    PortAudio_devicesChangedCallbackMethodID
                        = devicesChangedCallbackMethodID;
                    Pa_SetDevicesChangedCallback(
                            NULL,
                            PortAudio_devicesChangedCallback);
                }
            }
        }
    }
    else
        PortAudio_throwException(env, err);
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_IsFormatSupported
    (JNIEnv *env, jclass clazz,
    jlong inputParameters, jlong outputParameters, jdouble sampleRate)
{
    if (Pa_IsFormatSupported(
                (PaStreamParameters *) (intptr_t) inputParameters,
                (PaStreamParameters *) (intptr_t) outputParameters,
                sampleRate)
            == paFormatIsSupported)
        return JNI_TRUE;
    else
        return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_OpenStream
    (JNIEnv *env, jclass clazz,
    jlong inputParameters, jlong outputParameters,
    jdouble sampleRate,
    jlong framesPerBuffer,
    jlong streamFlags,
    jobject streamCallback)
{
    PortAudioStream *s = PortAudioStream_new(env, streamCallback);
    PaStreamCallback *effectiveStreamCallback;
    PaStreamFinishedCallback *effectiveStreamFinishedCallback;
    unsigned long effectiveFramesPerBuffer = framesPerBuffer;
    PaHostApiTypeId hostApiType = paInDevelopment;
    PaError err;
    PaStreamParameters *inputStreamParameters
        = (PaStreamParameters *) (intptr_t) inputParameters;
    PaStreamParameters *outputStreamParameters
        = (PaStreamParameters *) (intptr_t) outputParameters;

    if (!s)
        return 0;

    if (streamCallback)
    {
        effectiveStreamCallback = PortAudioStream_javaCallback;
        effectiveStreamFinishedCallback = PortAudioStream_javaFinishedCallback;
        s->pseudoBlocking = JNI_FALSE;
    }
    else
    {
        /*
         * Some host APIs such as DirectSound don't really implement the
         * blocking stream interface. If we're to ever be able to try them out,
         * we'll have to implement the blocking stream interface on top of the
         * non-blocking stream interface.
         */

        effectiveStreamCallback = NULL;
        effectiveStreamFinishedCallback = NULL;
        s->pseudoBlocking = JNI_FALSE;

        /*
         * TODO It should be possible to implement the blocking stream interface
         * without a specific framesPerBuffer.
         */
        if ((paFramesPerBufferUnspecified != framesPerBuffer)
                && (framesPerBuffer > 0))
        {
            PaDeviceIndex device;

            if (outputStreamParameters)
                device = outputStreamParameters->device;
            else if (inputStreamParameters)
                device = inputStreamParameters->device;
            else
                device = paNoDevice;
            if (device != paNoDevice)
            {
                const PaDeviceInfo *deviceInfo = Pa_GetDeviceInfo(device);

                if (deviceInfo)
                {
                    const PaHostApiInfo *hostApiInfo
                        = Pa_GetHostApiInfo(deviceInfo->hostApi);

                    if (hostApiInfo)
                    {
                        switch (hostApiInfo->type)
                        {
                        case paCoreAudio:
                            /*
                             * If we are to ever succeed in requesting a higher
                             * latency in
                             * PortAudio_fixOutputParametersSuggestedLatency, we
                             * have to specify paFramesPerBufferUnspecified.
                             * Otherwise, the CoreAudio implementation of
                             * PortAudio will ignore our suggestedLatency.
                             */
                            if (outputStreamParameters
                                    && ((LATENCY_HIGH
                                            == outputStreamParameters
                                                ->suggestedLatency)
                                        || (LATENCY_UNSPECIFIED
                                            == outputStreamParameters
                                                ->suggestedLatency)))
                            {
                                effectiveFramesPerBuffer
                                    = paFramesPerBufferUnspecified;
                                hostApiType = hostApiInfo->type;
                            }
                            if (inputStreamParameters
                                    && ((LATENCY_HIGH
                                            == inputStreamParameters
                                                ->suggestedLatency)
                                        || (LATENCY_UNSPECIFIED
                                            == inputStreamParameters
                                                ->suggestedLatency)))
                            {
                                effectiveFramesPerBuffer
                                    = paFramesPerBufferUnspecified;
                                hostApiType = hostApiInfo->type;
                            }
                            break;
                        case paDirectSound:
                            effectiveStreamCallback
                                = PortAudioStream_pseudoBlockingCallback;
                            effectiveStreamFinishedCallback
                                = PortAudioStream_pseudoBlockingFinishedCallback;
                            s->pseudoBlocking = JNI_TRUE;
                            break;
                        default:
                            break;
                        }
                    }
                }
            }
        }
    }

    if (JNI_TRUE == s->pseudoBlocking)
    {
        s->mutex = Mutex_new(NULL);
        err = (s->mutex) ? paNoError : paInsufficientMemory;
    }
    else
        err = paNoError;

    if (paNoError == err)
    {
        err
            = Pa_OpenStream(
                &(s->stream),
                PortAudio_fixInputParametersSuggestedLatency(
                    inputStreamParameters,
                    sampleRate, framesPerBuffer,
                    hostApiType),
                PortAudio_fixOutputParametersSuggestedLatency(
                    outputStreamParameters,
                    sampleRate, framesPerBuffer,
                    hostApiType),
                sampleRate,
                effectiveFramesPerBuffer,
                streamFlags,
                effectiveStreamCallback,
                s);
    }

    if (paNoError == err)
    {
        s->framesPerBuffer = effectiveFramesPerBuffer;
        s->inputFrameSize = PortAudio_getFrameSize(inputStreamParameters);
        s->outputFrameSize = PortAudio_getFrameSize(outputStreamParameters);
        s->sampleRate = sampleRate;

        if (effectiveStreamFinishedCallback)
        {
            err
                = Pa_SetStreamFinishedCallback(
                    s->stream,
                    effectiveStreamFinishedCallback);
        }

        s->audioQualityImprovement
            = AudioQualityImprovement_getSharedInstance(
                AUDIO_QUALITY_IMPROVEMENT_STRING_ID,
                0);
        if (inputStreamParameters)
        {
            s->sampleSizeInBits
                = PortAudio_getSampleSizeInBits(inputStreamParameters);
            s->channels = inputStreamParameters->channelCount;

            /*
             * Prepare whatever is necessary for the pseudo-blocking stream
             * interface implementation. For example, allocate its memory early
             * because doing it in the stream callback may introduce latency.
             */
            if (s->pseudoBlocking
                    && !PortAudioStream_allocPseudoBlockingBuffer(
                            2 * framesPerBuffer * (s->inputFrameSize),
                            &(s->input),
                            &(s->inputLength),
                            &(s->inputCapacity),
                            &(s->inputMutex),
                            &(s->inputCondVar)))
            {
                Java_org_jitsi_impl_neomedia_portaudio_Pa_CloseStream(
                    env, clazz,
                    (jlong) (intptr_t) s);
                if (JNI_FALSE == (*env)->ExceptionCheck(env))
                {
                    PortAudio_throwException(env, paInsufficientMemory);
                    return 0;
                }
            }

            if (s->audioQualityImprovement)
            {
                AudioQualityImprovement_setSampleRate(
                    s->audioQualityImprovement,
                    (int) sampleRate);

                if (s->pseudoBlocking)
                {
                    const PaStreamInfo *streamInfo;

                    streamInfo = Pa_GetStreamInfo(s->stream);
                    if (streamInfo)
                    {
                        s->inputLatency
                                = (jlong) (streamInfo->inputLatency * 1000);
                    }
                }
            }
        }
        if (outputStreamParameters)
        {
            s->sampleSizeInBits
                = PortAudio_getSampleSizeInBits(outputStreamParameters);
            s->channels = outputStreamParameters->channelCount;

            if (s->pseudoBlocking
                    && !PortAudioStream_allocPseudoBlockingBuffer(
                            2 * framesPerBuffer * (s->outputFrameSize),
                            &(s->output),
                            &(s->outputLength),
                            &(s->outputCapacity),
                            &(s->outputMutex),
                            &(s->outputCondVar)))
            {
                Java_org_jitsi_impl_neomedia_portaudio_Pa_CloseStream(
                    env, clazz,
                    (jlong) (intptr_t) s);
                if (JNI_FALSE == (*env)->ExceptionCheck(env))
                {
                    PortAudio_throwException(env, paInsufficientMemory);
                    return 0;
                }
            }

            if (s->audioQualityImprovement)
            {
                const PaStreamInfo *streamInfo;

                streamInfo = Pa_GetStreamInfo(s->stream);
                if (streamInfo)
                {
                    s->outputLatency
                            = (jlong) (streamInfo->outputLatency * 1000);
                }
            }
        }

        if (s->pseudoBlocking)
            PortAudioStream_retain(s);

        return (jlong) (intptr_t) s;
    }
    else
    {
        PortAudioStream_free(env, s);
        PortAudio_throwException(env, err);
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_ReadStream
    (JNIEnv *env, jclass clazz, jlong stream, jbyteArray buffer, jlong frames)
{
    jbyte* data = (*env)->GetByteArrayElements(env, buffer, NULL);

    if (data)
    {
        PortAudioStream *s = (PortAudioStream *) (intptr_t) stream;
        PaError err;
        jlong framesInBytes = frames * s->inputFrameSize;

        if (s->pseudoBlocking)
        {
            if (Mutex_lock(s->inputMutex))
                err = paInternalError;
            else
            {
                jlong bytesRead = 0;

                err = paNoError;
                while (bytesRead < framesInBytes)
                {
                    jlong bytesToRead;

                    if (JNI_TRUE == s->finished)
                    {
                        err = paStreamIsStopped;
                        break;
                    }
                    if (!(s->inputLength))
                    {
                        ConditionVariable_wait(s->inputCondVar, s->inputMutex);
                        continue;
                    }

                    bytesToRead = framesInBytes - bytesRead;
                    if (bytesToRead > s->inputLength)
                        bytesToRead = s->inputLength;
                    memcpy(data + bytesRead, s->input, bytesToRead);
                    PortAudioStream_popFromPseudoBlockingBuffer(
                        s->input,
                        bytesToRead,
                        &(s->inputLength));
                    bytesRead += bytesToRead;
                }
                Mutex_unlock(s->inputMutex);
            }

            /* Improve the audio quality of the input if possible. */
            if ((paNoError == err) && s->audioQualityImprovement)
            {
                AudioQualityImprovement_process(
                    s->audioQualityImprovement,
                    AUDIO_QUALITY_IMPROVEMENT_SAMPLE_ORIGIN_INPUT,
                    s->sampleRate,
                    s->sampleSizeInBits,
                    s->channels,
                    s->inputLatency,
                    data, framesInBytes);
            }
        }
        else
        {
            err = Pa_ReadStream(s->stream, data, frames);
            if ((paNoError == err) || (paInputOverflowed == err))
            {
                err = paNoError;

                if (s->audioQualityImprovement)
                {
                    AudioQualityImprovement_process(
                        s->audioQualityImprovement,
                        AUDIO_QUALITY_IMPROVEMENT_SAMPLE_ORIGIN_INPUT,
                        s->sampleRate,
                        s->sampleSizeInBits,
                        s->channels,
                        s->inputLatency,
                        data, framesInBytes);
                }
            }
        }

        if (paNoError == err)
            (*env)->ReleaseByteArrayElements(env, buffer, data, 0);
        else
        {
            (*env)->ReleaseByteArrayElements(env, buffer, data, JNI_ABORT);
            PortAudio_throwException(env, err);
        }
    }
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_setDenoise
    (JNIEnv *env, jclass clazz, jlong stream, jboolean denoise)
{
    AudioQualityImprovement *aqi
        = ((PortAudioStream *) (intptr_t) stream)->audioQualityImprovement;

    if (aqi)
        AudioQualityImprovement_setDenoise(aqi, denoise);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_setEchoFilterLengthInMillis
    (JNIEnv *env, jclass clazz, jlong stream, jlong echoFilterLengthInMillis)
{
    AudioQualityImprovement *aqi
        = ((PortAudioStream *) (intptr_t) stream)->audioQualityImprovement;

    if (aqi)
    {
        AudioQualityImprovement_setEchoFilterLengthInMillis(
            aqi,
            echoFilterLengthInMillis);
    }
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_StartStream
    (JNIEnv *env, jclass clazz, jlong stream)
{
    PortAudioStream *s = (PortAudioStream *) (intptr_t) stream;
    PaError err;

    if (s->pseudoBlocking)
    {
        PortAudioStream_retain(s);
        if (Mutex_lock(s->mutex))
            err = paInternalError;
        else
        {
            s->finished = JNI_FALSE;
            err = Pa_StartStream(s->stream);
            if (paNoError != err)
                s->finished = JNI_TRUE;
            Mutex_unlock(s->mutex);
        }
        if (paNoError != err)
            PortAudioStream_release(s);
    }
    else
        err = Pa_StartStream(s->stream);
    if (paNoError != err)
        PortAudio_throwException(env, err);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_StopStream
    (JNIEnv *env, jclass clazz, jlong stream)
{
    PaError err
        = Pa_StopStream(((PortAudioStream *) (intptr_t) stream)->stream);

    if (paNoError != err)
        PortAudio_throwException(env, err);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_StreamParameters_1new
    (JNIEnv *env, jclass clazz,
    jint deviceIndex,
    jint channelCount,
    jlong sampleFormat,
    jdouble suggestedLatency)
{
    PaStreamParameters *sp
        = (PaStreamParameters *) malloc(sizeof(PaStreamParameters));

    if (sp)
    {
        sp->device = deviceIndex;
        sp->channelCount = channelCount;
        sp->sampleFormat = sampleFormat;
        sp->suggestedLatency = suggestedLatency;
        sp->hostApiSpecificStreamInfo = NULL;
    }
    return (jlong) (intptr_t) sp;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_UpdateAvailableDeviceList
    (JNIEnv *env, jclass clazz)
{
    Pa_UpdateAvailableDeviceList();
#ifdef _WIN32
    WMME_DSound_didUpdateAvailableDeviceList();
#endif /* #ifdef _WIN32 */
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_portaudio_Pa_WriteStream
    (JNIEnv *env, jclass clazz,
    jlong stream,
    jbyteArray buffer, jint offset, jlong frames,
    jint numberOfWrites)
{
    jbyte *bufferBytes;
    jbyte* data;
    PortAudioStream *s;
    jint i;
    PaError err = paNoError;
    jlong framesInBytes;
    AudioQualityImprovement *aqi;
    double sampleRate;
    unsigned long sampleSizeInBits;
    int channels;
    jlong outputLatency;

    bufferBytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (!bufferBytes)
        return;
    data = bufferBytes + offset;

    s = (PortAudioStream *) (intptr_t) stream;
    framesInBytes = frames * s->outputFrameSize;
    aqi = s->audioQualityImprovement;
    sampleRate = s->sampleRate;
    sampleSizeInBits = s->sampleSizeInBits;
    channels = s->channels;
    outputLatency = s->outputLatency;

    if (s->pseudoBlocking)
    {
        for (i = 0; i < numberOfWrites; i++)
        {
            if (Mutex_lock(s->outputMutex))
                err = paInternalError;
            else
            {
                jlong bytesWritten = 0;

                err = paNoError;
                while (bytesWritten < framesInBytes)
                {
                    size_t outputCapacity = s->outputCapacity - s->outputLength;
                    jlong bytesToWrite;

                    if (JNI_TRUE == s->finished)
                    {
                        err = paStreamIsStopped;
                        break;
                    }
                    if (outputCapacity < 1)
                    {
                        ConditionVariable_wait(
                            s->outputCondVar,
                            s->outputMutex);
                        continue;
                    }

                    bytesToWrite = framesInBytes - bytesWritten;
                    if (bytesToWrite > outputCapacity)
                        bytesToWrite = outputCapacity;
                    memcpy(
                        ((jbyte *) s->output) + s->outputLength,
                        data + bytesWritten,
                        bytesToWrite);

                    s->outputLength += bytesToWrite;
                    bytesWritten += bytesToWrite;
                }
                Mutex_unlock(s->outputMutex);
            }

            if (paNoError == err)
            {
                if (aqi)
                {
                    AudioQualityImprovement_process(
                        aqi,
                        AUDIO_QUALITY_IMPROVEMENT_SAMPLE_ORIGIN_OUTPUT,
                        sampleRate, sampleSizeInBits, channels,
                        outputLatency,
                        data, framesInBytes);
                }

                data += framesInBytes;
            }
        }
    }
    else
    {
        PaStream *paStream = s->stream;

        for (i = 0; i < numberOfWrites; i++)
        {
            err = Pa_WriteStream(paStream, data, frames);
            if ((paNoError != err) && (paOutputUnderflowed != err))
                break;
            else
            {
                if (aqi)
                {
                    AudioQualityImprovement_process(
                        aqi,
                        AUDIO_QUALITY_IMPROVEMENT_SAMPLE_ORIGIN_OUTPUT,
                        sampleRate, sampleSizeInBits, channels,
                        outputLatency,
                        data, framesInBytes);
                }
                data += framesInBytes;
            }
        }
    }

    (*env)->ReleaseByteArrayElements(env, buffer, bufferBytes, JNI_ABORT);

    if ((paNoError != err) && (paOutputUnderflowed != err))
        PortAudio_throwException(env, err);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved)
{
    PortAudio_vm = vm;
    AudioQualityImprovement_load();
#ifdef _WIN32
    WMME_DSound_load();
#endif /* #ifdef _WIN32 */

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved)
{
    AudioQualityImprovement_unload();
#ifdef _WIN32
    WMME_DSound_unload();
#endif /* #ifdef _WIN32 */
    PortAudio_vm = NULL;
}

static void
PortAudio_devicesChangedCallback(void *userData)
{
    JavaVM *vm = PortAudio_vm;

    (void) userData;

    if (vm)
    {
        JNIEnv *env;
        int err = (*vm)->AttachCurrentThreadAsDaemon(vm, (void **) &env, NULL);

        if (err < 0)
        {
            fprintf(
                stderr,
                "%s:%d: AttachCurrentThreadAsDaemon failed with error code/return value %d\n",
                __func__, (int) __LINE__, err);
            fflush(stderr);
        }
        else
        {
            jclass clazz = PortAudio_devicesChangedCallbackClass;
            jmethodID methodID = PortAudio_devicesChangedCallbackMethodID;

            if (clazz && methodID)
            {
                (*env)->CallStaticVoidMethod(env, clazz, methodID);
                /*
                 * Because we've called to Java from a native callback, make
                 * sure that any exception which is currently being thrown is
                 * cleared. Otherwise, the subsequent behavior may very well be
                 * undefined.
                 */
                (*env)->ExceptionClear(env);
            }
        }
    }
    else
    {
        fprintf(
            stderr,
            "%s:%d: JavaVM is unavailable\n", __func__, (int) __LINE__);
        fflush(stderr);
    }
}

static PaStreamParameters *
PortAudio_fixInputParametersSuggestedLatency
    (PaStreamParameters *inputParameters,
    jdouble sampleRate, jlong framesPerBuffer,
    PaHostApiTypeId hostApiType)
{
    if (inputParameters)
    {
        const PaDeviceInfo *deviceInfo
            = Pa_GetDeviceInfo(inputParameters->device);

        if (deviceInfo)
        {
            PaTime suggestedLatency = inputParameters->suggestedLatency;

            if (suggestedLatency == LATENCY_LOW)
            {
                inputParameters->suggestedLatency
                    = deviceInfo->defaultLowInputLatency;
            }
            else if ((suggestedLatency == LATENCY_HIGH)
                    || (suggestedLatency == LATENCY_UNSPECIFIED))
            {
                inputParameters->suggestedLatency
                    = deviceInfo->defaultHighInputLatency;

                /*
                 * When the input latency is too low, we do not have a great
                 * chance to perform echo cancellation using it. Since the
                 * caller does not care about the input latency, try to request
                 * an input latency which increases our chances.
                 */
                PortAudio_fixStreamParametersSuggestedLatency(
                    inputParameters,
                    sampleRate, framesPerBuffer,
                    hostApiType);
            }
        }
    }
    return inputParameters;
}

static PaStreamParameters *
PortAudio_fixOutputParametersSuggestedLatency(
    PaStreamParameters *outputParameters,
    jdouble sampleRate, jlong framesPerBuffer,
    PaHostApiTypeId hostApiType)
{
    if (outputParameters)
    {
        const PaDeviceInfo *deviceInfo
            = Pa_GetDeviceInfo(outputParameters->device);

        if (deviceInfo)
        {
            PaTime suggestedLatency = outputParameters->suggestedLatency;

            if (suggestedLatency == LATENCY_LOW)
            {
                outputParameters->suggestedLatency
                    = deviceInfo->defaultLowOutputLatency;
            }
            else if ((suggestedLatency == LATENCY_HIGH)
                    || (suggestedLatency == LATENCY_UNSPECIFIED))
            {
                outputParameters->suggestedLatency
                    = deviceInfo->defaultHighOutputLatency;

                /*
                 * When the output latency is too low, we do not have a great
                 * chance to perform echo cancellation using it. Since the
                 * caller does not care about the output latency, try to request
                 * an output latency which increases our chances.
                 */
                PortAudio_fixStreamParametersSuggestedLatency(
                    outputParameters,
                    sampleRate, framesPerBuffer,
                    hostApiType);
            }
        }
    }
    return outputParameters;
}

static PaStreamParameters *
PortAudio_fixStreamParametersSuggestedLatency
    (PaStreamParameters *streamParameters,
    jdouble sampleRate, jlong framesPerBuffer,
    PaHostApiTypeId hostApiType)
{
    if ((paCoreAudio == hostApiType)
            && sampleRate
            && (paFramesPerBufferUnspecified != framesPerBuffer))
    {
        PaTime minLatency
            = (MIN_PLAY_DELAY_IN_FRAMES
                    * streamParameters->channelCount
                    * framesPerBuffer)
                / (2 * sampleRate);

        if (streamParameters->suggestedLatency < minLatency)
            streamParameters->suggestedLatency = minLatency;
    }
    return streamParameters;
}

static long
PortAudio_getFrameSize(PaStreamParameters *streamParameters)
{
    if (streamParameters)
    {
        PaError sampleSize = Pa_GetSampleSize(streamParameters->sampleFormat);

        if (paSampleFormatNotSupported != sampleSize)
            return sampleSize * streamParameters->channelCount;
    }
    return 0;
}

static unsigned long
PortAudio_getSampleSizeInBits(PaStreamParameters *streamParameters)
{
    if (streamParameters)
    {
        PaError sampleSize = Pa_GetSampleSize(streamParameters->sampleFormat);

        if (paSampleFormatNotSupported != sampleSize)
            return sampleSize * 8;
    }
    return 0;
}

/**
 * Gets a new <tt>jbyteArray</tt> instance which is initialized with the bytes
 * of a specific C string i.e. <tt>const char *</tt>.
 *
 * @param env
 * @param str the bytes/C string to initialize the new <tt>jbyteArray</tt>
 * instance with
 * @return a new <tt>jbyteArray</tt> instance which is initialized with the
 * bytes of the specified <tt>str</tt>
 */
static jbyteArray
PortAudio_getStrBytes(JNIEnv *env, const char *str)
{
    jbyteArray bytes;

    if (str)
    {
        size_t length = strlen(str);

        bytes = (*env)->NewByteArray(env, length);
        if (bytes && length)
            (*env)->SetByteArrayRegion(env, bytes, 0, length, (jbyte *) str);
    }
    else
        bytes = NULL;
    return bytes;
}

static void
PortAudio_throwException(JNIEnv *env, PaError err)
{
    jclass clazz
        = (*env)->FindClass(
            env,
            "org/jitsi/impl/neomedia/portaudio/PortAudioException");

    /*
     * XXX If there is no clazz, an exception has already been thrown and the
     * current thread may no longer utilize JNIEnv methods.
     */
    if (clazz)
    {
        jmethodID methodID
            = (*env)->GetMethodID(
                    env,
                    clazz,
                    "<init>",
                    "(Ljava/lang/String;JI)V");

        if (methodID)
        {
            const char *message;
            jstring jmessage;
            jlong errorCode;
            jint hostApiType;

            /*
             * We may be able to provide further details in the case of
             * paUnanticipatedHostError by means of PaHostErrorInfo.
             */
            if (paUnanticipatedHostError == err)
            {
                const PaHostErrorInfo*  hostErr = Pa_GetLastHostErrorInfo();

                if (hostErr)
                {
                    message = hostErr->errorText;
                    /*
                     * PaHostErrorInfo's errorText is documented to possibly be
                     * an empty string. In such a case, the (detailed) message
                     * of the PortAudioException will fall back to
                     * Pa_GetErrorText.
                     */
                    if (!message || !strlen(message))
                        message = Pa_GetErrorText(err);
                    errorCode = hostErr->errorCode;
                    hostApiType = hostErr->hostApiType;
                }
                else
                {
                    message = Pa_GetErrorText(err);
                    errorCode = err;
                    hostApiType = -1;
                }
            }
            else
            {
                message = Pa_GetErrorText(err);
                errorCode = err;
                hostApiType = -1;
            }

            if (message)
            {
                jmessage = (*env)->NewStringUTF(env, message);
                if (!jmessage)
                {
                    /*
                     * XXX An exception has already been thrown and the current
                     * thread may no longer utilize JNIEnv methods.
                     */
                    return;
                }
            }
            else
                jmessage = 0;

            if (jmessage)
            {
                jobject t
                    = (*env)->NewObject(
                            env,
                            clazz,
                            methodID,
                            jmessage,
                            errorCode,
                            hostApiType);

                if (t)
                    (*env)->Throw(env, (jthrowable) t);
                /*
                 * XXX If there is no t, an exception has already been thrown
                 * and the current thread may no longer utilize JNIEnv methods.
                 */
                return;
            }
        }
        else
        {
            /*
             * XXX An exception has already been thrown and the current thread
             * may no longer utilize JNIEnv methods.
             */
            return;
        }

        (*env)->ThrowNew(env, clazz, Pa_GetErrorText(err));
    }
}

/**
 * Allocates (and initializes) the memory and its associated variables for a
 * specific buffer to be used by the pseudo-blocking stream interface
 * implementation of a <tt>PortAudioStream</tt>.
 *
 * @param capacity the number of bytes to be allocated to the buffer
 * @param bufferPtr a pointer which specifies where the location of the
 * allocated buffer is to be stored
 * @param bufferLengthPtr a pointer which specifies where the initial length
 * (i.e. zero) is to be stored
 * @param bufferCapacityPtr a pointer which specifies where the capacity of the
 * allocated buffer is to be stored
 * @param bufferMutexPtr a pointer which specifies where the <tt>Mute</tt> to
 * synchronize the access to the allocated buffer is to be stored
 * @param bufferCondVarPtr a pointer which specifies where the
 * <tt>ConditionVariable</tt> to synchronize the access to the allocated buffer
 * is to be stored
 * @return the location of the allocated buffer upon success; otherwise,
 * <tt>NULL</tt>
 */
static void *
PortAudioStream_allocPseudoBlockingBuffer
    (size_t capacity,
    void **bufferPtr, size_t *bufferLengthPtr, size_t *bufferCapacityPtr,
    Mutex **bufferMutexPtr, ConditionVariable **bufferCondVarPtr)
{
    void *buffer = malloc(capacity);

    if (buffer)
    {
        Mutex *mutex = Mutex_new(NULL);

        if (mutex)
        {
            ConditionVariable *condVar = ConditionVariable_new(NULL);

            if (condVar)
            {
                if (bufferPtr)
                    *bufferPtr = buffer;
                if (bufferLengthPtr)
                    *bufferLengthPtr = 0;
                if (bufferCapacityPtr)
                    *bufferCapacityPtr = capacity;
                *bufferMutexPtr = mutex;
                *bufferCondVarPtr = condVar;
            }
            else
            {
                Mutex_free(mutex);
                free(buffer);
                buffer = NULL;
            }
        }
        else
        {
            free(buffer);
            buffer = NULL;
        }
    }
    return buffer;
}

static void
PortAudioStream_free(JNIEnv *env, PortAudioStream *stream)
{
    if (stream->streamCallback)
        (*env)->DeleteGlobalRef(env, stream->streamCallback);

    if (stream->inputMutex && !Mutex_lock(stream->inputMutex))
    {
        if (stream->input)
            free(stream->input);
        ConditionVariable_free(stream->inputCondVar);
        Mutex_unlock(stream->inputMutex);
        Mutex_free(stream->inputMutex);
    }

    if (stream->outputMutex && !Mutex_lock(stream->outputMutex))
    {
        if (stream->output)
            free(stream->output);
        ConditionVariable_free(stream->outputCondVar);
        Mutex_unlock(stream->outputMutex);
        Mutex_free(stream->outputMutex);
    }

    if (stream->audioQualityImprovement)
        AudioQualityImprovement_release(stream->audioQualityImprovement);

    if (stream->mutex)
        Mutex_free(stream->mutex);

    free(stream);
}

static int
PortAudioStream_javaCallback
    (const void *input,
    void *output,
    unsigned long frameCount,
    const PaStreamCallbackTimeInfo *timeInfo,
    PaStreamCallbackFlags statusFlags,
    void *userData)
{
    PortAudioStream *s = (PortAudioStream *) userData;
    jobject streamCallback = s->streamCallback;
    JNIEnv *env;
    jmethodID streamCallbackMethodID;
    int ret;

    if (!streamCallback)
        return paContinue;

    env = s->env;
    if (!env)
    {
        JavaVM *vm = s->vm;

        if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void **) &env, NULL) < 0)
            return paAbort;
        else
            s->env = env;
    }
    streamCallbackMethodID = s->streamCallbackMethodID;
    if (!streamCallbackMethodID)
    {
        jclass streamCallbackClass
            = (*env)->GetObjectClass(env, streamCallback);

        streamCallbackMethodID
            = (*env)->GetMethodID(
                    env,
                    streamCallbackClass,
                    "callback",
                    "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)I");
        if (streamCallbackMethodID)
            s->streamCallbackMethodID = streamCallbackMethodID;
        else
            return paAbort;
    }

    ret
        = (*env)->CallIntMethod(
                env,
                streamCallback,
                streamCallbackMethodID,
                input
                    ? (*env)->NewDirectByteBuffer(
                            env,
                            (void *) input,
                            frameCount * s->inputFrameSize)
                    : NULL,
                output
                    ? (*env)->NewDirectByteBuffer(
                            env,
                            output,
                            frameCount * s->outputFrameSize)
                    : NULL);
    /*
     * Because we've called to Java from a native callback, make sure that any
     * exception which is currently being thrown is cleared. Otherwise, the
     * subsequent behavior may very well be undefined.
     */
    (*env)->ExceptionClear(env);
    return ret;
}

static void
PortAudioStream_javaFinishedCallback(void *userData)
{
    PortAudioStream *s = (PortAudioStream *) userData;
    jobject streamCallback = s->streamCallback;
    JNIEnv *env;
    jmethodID streamFinishedCallbackMethodID;

    if (!streamCallback)
        return;

    env = s->env;
    if (!env)
    {
        JavaVM *vm = s->vm;

        if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void **) &env, NULL) < 0)
            return;
        else
            s->env = env;
    }
    streamFinishedCallbackMethodID = s->streamFinishedCallbackMethodID;
    if (!streamFinishedCallbackMethodID)
    {
        jclass streamCallbackClass
            = (*env)->GetObjectClass(env, streamCallback);

        streamFinishedCallbackMethodID
            = (*env)->GetMethodID(
                    env,
                    streamCallbackClass,
                    "finishedCallback",
                    "()V");
        if (streamFinishedCallbackMethodID)
            s->streamFinishedCallbackMethodID
                = streamFinishedCallbackMethodID;
        else
            return;
    }

    (*env)->CallVoidMethod(env, streamCallback, streamFinishedCallbackMethodID);
    /*
     * Because we've called to Java from a native callback, make sure that any
     * exception which is currently being thrown is cleared. Otherwise, the
     * subsequent behavior may very well be undefined.
     */
    (*env)->ExceptionClear(env);
}

static PortAudioStream *
PortAudioStream_new(JNIEnv *env, jobject streamCallback)
{
    PortAudioStream *s = calloc(1, sizeof(PortAudioStream));

    if (!s)
    {
        PortAudio_throwException(env, paInsufficientMemory);
        return NULL;
    }

    if (streamCallback)
    {
        if ((*env)->GetJavaVM(env, &(s->vm)) < 0)
        {
            free(s);
            PortAudio_throwException(env, paInternalError);
            return NULL;
        }

        s->streamCallback = (*env)->NewGlobalRef(env, streamCallback);
        if (!(s->streamCallback))
        {
            free(s);
            PortAudio_throwException(env, paInsufficientMemory);
            return NULL;
        }
    }

    return s;
}

static void
PortAudioStream_popFromPseudoBlockingBuffer
    (void *buffer, size_t length, size_t *bufferLengthPtr)
{
    size_t i;
    size_t newLength = *bufferLengthPtr - length;
    jbyte *oldBuffer = (jbyte *) buffer;
    jbyte *newBuffer = ((jbyte *) buffer) + length;

    for (i = 0; i < newLength; i++)
        *oldBuffer++ = *newBuffer++;
    *bufferLengthPtr = newLength;
}

static int
PortAudioStream_pseudoBlockingCallback
    (const void *input,
    void *output,
    unsigned long frameCount,
    const PaStreamCallbackTimeInfo *timeInfo,
    PaStreamCallbackFlags statusFlags,
    void *userData)
{
    PortAudioStream *s = (PortAudioStream *) userData;

    if (input && s->inputMutex && !Mutex_lock(s->inputMutex))
    {
        size_t inputLength = frameCount * s->inputFrameSize;
        size_t newInputLength;
        void *inputInStream;

        /*
         * Remember the specified input so that it can be retrieved later on in
         * our pseudo-blocking Pa_ReadStream().
         */
        newInputLength = s->inputLength + inputLength;
        if (newInputLength > s->inputCapacity)
        {
            PortAudioStream_popFromPseudoBlockingBuffer(
                s->input,
                newInputLength - s->inputCapacity,
                &(s->inputLength));
        }
        inputInStream = ((jbyte *) (s->input)) + s->inputLength;
        memcpy(inputInStream, input, inputLength);
        s->inputLength += inputLength;

        ConditionVariable_notify(s->inputCondVar);
        Mutex_unlock(s->inputMutex);
    }
    if (output && s->outputMutex && !Mutex_lock(s->outputMutex))
    {
        size_t outputLength = frameCount * s->outputFrameSize;
        size_t availableOutputLength = outputLength;

        if (availableOutputLength > s->outputLength)
            availableOutputLength = s->outputLength;
        memcpy(output, s->output, availableOutputLength);
        PortAudioStream_popFromPseudoBlockingBuffer(
            s->output,
            availableOutputLength,
            &(s->outputLength));
        if (availableOutputLength < outputLength)
        {
            memset(
                ((jbyte *) output) + availableOutputLength,
                0,
                outputLength - availableOutputLength);
        }

        ConditionVariable_notify(s->outputCondVar);
        Mutex_unlock(s->outputMutex);
    }
    return paContinue;
}

static void
PortAudioStream_pseudoBlockingFinishedCallback(void *userData)
{
    PortAudioStream *s = (PortAudioStream *) userData;

    if (!Mutex_lock(s->mutex))
    {
        s->finished = JNI_TRUE;
        if (s->inputMutex && !Mutex_lock(s->inputMutex))
        {
            ConditionVariable_notify(s->inputCondVar);
            Mutex_unlock(s->inputMutex);
        }
        if (s->outputMutex && !Mutex_lock(s->outputMutex))
        {
            ConditionVariable_notify(s->outputCondVar);
            Mutex_unlock(s->outputMutex);
        }
        Mutex_unlock(s->mutex);
    }
    PortAudioStream_release(s);
}

static void
PortAudioStream_release(PortAudioStream *stream)
{
    if (!Mutex_lock(stream->mutex))
    {
        --(stream->retainCount);
        if (stream->retainCount < 1)
        {
            Mutex_unlock(stream->mutex);
            PortAudioStream_free(NULL, stream);
        }
        else
            Mutex_unlock(stream->mutex);
    }
}

static void
PortAudioStream_retain(PortAudioStream *stream)
{
    if (!Mutex_lock(stream->mutex))
    {
        ++(stream->retainCount);
        Mutex_unlock(stream->mutex);
    }
}
