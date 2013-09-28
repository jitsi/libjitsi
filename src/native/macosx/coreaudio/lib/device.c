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
const char* transportTypeAggregate = "Aggregate";
const char* transportTypeAirPlay = "AirPlay";
const char* transportTypeAutoAggregate = "Auto aggregate";
const char* transportTypeAVB = "AVB";
const char* transportTypeBlueTooth = "Bluetooth";
const char* transportTypeBuiltIn = "Built-in";
const char* transportTypeDisplayPort = "DisplayPort";
const char* transportTypeFireWire = "FireWire";
const char* transportTypeHDMI = "HDMI";
const char* transportTypePCI = "PCI";
const char* transportTypeThunderbolt = "Thunderbolt";
const char* transportTypeUnknown = "Unknown";
const char* transportTypeUSB = "USB";
const char* transportTypeVirtual = "Virtual";

/**
 * Private definition of functions,
 */

AudioDeviceID maccoreaudio_getDevice(
        const char * deviceUID);

AudioDeviceID maccoreaudio_getDeviceForSpecificScope(
        const char * deviceUID,
        UInt32 inputOutputScope);

char* maccoreaudio_getDeviceProperty(
        const char * deviceUID,
        AudioObjectPropertySelector propertySelector);

char* maccoreaudio_getAudioDeviceProperty(
        AudioDeviceID device,
        AudioObjectPropertySelector propertySelector);

OSStatus maccoreaudio_setDeviceVolume(
        const char * deviceUID,
        Float32 volume,
        UInt32 inputOutputScope);

Float32 maccoreaudio_getDeviceVolume(
        const char * deviceUID,
        UInt32 inputOutputScope);

OSStatus maccoreaudio_getChannelsForStereo(
        const char * deviceUID,
        UInt32 * channels);

int maccoreaudio_countChannels(
        const char * deviceUID,
        AudioObjectPropertyScope inputOutputScope);

static OSStatus maccoreaudio_devicesChangedCallback(
        AudioObjectID inObjectID,
        UInt32 inNumberAddresses,
        const AudioObjectPropertyAddress inAddresses[],
        void *inClientData);

OSStatus maccoreaudio_initConverter(
        const char * deviceUID,
        const AudioStreamBasicDescription * javaFormat,
        unsigned char isJavaFormatSource,
        AudioConverterRef * converter,
        double * conversionRatio);

inline UInt32 CalculateLPCMFlags (
        UInt32 inValidBitsPerChannel,
        UInt32 inTotalBitsPerChannel,
        bool inIsFloat,
        bool inIsBigEndian,
        bool inIsNonInterleaved);

inline void FillOutASBDForLPCM(
        AudioStreamBasicDescription * outASBD,
        Float64 inSampleRate,
        UInt32 inChannelsPerFrame,
        UInt32 inValidBitsPerChannel,
        UInt32 inTotalBitsPerChannel,
        bool inIsFloat,
        bool inIsBigEndian,
        bool inIsNonInterleaved);

char* maccoreaudio_getDefaultDeviceUID(
        UInt32 inputOutputScope);

maccoreaudio_stream * maccoreaudio_startStream(
        const char * deviceUID,
        void* callbackFunction,
        void* callbackObject,
        void* callbackMethod,
        void* readWriteFunction,
        unsigned char isJavaFormatSource,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved);

OSStatus maccoreaudio_readInputStream(
        AudioDeviceID inDevice,
        const AudioTimeStamp* inNow,
        const AudioBufferList* inInputData,
        const AudioTimeStamp* inInputTime,
        AudioBufferList* outOutputData,
        const AudioTimeStamp* inOutputTime,
        void* inClientData);

OSStatus maccoreaudio_writeOutputStream(
        AudioDeviceID inDevice,
        const AudioTimeStamp* inNow,
        const AudioBufferList* inInputData,
        const AudioTimeStamp* inInputTime,
        AudioBufferList* outOutputData,
        const AudioTimeStamp* inOutputTime,
        void* inClientData);

OSStatus maccoreaudio_getStreamVirtualFormat(
        AudioStreamID stream,
        AudioStreamBasicDescription * format);

/**
 * Do nothing: there is no need to initializes anything to get device
 * information on MacOsX.
 *
 * @return Always returns 0 (always works).
 */
int maccoreaudio_initDevices(void)
{
    return 0;
}

/**
 * Do nothing: there is no need to frees anything once getting device
 * information is finished on MacOsX.
 */
void maccoreaudio_freeDevices(void)
{
    // Nothing to do.
}

/**
 * Returns if the audio device is an input device.
 *
 * @param deviceUID The device UID.
 *
 * @return True if the given device identifier correspond to an input device.
 * False otherwise.
 */
int maccoreaudio_isInputDevice(
        const char * deviceUID)
{
    return (maccoreaudio_countChannels(
                deviceUID,
                kAudioDevicePropertyScopeInput) > 0);
}

