/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
