/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_quicktime_NSMutableDictionary.h"

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSValue.h>
#include <stdint.h>

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_quicktime_NSMutableDictionary_allocAndInit
    (JNIEnv *jniEnv, jclass clazz)
{
    NSAutoreleasePool *autoreleasePool;
    NSMutableDictionary *mutableDictionary;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    mutableDictionary = [[NSMutableDictionary alloc] init];

    [autoreleasePool release];
    return (jlong) (intptr_t) mutableDictionary;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_NSMutableDictionary_setIntForKey
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jint value, jlong key)
{
    NSMutableDictionary *mutableDictionary;
    NSAutoreleasePool *autoreleasePool;

    mutableDictionary = (NSMutableDictionary *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    [mutableDictionary setObject:[NSNumber numberWithInt:value] forKey:(id)(intptr_t)key];

    [autoreleasePool release];
}