/**
 * Returns if the audio device is an output device.
 *
 * @param deviceUID The device UID.
 *
 * @return True if the given device identifier correspond to an output device.
 * False otherwise.
 */
int maccoreaudio_isOutputDevice(
        const char * deviceUID)
{
    return (maccoreaudio_countChannels(
                deviceUID,
                kAudioDevicePropertyScopeOutput) > 0);
}

/**
 * Returns the audio device corresponding to the UID given in parameter. Or
 * kAudioObjectUnknown if the device is nonexistant or if anything as failed.
 *
 * @param deviceUID The device UID.
 *
 * @return The audio device corresponding to the UID given in parameter. Or
 * kAudioObjectUnknown if the device is nonexistant or if anything as failed.
 */
AudioDeviceID maccoreaudio_getDevice(
        const char * deviceUID)
{
    return maccoreaudio_getDeviceForSpecificScope(
            deviceUID,
            kAudioObjectPropertyScopeGlobal);
}

/**
 * Returns the audio device corresponding to the UID given in parameter for the
 * specified scope (global, input or output). Or kAudioObjectUnknown if the
 * device is nonexistant or if anything as failed.
 *
 * @param deviceUID The device UID.
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return The audio device corresponding to the UID given in parameter. Or
 * kAudioObjectUnknown if the device is nonexistant or if anything as failed.
 */
AudioDeviceID maccoreaudio_getDeviceForSpecificScope(
        const char * deviceUID,
        UInt32 inputOutputScope)
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
    address.mScope = inputOutputScope;
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
 * Returns the default input device UID.
 *
 * @return The default input device UID. NULL if an error occurs.
 */
char* maccoreaudio_getDefaultInputDeviceUID(void)
{
    return maccoreaudio_getDefaultDeviceUID(kAudioDevicePropertyScopeInput);
}

/**
 * Returns the default output device UID.
 *
 * @return The default output device UID. NULL if an error occurs.
 */
char* maccoreaudio_getDefaultOutputDeviceUID(void)
{
    return maccoreaudio_getDefaultDeviceUID(kAudioDevicePropertyScopeOutput);
}

/**
 * Returns the default device UID for input or ouput.
 *
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return The default device UID for input or ouput. NULL if an error occurs.
 */
