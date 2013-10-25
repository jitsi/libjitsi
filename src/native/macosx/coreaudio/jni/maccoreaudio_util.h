/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#ifndef __maccoreaudio_util_h
#define __maccoreaudio_util_h

#include <jni.h>

/**
 * JNI utilities.
 *
 * @author Vincent Lucas
 */
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *pvt);

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *pvt);

jbyteArray maccoreaudio_getStrBytes(
        JNIEnv *env,
        const char *str);

jmethodID maccoreaudio_getCallbackMethodID(
        JNIEnv *env,
        jobject callback,
        char* callbackFunctionName);

void maccoreaudio_callbackMethod(
        char *buffer,
        int bufferLength,
        void* callback,
        void* callbackMethod);

void maccoreaudio_devicesChangedCallbackMethod(
        void);

void maccoreaudio_log(
        const char * error_format,
        ...);

#endif
