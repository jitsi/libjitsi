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

#include "org_jitsi_impl_neomedia_quicktime_QTFormatDescription.h"

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSGeometry.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>
#import <QTKit/QTFormatDescription.h>
#include <stdint.h>

JNIEXPORT jobject JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTFormatDescription_sizeForKey
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jstring key)
{
    const char *cKey;
    jobject size = NULL;

    cKey = (const char *) (*jniEnv)->GetStringUTFChars(jniEnv, key, NULL);
    if (cKey)
    {
        QTFormatDescription *formatDescription;
        NSAutoreleasePool *autoreleasePool;
        NSString *oKey;
        NSValue *attribute;

        formatDescription = (QTFormatDescription *) (intptr_t) ptr;
        autoreleasePool = [[NSAutoreleasePool alloc] init];

        oKey = [NSString stringWithUTF8String:cKey];
        (*jniEnv)->ReleaseStringUTFChars(jniEnv, key, cKey);

        attribute = [formatDescription attributeForKey:oKey];
        if (attribute)
        {
            NSSize oSize;
            jclass dimensionClass;

            oSize = [attribute sizeValue];

            dimensionClass = (*jniEnv)->FindClass(jniEnv, "java/awt/Dimension");
            if (dimensionClass)
            {
                jmethodID dimensionCtorMethodID;

                dimensionCtorMethodID
                    = (*jniEnv)
                        ->GetMethodID(
                            jniEnv,
                            dimensionClass,
                            "<init>",
                            "(II)V");
                if (dimensionCtorMethodID)
                    size
                        = (*jniEnv)
                            ->NewObject(
                                jniEnv,
                                dimensionClass,
                                dimensionCtorMethodID,
                                (jint) oSize.width,
                                (jint) oSize.height);
            }
        }

        [autoreleasePool release];
    }
    return size;
}

JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTFormatDescription_VideoEncodedPixelsSizeAttribute
    (JNIEnv *jniEnv, jclass clazz)
{
    NSAutoreleasePool *autoreleasePool;
    jstring jstr;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    jstr
        = (*jniEnv)
            ->NewStringUTF(
                jniEnv,
                [QTFormatDescriptionVideoEncodedPixelsSizeAttribute
                    UTF8String]);

    [autoreleasePool release];
    return jstr;
}