char* maccoreaudio_getDefaultDeviceUID(
        UInt32 inputOutputScope)
{
    OSStatus err = noErr;
    AudioDeviceID device;
    UInt32 size = sizeof(AudioDeviceID);
    AudioObjectPropertyAddress address;
    char * deviceUID = NULL;

    if(inputOutputScope == kAudioDevicePropertyScopeInput)
    {
        address.mSelector = kAudioHardwarePropertyDefaultInputDevice;
    }
    else
    {
        address.mSelector = kAudioHardwarePropertyDefaultOutputDevice;
    }
    address.mScope = inputOutputScope;
    address.mElement = kAudioObjectPropertyElementMaster;

    if((err = AudioObjectGetPropertyData(
                    kAudioObjectSystemObject,
                    &address,
                    0,
                    NULL,
                    &size,
                    &device))
            != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_getDefaultDeviceUID (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d\n",
                    ((int) err));
        return NULL;
    }

    if((deviceUID = maccoreaudio_getAudioDeviceProperty(
                    device,
                    kAudioDevicePropertyDeviceUID))
            == NULL)
    {
        fprintf(stderr,
                "maccoreaudio_getDefaultDeviceUID (coreaudio/device.c): \
                    \n\tgetAudioDeviceProperty\n");
        return NULL;
    }

    return deviceUID;
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
char* maccoreaudio_getDeviceName(
        const char * deviceUID)
{
    return maccoreaudio_getDeviceProperty(deviceUID, kAudioObjectPropertyName);
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
char* maccoreaudio_getDeviceModelIdentifier(
        const char * deviceUID)
{
    return
        maccoreaudio_getDeviceProperty(deviceUID, kAudioDevicePropertyModelUID);
}

/**
 * Returns the requested device property for the given device UID. Or NULL, if
 * not available. The returned string must be freed by the caller.
 *
 * @param deviceUID The device identifier to get the property from.
 * @param propertySelector The property we want to retrieve.
 *
 * @return The requested device property for the given device UID. Or NULL, if
 * not available. The returned string must be freed by the caller.
 */
char* maccoreaudio_getDeviceProperty(
        const char * deviceUID,
        AudioObjectPropertySelector propertySelector)
{
    AudioDeviceID device;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return NULL;
    }

    return maccoreaudio_getAudioDeviceProperty(device, propertySelector);
}

/**
 * Returns the requested device property for the given device. Or NULL, if not
 * available. The returned string must be freed by the caller.
 *
 * @param device The device to get the name from.
 * @param propertySelector The property we want to retrieve.
 *
 * @return The requested device property for the given device. Or NULL, if not
 * available. The returned string must be freed by the caller.
 */
char* maccoreaudio_getAudioDeviceProperty(
        AudioDeviceID device,
        AudioObjectPropertySelector propertySelector)
{
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;

    // Gets the device property
    CFStringRef deviceProperty;
    size = sizeof(deviceProperty);
    address.mSelector = propertySelector;
    address.mScope = kAudioObjectPropertyScopeGlobal;
    address.mElement = kAudioObjectPropertyElementMaster;

    if((err = AudioObjectGetPropertyData(
            device,
            &address,
            0,
            NULL,
            &size,
            &deviceProperty)) != noErr)
    {
        fprintf(stderr,
                "getDeviceProperty (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d\n",
                ((int) err));
        return NULL;
    }

    // Converts the device property to ASCII.
    CFIndex devicePropertyLength = CFStringGetLength(deviceProperty) + 1;
    char * deviceASCIIProperty;
    // The caller of this function must free the string.
    if((deviceASCIIProperty
                = (char *) malloc(devicePropertyLength * sizeof(char)))
            == NULL)
    {
        perror("getDeviceProperty (coreaudio/device.c): \
                    \n\tmalloc\n");
        return NULL;
    }
    if(CFStringGetCString(
                deviceProperty,
                deviceASCIIProperty,
                devicePropertyLength,
                kCFStringEncodingASCII))
    {
        return deviceASCIIProperty;
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
OSStatus maccoreaudio_setInputDeviceVolume(
        const char * deviceUID,
        Float32 volume)
{
    return maccoreaudio_setDeviceVolume(
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
OSStatus maccoreaudio_setOutputDeviceVolume(
        const char * deviceUID,
        Float32 volume)
{
    return maccoreaudio_setDeviceVolume(
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
OSStatus maccoreaudio_setDeviceVolume(
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
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "setDeviceVolume (coreaudio/device.c): \
                    \n\tgetDevice (unknown device for UID: %s)\n", deviceUID);
        return -1;
    }

    // get the input device stereo channels
    if((maccoreaudio_getChannelsForStereo(deviceUID, channels)) != noErr)
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
Float32 maccoreaudio_getInputDeviceVolume(
        const char * deviceUID)
{
    return maccoreaudio_getDeviceVolume(
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
Float32 maccoreaudio_getOutputDeviceVolume(
        const char * deviceUID)
{
    return maccoreaudio_getDeviceVolume(
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
Float32 maccoreaudio_getDeviceVolume(
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
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "getDeviceVolume (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return -1.0;
    }

    // get the input device stereo channels
    if((maccoreaudio_getChannelsForStereo(deviceUID, channels)) != noErr)
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
OSStatus maccoreaudio_getChannelsForStereo(
        const char * deviceUID,
        UInt32 * channels)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
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

/**
 * Returns the number of channels avaialable for input device.
 *
 * @param deviceUID The device UID to get the channels from.
 *
 * @return The number of channels avaialable for a given input device.
 * -1 if an error occurs.
 */
int maccoreaudio_countInputChannels(
        const char * deviceUID)
{
    return maccoreaudio_countChannels(
                deviceUID,
                kAudioDevicePropertyScopeInput);
}

/**
 * Returns the number of channels avaialable for output device.
 *
 * @param deviceUID The device UID to get the channels from.
 *
 * @return The number of channels avaialable for a given output device.
 * -1 if an error occurs.
 */
int maccoreaudio_countOutputChannels(
        const char * deviceUID)
{
    return maccoreaudio_countChannels(
                deviceUID,
                kAudioDevicePropertyScopeOutput);
}

/**
 * Returns the number of channels avaialable for a given input / output device.
 *
 * @param deviceUID The device UID to get the channels from.
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return The number of channels avaialable for a given input / output device.
 * -1 if an error occurs.
 */
int maccoreaudio_countChannels(
        const char * deviceUID,
        AudioObjectPropertyScope inputOutputScope)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    AudioBufferList *audioBufferList = NULL;
    int nbChannels = 0;
    int i;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "getChannelsForStereo (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return -1;
    }

    // Gets the size of the streams for this device.
    address.mSelector = kAudioDevicePropertyStreamConfiguration;
    address.mScope = inputOutputScope;
    address.mElement = kAudioObjectPropertyElementWildcard; // 0
    if((err = AudioObjectGetPropertyDataSize(device, &address, 0, NULL, &size))
            != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_countChannels (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyDataSize, err: %d\n",
                    ((int) err));
        return -1;
    }

    // Gets the number of channels ofr each stream.
    if((audioBufferList = (AudioBufferList *) malloc(size)) == NULL)
    {
        perror("maccoreaudio_countChannels (coreaudio/device.c): \
                \n\tmalloc\n");
        return -1;
    }
    if((err = AudioObjectGetPropertyData(
                    device,
                    &address,
                    0,
                    NULL,
                    &size,
                    audioBufferList))
            != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_countChannels (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d\n",
                    ((int) err));
        return -1;
    }
    for(i = 0; i < audioBufferList->mNumberBuffers; ++i)
    {
        nbChannels += audioBufferList->mBuffers[i].mNumberChannels;
    }
    free(audioBufferList);

    return nbChannels;
}

/**
 * Returns the nominal sample rate for the given device.
 *
 * @param deviceUID The device UID to get the channels from.
 *
 * @return The nominal sample rate for the given device. -1.0 if an error
 * occurs.
 */
Float64 maccoreaudio_getNominalSampleRate(
        const char * deviceUID)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    Float64 rate = -1.0;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "getNominalSampleRate (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return -1.0;
    }

    // Gets the sample rate.
    size = sizeof(Float64);
    address.mSelector = kAudioDevicePropertyNominalSampleRate;
    address.mScope = kAudioObjectPropertyScopeGlobal;
    address.mElement = kAudioObjectPropertyElementMaster;

    if((err = AudioObjectGetPropertyData(
                    device,
                    &address,
                    0,
                    NULL,
                    &size,
                    &rate))
            != noErr)
    {
        fprintf(stderr,
                "getNominalSampleRate (coreaudio/device.c): \
                    \n\tAudioObjactGetPropertyData, err: %d\n",
                    (int) err);
        return -1.0;
    }

    return rate;
}

/**
 * Gets the minimal and maximal nominal sample rate for the given device.
 *
 * @param deviceUID The device UID to get the channels from.
 * @param minRate The minimal rate available for this device.
 * @param maxRate The maximal rate available for this device.
 *
 * @return noErr if everything is alright. -1.0 if an error occurs.
 */
OSStatus maccoreaudio_getAvailableNominalSampleRates(
        const char * deviceUID,
        Float64 * minRate,
        Float64 * maxRate)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    AudioValueRange minMaxRate;
    minMaxRate.mMinimum = -1.0;
    minMaxRate.mMaximum = -1.0;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "getAvailableNominalSampleRates (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return -1.0;
    }

    // Gets the available sample ratea.
    size = sizeof(AudioValueRange);
    address.mSelector = kAudioDevicePropertyAvailableNominalSampleRates;
    address.mScope = kAudioObjectPropertyScopeGlobal;
    address.mElement = kAudioObjectPropertyElementMaster;

    if((err = AudioObjectGetPropertyData(
                    device,
                    &address,
                    0,
                    NULL,
                    &size,
                    &minMaxRate))
            != noErr)
    {
        fprintf(stderr,
                "getAvailableNominalSampleRates (coreaudio/device.c): \
                    \n\tAudioObjactGetPropertyData, err: %d\n",
                    (int) err);
        return -1.0;
    }

    (*minRate) = minMaxRate.mMinimum;
    (*maxRate) = minMaxRate.mMaximum;

    return noErr;
}

/**
 * Lists the audio devices available and stores their UIDs in the provided
 * parameter.
 *
 * @param deviceUIDList A pointer which will be filled in with a list of device
 * UID strings. The caller is responsible to free this list and all the items.
 *
 * @return -1 in case of error. Otherwise, returns the number of devices stored
 * in the deviceUIDList.
 */
int maccoreaudio_getDeviceUIDList(
        char *** deviceUIDList)
{
    OSStatus err = noErr;
    UInt32 propsize;
    int nbDevices = -1;

    AudioObjectPropertyAddress address =
    {
        kAudioHardwarePropertyDevices,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMaster
    };
    if((err = AudioObjectGetPropertyDataSize(
                    kAudioObjectSystemObject,
                    &address,
                    0,
                    NULL,
                    &propsize))
            != noErr)
    {
        fprintf(stderr,
                "getDeviceUIDList (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyDataSize, err: %d\n",
                    ((int) err));
        return -1;
    }

    nbDevices = propsize / sizeof(AudioDeviceID);    
    AudioDeviceID *devices = NULL;
    if((devices = (AudioDeviceID*) malloc(nbDevices * sizeof(AudioDeviceID)))
            == NULL)
    {
        perror("getDeviceUIDList (coreaudio/device.c): \
                    \n\tmalloc\n");
        return -1;
    }

    if((err = AudioObjectGetPropertyData(
                    kAudioObjectSystemObject,
                    &address,
                    0,
                    NULL,
                    &propsize,
                    devices))
            != noErr)
    {
        free(devices);
        fprintf(stderr,
                "getDeviceUIDList (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d\n",
                    ((int) err));
        return -1;
    }

    if(((*deviceUIDList) = (char**) malloc(nbDevices * sizeof(char*)))
            == NULL)
    {
        free(devices);
        perror("getDeviceUIDList (coreaudio/device.c): \
                    \n\tmalloc\n");
        return -1;
    }

    int i;
    for(i = 0; i < nbDevices; ++i)
    {
        if(((*deviceUIDList)[i] = maccoreaudio_getAudioDeviceProperty(
                        devices[i],
                        kAudioDevicePropertyDeviceUID))
                == NULL)
        {
            int j;
            for(j = 0; j < i; ++j)
            {
                free((*deviceUIDList)[j]);
            }
            free(*deviceUIDList);
            free(devices);
            fprintf(stderr,
                    "getDeviceUIDList (coreaudio/device.c): \
                    \n\tgetAudioDeviceProperty\n");
            return -1;
        }
    }

    free(devices);

    return nbDevices;
}
 
/**
 * Registers the listener for new plugged-in/out devices.
 */
void maccoreaudio_initializeHotplug(
        void* callbackFunction)
{
    AudioObjectPropertyAddress address =
    {
        kAudioHardwarePropertyDevices,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMaster
    };

    AudioObjectAddPropertyListener(
            kAudioObjectSystemObject,
            &address,
            maccoreaudio_devicesChangedCallback,
            callbackFunction);
}

/**
 * Unregisters the listener for new plugged-in/out devices.
 */
void maccoreaudio_uninitializeHotplug()
{
    AudioObjectPropertyAddress address =
    {
        kAudioHardwarePropertyDevices,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMaster
    };

    AudioObjectRemovePropertyListener(
            kAudioObjectSystemObject,
            &address,
            maccoreaudio_devicesChangedCallback,
            NULL);
}

/**
 * The callback function called when a device is plugged-in/out.
 *
 * @param inObjectID The AudioObject whose properties have changed.
 * @param inNumberAddresses The number of elements in the inAddresses array.
 * @param inAddresses An array of AudioObjectPropertyAddresses indicating which
 * properties changed.
 * @param inClientData A pointer to client data established when the listener
 * proc was registered with the AudioObject.
 *
 * @return The return value is currently unused and should always be 0.
 */
static OSStatus maccoreaudio_devicesChangedCallback(
        AudioObjectID inObjectID,
        UInt32 inNumberAddresses,
        const AudioObjectPropertyAddress inAddresses[],
        void *inClientData)
{
    void (*callbackFunction) (void) = inClientData;
    callbackFunction();

    return noErr;
}

/**
 * Returns a string identifier of the device transport type.
 *
 * @param deviceUID The device UID to get the transport type from.
 *
 * @return The string identifier of the device transport type. Or NULL if
 * failed.
 */
const char* maccoreaudio_getTransportType(
        const char * deviceUID)
{
    AudioDeviceID device;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "maccoreaudio_getTransportType (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return NULL;
    }
    // target device transport type property
    AudioObjectPropertyAddress address;
    address.mSelector = kAudioDevicePropertyTransportType;
    address.mScope = kAudioObjectPropertyScopeGlobal;
    address.mElement = kAudioObjectPropertyElementMaster;

    OSStatus err;
    unsigned int transportType = 0;
    UInt32 size = sizeof(transportType);
    if((err = AudioObjectGetPropertyData(
                    device,
                    &address,
                    0,
                    NULL,
                    &size,
                    &transportType))
            != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_getTransportType (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData: err: %d\n",
                    (int) err);
        return NULL;
    }

    switch(transportType)
    {
        case kAudioDeviceTransportTypeAggregate:
            return transportTypeAggregate;
            break;
        case kAudioDeviceTransportTypeAirPlay:
            return transportTypeAirPlay;
            break;
        case kAudioDeviceTransportTypeAutoAggregate:
            return transportTypeAutoAggregate;
            break;
        case kAudioDeviceTransportTypeAVB:
            return transportTypeAVB;
            break;
        case kAudioDeviceTransportTypeBluetooth:
            return transportTypeBlueTooth;
            break;
        case kAudioDeviceTransportTypeBuiltIn:
            return transportTypeBuiltIn;
            break;
        case kAudioDeviceTransportTypeDisplayPort:
            return transportTypeDisplayPort;
            break;
        case kAudioDeviceTransportTypeFireWire:
            return transportTypeFireWire;
            break;
        case kAudioDeviceTransportTypeHDMI:
            return transportTypeHDMI;
            break;
        case kAudioDeviceTransportTypePCI:
            return transportTypePCI;
            break;
        case kAudioDeviceTransportTypeThunderbolt:
            return transportTypeThunderbolt;
            break;
        case kAudioDeviceTransportTypeUnknown:
            return transportTypeUnknown;
            break;
        case kAudioDeviceTransportTypeUSB:
            return transportTypeUSB;
            break;
        case kAudioDeviceTransportTypeVirtual:
            return transportTypeVirtual;
            break;
        default:
            return NULL;
            break;
    }
}

maccoreaudio_stream * maccoreaudio_startInputStream(
        const char * deviceUID,
        void* callbackFunction,
        void* callbackObject,
        void* callbackMethod,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved)
{
    return maccoreaudio_startStream(
            deviceUID,
            callbackFunction,
            callbackObject,
            callbackMethod,
            maccoreaudio_readInputStream,
            false,
            sampleRate,
            nbChannels,
            bitsPerChannel,
            isFloat,
            isBigEndian,
            isNonInterleaved);
}

maccoreaudio_stream * maccoreaudio_startOutputStream(
        const char * deviceUID,
        void* callbackFunction,
        void* callbackObject,
        void* callbackMethod,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved)
{
    return maccoreaudio_startStream(
            deviceUID,
            callbackFunction,
            callbackObject,
            callbackMethod,
            maccoreaudio_writeOutputStream,
            true,
            sampleRate,
            nbChannels,
            bitsPerChannel,
            isFloat,
            isBigEndian,
            isNonInterleaved);
}

/**
 * The the IO processing of a device.
 *
 * @param deviceUID The device UID to get the data from / to.
 * @param callbackFunction A function called 
 * @param readWriteFunction A function pointer called by the IO when data are
 * available for read / write.
 */
maccoreaudio_stream * maccoreaudio_startStream(
        const char * deviceUID,
        void* callbackFunction,
        void* callbackObject,
        void* callbackMethod,
        void* readWriteFunction,
        unsigned char isJavaFormatSource,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved)
{
    AudioDeviceID device;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "maccoreaudio_startStream (coreaudio/device.c): \
                    \n\tgetDevice\n");
        return NULL;
    }

    // Init the stream structure.
    maccoreaudio_stream * stream;
    if((stream = (maccoreaudio_stream*) malloc(sizeof(maccoreaudio_stream)))
            == NULL)
    {
        perror("maccoreaudio_startStream (coreaudio/device.c): \
                    \n\tmalloc\n");
        return NULL;
    }
    stream->ioProcId = NULL;
    stream->callbackFunction = callbackFunction;
    stream->callbackObject = callbackObject;
    stream->callbackMethod = callbackMethod;

    AudioStreamBasicDescription javaFormat;
    FillOutASBDForLPCM(
            &javaFormat,
            sampleRate,
            nbChannels,
            bitsPerChannel,
            bitsPerChannel,
            isFloat,
            isBigEndian,
            isNonInterleaved); 
    if(maccoreaudio_initConverter(
                deviceUID,
                &javaFormat,
                isJavaFormatSource,
                &stream->converter,
                &stream->conversionRatio)
            != noErr)
    {
        free(stream);
        fprintf(stderr,
                "maccoreaudio_startStream (coreaudio/device.c): \
                    \n\tmaccoreaudio_initConverter\n");
        return NULL;
    }

    //  register the IOProc
    if(AudioDeviceCreateIOProcID(
            device,
            readWriteFunction,
            stream,
            &stream->ioProcId) != noErr)
    {
        free(stream);
        fprintf(stderr,
                "maccoreaudio_startStream (coreaudio/device.c): \
                    \n\tAudioDeviceIOProcID\n");
        return NULL;
    }

    //  start IO
    AudioDeviceStart(device, stream->ioProcId);

    return stream;
}

void maccoreaudio_stopStream(
        const char * deviceUID,
        maccoreaudio_stream * stream)
{
    AudioDeviceID device;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "maccoreaudio_stopStream (coreaudio/device.c): \
                    \n\tgetDevice: %s\n",
                    deviceUID);
        fflush(stderr);
        return;
    }

    //  stop IO
    AudioDeviceStop(device, stream->ioProcId);

    //  unregister the IOProc
    AudioDeviceDestroyIOProcID(device, stream->ioProcId);

    AudioConverterDispose(stream->converter);

    free(stream);
}

