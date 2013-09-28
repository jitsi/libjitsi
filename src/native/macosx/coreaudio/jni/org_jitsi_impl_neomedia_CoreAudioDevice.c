/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_CoreAudioDevice.h"

#include "../lib/device.h"
#include "maccoreaudio_util.h"

/**
 * JNI code for CoreAudioDevice.
 *
 * @author Vincent Lucas
 */

// Implementation

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_CoreAudioDevice_initDevices
  (JNIEnv *env, jclass clazz)
{
    return maccoreaudio_initDevices();
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_CoreAudioDevice_freeDevices
  (JNIEnv *env, jclass clazz)
{
    maccoreaudio_freeDevices();
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_CoreAudioDevice_getDeviceNameBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    char * deviceName = maccoreaudio_getDeviceName(deviceUIDPtr);
    jbyteArray deviceNameBytes = maccoreaudio_getStrBytes(env, deviceName);
    // Free
    free(deviceName);
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return deviceNameBytes;
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_CoreAudioDevice_getDeviceModelIdentifierBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    char * deviceModelIdentifier
        = maccoreaudio_getDeviceModelIdentifier(deviceUIDPtr);
    jbyteArray deviceModelIdentifierBytes
        = maccoreaudio_getStrBytes(env, deviceModelIdentifier);
    // Free
    free(deviceModelIdentifier);
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return deviceModelIdentifierBytes;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_CoreAudioDevice_setInputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID, jfloat volume)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint err = maccoreaudio_setInputDeviceVolume(deviceUIDPtr, volume);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return err;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_CoreAudioDevice_setOutputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID, jfloat volume)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint err = maccoreaudio_setOutputDeviceVolume(deviceUIDPtr, volume);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return err;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_CoreAudioDevice_getInputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jfloat volume = maccoreaudio_getInputDeviceVolume(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return volume;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_CoreAudioDevice_getOutputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jfloat volume = maccoreaudio_getOutputDeviceVolume(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return volume;
}
