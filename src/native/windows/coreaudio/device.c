/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#include "device.h"

#include <initguid.h>
#include <stdio.h>
#include <tchar.h>

#include <windows.h>
#include <propkeydef.h> // Must be defined after windows.h

#include <commctrl.h> // Must be defined after mmdeviceapi.h
#include <endpointvolume.h> // Must be defined after mmdeviceapi.h


/**
 * Functions to list, access and modifies audio devices via coreaudio.
 *
 * @author Vincent Lucas
 */

/**
 * Private definition of functions,
 */
IAudioEndpointVolume * getEndpointVolume(
        const char * deviceUID);

void freeEndpointVolume(
        IAudioEndpointVolume * endpointVolume);

char* getDeviceDescription(
        const char * deviceUID);

char* getDeviceInterfaceName(
        const char * deviceUID);

char* getDeviceProperty(
        const char * deviceUID,
        PROPERTYKEY propertyKey);

int setDeviceVolume(
        const char * deviceUID,
        float volume);

float getDeviceVolume(
        const char * deviceUID);

// Definitions find in "functiondiscoverykeys_devpkey.h" but not found by
// MINGW64 TDM-gcc compiler.
DEFINE_PROPERTYKEY(PKEY_DeviceInterface_FriendlyName,  0x026e516e, 0xb814, 0x414b, 0x83, 0xcd, 0x85, 0x6d, 0x6f, 0xef, 0x48, 0x22, 2); // DEVPROP_TYPE_STRING
DEFINE_PROPERTYKEY(PKEY_Device_DeviceDesc,             0xa45c254e, 0xdf1c, 0x4efd, 0x80, 0x20, 0x67, 0xd1, 0x46, 0xa8, 0x50, 0xe0, 2);     // DEVPROP_TYPE_STRING
DEFINE_PROPERTYKEY(PKEY_Device_FriendlyName,           0xa45c254e, 0xdf1c, 0x4efd, 0x80, 0x20, 0x67, 0xd1, 0x46, 0xa8, 0x50, 0xe0, 14);    // DEVPROP_TYPE_STRING


DEFINE_PROPERTYKEY(PKEY_Device_BusNumber,              0xa45c254e, 0xdf1c, 0x4efd, 0x80, 0x20, 0x67, 0xd1, 0x46, 0xa8, 0x50, 0xe0, 23);    // DEVPROP_TYPE_UINT32

/**
 * Initializes the COM component. This function must be called first in order to
 * able each following function to work correctly. Once finished, the caller of
 * this function must call the "freeDevices" function.
 *
 * @return 0 if everything is OK. -1 if an error has occured.
 */
int initDevices(void)
{
    if(CoInitializeEx(NULL, COINIT_MULTITHREADED) != S_OK)
    {
        fprintf(stderr,
                "initDevices (coreaudio/device.c): \
                    \n\tCoInitialize\n");
        fflush(stderr);
        return -1;
    }
    return 0;
}

/**
 * Frees the resources used by the COM component. This function must be called
 * last.
 */
void freeDevices(void)
{
    CoUninitialize();
}

/**
 * Returns the audio device corresponding to the UID given in parameter. Or
 * NULL if the device is nonexistant or if anything as failed. The device must
 * be freed by the caller of this function.
 *
 * @param deviceUID The device UID.
 *
 * @return The audio device corresponding to the UID given in parameter. Or
 * NULL if the device is nonexistant or if anything as failed.
 */