OSStatus maccoreaudio_readInputStream(
        AudioDeviceID inDevice,
        const AudioTimeStamp* inNow,
        const AudioBufferList* inInputData,
        const AudioTimeStamp* inInputTime,
        AudioBufferList* outOutputData,
        const AudioTimeStamp* inOutputTime,
        void* inClientData)
{
    OSStatus err = noErr;
    maccoreaudio_stream * stream = (maccoreaudio_stream*) inClientData;
    void (*callbackFunction) (char*, int, void*, void*)
        = stream->callbackFunction;
    UInt32 tmpLength
        = inInputData->mBuffers[0].mDataByteSize * stream->conversionRatio;
    char tmpBuffer[tmpLength];
    int i;
    for(i = 0; i < inInputData->mNumberBuffers; ++i)
    {
        if(inInputData->mBuffers[i].mData != NULL
                && inInputData->mBuffers[i].mDataByteSize > 0)
        {
            if((err = AudioConverterConvertBuffer(
                            stream->converter,
                            inInputData->mBuffers[i].mDataByteSize,
                            inInputData->mBuffers[i].mData,
                            &tmpLength,
                            tmpBuffer))
                    != noErr)
            {
                fprintf(stderr,
                        "maccoreaudio_readInputStream (coreaudio/device.c): \
                            \n\tAudioConverterConvertBuffer: %x\n",
                            (int) err);
                fflush(stderr);
                return err;
            }

            callbackFunction(
                    tmpBuffer,
                    tmpLength,
                    stream->callbackObject,
                    stream->callbackMethod);
        }
    }

    return noErr;
}

