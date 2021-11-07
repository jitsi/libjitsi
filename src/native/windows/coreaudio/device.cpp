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
#include "device.h"

#include <cstdio>
#include <tchar.h>

#include <windows.h>
#include <malloc.h>

#include <endpointvolume.h> // Must be defined after mmdeviceapi.h
#include <Functiondiscoverykeys_devpkey.h>

const CLSID CLSID_MMDeviceEnumerator = __uuidof(MMDeviceEnumerator);
const IID IID_IMMDeviceEnumerator = __uuidof(IMMDeviceEnumerator);
const IID IID_IAudioEndpointVolume = __uuidof(IAudioEndpointVolume);

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

/**
 * Initializes the COM component. This function must be called first in order to
 * able each following function to work correctly. Once finished, the caller of
 * this function must call the "freeDevices" function.
 *
 * @return 0 if everything is OK. -1 if an error has occured.
 */
int initDevices()
{
    if(CoInitializeEx(nullptr, COINIT_MULTITHREADED) != S_OK)
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
void freeDevices()
{
    CoUninitialize();
}

/**
 * Returns the audio device corresponding to the UID given in parameter. Or
 * nullptr if the device is nonexistant or if anything as failed. The device must
 * be freed by the caller of this function.
 *
 * @param deviceUID The device UID.
 *
 * @return The audio device corresponding to the UID given in parameter. Or
 * nullptr if the device is nonexistant or if anything as failed.
 */
IMMDevice * getDevice(
        const char * deviceUID)
{
    // Gets the enumerator of the system devices.
    HRESULT err;
    IMMDeviceEnumerator * enumerator = nullptr;

    if((err = CoCreateInstance(
                CLSID_MMDeviceEnumerator,
                nullptr,
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
        return nullptr;
    }

    // Gets the requested device selected by its UID.
    IMMDevice *device = nullptr;
    size_t deviceUIDLength = strlen(deviceUID);
    auto *wCharDeviceUID = (wchar_t*) alloca(sizeof(wchar_t) * (deviceUIDLength + 1));
    if(mbstowcs(wCharDeviceUID, deviceUID, deviceUIDLength + 1)
            != deviceUIDLength)
    {
        fprintf(stderr,
                "getDevice (coreaudio/device.c): \
                    \n\tmbstowcs\n");
        fflush(stderr);
        return nullptr;
    }
    if(enumerator->GetDevice(wCharDeviceUID, &device) != S_OK)
    {
        fprintf(stderr,
                "getDevice (coreaudio/device.c): \
                    \n\tIMMDeviceEnumerator.GetDevice\n");
        fflush(stderr);
        return nullptr;
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
 * parameter. Or nullptr if the endpoint is nonexistant or if anything as failed.
 * The endpoint must be freed by the caller of this function.
 *
 * @param deviceUID The device UID.
 *
 * @return the audio device volume endpoint corresponding to the UID given in
 * parameter. Or nullptr if the endpoint is nonexistant or if anything as failed.
 */
IAudioEndpointVolume * getEndpointVolume(
        const char * deviceUID)
{
    // Gets the device corresponding to its UID.
    IMMDevice * device = getDevice(deviceUID);
    if(device == nullptr)
    {
        fprintf(stderr,
                "getEndpointVolume (coreaudio/device.c): \
                    \n\tgetDevice\n");
        fflush(stderr);
        return nullptr;
    }

    // retrives the volume endpoint.
    IAudioEndpointVolume *endpointVolume = nullptr;
    if(device->Activate(
            IID_IAudioEndpointVolume,
            CLSCTX_ALL,
            nullptr,
            (void**) &endpointVolume) != S_OK)
    {
        fprintf(stderr,
                "getEndpointVolume (coreaudio/device.c): \
                    \n\tIMMDevice.Activate\n");
        fflush(stderr);
        return nullptr;
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
 * Returns the device name for the given device. Or nullptr, if not available. The
 * returned string must be freed by the caller. The device name is composed of
 * the device description and of the device interface name.
 *
 * @param device The device to get the name from.
 *
 * @return The device name for the given device. Or nullptr, if not available. The
 * returned string must be freed by the caller.
 */
char* getDeviceName(
        const char * deviceUID)
{
    return getDeviceProperty(deviceUID, PKEY_Device_FriendlyName);
}

/**
 * Returns the device model identifier for the given device. Or nullptr, if not
 * available. The returned string must be freed by the caller.
 *
 * @param device The device to get the name from.
 *
 * @return The device model identifier for the given device. Or nullptr, if not
 * available. The returned string must be freed by the caller.
 */
char* getDeviceModelIdentifier(
        const char * deviceUID)
{
    int deviceModelIdentifierLength;
    char * deviceModelIdentifier;
    char * deviceDescription;
    char * deviceInterface;
    char * genericDeviceInterface;

    if((deviceDescription = getDeviceDescription(deviceUID)) == nullptr)
    {
        fprintf(stderr,
                "getDeviceModelIdentifier (coreaudio/device.c): \
                    \n\tgetDeviceDescription\n");
        fflush(stderr);
        return nullptr;
    }
    if((deviceInterface = getDeviceInterfaceName(deviceUID)) == nullptr)
    {
        fprintf(stderr,
                "getDeviceModelIdentifier (coreaudio/device.c): \
                    \n\tgetDeviceInterfaceName\n");
        fflush(stderr);
        return nullptr;
    }

    // A USB device (without a serial ID) puts into a USB port will add the
    // port number (if greater than 1) with the following prefix: "X- "
    // (with X the port number).
    genericDeviceInterface = deviceInterface;
    // First skip the number at the beginning of the prefix.
    while(genericDeviceInterface[0] >= '0'
            && genericDeviceInterface[0] <= '9')
    {
        ++genericDeviceInterface;
    }
    // Then skips the "-".
    if(genericDeviceInterface[0] == '-')
    {
        ++genericDeviceInterface;
    }
    // Finally skips the " ".
    if(genericDeviceInterface[0] == ' ')
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
            == nullptr)
    {
        fprintf(stderr,
                "getDeviceModelIdentifier (coreaudio/device.c): \
                    \n\tmalloc\n");
        fflush(stderr);
        return nullptr;
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
        return nullptr;
    }

    // Frees memory.
    free(deviceDescription);
    free(deviceInterface);

    return deviceModelIdentifier;
}

/**
 * Returns the device description for the given device. Or nullptr, if not
 * available. The returned string must be freed by the caller. The device
 * description is a generic name such as "microphone", "speaker", etc.
 *
 * @param device The device to get the name from.
 *
 * @return The device description for the given device. Or nullptr, if not
 * available. The returned string must be freed by the caller.
 */
char* getDeviceDescription(
        const char * deviceUID)
{
    return getDeviceProperty(deviceUID, PKEY_Device_DeviceDesc);
}

/**
 * Returns the device interface name for the given device. Or nullptr, if not
 * available. The returned string must be freed by the caller. The device
 * interface name describes the way the device is connected, such as "USB audio
 * adapter".
 *
 * @param device The device to get the name from.
 *
 * @return The device interface name for the given device. Or nullptr, if not
 * available. The returned string must be freed by the caller.
 */
char* getDeviceInterfaceName(
        const char * deviceUID)
{
    return getDeviceProperty(deviceUID, PKEY_DeviceInterface_FriendlyName);
}

/**
 * Returns the device property for the given device. Or nullptr, if not available.
 * The returned string must be freed by the caller.
 *
 * @param device The device to get the name from.
 * @param propertyKey The requested property (i.e. PKEY_Device_FriendlyName for
 * the device name).
 *
 * @return The device property for the given device. Or nullptr, if not available.
 * The returned string must be freed by the caller.
 */
char* getDeviceProperty(
        const char * deviceUID,
        PROPERTYKEY propertyKey)
{
    size_t devicePropertyLength;
    char * deviceProperty;
    PROPVARIANT propertyDevice;
    PropVariantInit(&propertyDevice);
    IPropertyStore * properties = nullptr;

    // Gets the audio device.
    IMMDevice * device = getDevice(deviceUID);
    if(device == nullptr)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tgetDevice\n");
        fflush(stderr);
        return nullptr;
    }

    // Read the properties from the audio device.
    if(device->OpenPropertyStore(STGM_READ, &properties) != S_OK)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tIMMDevice.OpenPropertyStore\n");
        fflush(stderr);
        return nullptr;
    }
    if(properties->GetValue(propertyKey, &propertyDevice)
            != S_OK)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tIPropertyStore.GetValue\n");
        fflush(stderr);
        return nullptr;
    }
    devicePropertyLength = wcslen(propertyDevice.pwszVal);
    if((deviceProperty
                = (char *) malloc((devicePropertyLength + 1) * sizeof(char)))
            == nullptr)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tmalloc\n");
        fflush(stderr);
        return nullptr;
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
        return nullptr;
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
    if(endpointVolume == nullptr)
    {
        fprintf(stderr,
                "setDeviceVolume (coreaudio/device.c): \
                    \n\tgetEndpointVolume\n");
        fflush(stderr);
        return -1;
    }
    if(endpointVolume->SetMasterVolumeLevelScalar(volume, nullptr) != S_OK)
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
    if(endpointVolume == nullptr)
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
