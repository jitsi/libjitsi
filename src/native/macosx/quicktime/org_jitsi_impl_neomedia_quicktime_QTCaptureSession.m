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

#include "org_jitsi_impl_neomedia_quicktime_QTCaptureSession.h"

#import <Foundation/NSAutoreleasePool.h>
#import <AVFoundation/AVCaptureInput.h>
#import <AVFoundation/AVCaptureOutput.h>

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_addInput
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong inputPtr)
{
    AVCaptureSession *captureSession;
    AVCaptureInput *input;
    NSAutoreleasePool *autoreleasePool;
    BOOL ret = NO;

    captureSession = (AVCaptureSession *) (intptr_t) ptr;
    input = (AVCaptureInput *) (intptr_t) inputPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if ([captureSession canAddInput:input])
    {
        [captureSession beginConfiguration];
        [captureSession addInput:input];
        [captureSession commitConfiguration];
    }

    [autoreleasePool release];
    return (YES == ret) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_addOutput
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong outputPtr)
{
    AVCaptureSession *captureSession;
    AVCaptureOutput *output;
    NSAutoreleasePool *autoreleasePool;
    BOOL ret = NO;

    captureSession = (AVCaptureSession *) (intptr_t) ptr;
    output = (AVCaptureOutput *) (intptr_t) outputPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if ([captureSession canAddOutput:output])
    {
        [captureSession beginConfiguration];
        [captureSession addOutput:output];
        [captureSession commitConfiguration];
    }

    [autoreleasePool release];
    return (YES == ret) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_allocAndInit
    (JNIEnv *jniEnv, jclass clazz)
{
    NSAutoreleasePool *autoreleasePool;
    AVCaptureSession *captureSession;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    captureSession = [[AVCaptureSession alloc] init];

    [autoreleasePool release];
    return (jlong) (intptr_t) captureSession;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_startRunning
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureSession *captureSession = (AVCaptureSession *) (intptr_t) ptr;
    [captureSession startRunning];
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_stopRunning
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureSession *captureSession = (AVCaptureSession *) (intptr_t) ptr;
    [captureSession stopRunning];
}
