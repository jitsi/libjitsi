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

#include "org_jitsi_impl_neomedia_quicktime_QTFormatDescription.h"

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSGeometry.h>
#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CoreMedia.h>

JNIEXPORT jobject JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTFormatDescription_size
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    jobject size = NULL;
    jclass dimensionClass = (*jniEnv)->FindClass(jniEnv, "java/awt/Dimension");
    if (dimensionClass)
    {
        AVCaptureDeviceFormat *captureDeviceFormat = (AVCaptureDeviceFormat *) (intptr_t) ptr;
        NSAutoreleasePool *autoreleasePool = [[NSAutoreleasePool alloc] init];
        CMVideoDimensions attribute = CMVideoFormatDescriptionGetDimensions(captureDeviceFormat.formatDescription);

        jmethodID dimensionCtorMethodID
            = (*jniEnv)
                ->GetMethodID(
                    jniEnv,
                    dimensionClass,
                    "<init>",
                    "(II)V");
        if (dimensionCtorMethodID)
            size
                = (*jniEnv)
                    ->NewObject(
                        jniEnv,
                        dimensionClass,
                        dimensionCtorMethodID,
                        (jint) attribute.width,
                        (jint) attribute.height);

        [autoreleasePool release];
    }

    return size;
}