IMMDevice * getDevice(
        const char * deviceUID)
{
    // Gets the enumerator of the system devices.
    HRESULT err;
    IMMDeviceEnumerator * enumerator = NULL;

    if((err = CoCreateInstance(
                CLSID_MMDeviceEnumerator,
                NULL,
                CLSCTX_ALL,
                IID_IMMDeviceEnumerator,
                (void**) &enumerator))
            != S_OK)
    {
        fprintf(stderr,
                "getDevice (coreaudio/device.c): \
                    \n\tCoCreateInstance\n");
        fflush(stderr);
        if(err == REGDB_E_CLASSNOTREG)
        {
            fprintf(stderr,
                    "getDevice (coreaudio/device.c): \
                        \n\tCoCreateInstance: REGDB_E_CLASSNOTREG\n");
            fflush(stderr);
        }
        else if(err == CLASS_E_NOAGGREGATION)
        {
            fprintf(stderr,
                    "getDevice (coreaudio/device.c): \
                        \n\tCoCreateInstance: CLASS_E_NOAGGREGATION\n");
            fflush(stderr);
        }
        else if(err == E_NOINTERFACE)
        {
            fprintf(stderr,
                    "getDevice (coreaudio/device.c): \
                        \n\tCoCreateInstance: E_NOINTERFACE\n");
            fflush(stderr);
        }
        else if(err == E_POINTER)
        {
            fprintf(stderr,
                    "getDevice (coreaudio/device.c): \
                        \n\tCoCreateInstance: E_POINTER\n");
            fflush(stderr);
        }
        return NULL;
    }

    // Gets the requested device selected by its UID.
    IMMDevice *device = NULL;
    size_t deviceUIDLength = strlen(deviceUID);
    wchar_t wCharDeviceUID[deviceUIDLength + 1];
    if(mbstowcs(wCharDeviceUID, deviceUID, deviceUIDLength + 1)
            != deviceUIDLength)
    {
        fprintf(stderr,
                "getDevice (coreaudio/device.c): \
                    \n\tmbstowcs\n");
        fflush(stderr);
        return NULL;
    }
    if(enumerator->GetDevice(wCharDeviceUID, &device) != S_OK)
    {
        fprintf(stderr,
                "getDevice (coreaudio/device.c): \
                    \n\tIMMDeviceEnumerator.GetDevice\n");
        fflush(stderr);
        return NULL;
    }

    return device;
}

/**
 * Frees an audio device returned by the function getDevice.
 *
 * @param device The audio device
 */
void freeDevice(
        IMMDevice * device)
{
    device->Release();
}

/**
 * Returns the audio device volume endpoint corresponding to the UID given in
 * parameter. Or NULL if the endpoint is nonexistant or if anything as failed.
 * The endpoint must be freed by the caller of this function.
 *
 * @param deviceUID The device UID.
 *
 * @return the audio device volume endpoint corresponding to the UID given in
 * parameter. Or NULL if the endpoint is nonexistant or if anything as failed.
 */
IAudioEndpointVolume * getEndpointVolume(
        const char * deviceUID)
{
    // Gets the device corresponding to its UID.
    IMMDevice * device = getDevice(deviceUID);
    if(device == NULL)
    {
        fprintf(stderr,
                "getEndpointVolume (coreaudio/device.c): \
                    \n\tgetDevice\n");
        fflush(stderr);
        return NULL;
    }

    // retrives the volume endpoint.
    IAudioEndpointVolume *endpointVolume = NULL;
    if(device->Activate(
            IID_IAudioEndpointVolume,
            CLSCTX_ALL,
            NULL,
            (void**) &endpointVolume) != S_OK)
    {
        fprintf(stderr,
                "getEndpointVolume (coreaudio/device.c): \
                    \n\tIMMDevice.Activate\n");
        fflush(stderr);
        return NULL;
    }

    // Frees the device.
    freeDevice(device);

    return endpointVolume;
}

/**
 * Frees an audio device volume endpoint returned by the function
 * getEndpointVolume.
 *
 * @param endpointVolume The audio device volume endpoint.
 */
void freeEndpointVolume(
        IAudioEndpointVolume * endpointVolume)
{
    endpointVolume->Release();
}

/**
 * Returns the device name for the given device. Or NULL, if not available. The
 * returned string must be freed by the caller. The device name is composed of
 * the device description and of the device interface name.
 *
 * @param device The device to get the name from.
 *
 * @return The device name for the given device. Or NULL, if not available. The
 * returned string must be freed by the caller.
 */
char* getDeviceName(
        const char * deviceUID)
{
    return getDeviceProperty(deviceUID, PKEY_Device_FriendlyName);
}

/**
 * Returns the device model identifier for the given device. Or NULL, if not
 * available. The returned string must be freed by the caller.
 *
 * @param device The device to get the name from.
 *
 * @return The device model identifier for the given device. Or NULL, if not
 * available. The returned string must be freed by the caller.
 */
