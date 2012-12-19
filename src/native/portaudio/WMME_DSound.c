/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#ifdef _WIN32

#include "WMME_DSound.h"

#include <dsound.h>
#include <objbase.h>
#include <stdlib.h>
#include <string.h>

/**
 * The length in characters including the C string terminating NULL character of
 * a GUID represented as a C string of printable characters including enclosing
 * braces.
 */
#define GUID_LENGTH 39

/**
 * Represents the information related to a DirectSound device reported by
 * either the <tt>DirectSoundCaptureEnumerate</tt> function or the
 * <tt>DirectSoundEnumerate</tt> function.
 */
typedef struct _WMMEDSoundDeviceInfo
{
    /**
     * The textual description of the DirectSound device that this instance
     * provides information about. Represents the name of the device which is
     * to be displayed to the user.
     */
    char *description;
    /**
     * The GUID which (uniquely) identifies the DirectSound device that this
     * instance provides information about.
     */
    char guid[GUID_LENGTH];
    /**
     * The name of the module of the DirectSound driver corresponding to the
     * device this instance provides information about.
     */
    char *module;

    /**
     * The next <tt>WMMEDSoundDeviceInfo</tt> in the linked list of
     * <tt>WMMEDSoundDeviceInfo</tt>s that this instance is an element of.
     */
    struct _WMMEDSoundDeviceInfo *next;
}
WMMEDSoundDeviceInfo;

static BOOL CALLBACK WMME_DSound_dsEnumCallback(
        LPGUID guid, LPCSTR description, LPCSTR module,
        LPVOID context);
static WMMEDSoundDeviceInfo *WMME_DSound_findDeviceInfoByDeviceUID(
        WMMEDSoundDeviceInfo *deviceInfos,
        const char *deviceUID);
/**
 * Frees (the memory allocated to) a specific linked list of
 * <tt>WMMEDSoundDeviceInfo</tt>s.
 *
 * @param deviceInfos the linked list of <tt>WMMEDSoundDeviceInfo</tt>s to be
 * freed
 */
static void WMME_DSound_freeDeviceInfos(WMMEDSoundDeviceInfo **deviceInfos);

/**
 * The linked list of DirectSound capture devices enumerated till now and cached
 * so that it may be searched through for the purposes of, for example,
 * retrieving the non-truncated name of a <tt>PaDeviceInfo</tt> detected by
 * PortAudio's WMME backend.
 */
static WMMEDSoundDeviceInfo *WMME_DSound_captureDeviceInfos = NULL;
/**
 * The linked list of DirectSound playback devices enumerated till now and
 * cached so that it may be searched through for the purposes of, for example,
 * retrieving the non-truncated name of a <tt>PaDeviceInfo</tt> detected by
 * PortAudio's WMME backend.
 */
static WMMEDSoundDeviceInfo *WMME_DSound_playbackDeviceInfos = NULL;

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
const char *
WMME_DSound_DeviceInfo_getName(PaDeviceInfo *deviceInfo)
{
    const char *deviceUID = deviceInfo->deviceUID;
    const char *name = NULL;

    if (deviceUID)
    {
        if (deviceInfo->maxInputChannels)
        {
            if (!WMME_DSound_captureDeviceInfos)
            {
                HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);

                if ((S_OK == hr) || (S_FALSE == hr))
                {
                    DirectSoundCaptureEnumerateA(
                            WMME_DSound_dsEnumCallback,
                            (LPVOID) &WMME_DSound_captureDeviceInfos);
                    CoUninitialize();
                }
            }
            if (WMME_DSound_captureDeviceInfos)
            {
                WMMEDSoundDeviceInfo *match
                    = WMME_DSound_findDeviceInfoByDeviceUID(
                            WMME_DSound_captureDeviceInfos,
                            deviceUID);

                if (match)
                    name = match->description;
            }
        }
        if (!name && deviceInfo->maxOutputChannels)
        {
            if (!WMME_DSound_playbackDeviceInfos)
            {
                HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);

                if ((S_OK == hr) || (S_FALSE == hr))
                {
                    DirectSoundEnumerateA(
                            WMME_DSound_dsEnumCallback,
                            (LPVOID) &WMME_DSound_playbackDeviceInfos);
                    CoUninitialize();
                }
            }
            if (WMME_DSound_playbackDeviceInfos)
            {
                WMMEDSoundDeviceInfo *match
                    = WMME_DSound_findDeviceInfoByDeviceUID(
                            WMME_DSound_playbackDeviceInfos,
                            deviceUID);

                if (match)
                    name = match->description;
            }
        }
    }
    return name;
}

