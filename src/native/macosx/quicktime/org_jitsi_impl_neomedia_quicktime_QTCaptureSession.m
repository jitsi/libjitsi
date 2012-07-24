/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_quicktime_QTCaptureSession.h"

#include "common.h"

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSError.h>
#import <QTKit/QTCaptureInput.h>
#import <QTKit/QTCaptureOutput.h>
#import <QTKit/QTCaptureSession.h>
#include <stdint.h>

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_addInput
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong inputPtr)
{
    QTCaptureSession *captureSession;
    QTCaptureInput *input;
    NSAutoreleasePool *autoreleasePool;
    BOOL ret;
    NSError *error;

    captureSession = (QTCaptureSession *) (intptr_t) ptr;
    input = (QTCaptureInput *) (intptr_t) inputPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    ret = [captureSession addInput:input error:&error];

    [autoreleasePool release];
    return (YES == ret) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_addOutput
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong outputPtr)
{
    QTCaptureSession *captureSession;
    QTCaptureOutput *output;
    NSAutoreleasePool *autoreleasePool;
    BOOL ret;
    NSError *error;

    captureSession = (QTCaptureSession *) (intptr_t) ptr;
    output = (QTCaptureOutput *) (intptr_t) outputPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    ret = [captureSession addOutput:output error:&error];

    [autoreleasePool release];
    return (YES == ret) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_allocAndInit
    (JNIEnv *jniEnv, jclass clazz)
{
    NSAutoreleasePool *autoreleasePool;
    QTCaptureSession *captureSession;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    captureSession = [[QTCaptureSession alloc] init];

    [autoreleasePool release];
    return (jlong) (intptr_t) captureSession;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_startRunning
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    NSObject_performSelector((id) (intptr_t) ptr, @"startRunning");
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureSession_stopRunning
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    NSObject_performSelector((id) (intptr_t) ptr, @"stopRunning");
}
