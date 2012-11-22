/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_coreaudio_CoreAudioDevice.h"

#include "../lib/device.h"

/**
 * JNI code for CoreAudioDevice.
 *
 * @author Vicnent Lucas
 */

// Private functions

static jbyteArray getStrBytes(JNIEnv *env, const char *str);

// Implementation

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_coreaudio_CoreAudioDevice_getDeviceNameBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    char * deviceName = getDeviceName(deviceUIDPtr);
    jbyteArray deviceNameBytes = getStrBytes(env, deviceName);
    // Free
    free(deviceName);
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return deviceNameBytes;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_coreaudio_CoreAudioDevice_setInputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID, jfloat volume)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint err = setInputDeviceVolume(deviceUIDPtr, volume);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return err;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_coreaudio_CoreAudioDevice_setOutputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID, jfloat volume)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint err = setOutputDeviceVolume(deviceUIDPtr, volume);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return err;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_coreaudio_CoreAudioDevice_getInputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jfloat volume = getInputDeviceVolume(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return volume;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_coreaudio_CoreAudioDevice_getOutputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jfloat volume = getOutputDeviceVolume(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return volume;
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
static jbyteArray getStrBytes(JNIEnv *env, const char *str)
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
