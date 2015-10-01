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
