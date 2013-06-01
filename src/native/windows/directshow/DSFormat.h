/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file DSFormat.h
 * \brief Useful structures and enumerations for video/DirectShow format.
 * \author Sebastien Vincent
 */

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSFORMAT_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSFORMAT_H_

/**
 * \struct DSFormat
 * \brief Information about video/DirectShow format
 */
struct DSFormat
{
    size_t width; /**< Video width */
    size_t height; /**< Video height */
    DWORD pixelFormat; /**< Pixel format */
    GUID mediaType; /**< Media type */
};

#endif /* _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSFORMAT_H_ */
