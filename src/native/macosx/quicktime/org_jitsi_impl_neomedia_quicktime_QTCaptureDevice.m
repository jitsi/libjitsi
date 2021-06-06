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

#include "org_jitsi_impl_neomedia_quicktime_QTCaptureDevice.h"

#include <string.h>

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSError.h>
#import <AVFoundation/AVFoundation.h>

jstring QTCaptureDevice_getString(JNIEnv *, jlong, NSString *);
NSString * QTCaptureDevice_jstringToMediaType(JNIEnv *, jobject);
jlongArray QTCaptureDevice_nsArrayToJlongArray(JNIEnv *, NSArray *);

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_setVideoMinFrameDuration
        (JNIEnv *jniEnv, jclass clazz, jlong ptr, jdouble videoMinframeDuration)
{
    AVCaptureDevice *captureDevice = (AVCaptureDevice *) (intptr_t) ptr;
    [captureDevice setActiveVideoMinFrameDuration:CMTimeMakeWithSeconds(videoMinframeDuration, 1000)];
    return CMTimeGetSeconds([captureDevice activeVideoMinFrameDuration]);
}

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_getVideoMinFrameDuration
        (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureDevice *captureDevice = (AVCaptureDevice *) (intptr_t) ptr;
    return CMTimeGetSeconds([captureDevice activeVideoMinFrameDuration]);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_close
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    //TODO do something here?
    //AVCaptureDevice *captureDevice = (AVCaptureDevice *) (intptr_t) ptr;
    //[captureDevice close];
}

JNIEXPORT jlongArray JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_formatDescriptions
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureDevice *captureDevice;
    NSAutoreleasePool *autoreleasePool;
    NSArray<AVCaptureDeviceFormat *> *formatDescriptions;
    jlongArray formatDescriptionPtrs;

    captureDevice = (AVCaptureDevice *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    formatDescriptions = [captureDevice formats];
    formatDescriptionPtrs
        = QTCaptureDevice_nsArrayToJlongArray(jniEnv, formatDescriptions);

    [autoreleasePool release];
    return formatDescriptionPtrs;
}

JNIEXPORT jlongArray JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_inputDevicesWithMediaType
    (JNIEnv *jniEnv, jclass clazz, jstring mediaType)
{
    NSAutoreleasePool *autoreleasePool;
    NSArray<AVCaptureDevice *> *inputDevices;
    jlongArray inputDevicePtrs;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    inputDevices
        = [AVCaptureDevice
            devicesWithMediaType:
                QTCaptureDevice_jstringToMediaType(jniEnv, mediaType)];
    inputDevicePtrs = QTCaptureDevice_nsArrayToJlongArray(jniEnv, inputDevices);

    [autoreleasePool release];
    return inputDevicePtrs;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_isConnected
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureDevice *captureDevice = (AVCaptureDevice *) (intptr_t) ptr;
    return [captureDevice isConnected] == YES ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_localizedDisplayName
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    return QTCaptureDevice_getString (jniEnv, ptr, @"localizedName");
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_open
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureDevice *captureDevice;
    NSAutoreleasePool *autoreleasePool;
    BOOL ret;
    NSError *error;

    captureDevice = (AVCaptureDevice *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    ret = YES; //TODO ??? [captureDevice open:&error];

    [autoreleasePool release];
    return (YES == ret) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_quicktime_QTCaptureDevice_uniqueID
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    return QTCaptureDevice_getString (jniEnv, ptr, @"uniqueID");
}

jstring
QTCaptureDevice_getString(JNIEnv *jniEnv, jlong ptr, NSString *selectorName)
{
    id obj;
    NSAutoreleasePool *autoreleasePool;
    SEL selector;
    NSString *str;
    jstring jstr;

    obj = (id) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    selector = NSSelectorFromString(selectorName);
    if (selector)
    {
        str = [obj performSelector:selector];
        jstr = str ? (*jniEnv)->NewStringUTF(jniEnv, [str UTF8String]) : NULL;
    }
    else
        jstr = NULL;

    [autoreleasePool release];
    return jstr;
}

NSString *
QTCaptureDevice_jstringToMediaType(JNIEnv *jniEnv, jstring str)
{
    const char *cstr;
    NSString *mediaType;

    cstr = (const char *) (*jniEnv)->GetStringUTFChars (jniEnv, str, NULL);
    if (cstr)
    {
        if (0 == strcmp ("Muxed", cstr))
            mediaType = AVMediaTypeMuxed;
        else if (0 == strcmp ("Sound", cstr))
            mediaType = AVMediaTypeAudio;
        else if (0 == strcmp ("Video", cstr))
            mediaType = AVMediaTypeVideo;
        else
            mediaType = nil;
        (*jniEnv)->ReleaseStringUTFChars (jniEnv, str, cstr);
    }
    else
        mediaType = nil;
    return mediaType;
}

jlongArray
QTCaptureDevice_nsArrayToJlongArray(JNIEnv *jniEnv, NSArray *oArray)
{
    jlongArray jArray;

    if (oArray)
    {
        NSUInteger count;

        count = [oArray count];
        jArray = (*jniEnv)->NewLongArray(jniEnv, count);
        if (jArray)
        {
            NSUInteger i;

            for (i = 0; i < count; i++)
            {
                id obj;
                jlong ptr;

                obj = oArray[i];
                ptr = (jlong) (intptr_t) obj;
                (*jniEnv)->SetLongArrayRegion(jniEnv, jArray, i, 1, &ptr);
                [obj retain];
                if ((*jniEnv)->ExceptionCheck(jniEnv))
                {
                    NSUInteger j;

                    for (j = 0; j < i; j++)
                        [oArray[j] release];
                    break;
                }
            }
        }
    }
    else
        jArray = NULL;
    return jArray;
}