char* getDeviceModelIdentifier(
        const char * deviceUID)
{
    int deviceModelIdentifierLength;
    char * deviceModelIdentifier = NULL;
    char * deviceDescription;
    char * deviceInterface;
    char * genericDeviceInterface;

    if((deviceDescription = getDeviceDescription(deviceUID)) == NULL)
    {
        fprintf(stderr,
                "getDeviceModelIdentifier (coreaudio/device.c): \
                    \n\tgetDeviceDescription\n");
        fflush(stderr);
        return NULL;
    }
    if((deviceInterface = getDeviceInterfaceName(deviceUID)) == NULL)
    {
        fprintf(stderr,
                "getDeviceModelIdentifier (coreaudio/device.c): \
                    \n\tgetDeviceInterfaceName\n");
        fflush(stderr);
        return NULL;
    }

    // A USB device (without a serial ID) puts into a USB port will add the
    // port number (if greater than 1) with the following prefix: "X- "
    // (with X the port number).
    genericDeviceInterface = deviceInterface;
    // First skip the number at the beginning of the prefix.
    while(genericDeviceInterface[0] != '\0'
            && genericDeviceInterface[0] >= '0'
            && genericDeviceInterface[0] <= '9')
    {
        ++genericDeviceInterface;
    }
    // Then skips the "-".
    if(genericDeviceInterface[0] != '\0'
            && genericDeviceInterface[0] == '-')
    {
        ++genericDeviceInterface;
    }
    // Finally skips the " ".
    if(genericDeviceInterface[0] != '\0'
            && genericDeviceInterface[0] == ' ')
    {
        ++genericDeviceInterface;
    }
    // If we have reached the end of the string, then the string does not
    // contain the prefix.
    if(genericDeviceInterface[0] == '\0')
    {
        genericDeviceInterface = deviceInterface;
    }

    // Finally, concatenate the device description and its generic device
    // interface.
    deviceModelIdentifierLength
        = strlen(deviceDescription) + 2 + strlen(genericDeviceInterface) + 2;
    if((deviceModelIdentifier
                = (char*) malloc(deviceModelIdentifierLength * sizeof(char)))
            == NULL)
    {
        fprintf(stderr,
                "getDeviceModelIdentifier (coreaudio/device.c): \
                    \n\tmalloc\n");
        fflush(stderr);
        return NULL;
    }
    if(snprintf(
            deviceModelIdentifier,
            deviceModelIdentifierLength,
            "%s (%s)",
            deviceDescription,
            genericDeviceInterface) != (deviceModelIdentifierLength - 1))
    {
        free(deviceModelIdentifier);
        fprintf(stderr,
                "getDeviceModelIdentifier (coreaudio/device.c): \
                    \n\tsnprintf\n");
        fflush(stderr);
        return NULL;
    }

    // Frees memory.
    free(deviceDescription);
    free(deviceInterface);

    return deviceModelIdentifier;
}

/**
 * Returns the device description for the given device. Or NULL, if not
 * available. The returned string must be freed by the caller. The device
 * description is a generic name such as "microphone", "speaker", etc.
 *
 * @param device The device to get the name from.
 *
 * @return The device description for the given device. Or NULL, if not
 * available. The returned string must be freed by the caller.
 */
char* getDeviceDescription(
        const char * deviceUID)
{
    return getDeviceProperty(deviceUID, PKEY_Device_DeviceDesc);
}

/**
 * Returns the device interface name for the given device. Or NULL, if not
 * available. The returned string must be freed by the caller. The device
 * interface name describes the way the device is connected, such as "USB audio
 * adapter".
 *
 * @param device The device to get the name from.
 *
 * @return The device interface name for the given device. Or NULL, if not
 * available. The returned string must be freed by the caller.
 */
char* getDeviceInterfaceName(
        const char * deviceUID)
{
    return getDeviceProperty(deviceUID, PKEY_DeviceInterface_FriendlyName);
}

/**
 * Returns the device property for the given device. Or NULL, if not available.
 * The returned string must be freed by the caller.
 *
 * @param device The device to get the name from.
 * @param propertyKey The requested property (i.e. PKEY_Device_FriendlyName for
 * the device name).
 *
 * @return The device property for the given device. Or NULL, if not available.
 * The returned string must be freed by the caller.
 */
char* getDeviceProperty(
        const char * deviceUID,
        PROPERTYKEY propertyKey)
{
    size_t devicePropertyLength;
    char * deviceProperty = NULL;
    PROPVARIANT propertyDevice;
    PropVariantInit(&propertyDevice);
    IPropertyStore * properties = NULL;

    // Gets the audio device.
    IMMDevice * device = getDevice(deviceUID);
    if(device == NULL)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tgetDevice\n");
        fflush(stderr);
        return NULL;
    }

    // Read the properties from the audio device.
    if(device->OpenPropertyStore(STGM_READ, &properties) != S_OK)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tIMMDevice.OpenPropertyStore\n");
        fflush(stderr);
        return NULL;
    }
    if(properties->GetValue(propertyKey, &propertyDevice)
            != S_OK)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tIPropertyStore.GetValue\n");
        fflush(stderr);
        return NULL;
    }
    devicePropertyLength = wcslen(propertyDevice.pwszVal);
    if((deviceProperty
                = (char *) malloc((devicePropertyLength + 1) * sizeof(char)))
            == NULL)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tmalloc\n");
        fflush(stderr);
        return NULL;
    }
    if(wcstombs(
                deviceProperty,
                propertyDevice.pwszVal,
                devicePropertyLength + 1)
            != devicePropertyLength)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\twcstombs\n");
        fflush(stderr);
        return NULL;
    }

    // Frees.
    freeDevice(device);
    PropVariantClear(&propertyDevice);

    return deviceProperty;
}

