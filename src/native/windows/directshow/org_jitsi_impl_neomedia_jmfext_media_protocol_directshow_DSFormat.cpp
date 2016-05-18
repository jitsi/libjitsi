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

#include "org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSFormat.h"

#include <windows.h>
#include <dshow.h>
#include <uuids.h>

#define DEFINE_DSFORMAT_PIXELFORMAT(pixFmt) \
    JNIEXPORT jint JNICALL \
    Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSFormat_##pixFmt \
        (JNIEnv *env, jclass clazz) \
    { \
        return MEDIASUBTYPE_##pixFmt.Data1; \
    }

#ifndef MEDIASUBTYPE_I420
#include <wmsdkidl.h>
#define MEDIASUBTYPE_I420 WMMEDIASUBTYPE_I420
#endif

DEFINE_DSFORMAT_PIXELFORMAT(ARGB32)
DEFINE_DSFORMAT_PIXELFORMAT(AYUV)
DEFINE_DSFORMAT_PIXELFORMAT(I420)
DEFINE_DSFORMAT_PIXELFORMAT(IF09)
DEFINE_DSFORMAT_PIXELFORMAT(IMC1)
DEFINE_DSFORMAT_PIXELFORMAT(IMC2)
DEFINE_DSFORMAT_PIXELFORMAT(IMC3)
DEFINE_DSFORMAT_PIXELFORMAT(IMC4)
DEFINE_DSFORMAT_PIXELFORMAT(IYUV)
DEFINE_DSFORMAT_PIXELFORMAT(MJPG)
DEFINE_DSFORMAT_PIXELFORMAT(NV12)
DEFINE_DSFORMAT_PIXELFORMAT(RGB24)
DEFINE_DSFORMAT_PIXELFORMAT(RGB32)
DEFINE_DSFORMAT_PIXELFORMAT(UYVY)
DEFINE_DSFORMAT_PIXELFORMAT(Y211)
DEFINE_DSFORMAT_PIXELFORMAT(Y411)
DEFINE_DSFORMAT_PIXELFORMAT(Y41P)
DEFINE_DSFORMAT_PIXELFORMAT(YUY2)
DEFINE_DSFORMAT_PIXELFORMAT(YV12)
DEFINE_DSFORMAT_PIXELFORMAT(YVU9)
DEFINE_DSFORMAT_PIXELFORMAT(YVYU)
