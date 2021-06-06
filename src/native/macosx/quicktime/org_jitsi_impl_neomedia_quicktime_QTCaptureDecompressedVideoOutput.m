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

#include "org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput.h"

#import <CoreVideo/CVImageBuffer.h>
#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSObject.h>
#import <AVFoundation/AVFoundation.h>
#import <stdint.h>

@interface QTCaptureDecompressedVideoOutputDelegate : NSObject<AVCaptureVideoDataOutputSampleBufferDelegate>
{
@private
    jobject _delegate;
    JavaVM *_vm;
    jmethodID _didOutputVideoFrameWithSampleBufferMethodID;
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput
        didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
        fromConnection:(AVCaptureConnection *)connection;
- (void)dealloc;
- (id)init;
- (void)setDelegate:(jobject)delegate inJNIEnv:(JNIEnv *)jniEnv;

@end

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_allocAndInit
    (JNIEnv *jniEnv, jclass clazz)
{
    NSAutoreleasePool *autoreleasePool;
    AVCaptureVideoDataOutput *captureDecompressedVideoOutput;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    captureDecompressedVideoOutput
        = [[AVCaptureVideoDataOutput alloc] init];

    [autoreleasePool release];
    return (jlong) (intptr_t) captureDecompressedVideoOutput;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_pixelBufferAttributes
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureVideoDataOutput *captureDecompressedVideoOutput;
    NSAutoreleasePool *autoreleasePool;
    NSDictionary<NSString *, id> *pixelBufferAttributes;

    captureDecompressedVideoOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    pixelBufferAttributes
        = [captureDecompressedVideoOutput videoSettings];
    if (pixelBufferAttributes)
        [pixelBufferAttributes retain];

    [autoreleasePool release];
    return (jlong) (intptr_t) pixelBufferAttributes;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_setAutomaticallyDropsLateVideoFrames
    (JNIEnv *jniEnv, jclass clazz, jlong ptr,
        jboolean automaticallyDropsLateVideoFrames)
{
    AVCaptureVideoDataOutput *captureDecompressedVideoOutput;
    NSAutoreleasePool *autoreleasePool;

    captureDecompressedVideoOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    [captureDecompressedVideoOutput
        setAlwaysDiscardsLateVideoFrames:
            ((JNI_TRUE == automaticallyDropsLateVideoFrames) ? YES : NO)];
    automaticallyDropsLateVideoFrames
        = [captureDecompressedVideoOutput alwaysDiscardsLateVideoFrames] == YES
                ? JNI_TRUE
                : JNI_FALSE;

    [autoreleasePool release];
    return automaticallyDropsLateVideoFrames;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_setDelegate
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jobject delegate)
{
    AVCaptureVideoDataOutput *captureDecompressedVideoOutput;
    NSAutoreleasePool *autoreleasePool;
    QTCaptureDecompressedVideoOutputDelegate *oDelegate;
    id oPrevDelegate;

    captureDecompressedVideoOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if (delegate)
    {
        oDelegate = [[QTCaptureDecompressedVideoOutputDelegate alloc] init];
        [oDelegate setDelegate:delegate inJNIEnv:jniEnv];
    }
    else
        oDelegate = nil;

    oPrevDelegate = [captureDecompressedVideoOutput sampleBufferDelegate];
    if (oDelegate != oPrevDelegate)
    {
        [captureDecompressedVideoOutput
                setSampleBufferDelegate:oDelegate
                                  queue:dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0)];
        if (oPrevDelegate)
            [oPrevDelegate release];
    }

    [autoreleasePool release];
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_setPixelBufferAttributes
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong pixelBufferAttributesPtr)
{
    AVCaptureVideoDataOutput *captureDecompressedVideoOutput;
    NSDictionary<NSString *, id> *pixelBufferAttributes;
    NSAutoreleasePool *autoreleasePool;

    captureDecompressedVideoOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    pixelBufferAttributes = (NSDictionary *) (intptr_t) pixelBufferAttributesPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    [captureDecompressedVideoOutput
        setVideoSettings:pixelBufferAttributes];

    [autoreleasePool release];
}

@implementation QTCaptureDecompressedVideoOutputDelegate

- (void)captureOutput:(AVCaptureOutput *)captureOutput
        didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
        fromConnection:(AVCaptureConnection *)connection
{
    jobject delegate = _delegate;
    if (!delegate)
        return;

    JavaVM *vm = _vm;
    JNIEnv *jniEnv = NULL;
    if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void **) &jniEnv, NULL) != JNI_OK || jniEnv == NULL)
        return;

    CVImageBufferRef videoFrame = CMSampleBufferGetImageBuffer(sampleBuffer);
    (*jniEnv)->CallVoidMethod(
            jniEnv,
            _delegate,
            _didOutputVideoFrameWithSampleBufferMethodID,
            (jlong) (intptr_t) videoFrame,
            (jlong) (intptr_t) sampleBuffer);
    (*jniEnv)->ExceptionClear(jniEnv);
}

- (void)dealloc
{
    [self setDelegate:NULL inJNIEnv:NULL];
    [super dealloc];
}

- (id)init
{
    if ((self = [super init]))
    {
        _delegate = NULL;
        _vm = NULL;
        _didOutputVideoFrameWithSampleBufferMethodID = NULL;
    }
    return self;
}

- (void)setDelegate:(jobject)delegate inJNIEnv:(JNIEnv *)jniEnv
{
    // release existing references
    if (_delegate)
    {
        if (!jniEnv && _vm)
        {
            (*(_vm))->AttachCurrentThread(_vm, (void **) &jniEnv, NULL);
        }
        if (jniEnv)
        {
            (*jniEnv)->DeleteGlobalRef(jniEnv, _delegate);
        }

        _delegate = NULL;
        _vm = NULL;
        _didOutputVideoFrameWithSampleBufferMethodID = NULL;
    }

    if (delegate && jniEnv)
    {
        (*jniEnv)->GetJavaVM(jniEnv, &_vm);
        if (!_vm)
        {
            return;
        }

        delegate = (*jniEnv)->NewGlobalRef(jniEnv, delegate);
        if (delegate)
        {
            jclass delegateClass
                    = (*jniEnv)->GetObjectClass(jniEnv, delegate);
            _didOutputVideoFrameWithSampleBufferMethodID
                    = (*jniEnv)->GetMethodID(
                        jniEnv,
                        delegateClass,
                        "outputVideoFrameWithSampleBuffer",
                        "(JJ)V");
            if (_didOutputVideoFrameWithSampleBufferMethodID)
            {
                _delegate = delegate;
            }
            else
            {
                (*jniEnv)->DeleteGlobalRef(jniEnv, delegate);
            }
        }
    }
}

@end
