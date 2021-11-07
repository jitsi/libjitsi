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

#include "org_jitsi_impl_neomedia_quicktime_QTSampleBuffer.h"

#import <Foundation/NSAutoreleasePool.h>
#import <AVFoundation/AVFoundation.h>
#include <stdint.h>

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTSampleBuffer_bytesForAllSamples
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    CMSampleBufferRef sampleBuffer;
    NSAutoreleasePool *autoreleasePool;
    NSUInteger lengthForAllSamples;
    jbyteArray jBytesForAllSamples;

    sampleBuffer = (CMSampleBufferRef) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    lengthForAllSamples = CMSampleBufferGetTotalSampleSize(sampleBuffer);
    if (lengthForAllSamples)
    {
        jBytesForAllSamples
            = (*jniEnv)->NewByteArray(jniEnv, lengthForAllSamples);
        if (jBytesForAllSamples)
        {
            // TODO check for planar data like Chromium and https://github.com/BelledonneCommunications/mediastreamer2/pull/10/files ?
            jbyte *bytesForAllSamples = CMSampleBufferGetImageBuffer(sampleBuffer);

            (*jniEnv)
                ->SetByteArrayRegion(
                    jniEnv,
                    jBytesForAllSamples,
                    0,
                    lengthForAllSamples,
                    bytesForAllSamples);
        }
    }
    else
        jBytesForAllSamples = NULL;

    [autoreleasePool release];
    return jBytesForAllSamples;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTSampleBuffer_formatDescription
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    CMSampleBufferRef sampleBuffer
            = (CMSampleBufferRef) (intptr_t) ptr;
    CMFormatDescriptionRef formatDescription
            = CMSampleBufferGetFormatDescription(sampleBuffer);
    return (jlong) (intptr_t) formatDescription;
}
