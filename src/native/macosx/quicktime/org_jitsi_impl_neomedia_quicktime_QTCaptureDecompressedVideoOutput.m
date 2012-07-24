/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput.h"

#import <CoreVideo/CVImageBuffer.h>
#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSObject.h>
#import <QTKit/QTCaptureDecompressedVideoOutput.h>
#import <QTKit/QTCaptureConnection.h>
#import <QTKit/QTCaptureOutput.h>
#import <QTKit/QTSampleBuffer.h>
#import <stdint.h>

@interface QTCaptureDecompressedVideoOutputDelegate : NSObject
{
@private
    jobject _delegate;
    JavaVM *_vm;
}

- (void)captureOutput:(QTCaptureOutput *)captureOutput
        didOutputVideoFrame:(CVImageBufferRef *)videoFrame
        withSampleBuffer:(QTSampleBuffer *)sampleBuffer
        fromConnection:(QTCaptureConnection *)connection;
- (void)dealloc;
- (id)init;
- (void)setDelegate:(jobject)delegate inJNIEnv:(JNIEnv *)jniEnv;

@end

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_allocAndInit
    (JNIEnv *jniEnv, jclass clazz)
{
    NSAutoreleasePool *autoreleasePool;
    QTCaptureDecompressedVideoOutput *captureDecompressedVideoOutput;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    captureDecompressedVideoOutput
        = [[QTCaptureDecompressedVideoOutput alloc] init];

    [autoreleasePool release];
    return (jlong) (intptr_t) captureDecompressedVideoOutput;
}

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_minimumVideoFrameInterval
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    QTCaptureDecompressedVideoOutput *captureDecompressedVideoOutput;
    NSAutoreleasePool *autoreleasePool;
    NSTimeInterval minimumVideoFrameInterval;

    captureDecompressedVideoOutput = (QTCaptureDecompressedVideoOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    minimumVideoFrameInterval
        = [captureDecompressedVideoOutput minimumVideoFrameInterval];

    [autoreleasePool release];
    return (jdouble) minimumVideoFrameInterval;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_pixelBufferAttributes
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    QTCaptureDecompressedVideoOutput *captureDecompressedVideoOutput;
    NSAutoreleasePool *autoreleasePool;
    NSDictionary *pixelBufferAttributes;

    captureDecompressedVideoOutput = (QTCaptureDecompressedVideoOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    pixelBufferAttributes
        = [captureDecompressedVideoOutput pixelBufferAttributes];
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
    QTCaptureDecompressedVideoOutput *captureDecompressedVideoOutput;
    NSAutoreleasePool *autoreleasePool;

    captureDecompressedVideoOutput = (QTCaptureDecompressedVideoOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if ([captureDecompressedVideoOutput
            respondsToSelector:@selector(setAutomaticallyDropsLateVideoFrames)])
    {
        [captureDecompressedVideoOutput
            setAutomaticallyDropsLateVideoFrames:
                ((JNI_TRUE == automaticallyDropsLateVideoFrames) ? YES : NO)];
        automaticallyDropsLateVideoFrames
            = [captureDecompressedVideoOutput automaticallyDropsLateVideoFrames];
    }
    else
        automaticallyDropsLateVideoFrames = JNI_FALSE;

    [autoreleasePool release];
    return automaticallyDropsLateVideoFrames;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_setDelegate
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jobject delegate)
{
    QTCaptureDecompressedVideoOutput *captureDecompressedVideoOutput;
    NSAutoreleasePool *autoreleasePool;
    QTCaptureDecompressedVideoOutputDelegate *oDelegate;
    id oPrevDelegate;

    captureDecompressedVideoOutput = (QTCaptureDecompressedVideoOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if (delegate)
    {
        oDelegate = [[QTCaptureDecompressedVideoOutputDelegate alloc] init];
        [oDelegate setDelegate:delegate inJNIEnv:jniEnv];
    }
    else
        oDelegate = nil;
    oPrevDelegate = [captureDecompressedVideoOutput delegate];
    if (oDelegate != oPrevDelegate)
    {
        [captureDecompressedVideoOutput setDelegate:oDelegate];
        if (oPrevDelegate)
            [oPrevDelegate release];
    }

    [autoreleasePool release];
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_setMinimumVideoFrameInterval
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jdouble minimumVideoFrameInterval)
{
    QTCaptureDecompressedVideoOutput *captureDecompressedVideoOutput;
    NSAutoreleasePool *autoreleasePool;

    captureDecompressedVideoOutput = (QTCaptureDecompressedVideoOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    [captureDecompressedVideoOutput
	    setMinimumVideoFrameInterval:(NSTimeInterval)minimumVideoFrameInterval];

    [autoreleasePool release];
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput_setPixelBufferAttributes
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong pixelBufferAttributesPtr)
{
    QTCaptureDecompressedVideoOutput *captureDecompressedVideoOutput;
    NSDictionary *pixelBufferAttributes;
    NSAutoreleasePool *autoreleasePool;

    captureDecompressedVideoOutput = (QTCaptureDecompressedVideoOutput *) (intptr_t) ptr;
    pixelBufferAttributes = (NSDictionary *) (intptr_t) pixelBufferAttributesPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    [captureDecompressedVideoOutput
        setPixelBufferAttributes:pixelBufferAttributes];

    [autoreleasePool release];
}

@implementation QTCaptureDecompressedVideoOutputDelegate

- (void)captureOutput:(QTCaptureOutput *)captureOutput
        didOutputVideoFrame:(CVImageBufferRef *)videoFrame
        withSampleBuffer:(QTSampleBuffer *)sampleBuffer
        fromConnection:(QTCaptureConnection *)connection
{
    jobject delegate;
    JavaVM *vm;
    JNIEnv *jniEnv;
    jclass delegateClass;

    delegate = self->_delegate;
    if (!delegate)
        return;

    vm = self->_vm;
    if (0 != (*vm)->AttachCurrentThreadAsDaemon(vm, (void **) &jniEnv, NULL))
        return;

    delegateClass = (*jniEnv)->GetObjectClass(jniEnv, delegate);
    if (delegateClass)
    {
        jmethodID didOutputVideoFrameWithSampleBufferMethodID;

        didOutputVideoFrameWithSampleBufferMethodID
            = (*jniEnv)
                ->GetMethodID(
                    jniEnv,
                    delegateClass,
                    "outputVideoFrameWithSampleBuffer",
                    "(JJ)V");
        if (didOutputVideoFrameWithSampleBufferMethodID)
            (*jniEnv)->CallVoidMethod(
                    jniEnv,
                    delegate,
                    didOutputVideoFrameWithSampleBufferMethodID,
                    (jlong) (intptr_t) videoFrame,
                    (jlong) (intptr_t) sampleBuffer);
    }
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
        self->_delegate = NULL;
        self->_vm = NULL;
    }
    return self;
}

- (void)setDelegate:(jobject)delegate inJNIEnv:(JNIEnv *)jniEnv
{
    if (self->_delegate)
    {
        if (!jniEnv)
            (*(self->_vm))->AttachCurrentThread(self->_vm, (void **) &jniEnv, NULL);
        (*jniEnv)->DeleteGlobalRef(jniEnv, self->_delegate);
        self->_delegate = NULL;
        self->_vm = NULL;
    }
    if (delegate)
    {
        delegate = (*jniEnv)->NewGlobalRef(jniEnv, delegate);
        if (delegate)
        {
            (*jniEnv)->GetJavaVM(jniEnv, &(self->_vm));
            self->_delegate = delegate;
        }
    }
}

@end
