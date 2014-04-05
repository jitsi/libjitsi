/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_device_CoreAudioDevice.h"

#include "device.h"
#include "MacCoreaudio_util.h"

/**
 * JNI code for CoreAudioDevice.
 *
 * @author Vincent Lucas
 */

// Implementation

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_initDevices
    (JNIEnv *env, jclass clazz)
{
    // TODO Auto-generated method stub
    return 0;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_freeDevices
    (JNIEnv *env, jclass clazz)
{
    // TODO Auto-generated method stub
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_getDeviceNameBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    char * deviceName = MacCoreaudio_getDeviceName(deviceUIDPtr);
    jbyteArray deviceNameBytes = MacCoreaudio_getStrBytes(env, deviceName);
    // Free
    free(deviceName);
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return deviceNameBytes;
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_getDeviceModelIdentifierBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    char * deviceModelIdentifier
        = MacCoreaudio_getDeviceModelIdentifier(deviceUIDPtr);
    jbyteArray deviceModelIdentifierBytes
        = MacCoreaudio_getStrBytes(env, deviceModelIdentifier);
    // Free
    free(deviceModelIdentifier);
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return deviceModelIdentifierBytes;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_setInputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID, jfloat volume)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint err = MacCoreaudio_setInputDeviceVolume(deviceUIDPtr, volume);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return err;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_setOutputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID, jfloat volume)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jint err = MacCoreaudio_setOutputDeviceVolume(deviceUIDPtr, volume);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return err;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_getInputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jfloat volume = MacCoreaudio_getInputDeviceVolume(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return volume;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_getOutputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = (*env)->GetStringUTFChars(env, deviceUID, 0);
    jfloat volume = MacCoreaudio_getOutputDeviceVolume(deviceUIDPtr);
    // Free
    (*env)->ReleaseStringUTFChars(env, deviceUID, deviceUIDPtr);

    return volume;
}
