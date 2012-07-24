/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_quicktime_NSDictionary.h"

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSValue.h>
#include <stdint.h>

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_quicktime_NSDictionary_intForKey
  (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong key)
{
    NSDictionary *dictionary;
    NSAutoreleasePool *autoreleasePool;
    NSNumber *value;
    jint jvalue;

    dictionary = (NSDictionary *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    value = [dictionary objectForKey:(id)(intptr_t)key];
    jvalue = value ? [value intValue] : 0;

    [autoreleasePool release];
    return jvalue;
}
