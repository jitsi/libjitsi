/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#include "device.h"

#include <CoreAudio/CoreAudio.h>
#include <CoreFoundation/CFString.h>
#include <stdio.h>
/**
 * Functions to list, access and modifies audio devices via coreaudio.
 *
 * @author Vincent Lucas
 */

/**
 * Private definition of functions,
 */
OSStatus setDeviceVolume(
        const char * deviceUID,
        Float32 volume,
        UInt32 inputOutputScope);

Float32 getDeviceVolume(
        const char * deviceUID,
        UInt32 inputOutputScope);

OSStatus getChannelsForStereo(
        const char * deviceUID,
        UInt32 * channels);

/**
 * Returns the audio device corresponding to the UID given in parameter. Or
 * kAudioObjectUnknown if the device is nonexistant or if anything as failed.
 *
 * @pqrqm deviceUID The device UID.
 *
 * @return The audio device corresponding to the UID given in parameter. Or
 * kAudioObjectUnknown if the device is nonexistant or if anything as failed.
 */
AudioDeviceID getDevice(
        const char * deviceUID)
{
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    AudioDeviceID device = kAudioObjectUnknown;
    UInt32 size;

    // Converts the device UID into a ref.
    CFStringRef deviceUIDRef;
    if((deviceUIDRef = CFStringCreateWithCString(
            kCFAllocatorDefault,
            deviceUID,
            kCFStringEncodingASCII)) == NULL)
    {
        fprintf(stderr,
                "getDevice (coreaudio/device.c): \
                    \n\tCFStringCreateWithCString\n");
        return kAudioObjectUnknown;
    }

    // Gets the device corresponding to the given UID.
    AudioValueTranslation translation;
    translation.mInputData = &deviceUIDRef;
    translation.mInputDataSize = sizeof(deviceUIDRef);
    translation.mOutputData = &device;
    translation.mOutputDataSize = sizeof(device);
    size = sizeof(translation);
    address.mSelector = kAudioHardwarePropertyDeviceForUID;
    address.mScope = kAudioObjectPropertyScopeGlobal;
    address.mElement = kAudioObjectPropertyElementMaster;

    if((err = AudioObjectGetPropertyData(
            kAudioObjectSystemObject,
            &address,
            0,
            NULL,
            &size,
            &translation)) != noErr)
    {
        fprintf(stderr,
                "getDevice (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d\n",
                ((int) err));
        return kAudioObjectUnknown;
    }

    // Frees the allocated device UID ref.
    CFRelease(deviceUIDRef);

    return device;
}

/**
 * Returns the device name for the given device. Or NULL, if not available. The
 * returned string must be freed by the caller.
 *
 * @param device The device to get the name from.
 *
 * @return The device name for the given device. Or NULL, if not available. The
 * returned string must be freed by the caller.
 */
char* getDeviceName(
        const char * deviceUID)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;

    // Gets the correspoding device
    if((device = getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "getDeviceName (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return NULL;
    }

    // Gets the device name
    CFStringRef deviceName;
    size = sizeof(deviceName);
    address.mSelector = kAudioObjectPropertyName;
    address.mScope = kAudioObjectPropertyScopeGlobal;
    address.mElement = kAudioObjectPropertyElementMaster;

    if((err = AudioObjectGetPropertyData(
            device,
            &address,
            0,
            NULL,
            &size,
            &deviceName)) != noErr)
    {
        fprintf(stderr,
                "getDeviceName (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d\n",
                ((int) err));
        return NULL;
    }

    // Converts the device name to ASCII.
    CFIndex deviceNameLength = CFStringGetLength(deviceName) + 1;
    char * deviceASCIIName;
    // The caller of this function must free the string.
    if((deviceASCIIName = (char *) malloc(deviceNameLength * sizeof(char)))
            == NULL)
    {
        perror("getDeviceName (coreaudio/device.c): \
                    \n\tmalloc\n");
        return NULL;
    }
    if(CFStringGetCString(
                deviceName,
                deviceASCIIName,
                deviceNameLength,
                kCFStringEncodingASCII))
    {
        return deviceASCIIName;
    }
    return NULL;
}

/**
 * Sets the input volume for a given device.
 *
 * @param device The device which volume must be changed.
 * @param volume The new volume of the device. This is a scalar value between
 * 0.0 and 1.0
 *
 * @return noErr if everything works well. Another value if an error has
 * occured.  
 */
OSStatus setInputDeviceVolume(
        const char * deviceUID,
        Float32 volume)
{
    return setDeviceVolume(
            deviceUID,
            volume,
            kAudioDevicePropertyScopeInput);
}

/**
 * Sets the output volume for a given device.
 *
 * @param device The device which volume must be changed.
 * @param volume The new volume of the device. This is a scalar value between
 * 0.0 and 1.0
 *
 * @return noErr if everything works well. Another value if an error has
 * occured.  
 */
OSStatus setOutputDeviceVolume(
        const char * deviceUID,
        Float32 volume)
{
    return setDeviceVolume(
            deviceUID,
            volume,
            kAudioDevicePropertyScopeOutput);
}

/**
 * Sets the input or output volume for a given device. This is an internal
 * (private) function and must only be called by setInputDeviceVolume or
 * setOutputDeviceVolume.
 *
 * @param device The device which volume must be changed.
 * @param volume The new volume of the device. This is a scalar value between
 * 0.0 and 1.0
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return noErr if everything works well. Another value if an error has
 * occured.  
 */