OSStatus maccoreaudio_writeOutputStream(
        AudioDeviceID inDevice,
        const AudioTimeStamp* inNow,
        const AudioBufferList* inInputData,
        const AudioTimeStamp* inInputTime,
        AudioBufferList* outOutputData,
        const AudioTimeStamp* inOutputTime,
        void* inClientData)
{
    OSStatus err = noErr;

    maccoreaudio_stream * stream = (maccoreaudio_stream*) inClientData;
    void (*callbackFunction) (char*, int, void*, void*)
        = stream->callbackFunction;

    if(outOutputData->mNumberBuffers == 0)
    {
        return err;
    }

    int tmpLength
        = outOutputData->mBuffers[0].mDataByteSize * stream->conversionRatio;
    char tmpBuffer[tmpLength];

    callbackFunction(
            tmpBuffer,
            tmpLength,
            stream->callbackObject,
            stream->callbackMethod);

    if((err = AudioConverterConvertBuffer(
                    stream->converter,
                    tmpLength,
                    tmpBuffer,
                    &outOutputData->mBuffers[0].mDataByteSize,
                    outOutputData->mBuffers[0].mData))
            != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_writeOutputStream (coreaudio/device.c): \
                    \n\tAudioConverterConvertBuffer\n");
        fflush(stderr);
        return err;
    }

    // Copies the same data into the other buffers.
    int i;
    UInt32 length;
    for(i = 1; i < outOutputData->mNumberBuffers; ++i)
    {
        // Copies available data.
        length = outOutputData->mBuffers[i].mDataByteSize;
        if(length > outOutputData->mBuffers[0].mDataByteSize)
        {
            length = outOutputData->mBuffers[0].mDataByteSize;
        }
        memcpy(
                outOutputData->mBuffers[i].mData,
                outOutputData->mBuffers[0].mData,
                length);

        // Resets the resting buffer.
        if(outOutputData->mBuffers[i].mDataByteSize
                > outOutputData->mBuffers[0].mDataByteSize)
        {
            memset(
                    outOutputData->mBuffers[i].mData
                        + outOutputData->mBuffers[0].mDataByteSize,
                    0,
                    outOutputData->mBuffers[i].mDataByteSize
                        - outOutputData->mBuffers[0].mDataByteSize);
        }
    }

    return noErr;
}