/**
 * Sets the input volume for a given device.
 *
 * @param deviceUID The device UID which volume must be changed.
 * @param volume The new volume of the device. This is a scalar value between
 * 0.0 and 1.0
 *
 * @return 0 if everything works well. -1 if an error has occured.  
 */
int setInputDeviceVolume(
        const char * deviceUID,
        float volume)
{
    return setDeviceVolume(deviceUID, volume);
}

/**
 * Sets the output volume for a given device.
 *
 * @param deviceUID The device UID which volume must be changed.
 * @param volume The new volume of the device. This is a scalar value between
 * 0.0 and 1.0
 *
 * @return 0 if everything works well. -1 if an error has occured.  
 */
int setOutputDeviceVolume(
        const char * deviceUID,
        float volume)
{
    return setDeviceVolume(deviceUID, volume);
}

/**
 * Sets the input or output volume for a given device. This is an internal
 * (private) function and must only be called by setInputDeviceVolume or
 * setOutputDeviceVolume.
 *
 * @param deviceUID The device UID which volume must be changed.
 * @param volume The new volume of the device. This is a scalar value between
 * 0.0 and 1.0
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return 0 if everything works well. -1 if an error has occured.  
 */
int setDeviceVolume(
        const char * deviceUID,
        float volume)
{
    IAudioEndpointVolume *endpointVolume = getEndpointVolume(deviceUID);
    if(endpointVolume == NULL)
    {
        fprintf(stderr,
                "setDeviceVolume (coreaudio/device.c): \
                    \n\tgetEndpointVolume\n");
        fflush(stderr);
        return -1;
    }
    if(endpointVolume->SetMasterVolumeLevelScalar(volume, NULL) != S_OK)
    {
        fprintf(stderr,
                "setDeviceVolume (coreaudio/device.c): \
                    \n\tSetMasterVolumeLevelScalar\n");
        fflush(stderr);
        return -1;
    }
    freeEndpointVolume(endpointVolume);

    return 0;
}

/**
 * Gets the input volume for a given device.
 *
 * @param deviceUID The device UID to get volume from.
 *
 * @return The device volume as a scalar value between 0.0 and 1.0. Returns -1.0
 * if an error occurs.
 */
float getInputDeviceVolume(
        const char * deviceUID)
{
    return getDeviceVolume(deviceUID);
}

/**
 * Gets the output volume for a given device.
 *
 * @param deviceUID The device UID to get volume from.
 *
 * @return The device volume as a scalar value between 0.0 and 1.0. Returns -1.0
 * if an error occurs.
 */
float getOutputDeviceVolume(
        const char * deviceUID)
{
    return getDeviceVolume(deviceUID);
}

/**
 * Gets the input or output volume for a given device. This is an internal
 * (private) function and must only be called by getInputDeviceVolume or
 * getOutputDeviceVolume.
 *
 * @param deviceUID The device UID to get volume from.
 *
 * @return The device volume as a scalar value between 0.0 and 1.0. Returns -1.0
 * if an error occurs.
 */
float getDeviceVolume(
        const char * deviceUID)
{
    float volume;

    IAudioEndpointVolume *endpointVolume = getEndpointVolume(deviceUID);
    if(endpointVolume == NULL)
    {
        fprintf(stderr,
                "getDeviceVolume (coreaudio/device.c): \
                    \n\tgetEndpointVolume\n");
        fflush(stderr);
        return -1;
    }
    if(endpointVolume->GetMasterVolumeLevelScalar(&volume) != S_OK)
    {
        fprintf(stderr,
                "getDeviceVolume (coreaudio/device.c): \
                    \n\tGetMasterVolumeLevelScalar\n");
        fflush(stderr);
        return -1;
    }
    freeEndpointVolume(endpointVolume);

    return volume;
}
