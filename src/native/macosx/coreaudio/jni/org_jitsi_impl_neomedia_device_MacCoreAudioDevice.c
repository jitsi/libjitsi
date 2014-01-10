/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_device_MacCoreAudioDevice.h"

#include "../lib/device.h"
#include "maccoreaudio_util.h"

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
    int nbDevices = maccoreaudio_getDeviceUIDList(&deviceUIDList);
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
    jint isInputDevice = maccoreaudio_isInputDevice(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return (isInputDevice != 0);
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_isOutputDevice
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint isOutputDevice = maccoreaudio_isOutputDevice(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return (isOutputDevice != 0);
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getTransportTypeBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    const char * transportType = maccoreaudio_getTransportType(deviceUIDPtr);
    jbyteArray transportTypeBytes
        = maccoreaudio_getStrBytes(env, transportType);
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
    jfloat rate = maccoreaudio_getNominalSampleRate(
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
    if(maccoreaudio_getAvailableNominalSampleRates(
                deviceUIDPtr,
                &minRate,
                &maxRate,
                isOutputStream,
                isEchoCancel)
            != noErr)
    {
        fprintf(stderr,
                "MacCoreAudioDevice_getMinimalNominalSampleRate\
                    \n\tmaccoreaudio_getAvailableNominalSampleRates\n");
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
    if(maccoreaudio_getAvailableNominalSampleRates(
                deviceUIDPtr,
                &minRate,
                &maxRate,
                isOutputStream,
                isEchoCancel)
            != noErr)
    {
        fprintf(stderr,
                "MacCoreAudioDevice_getMaximalNominalSampleRate\
                    \n\tmaccoreaudio_getAvailableNominalSampleRates\n");
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
    char* defaultInputDeviceUID = maccoreaudio_getDefaultInputDeviceUID();
    jbyteArray defaultInputDeviceUIDBytes
        = maccoreaudio_getStrBytes(env, defaultInputDeviceUID);
    // Free
    free(defaultInputDeviceUID);

    return defaultInputDeviceUIDBytes;
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_getDefaultOutputDeviceUIDBytes
  (JNIEnv *env, jclass clazz)
{
    char* defaultOutputDeviceUID
        = maccoreaudio_getDefaultOutputDeviceUID();
    jbyteArray defaultOutputDeviceUIDBytes
        = maccoreaudio_getStrBytes(env, defaultOutputDeviceUID);
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
    maccoreaudio_stream* stream = NULL;

    if(isInput && maccoreaudio_isInputDevice(deviceUIDPtr)) // input
    {
        jmethodID callbackMethod = maccoreaudio_getCallbackMethodID(
                env,
                callback,
                "readInput");
        stream = maccoreaudio_startInputStream(
                deviceUIDPtr,
                (void*) maccoreaudio_callbackMethod,
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
    else if(!isInput && maccoreaudio_isOutputDevice(deviceUIDPtr)) // output
    {
        jmethodID callbackMethod = maccoreaudio_getCallbackMethodID(
                env,
                callback,
                "writeOutput");
        stream = maccoreaudio_startOutputStream(
                deviceUIDPtr,
                (void*) maccoreaudio_callbackMethod,
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
    maccoreaudio_stream * stream = (maccoreaudio_stream*) (long) streamPtr;
    jobject callbackObject = stream->callbackObject;

    maccoreaudio_stopStream(deviceUIDPtr, stream);

    // Free
    (*env)->DeleteGlobalRef(env, callbackObject);
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_countInputChannels
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint nbChannels = maccoreaudio_countInputChannels(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return nbChannels;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_MacCoreAudioDevice_countOutputChannels
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint nbChannels = maccoreaudio_countOutputChannels(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return nbChannels;
}
