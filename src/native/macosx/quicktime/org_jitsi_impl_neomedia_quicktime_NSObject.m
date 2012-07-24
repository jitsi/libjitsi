/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_quicktime_NSObject.h"

#include "common.h"
#include <stdint.h>

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_NSObject_release
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    NSObject_performSelector((id) (intptr_t) ptr, @"release");
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_NSObject_retain
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    NSObject_performSelector((id) (intptr_t) ptr, @"retain");
}
