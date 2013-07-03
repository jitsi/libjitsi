/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
