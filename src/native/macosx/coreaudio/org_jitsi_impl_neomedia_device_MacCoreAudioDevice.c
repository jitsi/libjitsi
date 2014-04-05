/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_device_MacCoreAudioDevice.h"

#include "device.h"
#include "MacCoreaudio_util.h"

/**
 * JNI code for CoreAudioDevice.
 *
 * @author Vicnent Lucas
 */

// Implementation

JNIEXPORT jobjectArray JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getDeviceUIDList
  (JNIEnv *env, jclass clazz)
{
    jobjectArray javaDeviceUIDList = NULL;

    char ** deviceUIDList;
    int nbDevices = MacCoreaudio_getDeviceUIDList(&deviceUIDList);
    if(nbDevices != -1)
    {
        jstring deviceUID;
        jclass stringClass = (*env)->FindClass(env, "java/lang/String");
        javaDeviceUIDList
            = (*env)->NewObjectArray(env, nbDevices, stringClass, NULL);
        int i;
        for(i = 0; i < nbDevices; ++i)
        {
            deviceUID = (*env)->NewStringUTF(env, deviceUIDList[i]);
            if(deviceUID != NULL)
            {
                (*env)->SetObjectArrayElement(
                        env,
                        javaDeviceUIDList,
                        i,
                        deviceUID);
            }

            free(deviceUIDList[i]);
        }

        free(deviceUIDList);
    }

    return javaDeviceUIDList;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_isInputDevice
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint isInputDevice = MacCoreaudio_isInputDevice(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return (isInputDevice != 0);
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_isOutputDevice
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint isOutputDevice = MacCoreaudio_isOutputDevice(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return (isOutputDevice != 0);
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getTransportTypeBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    const char * transportType = MacCoreaudio_getTransportType(deviceUIDPtr);
    jbyteArray transportTypeBytes
        = MacCoreaudio_getStrBytes(env, transportType);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return transportTypeBytes;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getNominalSampleRate
  (JNIEnv *env, jclass clazz, jstring deviceUID, jboolean isOutputStream,
   jboolean isEchoCancel)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jfloat rate = MacCoreaudio_getNominalSampleRate(
            deviceUIDPtr,
            isOutputStream,
            isEchoCancel);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return rate;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getMinimalNominalSampleRate
  (JNIEnv *env, jclass clazz, jstring deviceUID, jboolean isOutputStream,
   jboolean isEchoCancel)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    Float64 minRate;
    Float64 maxRate;
    if(MacCoreaudio_getAvailableNominalSampleRates(
                deviceUIDPtr,
                &minRate,
                &maxRate,
                isOutputStream,
                isEchoCancel)
            != noErr)
    {
        fprintf(stderr,
                "MacCoreAudioDevice_getMinimalNominalSampleRate\
                    \n\tMacCoreaudio_getAvailableNominalSampleRates\n");
        fflush(stderr);
        return -1.0;
    }
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return minRate;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getMaximalNominalSampleRate
  (JNIEnv *env, jclass clazz, jstring deviceUID, jboolean isOutputStream,
   jboolean isEchoCancel)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    Float64 minRate;
    Float64 maxRate;
    if(MacCoreaudio_getAvailableNominalSampleRates(
                deviceUIDPtr,
                &minRate,
                &maxRate,
                isOutputStream,
                isEchoCancel)
            != noErr)
    {
        fprintf(stderr,
                "MacCoreAudioDevice_getMaximalNominalSampleRate\
                    \n\tMacCoreaudio_getAvailableNominalSampleRates\n");
        fflush(stderr);
        return -1.0;
    }
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return maxRate;
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getDefaultInputDeviceUIDBytes
  (JNIEnv *env, jclass clazz)
{
    char* defaultInputDeviceUID = MacCoreaudio_getDefaultInputDeviceUID();
    jbyteArray defaultInputDeviceUIDBytes
        = MacCoreaudio_getStrBytes(env, defaultInputDeviceUID);
    // Free
    free(defaultInputDeviceUID);

    return defaultInputDeviceUIDBytes;
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getDefaultOutputDeviceUIDBytes
  (JNIEnv *env, jclass clazz)
{
    char* defaultOutputDeviceUID
        = MacCoreaudio_getDefaultOutputDeviceUID();
    jbyteArray defaultOutputDeviceUIDBytes
        = MacCoreaudio_getStrBytes(env, defaultOutputDeviceUID);
    // Free
    free(defaultOutputDeviceUID);

    return defaultOutputDeviceUIDBytes;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_startStream
  (JNIEnv *env, jclass clazz, jstring deviceUID, jobject callback,
        jfloat sampleRate,
        jint nbChannels,
        jint bitsPerChannel,
        jboolean isFloat,
        jboolean isBigEndian,
        jboolean isNonInterleaved,
        jboolean isInput,
        jboolean isEchoCancel)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jobject callbackObject = (*env)->NewGlobalRef(env, callback);
    MacCoreaudio_Stream* stream = NULL;

    if(isInput && MacCoreaudio_isInputDevice(deviceUIDPtr)) // input
    {
        jmethodID callbackMethod = MacCoreaudio_getCallbackMethodID(
                env,
                callback,
                "readInput");
        stream = MacCoreaudio_startInputStream(
                deviceUIDPtr,
                (void*) MacCoreaudio_callbackMethod,
                callbackObject,
                callbackMethod,
                sampleRate,
                nbChannels,
                bitsPerChannel,
                isFloat,
                isBigEndian,
                isNonInterleaved,
                isEchoCancel);
    }
    else if(!isInput && MacCoreaudio_isOutputDevice(deviceUIDPtr)) // output
    {
        jmethodID callbackMethod = MacCoreaudio_getCallbackMethodID(
                env,
                callback,
                "writeOutput");
        stream = MacCoreaudio_startOutputStream(
                deviceUIDPtr,
                (void*) MacCoreaudio_callbackMethod,
                callbackObject,
                callbackMethod,
                sampleRate,
                nbChannels,
                bitsPerChannel,
                isFloat,
                isBigEndian,
                isNonInterleaved,
                isEchoCancel);
    }

    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return (long) stream;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_stopStream
  (JNIEnv *env, jclass clazz, jstring deviceUID, jlong streamPtr)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    MacCoreaudio_Stream * stream = (MacCoreaudio_Stream*) (long) streamPtr;
    jobject callbackObject = stream->callbackObject;

    MacCoreaudio_stopStream(deviceUIDPtr, stream);

    // Free
    (*env)->DeleteGlobalRef(env, callbackObject);
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_countInputChannels
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint nbChannels = MacCoreaudio_countInputChannels(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return nbChannels;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_countOutputChannels
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint nbChannels = MacCoreaudio_countOutputChannels(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return nbChannels;
}
