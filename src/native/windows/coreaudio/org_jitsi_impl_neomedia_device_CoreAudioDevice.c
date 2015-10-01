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

#include "../lib/device.h"

/**
 * JNI code for CoreAudioDevice.
 *
 * @author Vicnent Lucas
 */

// Private functions

static jbyteArray getStrBytes(JNIEnv *env, const char *str);

// Implementation

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_initDevices
  (JNIEnv *env, jclass clazz)
{
    return initDevices();
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_freeDevices
  (JNIEnv *env, jclass clazz)
{
    freeDevices();
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_getDeviceNameBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = env->GetStringUTFChars(deviceUID, 0);
    char * deviceName = getDeviceName(deviceUIDPtr);
    jbyteArray deviceNameBytes = getStrBytes(env, deviceName);
    // Free
    free(deviceName);
    env->ReleaseStringUTFChars(deviceUID, deviceUIDPtr);

    return deviceNameBytes;
}

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_getDeviceModelIdentifierBytes
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = env->GetStringUTFChars(deviceUID, 0);
    char * deviceModelIdentifier = getDeviceModelIdentifier(deviceUIDPtr);
    jbyteArray deviceModelIdentifierBytes
        = getStrBytes(env, deviceModelIdentifier);
    // Free
    free(deviceModelIdentifier);
    env->ReleaseStringUTFChars(deviceUID, deviceUIDPtr);

    return deviceModelIdentifierBytes;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_setInputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID, jfloat volume)
{
    const char * deviceUIDPtr = env->GetStringUTFChars(deviceUID, 0);
    jint err = setInputDeviceVolume(deviceUIDPtr, volume);
    // Free
    env->ReleaseStringUTFChars(deviceUID, deviceUIDPtr);

    return err;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_setOutputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID, jfloat volume)
{
    const char * deviceUIDPtr = env->GetStringUTFChars(deviceUID, 0);
    jint err = setOutputDeviceVolume(deviceUIDPtr, volume);
    // Free
    env->ReleaseStringUTFChars(deviceUID, deviceUIDPtr);

    return err;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_getInputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = env->GetStringUTFChars(deviceUID, 0);
    jfloat volume = getInputDeviceVolume(deviceUIDPtr);
    // Free
    env->ReleaseStringUTFChars(deviceUID, deviceUIDPtr);

    return volume;
}

JNIEXPORT jfloat JNICALL
Java_org_jitsi_impl_neomedia_device_CoreAudioDevice_getOutputDeviceVolume
  (JNIEnv *env, jclass clazz, jstring deviceUID)
{
    const char * deviceUIDPtr = env->GetStringUTFChars(deviceUID, 0);
    jfloat volume = getOutputDeviceVolume(deviceUIDPtr);
    // Free
    env->ReleaseStringUTFChars(deviceUID, deviceUIDPtr);

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

        bytes = env->NewByteArray(length);
        if (bytes && length)
            env->SetByteArrayRegion(bytes, 0, length, (jbyte *) str);
    }
    else
        bytes = NULL;
    return bytes;
}
