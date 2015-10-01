/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
