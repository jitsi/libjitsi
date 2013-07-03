/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