/**
 * Returns the stream virtual format for a given stream.
 *
 * @param stream The stream to get the format.
 * @param format The variable to write the forat into.
 *
 * @return noErr if everything works fine. Any other value if failed.
 */
OSStatus maccoreaudio_getStreamVirtualFormat(
        AudioStreamID stream,
        AudioStreamBasicDescription * format)
{
    // Gets the audio format of the stream.
    OSStatus err = noErr;
    UInt32 size = sizeof(AudioStreamBasicDescription);
    AudioObjectPropertyAddress address;
    address.mSelector = kAudioStreamPropertyVirtualFormat;
    address.mScope = kAudioObjectPropertyScopeGlobal;
    address.mElement = kAudioObjectPropertyElementMaster;
    if((err = AudioObjectGetPropertyData(
                    stream,
                    &address,
                    0,
                    NULL,
                    &size,
                    format))
            != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_getStreamVirtualFormat (coreaudio/device.c): \
                \n\tAudioObjectGetPropertyData, err: 0x%x\n",
                ((int) err));
        fflush(stderr);
        return err;
    }

    return err;
}

/**
 * Initializes a new audio converter to work between the given device and the
 * format description.
 *
 * @param deviceUID The device identifier.
 * @param javaFormat The format needed by the upper layer Java aplication.
 * @param isJavaFormatSource True if the Java format is the source of this
 * converter and the device the ouput. False otherwise.
 * @param converter A pointer to the converter used to store the new created
 * converter.
 *
 * @return noErr if everything works correctly. Any other vlue otherwise.
 */
