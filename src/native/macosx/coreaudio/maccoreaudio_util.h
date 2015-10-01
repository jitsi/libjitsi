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

#ifndef __MacCoreaudio_util_h
#define __MacCoreaudio_util_h

#include <jni.h>

/**
 * JNI utilities.
 *
 * @author Vincent Lucas
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *pvt);

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *pvt);

jbyteArray MacCoreaudio_getStrBytes(JNIEnv *env, const char *str);

jmethodID MacCoreaudio_getCallbackMethodID(
        JNIEnv *env,
        jobject callback,
        char *callbackFunctionName);

void MacCoreaudio_callbackMethod(
        char *buffer,
        int bufferLength,
        void *callback,
        void *callbackMethod);

void MacCoreaudio_devicesChangedCallbackMethod();

void MacCoreaudio_log(const char *error_format, ...);

#endif
