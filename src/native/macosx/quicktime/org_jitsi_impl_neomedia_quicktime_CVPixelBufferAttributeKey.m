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

#include "org_jitsi_impl_neomedia_quicktime_CVPixelBufferAttributeKey.h"

#import <CoreVideo/CVPixelBuffer.h>
#include <stdint.h>

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_CVPixelBufferAttributeKey_kCVPixelBufferHeightKey
    (JNIEnv *jniEnv, jclass clazz)
{
    return (jlong) (intptr_t) kCVPixelBufferHeightKey;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_CVPixelBufferAttributeKey_kCVPixelBufferPixelFormatTypeKey
    (JNIEnv *jniEnv, jclass clazz)
{
    return (jlong) (intptr_t) kCVPixelBufferPixelFormatTypeKey;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_CVPixelBufferAttributeKey_kCVPixelBufferWidthKey
    (JNIEnv *jniEnv, jclass clazz)
{
    return (jlong) (intptr_t) kCVPixelBufferWidthKey;
}