OSStatus setDeviceVolume(
        const char * deviceUID,
        Float32 volume,
        UInt32 inputOutputScope)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    UInt32 channels[2];

    // Gets the correspoding device
    if((device = getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "setDeviceVolume (coreaudio/device.c): \
                    \n\tgetDevice (unknown device for UID: %s)\n", deviceUID);
        return -1;
    }

    // get the input device stereo channels
    if((getChannelsForStereo(deviceUID, channels)) != noErr)
    {
        fprintf(stderr,
                "setDeviceVolume (coreaudio/device.c): \
                    \n\tgetChannelsForStereo, err: %d\n",
                ((int) err));
        return err;
    }

    // Sets the volume
    size = sizeof(volume);
    address.mSelector = kAudioDevicePropertyVolumeScalar;
    address.mScope = inputOutputScope;
    int i;
    int elementsLength = 3;
    UInt32 elements[] =
    {
        // The global volume.
        kAudioObjectPropertyElementMaster,
        // The left channel.
        channels[0],
        // The right channel.
        channels[1]
    };

    // Applies the volume to the different elements of the device.
    for(i = 0; i < elementsLength; ++i)
    {
        address.mElement = elements[i];
        // Checks if this device volume can be set. If yes, then do so.
        if(AudioObjectHasProperty(device, &address))
        {
            if((err = AudioObjectSetPropertyData(
                    device,
                    &address,
                    0,
                    NULL,
                    size,
                    &volume)) != noErr)
            {
                fprintf(stderr,
                        "setDeviceVolume (coreaudio/device.c): \
                            \n\tAudioObjectSetPropertyData, err: %d\n",
                        ((int) err));
                return err;
            }
        }
    }

    return err;
}

/**
 * Gets the input volume for a given device.
 *
 * @param deviceUID The device UID to get volume from.
 *
 * @return The device volume as a scalar value between 0.0 and 1.0. Returns -1.0
 * if an error occurs.
 */
Float32 getInputDeviceVolume(
        const char * deviceUID)
{
    return getDeviceVolume(
            deviceUID,
            kAudioDevicePropertyScopeInput);
}

/**
 * Gets the output volume for a given device.
 *
 * @param deviceUID The device UID to get volume from.
 *
 * @return The device volume as a scalar value between 0.0 and 1.0. Returns -1.0
 * if an error occurs.
 */
Float32 getOutputDeviceVolume(
        const char * deviceUID)
{
    return getDeviceVolume(
            deviceUID,
            kAudioDevicePropertyScopeOutput);
}

/**
 * Gets the input or output volume for a given device. This is an internal
 * (private) function and must only be called by getInputDeviceVolume or
 * getOutputDeviceVolume.
 *
 * @param deviceUID The device UID to get volume from.
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return The device volume as a scalar value between 0.0 and 1.0. Returns -1.0
 * if an error occurs.
 */
Float32 getDeviceVolume(
        const char * deviceUID,
        UInt32 inputOutputScope)
{
    AudioDeviceID device;
    Float32 volume = -1.0;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    UInt32 channels[2];

    // Gets the correspoding device
    if((device = getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "getDeviceVolume (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return -1.0;
    }

    // get the input device stereo channels
    if((getChannelsForStereo(deviceUID, channels)) != noErr)
    {
        fprintf(stderr,
                "getDeviceVolume (coreaudio/device.c): \
                    \n\tgetChannelsForStereo, err: %d\n",
                ((int) err));
        return -1.0;
    }

    // Sets the volume
    size = sizeof(volume);
    address.mSelector = kAudioDevicePropertyVolumeScalar;
    address.mScope = inputOutputScope;
    int i;
    int elementsLength = 3;
    UInt32 elements[] =
    {
        // The global volume.
        kAudioObjectPropertyElementMaster,
        // The left channel.
        channels[0],
        // The right channel.
        channels[1]
    };

    // Applies the volume to the different elements of the device.
    for(i = 0; i < elementsLength; ++i)
    {
        address.mElement = elements[i];
        // Checks if this device volume can be set. If yes, then do so.
        if(AudioObjectHasProperty(device, &address))
        {
            if((err = AudioObjectGetPropertyData(
                    device,
                    &address,
                    0,
                    NULL,
                    &size,
                    &volume)) != noErr)
            {
                fprintf(stderr,
                        "getDeviceVolume (coreaudio/device.c): \
                            \n\tAudioObjectSetPropertyData, err: %d\n",
                        ((int) err));
                return -1.0;
            }
        }
    }

    return volume;
}

/**
 * Sets the channels for stereo of a given device.
 *
 * @param deviceUID The device UID to get the channels from.
 * @param channels The channels to be filled in with the correct values. This
 * must be a 2 item length array.
 *
 * @return An OSStatus set to noErr if everything works well. Any other vlaue
 * otherwise.
 */
OSStatus getChannelsForStereo(
        const char * deviceUID,
        UInt32 * channels)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;

    // Gets the correspoding device
    if((device = getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "getChannelsForStereo (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return -1;
    }

    // get the input device stereo channels
    address.mSelector = kAudioDevicePropertyPreferredChannelsForStereo;
    address.mScope = kAudioDevicePropertyScopeInput;
    address.mElement = kAudioObjectPropertyElementWildcard;
    size = sizeof(channels);
    if((err = AudioObjectGetPropertyData(
            device,
            &address,
            0,
            NULL,
            &size,
            channels)) != noErr)
    {
        fprintf(stderr,
                "getChannelsForStereo (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d\n",
                ((int) err));
        return err;
    }

    return err;
}