/**
 * Notifies the <tt>WMME_DSound</tt> module that PortAudio's
 * <tt>PaUpdateAvailableDeviceList()<tt> function has been invoked.
 * Frees/destroys the caches of the capture and playback DirectSound devices so
 * that they may be rebuilt and, consequently, up-to-date upon next use.
 */
void
WMME_DSound_didUpdateAvailableDeviceList()
{
    WMME_DSound_freeDeviceInfos(&WMME_DSound_captureDeviceInfos);
    WMME_DSound_freeDeviceInfos(&WMME_DSound_playbackDeviceInfos);
}

static BOOL CALLBACK
WMME_DSound_dsEnumCallback(
        LPGUID guid, LPCSTR description, LPCSTR module,
        LPVOID context)
{
    if (guid && description)
    {
        OLECHAR olestrGUID[GUID_LENGTH];

        if (StringFromGUID2(guid, olestrGUID, GUID_LENGTH))
        {
            WMMEDSoundDeviceInfo *deviceInfo
                = malloc(sizeof(WMMEDSoundDeviceInfo));

            if (deviceInfo)
            {
                if (WideCharToMultiByte(
                            CP_ACP,
                            0,
                            olestrGUID, -1,
                            deviceInfo->guid, GUID_LENGTH,
                            NULL,
                            NULL)
                        == GUID_LENGTH)
                {
                    deviceInfo->description = strdup(description);
                    if (deviceInfo->description)
                    {
                        WMMEDSoundDeviceInfo **deviceInfos
                            = (WMMEDSoundDeviceInfo **) context;

                        deviceInfo->module = strdup(module);

                        deviceInfo->next = *deviceInfos;
                        *deviceInfos = deviceInfo;
                    }
                    else
                        free(deviceInfo);
                }
                else
                    free(deviceInfo);
            }
        }
    }

    return TRUE;
}

static WMMEDSoundDeviceInfo *
WMME_DSound_findDeviceInfoByDeviceUID(
        WMMEDSoundDeviceInfo *deviceInfos,
        const char *deviceUID)
{
    while (deviceInfos)
    {
        if (strstr(deviceUID, deviceInfos->guid))
            return deviceInfos;
        else
        {
            const char *module = deviceInfos->module;

            if (module && strlen(module) && strstr(deviceUID, module))
                return deviceInfos;
        }

        deviceInfos = deviceInfos->next;
    }

    return NULL;
}

/**
 * Frees (the memory allocated to) a specific linked list of
 * <tt>WMMEDSoundDeviceInfo</tt>s.
 *
 * @param deviceInfos the linked list of <tt>WMMEDSoundDeviceInfo</tt>s to be
 * freed
 */
static void
WMME_DSound_freeDeviceInfos(WMMEDSoundDeviceInfo **deviceInfos)
{
    WMMEDSoundDeviceInfo *deviceInfo = *deviceInfos;

    while (deviceInfo)
    {
        char *description = deviceInfo->description;
        char *module = deviceInfo->module;
        WMMEDSoundDeviceInfo *next = deviceInfo->next;

        if (description)
            free(description);
        if (module)
            free(module);
        free(deviceInfo);
        deviceInfo = next;
    }
    *deviceInfos = NULL;
}

/**
 * Notifies the <tt>WMME_DSound</tt> module that the JNI library it is a part of
 * is loading.
 */
void
WMME_DSound_load()
{
    /* TODO Auto-generated method stub */
}

/**
 * Notifies the <tt>WMME_DSound</tt> module that the JNI library it is a part of
 * is unloading.
 */
void
WMME_DSound_unload()
{
    /*
     * WMME_DSound frees/destroys the cached DirectSound device information when
     * PortAudio is told to update its list of available devices. The very same
     * cleanup has to be performed when the JNI library is unloading.
     */
    WMME_DSound_didUpdateAvailableDeviceList();
}

#endif /* #ifdef _WIN32 */
