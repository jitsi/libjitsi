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

#include "org_jitsi_impl_neomedia_quicktime_QTCaptureDeviceInput.h"

#import <Foundation/NSException.h>
#import <AVFoundation/AVFoundation.h>

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDeviceInput_deviceInputWithDevice
    (JNIEnv *jniEnv, jclass clazz, jlong devicePtr)
{
    NSError *error = nil;
    AVCaptureDevice *device;
    NSAutoreleasePool *autoreleasePool;
    id deviceInput;

    device = (AVCaptureDevice *) (intptr_t) devicePtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    @try
    {
        deviceInput = [AVCaptureDeviceInput deviceInputWithDevice:device error:&error];
        if (error)
        {
            return 0l;
        }
    }
    @catch (NSException *ex)
    {
        deviceInput = nil;
    }
    if (deviceInput)
        [deviceInput retain];

    [autoreleasePool release];
    return (jlong) (intptr_t) deviceInput;
}