OSStatus maccoreaudio_initConverter(
        const char * deviceUID,
        const AudioStreamBasicDescription * javaFormat,
        unsigned char isJavaFormatSource,
        AudioConverterRef * converter,
        double * conversionRatio)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;

    // Gets the correspoding device
    if((device = maccoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        fprintf(stderr,
                "maccoreaudio_initConverter (coreaudio/device.c): \
                    \n\tgetDevice\n");
        fflush(stderr);
        return kAudioObjectUnknown;
    }

    AudioStreamBasicDescription deviceFormat;
    AudioStreamID audioStreamIds[1];
    UInt32 size = sizeof(AudioStreamID *);
    address.mSelector = kAudioDevicePropertyStreams;
    address.mScope = kAudioObjectPropertyScopeGlobal;
    address.mElement = kAudioObjectPropertyElementMaster;
    if((err = AudioObjectGetPropertyData(
                    device,
                    &address,
                    0,
                    NULL,
                    &size,
                    &audioStreamIds))
            != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_countChannels (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: 0x%x\n",
                    ((int) err));
        fflush(stderr);
        return err;
    }

    if((err = maccoreaudio_getStreamVirtualFormat(
                    audioStreamIds[0],
                    &deviceFormat))
                != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_countChannels (coreaudio/device.c): \
                    \n\tmaccoreaudiogetStreamVirtualFormat, err: 0x%x\n",
                    ((int) err));
        fflush(stderr);
        return err;
    }
    
    const AudioStreamBasicDescription *inFormat = javaFormat;
    const AudioStreamBasicDescription *outFormat = &deviceFormat;
    if(!isJavaFormatSource)
    {
        inFormat = &deviceFormat;
        outFormat = javaFormat;
    }

    if((err = AudioConverterNew(inFormat, outFormat, converter))
            != noErr)
    {
        fprintf(stderr,
                "maccoreaudio_countChannels (coreaudio/device.c): \
                    \n\tAudioConverterNew, err: 0x%x\n",
                    ((int) err));
        fflush(stderr);
        return err;
    }

    *conversionRatio =
        ((double) javaFormat->mBytesPerFrame)
            / ((double) deviceFormat.mBytesPerFrame)
        * javaFormat->mSampleRate / deviceFormat.mSampleRate;

    return err;
}

