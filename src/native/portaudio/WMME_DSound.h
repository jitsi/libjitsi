/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_WMME_DSOUND_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_WMME_DSOUND_H_

#include <portaudio.h>

/**
 * Retrieve a human-readable name for a specific <tt>PaDeviceInfo</tt> by
 * utilizing information from DirectSound. The implementation is provided in an
 * attempt to overcome a limitation of the legacy API employed by PortAudio's
 * WMME backend which limits the names of the devices to 32 characters.
 *
 * @param deviceInfo the <tt>PaDeviceInfo</tt> to retrieve a human-readable name
 * for by utilizing information from DirectSound
 * @return a human-readable name of the specified <tt>deviceInfo</tt> retrieved
 * by utilizing information from DirectSound or <tt>NULL</tt> if no such
 * information is available for the specified <tt>deviceInfo</tt>
 */
const char *WMME_DSound_DeviceInfo_getName(PaDeviceInfo *deviceInfo);

/**
 * Notifies the <tt>WMME_DSound</tt> module that PortAudio's
 * <tt>PaUpdateAvailableDeviceList()<tt> function has been invoked.
 * Frees/destroys the caches of the capture and playback DirectSound devices so
 * that they may be rebuilt and, consequently, up-to-date upon next use.
 */
void WMME_DSound_didUpdateAvailableDeviceList();

/**
 * Notifies the <tt>WMME_DSound</tt> module that the JNI library it is a part of
 * is loading.
 */
void WMME_DSound_load();

/**
 * Notifies the <tt>WMME_DSound</tt> module that the JNI library it is a part of
 * is unloading.
 */
void WMME_DSound_unload();

#endif /* #ifndef _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_WMME_DSOUND_H_ */
