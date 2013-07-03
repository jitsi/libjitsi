/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_MEDIABUFFER_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_MEDIABUFFER_

#include "MinGW_dmo.h" /* IMediaBuffer */

typedef struct MediaBuffer MediaBuffer;

MediaBuffer *MediaBuffer_alloc(DWORD maxLength);
DWORD MediaBuffer_pop(MediaBuffer *thiz, BYTE *buffer, DWORD length);
DWORD MediaBuffer_push(MediaBuffer *thiz, BYTE *buffer, DWORD length);

#endif /* #ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_MEDIABUFFER_ */