/**
 * Computes the value for the audio stream basic description mFormatFlags
 * field for linear PCM data. This function does not support specifying sample
 * formats that are either unsigned integer or low-aligned.
 *
 * @param inValidBitsPerChannel The number of valid bits in each sample.
 * @param inTotalBitsPerChannel The total number of bits in each sample.
 * @param inIsFloat Use true if the samples are represented with floating point
             numbers.
 * @param inIsBigEndian Use true if the samples are big endian.
 * @param inIsNonInterleaved Use true if the samples are noninterleaved.
 *
 * @return A UInt32 value containing the calculated format flags.
 */
inline UInt32 CalculateLPCMFlags (
        UInt32 inValidBitsPerChannel,
        UInt32 inTotalBitsPerChannel,
        bool inIsFloat,
        bool inIsBigEndian,
        bool inIsNonInterleaved)
{
    return
        (inIsFloat ? kAudioFormatFlagIsFloat : kAudioFormatFlagIsSignedInteger)
        | (inIsBigEndian ? ((UInt32)kAudioFormatFlagIsBigEndian) : 0)
        | ((!inIsFloat && (inValidBitsPerChannel == inTotalBitsPerChannel)) ?
                kAudioFormatFlagIsPacked : kAudioFormatFlagIsAlignedHigh)
        | (inIsNonInterleaved ? ((UInt32)kAudioFormatFlagIsNonInterleaved) : 0);
}

/**
 * Fills AudioStreamBasicDescription information.
 *
 * @param outASBD On output, a filled-out AudioStreamBasicDescription structure.
 * @param inSampleRate The number of sample frames per second of the data in the
 * stream.
 * @param inChannelsPerFrame The number of channels in each frame of data.
 * @param inValidBitsPerChannel The number of valid bits in each sample.
 * @param inTotalBitsPerChannel The total number of bits in each sample.
 * @param inIsFloat Use true if the samples are represented as floating-point
 * numbers.
 * @param inIsBigEndian Use true if the samples are big endian.
 * @param inIsNonInterleaved Use true if the samples are noninterleaved.
 */
inline void FillOutASBDForLPCM(
        AudioStreamBasicDescription * outASBD,
        Float64 inSampleRate,
        UInt32 inChannelsPerFrame,
        UInt32 inValidBitsPerChannel,
        UInt32 inTotalBitsPerChannel,
        bool inIsFloat,
        bool inIsBigEndian,
        bool inIsNonInterleaved)
{
    outASBD->mSampleRate = inSampleRate;
    outASBD->mFormatID = kAudioFormatLinearPCM;
    outASBD->mFormatFlags = CalculateLPCMFlags(
            inValidBitsPerChannel,
            inTotalBitsPerChannel,
            inIsFloat,
            inIsBigEndian,
            inIsNonInterleaved);
    outASBD->mBytesPerPacket =
        (inIsNonInterleaved ? 1 : inChannelsPerFrame) *
        (inTotalBitsPerChannel/8);
    outASBD->mFramesPerPacket = 1;
    outASBD->mBytesPerFrame =
        (inIsNonInterleaved ? 1 : inChannelsPerFrame) *
        (inTotalBitsPerChannel/8);
    outASBD->mChannelsPerFrame = inChannelsPerFrame;
    outASBD->mBitsPerChannel = inValidBitsPerChannel;
}
