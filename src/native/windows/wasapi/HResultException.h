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

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_HRESULTEXCEPTION_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_HRESULTEXCEPTION_H_

#include <jni.h> /* jclass, jmethodID, JNIEnv */
#include <windows.h> /* HRESULT */

#ifdef _MSC_VER
#define __func__ __FUNCTION__
#endif /* #ifdef _MSC_VER */

extern jclass WASAPI_hResultExceptionClass;
extern jmethodID WASAPI_hResultExceptionMethodID;

void WASAPI_throwNewHResultException
    (JNIEnv *env, HRESULT hresult, const char *func, unsigned int line);

#endif /* #ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_HRESULTEXCEPTION_H_ */
