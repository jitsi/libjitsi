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

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_TYPECASTING_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_TYPECASTING_H_

#include <jni.h> /* JNIEnv, jstring */
#include <windows.h> /* HRESULT */

#ifndef __uuidof
#define __uuidof(i) &i
#endif /* #ifndef __uuidof */

HRESULT WASAPI_iidFromString(JNIEnv *env, jstring str, LPIID iid);

#endif /* #ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_TYPECASTING_H_ */
