/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#include "device.h"

#include <math.h>
#include <pthread.h>
#include <stdio.h>
#include <sys/time.h>

extern void MacCoreaudio_log(const char * error_format, ...);

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

AudioDeviceID MacCoreaudio_getDevice(const char * deviceUID);

AudioDeviceID MacCoreaudio_getDeviceForSpecificScope(
        const char * deviceUID,
        UInt32 inputOutputScope);

char* MacCoreaudio_getDeviceProperty(
        const char * deviceUID,
        AudioObjectPropertySelector propertySelector);

char* MacCoreaudio_getAudioDeviceProperty(
        AudioDeviceID device,
        AudioObjectPropertySelector propertySelector);

OSStatus MacCoreaudio_setDeviceVolume(
        const char * deviceUID,
        Float32 volume,
        UInt32 inputOutputScope);

Float32 MacCoreaudio_getDeviceVolume(
        const char * deviceUID,
        UInt32 inputOutputScope);

OSStatus MacCoreaudio_getChannelsForStereo(
        const char * deviceUID,
        UInt32 * channels);

int MacCoreaudio_countChannels(
        const char * deviceUID,
        AudioObjectPropertyScope inputOutputScope);

static OSStatus MacCoreaudio_devicesChangedCallback(
        AudioObjectID inObjectID,
        UInt32 inNumberAddresses,
        const AudioObjectPropertyAddress inAddresses[],
        void *inClientData);

OSStatus MacCoreaudio_initConverter(MacCoreaudio_Stream * stream);

OSStatus MacCoreaudio_freeConverter(MacCoreaudio_Stream * stream);

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

char* MacCoreaudio_getDefaultDeviceUID(UInt32 inputOutputScope);

MacCoreaudio_Stream * MacCoreaudio_startStream(
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
        unsigned char isNonInterleaved,
        unsigned char isEchoCancel);

OSStatus MacCoreaudio_readInputStream(
        AudioDeviceID inDevice,
        const AudioTimeStamp* inNow,
        const AudioBufferList* inInputData,
        const AudioTimeStamp* inInputTime,
        AudioBufferList* outOutputData,
        const AudioTimeStamp* inOutputTime,
        void* inClientData);

OSStatus MacCoreaudio_writeOutputStream(
        AudioDeviceID inDevice,
        const AudioTimeStamp* inNow,
        const AudioBufferList* inInputData,
        const AudioTimeStamp* inInputTime,
        AudioBufferList* outOutputData,
        const AudioTimeStamp* inOutputTime,
        void* inClientData);
void MacCoreaudio_writeOutputStreamToAECStream(
        MacCoreaudio_Stream *src,
        UInt32 outBufferSize,
        MacCoreaudio_Stream *dst);
void MacCoreaudio_writeOutputStreamToAECStreams(
        MacCoreaudio_Stream *stream,
        UInt32 outBufferSize);

OSStatus MacCoreaudio_getStreamVirtualFormat(
        AudioStreamID stream,
        AudioStreamBasicDescription * format);

OSStatus MacCoreaudio_getDeviceFormat(
        const char * deviceUID,
        unsigned char isOutput,
        AudioStreamBasicDescription * deviceFormat);

OSStatus MacCoreaudio_getDeviceFormatDeprecated(
        const char * deviceUID,
        unsigned char isOutput,
        AudioStreamBasicDescription * deviceFormat);

void MacCoreaudio_getDefaultFormat(
        AudioStreamBasicDescription * deviceFormat);

OSStatus MacCoreaudio_converterComplexInputDataProc(
        AudioConverterRef inAudioConverter,
        UInt32* ioNumberDataPackets,
        AudioBufferList* ioData,
        AudioStreamPacketDescription** ioDataPacketDescription,
        void* inUserData);

int MacCoreaudio_getAECCorrespondingRate(int rate);

OSStatus MacCoreaudio_initDeviceFormat(
        const char * deviceUID,
        MacCoreaudio_Stream * stream);

void MacCoreaudio_updateBuffer(
        char** buffer,
        int* bufferLength,
        int newLength);

int MacCoreaudio_isSameFormat(
        AudioStreamBasicDescription format1,
        AudioStreamBasicDescription format2);

OSStatus MacCoreaudio_convert(
        MacCoreaudio_Stream * stream,
        unsigned short step,
        AudioConverterRef converter,
        char * inBuffer,
        int inBufferLength,
        AudioStreamBasicDescription inFormat,
        char * outBuffer,
        int outBufferLength,
        AudioStreamBasicDescription outFormat);

void MacCoreaudio_addAECStream(MacCoreaudio_Stream *stream);
void MacCoreaudio_removeAECStream(MacCoreaudio_Stream *stream);

unsigned int MacCoreaudio_aecStreamCapacity = 0;
unsigned int MacCoreaudio_aecStreamCount = 0;
pthread_mutex_t MacCoreaudio_aecStreamMutex = PTHREAD_MUTEX_INITIALIZER;
MacCoreaudio_Stream **MacCoreaudio_aecStreams = NULL;

/**
 * Returns if the audio device is an input device.
 *
 * @param deviceUID The device UID.
 *
 * @return True if the given device identifier correspond to an input device.
 * False otherwise.
 */
int MacCoreaudio_isInputDevice(const char * deviceUID)
{
    return
        (MacCoreaudio_countChannels(deviceUID, kAudioDevicePropertyScopeInput)
            > 0);
}

/**
 * Returns if the audio device is an output device.
 *
 * @param deviceUID The device UID.
 *
 * @return True if the given device identifier correspond to an output device.
 * False otherwise.
 */
int MacCoreaudio_isOutputDevice(const char * deviceUID)
{
    return
        (MacCoreaudio_countChannels(deviceUID, kAudioDevicePropertyScopeOutput)
            > 0);
}

/**
 * Returns the audio device corresponding to the UID given in parameter. Or
 * kAudioObjectUnknown if the device is non-existent or if anything as failed.
 *
 * @param deviceUID The device UID.
 *
 * @return The audio device corresponding to the UID given in parameter. Or
 * kAudioObjectUnknown if the device is non-existent or if anything as failed.
 */
AudioDeviceID MacCoreaudio_getDevice(const char * deviceUID)
{
    return
        MacCoreaudio_getDeviceForSpecificScope(
                deviceUID,
                kAudioObjectPropertyScopeGlobal);
}

/**
 * Returns the audio device corresponding to the UID given in parameter for the
 * specified scope (global, input or output). Or kAudioObjectUnknown if the
 * device is non-existent or if anything as failed.
 *
 * @param deviceUID The device UID.
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return The audio device corresponding to the UID given in parameter. Or
 * kAudioObjectUnknown if the device is non-existent or if anything as failed.
 */
AudioDeviceID MacCoreaudio_getDeviceForSpecificScope(
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
            kCFStringEncodingUTF8)) == NULL)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDevice (coreaudio/device.c): \
                    \n\tCFStringCreateWithCString for device %s",
                deviceUID);
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
        MacCoreaudio_log(
                "MacCoreaudio_getDevice (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d for device %s",
                ((int) err),
                deviceUID);

        // Frees the allocated device UID ref.
        CFRelease(deviceUIDRef);

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
char* MacCoreaudio_getDefaultInputDeviceUID(void)
{
    return MacCoreaudio_getDefaultDeviceUID(kAudioDevicePropertyScopeInput);
}

/**
 * Returns the default output device UID.
 *
 * @return The default output device UID. NULL if an error occurs.
 */
char* MacCoreaudio_getDefaultOutputDeviceUID(void)
{
    return MacCoreaudio_getDefaultDeviceUID(kAudioDevicePropertyScopeOutput);
}

/**
 * Returns the default device UID for input or output.
 *
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return The default device UID for input or output. NULL if an error occurs.
 */
char* MacCoreaudio_getDefaultDeviceUID(UInt32 inputOutputScope)
{
    OSStatus err = noErr;
    AudioDeviceID device;
    UInt32 size = sizeof(AudioDeviceID);
    AudioObjectPropertyAddress address;
    char * deviceUID = NULL;

    if(inputOutputScope == kAudioDevicePropertyScopeInput)
        address.mSelector = kAudioHardwarePropertyDefaultInputDevice;
    else
        address.mSelector = kAudioHardwarePropertyDefaultOutputDevice;
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
        MacCoreaudio_log(
                "MacCoreaudio_getDefaultDeviceUID (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d",
                ((int) err));
        return NULL;
    }

    if((deviceUID = MacCoreaudio_getAudioDeviceProperty(
                    device,
                    kAudioDevicePropertyDeviceUID))
            == NULL)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDefaultDeviceUID (coreaudio/device.c): \
                    \n\tgetAudioDeviceProperty");
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
char* MacCoreaudio_getDeviceName(const char * deviceUID)
{
    return MacCoreaudio_getDeviceProperty(deviceUID, kAudioObjectPropertyName);
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
char* MacCoreaudio_getDeviceModelIdentifier(const char * deviceUID)
{
    return
        MacCoreaudio_getDeviceProperty(deviceUID, kAudioDevicePropertyModelUID);
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
char* MacCoreaudio_getDeviceProperty(
        const char * deviceUID,
        AudioObjectPropertySelector propertySelector)
{
    AudioDeviceID device;

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceProperty (coreaudio/device.c): \
                    \n\tgetDevice: %s",
                deviceUID);
        return NULL;
    }

    return MacCoreaudio_getAudioDeviceProperty(device, propertySelector);
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
char* MacCoreaudio_getAudioDeviceProperty(
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
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceProperty (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d",
                ((int) err));
        return NULL;
    }

    // Converts the device property to UTF-8.
    CFIndex devicePropertyLength
        = (CFStringGetLength(deviceProperty) + 1) * 4 * sizeof(char);
    char *chars = (char *) malloc(devicePropertyLength);
    // The caller of this function must free the string.
    if(chars == NULL)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_getDeviceProperty (coreaudio/device.c): \
                    \n\tmalloc",
                strerror(errno));
        return NULL;
    }
    if(CFStringGetCString(
                deviceProperty,
                chars,
                devicePropertyLength,
                kCFStringEncodingUTF8))
    {
        return chars;
    }
    else
    {
        free(chars);
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
OSStatus MacCoreaudio_setInputDeviceVolume(
        const char * deviceUID,
        Float32 volume)
{
    return
        MacCoreaudio_setDeviceVolume(
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
OSStatus MacCoreaudio_setOutputDeviceVolume(
        const char * deviceUID,
        Float32 volume)
{
    return
        MacCoreaudio_setDeviceVolume(
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
OSStatus MacCoreaudio_setDeviceVolume(
        const char * deviceUID,
        Float32 volume,
        UInt32 inputOutputScope)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    UInt32 channels[2];

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_setDeviceVolume (coreaudio/device.c): \
                    \n\tMacCoreaudio_getDevice (unknown device for UID: %s)",
                deviceUID);
        return -1;
    }

    // get the input device stereo channels
    if((MacCoreaudio_getChannelsForStereo(deviceUID, channels)) != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_setDeviceVolume (coreaudio/device.c): \
                    \n\tMacCoreaudio_getChannelsForStereo, err: %d \
                    for device %s",
                ((int) err),
                deviceUID);
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
                MacCoreaudio_log(
                        "MacCoreaudio_setDeviceVolume (coreaudio/device.c): \
                            \n\tAudioObjectSetPropertyData, err: %d \
                            for device %s",
                            ((int) err),
                        deviceUID);
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
Float32 MacCoreaudio_getInputDeviceVolume(const char * deviceUID)
{
    return
        MacCoreaudio_getDeviceVolume(deviceUID, kAudioDevicePropertyScopeInput);
}

/**
 * Gets the output volume for a given device.
 *
 * @param deviceUID The device UID to get volume from.
 *
 * @return The device volume as a scalar value between 0.0 and 1.0. Returns -1.0
 * if an error occurs.
 */
Float32 MacCoreaudio_getOutputDeviceVolume(const char * deviceUID)
{
    return
        MacCoreaudio_getDeviceVolume(
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
Float32 MacCoreaudio_getDeviceVolume(
        const char * deviceUID,
        UInt32 inputOutputScope)
{
    AudioDeviceID device;
    Float32 volume = -1.0;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    UInt32 channels[2];

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceVolume (coreaudio/device.c): \
                    \n\tMacCoreaudio_getDevice: %s",
                deviceUID);
        return -1.0;
    }

    // get the input device stereo channels
    if((MacCoreaudio_getChannelsForStereo(deviceUID, channels)) != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceVolume (coreaudio/device.c): \
                    \n\tMacCoreaudio_getChannelsForStereo, err: %d \
                    for device %s",
                ((int) err),
                deviceUID);
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
                MacCoreaudio_log(
                        "MacCoreaudio_getDeviceVolume (coreaudio/device.c): \
                            \n\tAudioObjectSetPropertyData, err: %d \
                            for device %s",
                        ((int) err),
                        deviceUID);
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
 * @return An OSStatus set to noErr if everything works well. Any other value
 * otherwise.
 */
OSStatus
MacCoreaudio_getChannelsForStereo(const char *deviceUID, UInt32 *channels)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getChannelsForStereo (coreaudio/device.c): \
                    \n\tMacCoreaudio_getDevice: %s",
                deviceUID);
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
        MacCoreaudio_log(
                "MacCoreaudio_getChannelsForStereo (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d for device %s",
                ((int) err),
                deviceUID);
        return err;
    }

    return err;
}

/**
 * Returns the number of channels available for input device.
 *
 * @param deviceUID The device UID to get the channels from.
 *
 * @return The number of channels available for a given input device.
 * -1 if an error occurs.
 */
int MacCoreaudio_countInputChannels(const char * deviceUID)
{
    return
        MacCoreaudio_countChannels(deviceUID, kAudioDevicePropertyScopeInput);
}

/**
 * Returns the number of channels available for output device.
 *
 * @param deviceUID The device UID to get the channels from.
 *
 * @return The number of channels available for a given output device.
 * -1 if an error occurs.
 */
int MacCoreaudio_countOutputChannels(const char * deviceUID)
{
    return
        MacCoreaudio_countChannels(deviceUID, kAudioDevicePropertyScopeOutput);
}

/**
 * Returns the number of channels available for a given input / output device.
 *
 * @param deviceUID The device UID to get the channels from.
 * @param inputOutputScope The scope to tell if this is an output or an input
 * device.
 *
 * @return The number of channels available for a given input / output device.
 * -1 if an error occurs.
 */
int MacCoreaudio_countChannels(
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

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_countChannels (coreaudio/device.c): \
                    \n\tMacCoreaudio_getDevice: %s",
                deviceUID);
        return -1;
    }

    // Gets the size of the streams for this device.
    address.mSelector = kAudioDevicePropertyStreamConfiguration;
    address.mScope = inputOutputScope;
    address.mElement = kAudioObjectPropertyElementWildcard; // 0
    if((err = AudioObjectGetPropertyDataSize(device, &address, 0, NULL, &size))
            != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_countChannels (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyDataSize, err: %d \
                    for device %s",
                    ((int) err),
                deviceUID);
        return -1;
    }

    // Gets the number of channels of each stream.
    if((audioBufferList = (AudioBufferList *) malloc(size)) == NULL)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_countChannels (coreaudio/device.c): \
                    \n\tmalloc",
                strerror(errno));
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
        MacCoreaudio_log(
                "MacCoreaudio_countChannels (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d for device %s",
                    ((int) err),
                deviceUID);
        return -1;
    }
    for(i = 0; i < audioBufferList->mNumberBuffers; ++i)
        nbChannels += audioBufferList->mBuffers[i].mNumberChannels;
    free(audioBufferList);

    return nbChannels;
}

/**
 * Returns the nominal sample rate for the given device.
 *
 * @param deviceUID The device UID to get the channels from.
 * @param isEchoCancel True if the echo canceler will be used with this device.
 *
 * @return The nominal sample rate for the given device. -1.0 if an error
 * occurs.
 */
Float64 MacCoreaudio_getNominalSampleRate(
        const char * deviceUID,
        unsigned char isOutputStream,
        unsigned char isEchoCancel)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    Float64 rate = -1.0;

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getNominalSampleRate (coreaudio/device.c): \
                    \n\tMacCoreaudio_getDevice: %s",
                deviceUID);
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
        MacCoreaudio_log(
                "MacCoreaudio_getNominalSampleRate (coreaudio/device.c): \
                    \n\tAudioObjactGetPropertyData, err: %d for device %s",
                (int) err,
                deviceUID);
        return -1.0;
    }

    // The echo canceler must be set to met capture device requirement.
    if(!isOutputStream && isEchoCancel)
        rate = MacCoreaudio_getAECCorrespondingRate(rate);

    return rate;
}

/**
 * Gets the minimal and maximal nominal sample rate for the given device.
 *
 * @param deviceUID The device UID to get the channels from.
 * @param minRate The minimal rate available for this device.
 * @param maxRate The maximal rate available for this device.
 * @param isEchoCancel True if the echo canceler will be used with this device.
 *
 * @return noErr if everything is alright. -1.0 if an error occurs.
 */
OSStatus MacCoreaudio_getAvailableNominalSampleRates(
        const char * deviceUID,
        Float64 * minRate,
        Float64 * maxRate,
        unsigned char isOutputStream,
        unsigned char isEchoCancel)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;
    UInt32 size;
    AudioValueRange minMaxRate;
    minMaxRate.mMinimum = -1.0;
    minMaxRate.mMaximum = -1.0;

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getAvailableNominalSampleRates \
                    (coreaudio/device.c): \
                    \n\tMacCoreaudio_getDevice: %s",
                deviceUID);
        return -1.0;
    }

    // Gets the available sample rate.
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
        MacCoreaudio_log(
                "MacCoreaudio_getAvailableNominalSampleRates \
                    (coreaudio/device.c): \
                    \n\tAudioObjactGetPropertyData, err: %d for device %s",
                (int) err,
                deviceUID);
        return -1.0;
    }

    (*minRate) = minMaxRate.mMinimum;
    (*maxRate) = minMaxRate.mMaximum;

    // The echo canceller must be set to met capture device requirement.
    if(!isOutputStream && isEchoCancel)
    {
        (*minRate) = MacCoreaudio_getAECCorrespondingRate(*minRate);
        (*maxRate) = MacCoreaudio_getAECCorrespondingRate(*maxRate);
    }

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
int MacCoreaudio_getDeviceUIDList(char *** deviceUIDList)
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
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceUIDList (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyDataSize, err: %d",
                    ((int) err));
        return -1;
    }

    nbDevices = propsize / sizeof(AudioDeviceID);    
    AudioDeviceID *devices = NULL;
    if((devices = (AudioDeviceID*) malloc(nbDevices * sizeof(AudioDeviceID)))
            == NULL)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_getDeviceUIDList (coreaudio/device.c): \
                    \n\tmalloc",
                strerror(errno));
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
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceUIDList (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: %d",
                    ((int) err));
        return -1;
    }

    if(((*deviceUIDList) = (char**) malloc(nbDevices * sizeof(char*)))
            == NULL)
    {
        free(devices);
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_getDeviceUIDList (coreaudio/device.c): \
                    \n\tmalloc",
                strerror(errno));
        return -1;
    }

    int i;
    for(i = 0; i < nbDevices; ++i)
    {
        if(((*deviceUIDList)[i] = MacCoreaudio_getAudioDeviceProperty(
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
            MacCoreaudio_log(
                    "MacCoreaudio_getDeviceUIDList (coreaudio/device.c): \
                    \n\tMacCoreaudio_getAudioDeviceProperty");
            return -1;
        }
    }

    free(devices);

    return nbDevices;
}
 
/**
 * Registers the listener for new plugged-in/out devices.
 */
void MacCoreaudio_initializeHotplug(void* callbackFunction)
{
    AudioObjectPropertyAddress address =
    {
        kAudioHardwarePropertyDevices,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMaster
    };

    if(AudioObjectAddPropertyListener(
                kAudioObjectSystemObject,
                &address,
                MacCoreaudio_devicesChangedCallback,
                callbackFunction)
            != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_initializeHotplug (coreaudio/device.c): \
                    \n\tAudioObjectAddPropertyListener");
    }
}

/**
 * Unregisters the listener for new plugged-in/out devices.
 */
void MacCoreaudio_uninitializeHotplug()
{
    AudioObjectPropertyAddress address =
    {
        kAudioHardwarePropertyDevices,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMaster
    };

    if(AudioObjectRemovePropertyListener(
                kAudioObjectSystemObject,
                &address,
                MacCoreaudio_devicesChangedCallback,
                NULL)
            != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_uninitializeHotplug (coreaudio/device.c): \
                    \n\tAudioObjectRemovePropertyListener");
    }
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
static OSStatus MacCoreaudio_devicesChangedCallback(
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
const char* MacCoreaudio_getTransportType(const char * deviceUID)
{
    AudioDeviceID device;

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getTransportType (coreaudio/device.c): \
                    \n\tgetDevice: %s",
                deviceUID);
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
        MacCoreaudio_log(
                "MacCoreaudio_getTransportType (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData: err: 0x%x for device %s",
                (int) err,
                deviceUID);
        return NULL;
    }

    switch(transportType)
    {
    case kAudioDeviceTransportTypeUnknown:
        return transportTypeUnknown;
    case kAudioDeviceTransportTypeBuiltIn:
        return transportTypeBuiltIn;
    case kAudioDeviceTransportTypeAggregate:
        return transportTypeAggregate;
    case kAudioDeviceTransportTypeAutoAggregate:
        return transportTypeAutoAggregate;
    case kAudioDeviceTransportTypeVirtual:
        return transportTypeVirtual;
    case kAudioDeviceTransportTypePCI:
        return transportTypePCI;
    case kAudioDeviceTransportTypeUSB:
        return transportTypeUSB;
    case kAudioDeviceTransportTypeFireWire:
        return transportTypeFireWire;
    case kAudioDeviceTransportTypeBluetooth:
        return transportTypeBlueTooth;
    case kAudioDeviceTransportTypeHDMI:
        return transportTypeHDMI;
    case kAudioDeviceTransportTypeDisplayPort:
        return transportTypeDisplayPort;

    /*
     * XXX The following constants were added in OS X 10.8 but we want to target
     * earlier versions if possible.
     */
    case /* kAudioDeviceTransportTypeAirPlay */ 'airp':
        return transportTypeAirPlay;
    case /* kAudioDeviceTransportTypeAVB */ 'eavb':
        return transportTypeAVB;
    case /* kAudioDeviceTransportTypeThunderbolt */ 'thun':
        return transportTypeThunderbolt;

    default:
        MacCoreaudio_log(
                "MacCoreaudio_getTransportType (coreaudio/device.c): \
                    \n\tNo transport type found for device %s",
                deviceUID);
        return NULL;
    }
}

MacCoreaudio_Stream * MacCoreaudio_startInputStream(
        const char * deviceUID,
        void* callbackFunction,
        void* callbackObject,
        void* callbackMethod,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved,
        unsigned char isEchoCancel)
{
    return MacCoreaudio_startStream(
            deviceUID,
            callbackFunction,
            callbackObject,
            callbackMethod,
            MacCoreaudio_readInputStream,
            false,
            sampleRate,
            nbChannels,
            bitsPerChannel,
            isFloat,
            isBigEndian,
            isNonInterleaved,
            isEchoCancel);
}

MacCoreaudio_Stream * MacCoreaudio_startOutputStream(
        const char * deviceUID,
        void* callbackFunction,
        void* callbackObject,
        void* callbackMethod,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved,
        unsigned char isEchoCancel)
{
    return MacCoreaudio_startStream(
            deviceUID,
            callbackFunction,
            callbackObject,
            callbackMethod,
            MacCoreaudio_writeOutputStream,
            true,
            sampleRate,
            nbChannels,
            bitsPerChannel,
            isFloat,
            isBigEndian,
            isNonInterleaved,
            isEchoCancel);
}

/**
 * The the IO processing of a device.
 *
 * @param deviceUID The device UID to get the data from / to.
 * @param callbackFunction A function called 
 * @param readWriteFunction A function pointer called by the IO when data are
 * available for read / write.
 */
MacCoreaudio_Stream *
MacCoreaudio_startStream(
        const char *deviceUID,
        void *callbackFunction,
        void *callbackObject,
        void *callbackMethod,
        void *readWriteFunction,
        unsigned char isOutputStream,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved,
        unsigned char isEchoCancel)
{
    AudioDeviceID device;
    OSStatus err = noErr;

    // Gets the corresponding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_startStream (coreaudio/device.c): \
                    \n\tMacCoreaudio_getDevice: %s",
                deviceUID);
        return NULL;
    }

    // Init the stream structure.
    MacCoreaudio_Stream *stream
        = (MacCoreaudio_Stream *) calloc(1, sizeof(MacCoreaudio_Stream));

    if(stream == NULL)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_startStream (coreaudio/device.c): \
                    \n\tmalloc",
                strerror(errno));
        return NULL;
    }

    int i;
    pthread_mutexattr_t mutexattr;

    if ((i = pthread_mutexattr_init(&mutexattr)) == 0)
    {
        pthread_mutexattr_settype(&mutexattr, PTHREAD_MUTEX_DEFAULT);
        i = pthread_mutex_init(&stream->mutex, &mutexattr);
        pthread_mutexattr_destroy(&mutexattr);
    }
    if (i != 0)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_startStream (coreaudio/device.c): \
                    \n\tpthread_mutex_init",
                strerror(errno));
        /*
         * XXX At the current step of the execution of the function, the state
         * of stream is not initialized enough to support the execution of the
         * function MacCoreaudio_stopStream.
         */
        free(stream);
        return NULL;
    }

    stream->callbackFunction = callbackFunction;
    stream->callbackObject = callbackObject;
    stream->callbackMethod = callbackMethod;
    stream->isOutputStream = isOutputStream;

    if((err = MacCoreaudio_initDeviceFormat(deviceUID, stream)) != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_startStream (coreaudio/device.c): \
                    \n\tMacCoreaudio_initDeviceFormat: 0x%x for device %s",
                    (int) err,
                    deviceUID);
        MacCoreaudio_stopStream(deviceUID, stream);
        return NULL;
    }

    // Init AEC
    stream->isEchoCancel = isEchoCancel;
    if(!isOutputStream && isEchoCancel)
    {
        float aecSampleRate = MacCoreaudio_getAECCorrespondingRate(sampleRate);
        UInt32 aecNbChannels = nbChannels;
        UInt32 aecBitsPerChannel = 16;
        unsigned char aecIsFloat = false;
        unsigned char aecIsBigEndian = isBigEndian;
        unsigned char aecIsNonInterleaved = false;

        FillOutASBDForLPCM(
                &stream->aecFormat,
                aecSampleRate,
                aecNbChannels,
                aecBitsPerChannel,
                aecBitsPerChannel,
                aecIsFloat,
                aecIsBigEndian,
                aecIsNonInterleaved);

        stream->aec = LibJitsi_WebRTC_AEC_init();
        if (stream->aec)
        {
            err
                = LibJitsi_WebRTC_AEC_initAudioProcessing(
                        stream->aec,
                        aecSampleRate,
                        aecNbChannels,
                        stream->aecFormat);
            if(err)
            {
                MacCoreaudio_log(
                        "MacCoreaudio_startStream (coreaudio/device.c): \
                            \n\tLibJitsi_WebRTC_AEC_initAudioProcessing: 0x%x for device %s",
                        (int) err,
                        deviceUID);
                MacCoreaudio_stopStream(deviceUID, stream);
                return NULL;
            }

            LibJitsi_WebRTC_AEC_start(stream->aec);
        }
        else
            stream->isEchoCancel = 0;
    }
    else
        stream->aec = NULL;

    // Init the converter.
    FillOutASBDForLPCM(
            &stream->javaFormat,
            sampleRate,
            nbChannels,
            bitsPerChannel,
            bitsPerChannel,
            isFloat,
            isBigEndian,
            isNonInterleaved); 

    if((err = MacCoreaudio_initConverter(stream)) != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_startStream (coreaudio/device.c): \
                    \n\tMacCoreaudio_initConverter: 0x%x for device %s",
                (int) err,
                deviceUID);
        MacCoreaudio_stopStream(deviceUID, stream);
        return NULL;
    }

    //  register the IOProc
    if((err = AudioDeviceCreateIOProcID(
            device,
            readWriteFunction,
            stream,
            &stream->ioProcId)) != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_startStream (coreaudio/device.c): \
                    \n\tAudioDeviceIOProcID: 0x%x for device %s",
                (int) err,
                deviceUID);
        MacCoreaudio_stopStream(deviceUID, stream);
        return NULL;
    }

    //  start IO
    if((err = AudioDeviceStart(device, stream->ioProcId)) != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_startStream (coreaudio/device.c): \
                    \n\tAudioDeviceStart: 0x%x for device %s",
                (int) err,
                deviceUID);
        MacCoreaudio_stopStream(deviceUID, stream);
        return NULL;
    }

    /*
     * Input/capture streams with acoustic echo cancellation (AEC) enabled are
     * registered globally so that output/render streams with AEC enabled can
     * push audio samples to them.
     */
    if (stream->aec)
        MacCoreaudio_addAECStream(stream);

    return stream;
}

void
MacCoreaudio_addAECStream(MacCoreaudio_Stream *stream)
{
    if (0 == pthread_mutex_lock(&MacCoreaudio_aecStreamMutex))
    {
        if (MacCoreaudio_aecStreamCount < MacCoreaudio_aecStreamCapacity)
        {
            MacCoreaudio_aecStreams[MacCoreaudio_aecStreamCount] = stream;
            ++MacCoreaudio_aecStreamCount;
        }
        else
        {
            unsigned int newAECStreamCapacity
                = MacCoreaudio_aecStreamCapacity + 1;
            MacCoreaudio_Stream **newAECStreams
                = (MacCoreaudio_Stream **)
                    realloc(
                            MacCoreaudio_aecStreams,
                            newAECStreamCapacity
                                * sizeof(MacCoreaudio_Stream *));

            if (newAECStreams)
            {
                MacCoreaudio_aecStreamCapacity = newAECStreamCapacity;
                MacCoreaudio_aecStreams = newAECStreams;

                MacCoreaudio_aecStreams[MacCoreaudio_aecStreamCount] = stream;
                ++MacCoreaudio_aecStreamCount;
            }
        }
        pthread_mutex_unlock(&MacCoreaudio_aecStreamMutex);
    }
}

/**
 * Stops the stream for the given device.
 *
 * @param deviceUID The device identifier.
 * @param stream The stream to stop.
 */
void MacCoreaudio_stopStream(const char *deviceUID, MacCoreaudio_Stream *stream)
{
    AudioDeviceID device;
    OSStatus err = noErr;

    if(pthread_mutex_lock(&stream->mutex) != 0)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_stopStream (coreaudio/device.c): \
                    \n\tpthread_mutex_lock",
                strerror(errno));
    }

    if(stream->aec)
    {
        MacCoreaudio_removeAECStream(stream);

        LibJitsi_WebRTC_AEC_stop(stream->aec);
        LibJitsi_WebRTC_AEC_free(stream->aec);
        stream->aec = NULL;
    }

    if (stream->ioProcId)
    {
        // Gets the corresponding device
        if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
        {
            MacCoreaudio_log(
                    "MacCoreaudio_stopStream (coreaudio/device.c): \
                        \n\tMacCoreaudio_getDevice: %s",
                    deviceUID);
        }
        else
        {
            //  stop IO
            if((err = AudioDeviceStop(device, stream->ioProcId)) != noErr)
            {
                MacCoreaudio_log(
                        "MacCoreaudio_stopStream (coreaudio/device.c): \
                            \n\tAudioDeviceStop: 0x%x for device %s",
                        (int) err,
                        deviceUID);
            }
            //  unregister the IOProc
            if((err = AudioDeviceDestroyIOProcID(device, stream->ioProcId))
                    != noErr)
            {
                MacCoreaudio_log(
                        "MacCoreaudio_stopStream (coreaudio/device.c): \
                            \n\tAudioDeviceDestroyIOProcID: 0x%x for device %s",
                        (int) err,
                        deviceUID);
            }
        }
        stream->ioProcId = 0;
    }

    if((err = MacCoreaudio_freeConverter(stream)) != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_stopStream (coreaudio/device.c): \
                    \n\tMacCoreaudio_freeConverter: 0x%x for device %s",
                (int) err,
                deviceUID);
    }

    if(stream->outBuffer)
    {
        free(stream->outBuffer);
        stream->outBuffer = NULL;
    }
    stream->outBufferLength = 0;

    if(pthread_mutex_unlock(&stream->mutex) != 0)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_stopStream (coreaudio/device.c): \
                    \n\tpthread_mutex_unlock",
                strerror(errno));
    }
    else if(pthread_mutex_destroy(&stream->mutex) != 0)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_stopStream (coreaudio/device.c): \
                    \n\tpthread_mutex_destroy",
                strerror(errno));
    }

    free(stream);
}

void
MacCoreaudio_removeAECStream(MacCoreaudio_Stream *stream)
{
    if (0 == pthread_mutex_lock(&MacCoreaudio_aecStreamMutex))
    {
        if (MacCoreaudio_aecStreams)
        {
            unsigned int i;
            unsigned int j = 0;
            unsigned int newAECStreamCount = MacCoreaudio_aecStreamCount;

            for (i = 0; i < MacCoreaudio_aecStreamCount; ++i)
            {
                if (MacCoreaudio_aecStreams[i] == stream)
                {
                    MacCoreaudio_aecStreams[i] = NULL;
                    --newAECStreamCount;
                }
                else
                {
                    if (j != i)
                        MacCoreaudio_aecStreams[j] = MacCoreaudio_aecStreams[i];
                    ++j;
                }
            }
            MacCoreaudio_aecStreamCount = newAECStreamCount;
        }
        pthread_mutex_unlock(&MacCoreaudio_aecStreamMutex);
    }
}

/**
 * Callback called when the input device has provided some data.
 */
OSStatus
MacCoreaudio_readInputStream(
        /* in */ AudioDeviceID device,
        /* in */ const AudioTimeStamp *now,
        /* in */ const AudioBufferList *inData,
        /* in */ const AudioTimeStamp *inTime,
        /* out */ AudioBufferList *outData,
        /* in */ const AudioTimeStamp *outTime,
        /* in */ void *clientData)
{
    OSStatus err = noErr;
    int error = 0;

    MacCoreaudio_Stream *stream = (MacCoreaudio_Stream *) clientData;
    if((error = pthread_mutex_trylock(&stream->mutex)) == 0)
    {
        void (*callbackFunction)(char*, int, void*, void*)
            = stream->callbackFunction;
        int i;

        if(stream->ioProcId != 0)
        {
            LibJitsi_WebRTC_AEC *aec = stream->aec;

            for(i = 0; i < inData->mNumberBuffers; ++i)
            {
                UInt32 ioPropertyDataSize = sizeof(UInt32);

                if(inData->mBuffers[i].mData != NULL
                        && inData->mBuffers[i].mDataByteSize > 0)
                {
                    if(aec)
                    {
                        LibJitsi_WebRTC_AEC_lock(aec, 0);

                        UInt32 aecTmpLength
                            = inData->mBuffers[i].mDataByteSize;
                        AudioConverterGetProperty(
                                stream->aecConverter,
                                kAudioConverterPropertyCalculateOutputBufferSize,
                                &ioPropertyDataSize,
                                &aecTmpLength);
                        char * aecTmpBuffer;

                        if((aecTmpBuffer = (char*) LibJitsi_WebRTC_AEC_getData(
                                        aec,
                                        0,
                                        aecTmpLength / sizeof(int16_t)))
                                == NULL)
                        {
                            MacCoreaudio_log(
                            "MacCoreaudio_readInputStream (coreaudio/device.c):\
                                    \n\tLibJitsi_WebRTC_AEC_getData");
                            LibJitsi_WebRTC_AEC_unlock(aec, 0);
                            pthread_mutex_unlock(&stream->mutex);
                            return -1;
                        }

                        // Converts from device to AEC
                        if((err = MacCoreaudio_convert(
                                        stream,
                                        0,
                                        stream->aecConverter,
                                        // Input device
                                        inData->mBuffers[i].mData,
                                        inData->mBuffers[i].mDataByteSize,
                                        stream->deviceFormat,
                                        // Output AEC
                                        aecTmpBuffer,
                                        aecTmpLength,
                                        stream->aecFormat))
                                != noErr)
                        {
                            MacCoreaudio_log(
                            "MacCoreaudio_readInputStream (coreaudio/device.c): \
                                    \n\tMacCoreaudio_convert: 0x%x",
                                    (int) err);
                            LibJitsi_WebRTC_AEC_unlock(aec, 0);
                            pthread_mutex_unlock(&stream->mutex);
                            return err;
                        }

                        // Process AEC data.
                        int nb_process;
                        LibJitsi_WebRTC_AEC_lock(aec, 1);
                        if((nb_process = LibJitsi_WebRTC_AEC_process(aec)) < 0)
                        {
                            MacCoreaudio_log(
                            "MacCoreaudio_readInputStream (coreaudio/device.c): \
                                    \n\tLibJitsi_WebRTC_AEC_process: 0x%x",
                                    (int) nb_process);
                        }
                        while(nb_process != 0)
                        {
                            UInt32 inputOutTmpLength
                                = nb_process * sizeof(int16_t);
                            char * inputOutTmpBuffer
                                = (char*)
                                    LibJitsi_WebRTC_AEC_getProcessedData(aec);

                            if(!MacCoreaudio_isSameFormat(
                                        stream->aecFormat,
                                        stream->javaFormat))
                            {
                                UInt32 outTmpLength = inputOutTmpLength;
                                AudioConverterGetProperty(
                                        stream->outConverter,
                                kAudioConverterPropertyCalculateOutputBufferSize,
                                        &ioPropertyDataSize,
                                        &outTmpLength);
                                MacCoreaudio_updateBuffer(
                                        &stream->outBuffer,
                                        &stream->outBufferLength,
                                        outTmpLength);

                                // Converts from AEC to Java
                                if((err = MacCoreaudio_convert(
                                                stream,
                                                1,
                                                stream->outConverter,
                                                // Input device
                                                inputOutTmpBuffer,
                                                inputOutTmpLength,
                                                stream->aecFormat,
                                                // Output Java
                                                stream->outBuffer,
                                                outTmpLength,
                                                stream->javaFormat))
                                        != noErr)
                                {
                                    MacCoreaudio_log(
                            "MacCoreaudio_readInputStream (coreaudio/device.c): \
                                            \n\tMacCoreaudio_convert: 0x%x",
                                            (int) err);
                                    LibJitsi_WebRTC_AEC_unlock(aec, 1);
                                    LibJitsi_WebRTC_AEC_unlock(aec, 0);
                                    pthread_mutex_unlock(&stream->mutex);
                                    return err;
                                }

                                // Puts data to Java.
                                callbackFunction(
                                        stream->outBuffer,
                                        outTmpLength,
                                        stream->callbackObject,
                                        stream->callbackMethod);
                            }
                            else
                            {
                                // Puts data to Java.
                                callbackFunction(
                                        inputOutTmpBuffer,
                                        inputOutTmpLength,
                                        stream->callbackObject,
                                        stream->callbackMethod);
                            }
                            LibJitsi_WebRTC_AEC_completeProcess(aec, 0);
                            LibJitsi_WebRTC_AEC_completeProcess(aec, 1);
                            if((nb_process = LibJitsi_WebRTC_AEC_process(aec)) < 0)
                            {
                                MacCoreaudio_log(
                            "MacCoreaudio_readInputStream (coreaudio/device.c): \
                                        \n\tLibJitsi_WebRTC_AEC_process: 0x%x",
                                        (int) nb_process);
                            }
                        }
                        LibJitsi_WebRTC_AEC_unlock(aec, 1);
                        LibJitsi_WebRTC_AEC_unlock(aec, 0);
                    }
                    else // Stream without AEC
                    {
                        UInt32 outTmpLength = inData->mBuffers[0].mDataByteSize;
                        AudioConverterGetProperty(
                                stream->outConverter,
                                kAudioConverterPropertyCalculateOutputBufferSize,
                                &ioPropertyDataSize,
                                &outTmpLength);
                        MacCoreaudio_updateBuffer(
                                &stream->outBuffer,
                                &stream->outBufferLength,
                                outTmpLength);
                        
                        // Converts from device to Java
                        if((err = MacCoreaudio_convert(
                                        stream,
                                        0,
                                        stream->outConverter,
                                        // Input device
                                        inData->mBuffers[i].mData,
                                        inData->mBuffers[i].mDataByteSize,
                                        stream->deviceFormat,
                                        // Output Java
                                        stream->outBuffer,
                                        outTmpLength,
                                        stream->javaFormat))
                                != noErr)
                        {
                            MacCoreaudio_log(
                                    "MacCoreaudio_readInputStream (coreaudio/device.c): \
                                        \n\tMacCoreaudio_convert: 0x%x",
                                    (int) err);
                            pthread_mutex_unlock(&stream->mutex);
                            return err;
                        }

                        // Puts data to Java.
                        callbackFunction(
                                stream->outBuffer,
                                outTmpLength,
                                stream->callbackObject,
                                stream->callbackMethod);
                    }
                }
            }
        }

        if(pthread_mutex_unlock(&stream->mutex) != 0)
        {
            MacCoreaudio_log(
                    "%s: %s\n",
                    "MacCoreaudio_readInputStream (coreaudio/device.c): \
                        \n\tpthread_mutex_unlock",
                    strerror(errno));
        }
    }
    // If the error equals EBUSY, this means that the mutex is already locked by
    // the stop function.
    else if(error != EBUSY)
    {
        MacCoreaudio_log(
                "%s: %s\n",
                "MacCoreaudio_readInputStream (coreaudio/device.c): \
                    \n\tpthread_mutex_lock",
                strerror(errno));
    }

    return noErr;
}

/**
 * Callback called when the output device is ready to render some data.
 */
OSStatus
MacCoreaudio_writeOutputStream(
        /* in */ AudioDeviceID device,
        /* in */ const AudioTimeStamp *now,
        /* in */ const AudioBufferList *inData,
        /* in */ const AudioTimeStamp *inTime,
        /* out */ AudioBufferList *outData,
        /* in */ const AudioTimeStamp *outTime,
        /* in */ void *clientData)
{
    OSStatus err = noErr;
    int error = 0;

    if(outData->mNumberBuffers == 0
            || outData->mBuffers[0].mData == NULL
            || outData->mBuffers[0].mDataByteSize == 0)
    {
        return err;
    }

    MacCoreaudio_Stream *stream = (MacCoreaudio_Stream *) clientData;

    if((error = pthread_mutex_trylock(&stream->mutex)) == 0)
    {
        if(stream->ioProcId != 0)
        {
            UInt32 ioPropertyDataSize = sizeof(UInt32);
            UInt32 outConverterInBufferSize
                = outData->mBuffers[0].mDataByteSize;

            AudioConverterGetProperty(
                    stream->outConverter,
                    kAudioConverterPropertyCalculateInputBufferSize,
                    &ioPropertyDataSize,
                    &outConverterInBufferSize);
            MacCoreaudio_updateBuffer(
                    &stream->outBuffer,
                    &stream->outBufferLength,
                    outConverterInBufferSize);

            // Get data from java.
            void (*callbackFunction)(char*, int, void*, void*)
                = stream->callbackFunction;

            callbackFunction(
                    stream->outBuffer,
                    outConverterInBufferSize,
                    stream->callbackObject,
                    stream->callbackMethod);
            // Convert from java to device.
            if((err = MacCoreaudio_convert(
                            stream,
                            0,
                            stream->outConverter,
                            // Input Java
                            stream->outBuffer,
                            outConverterInBufferSize,
                            stream->javaFormat,
                            // Output device
                            outData->mBuffers[0].mData,
                            outData->mBuffers[0].mDataByteSize,
                            stream->deviceFormat))
                    != noErr)
            {
                MacCoreaudio_log(
                        "MacCoreaudio_writeOutputStream (coreaudio/device.c): \
                            \n\tMacCoreaudio_convert: 0x%x",
                        (int) err);
                pthread_mutex_unlock(&stream->mutex);
                return err;
            }

            if (stream->isEchoCancel)
            {
                MacCoreaudio_writeOutputStreamToAECStreams(
                        stream,
                        outConverterInBufferSize);
            }
        }

        if(pthread_mutex_unlock(&stream->mutex) != 0)
        {
            MacCoreaudio_log(
                    "%s: %s\n",
                    "MacCoreaudio_writeOutputStream (coreaudio/device.c): \
                        \n\tpthread_mutex_unlock",
                    strerror(errno));
        }
    }
    else
    {
        outData->mBuffers[0].mDataByteSize = 0;

        // If the error equals EBUSY, this means that the mutex is already
        // locked by the stop function.
        if(error != EBUSY)
        {
            MacCoreaudio_log(
                    "%s: %s\n",
                    "MacCoreaudio_writeOutputStream (coreaudio/device.c): \
                        \n\tpthread_mutex_lock",
                    strerror(errno));
        }
    }

    // Copies the same data into the other buffers.
    int i;
    UInt32 length;
    for(i = 1; i < outData->mNumberBuffers; ++i)
    {
        // Copies available data.
        length = outData->mBuffers[i].mDataByteSize;
        if(length > outData->mBuffers[0].mDataByteSize)
            length = outData->mBuffers[0].mDataByteSize;

        memcpy(outData->mBuffers[i].mData, outData->mBuffers[0].mData, length);

        // Resets the resting buffer.
        if(outData->mBuffers[i].mDataByteSize
                > outData->mBuffers[0].mDataByteSize)
        {
            memset(
                    outData->mBuffers[i].mData
                        + outData->mBuffers[0].mDataByteSize,
                    0,
                    outData->mBuffers[i].mDataByteSize
                        - outData->mBuffers[0].mDataByteSize);
        }
    }

    return noErr;
}

void
MacCoreaudio_writeOutputStreamToAECStream(
        MacCoreaudio_Stream *src,
        UInt32 outBufferSize,
        MacCoreaudio_Stream *dst)
{
    LibJitsi_WebRTC_AEC *aec = dst->aec;

    if (LibJitsi_WebRTC_AEC_lock(aec, 1) == 0)
    {
        // Reinit the AEC format to adapt to the capture stream
        AudioStreamBasicDescription aecFormat;

        if (LibJitsi_WebRTC_AEC_getCaptureFormat(aec, &aecFormat))
        {
            OSStatus status = noErr;

            if(!MacCoreaudio_isSameFormat(aecFormat, src->aecFormat))
            {
                if (src->aecConverter)
                {
                    AudioConverterDispose(src->aecConverter);
                    src->aecConverter = NULL;
                }
                src->aecFormat = aecFormat;
                status
                    = AudioConverterNew(
                            &src->javaFormat,
                            &aecFormat,
                            &src->aecConverter);
            }
            if (noErr == status)
            {
                UInt32 ioPropertyDataSize = sizeof(UInt32);
                UInt32 aecConverterOutBufferSize = outBufferSize;
                char *data;

                AudioConverterGetProperty(
                        src->aecConverter,
                        kAudioConverterPropertyCalculateOutputBufferSize,
                        &ioPropertyDataSize,
                        &aecConverterOutBufferSize);

                data
                    = (char*)
                        LibJitsi_WebRTC_AEC_getData(
                                aec,
                                1,
                                aecConverterOutBufferSize / sizeof(int16_t));
                if (data)
                {
                    // Convert from Java to AEC.
                    status
                        = MacCoreaudio_convert(
                                src,
                                0,
                                src->aecConverter,
                                // Input Java
                                src->outBuffer,
                                outBufferSize,
                                src->javaFormat,
                                // Output AEC
                                data,
                                aecConverterOutBufferSize,
                                aecFormat);
                    if(noErr != status)
                    {
                        MacCoreaudio_log(
                                "MacCoreaudio_writeOutputStream (coreaudio/device.c): \
                                    \n\tMacCoreaudio_convert: 0x%x",
                                (int) status);
                    }
                }
            }
        }
        LibJitsi_WebRTC_AEC_unlock(aec, 1);
    }
}

void
MacCoreaudio_writeOutputStreamToAECStreams(
        MacCoreaudio_Stream *stream,
        UInt32 outBufferSize)
{
    if (0 == pthread_mutex_trylock(&MacCoreaudio_aecStreamMutex))
    {
        if (MacCoreaudio_aecStreams && MacCoreaudio_aecStreamCount)
        {
            unsigned int i;

            for (i = 0; i < MacCoreaudio_aecStreamCount; ++i)
            {
                MacCoreaudio_Stream *aecStream
                    = MacCoreaudio_aecStreams[i];

                if (pthread_mutex_trylock(&(aecStream->mutex)) == 0)
                {
                    MacCoreaudio_writeOutputStreamToAECStream(
                            stream,
                            outBufferSize,
                            aecStream);
                    pthread_mutex_unlock(&(aecStream->mutex));
                }
            }
        }
        pthread_mutex_unlock(&MacCoreaudio_aecStreamMutex);
    }
}

/**
 * Returns the stream virtual format for a given stream.
 *
 * @param stream The stream to get the format.
 * @param format The variable to write the format into.
 *
 * @return noErr if everything works fine. Any other value if failed.
 */
OSStatus MacCoreaudio_getStreamVirtualFormat(
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
        MacCoreaudio_log(
                "MacCoreaudio_getStreamVirtualFormat (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: 0x%x",
                ((int) err));
        return err;
    }

    return err;
}

/**
 * Initializes a new audio converter to work between the given device and the
 * format description.
 *
 * @param stream The stream using the provided device.
 *
 * @return noErr if everything works correctly. Any other value otherwise.
 */
OSStatus MacCoreaudio_initConverter(MacCoreaudio_Stream *stream)
{
    OSStatus err = noErr;

    if(stream->isOutputStream)
    {
        err
            = AudioConverterNew(
                    &stream->javaFormat,
                    &stream->deviceFormat,
                    &stream->outConverter);
    }
    else if (stream->aec)
    {
        err
            = AudioConverterNew(
                    &stream->deviceFormat,
                    &stream->aecFormat,
                    &stream->aecConverter);
        if(noErr == err)
        {
            err
                = AudioConverterNew(
                        &stream->aecFormat,
                        &stream->javaFormat,
                        &stream->outConverter);
        }
    }
    else
    {
        err
            = AudioConverterNew(
                    &stream->deviceFormat,
                    &stream->javaFormat,
                    &stream->outConverter);
    }
    if (noErr != err)
    {
        MacCoreaudio_log(
                "MacCoreaudio_initConverter (coreaudio/device.c): \
                    \n\tAudioConverterNew, err: 0x%x",
                (int) err);
    }
    return err;
}

/**
 * Frees audio converters to work between the given device and the format
 * description.
 *
 * @param stream The stream using the provided device.
 *
 * @return noErr if everything works correctly. Any other value otherwise.
 */
OSStatus MacCoreaudio_freeConverter(MacCoreaudio_Stream * stream)
{
    OSStatus err = noErr;

    if(stream->aecConverter)
    {
        if((err = AudioConverterDispose(stream->aecConverter)) != noErr)
        {
            MacCoreaudio_log(
                    "MacCoreaudio_freeConverter (coreaudio/device.c): \
                        \n\tAudioConverterDispose: 0x%x",
                    (int) err);
        }
        stream->aecConverter = NULL;
    }
    if (stream->outConverter)
    {
        if((err = AudioConverterDispose(stream->outConverter)) != noErr)
        {
            MacCoreaudio_log(
                    "MacCoreaudio_freeConverter (coreaudio/device.c): \
                        \n\tAudioConverterDispose: 0x%x",
                    (int) err);
        }
        stream->outConverter = NULL;
    }

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
 * @param inIsNonInterleaved Use true if the samples are non-interleaved.
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
 * @param inIsNonInterleaved Use true if the samples are non-interleaved.
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

/**
 * Returns the device format.
 *
 * @param deviceUID The device identifier.
 * @param isOutput True if the device is an output device.
 * @param deviceFormat The structure to fill in with the device format.
 *
 * @return noErr if everything works fine. Any other value for an error.
 */
OSStatus MacCoreaudio_getDeviceFormat(
        const char * deviceUID,
        unsigned char isOutput,
        AudioStreamBasicDescription * deviceFormat)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;

    // Gets the correspoding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceFormat (coreaudio/device.c): \
                    \n\tgetDevice: %s",
                deviceUID);
        return kAudioObjectUnknown;
    }

    AudioStreamID audioStreamIds[1];
    UInt32 size = sizeof(AudioStreamID *);
    address.mSelector = kAudioDevicePropertyStreams;
    if(isOutput)
        address.mScope = kAudioDevicePropertyScopeOutput;
    else
        address.mScope = kAudioDevicePropertyScopeInput;
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
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceFormat (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData, err: 0x%x for device %s",
                ((int) err),
                deviceUID);
        return err;
    }

    if((err = MacCoreaudio_getStreamVirtualFormat(
                    audioStreamIds[0],
                    deviceFormat))
                != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceFormat (coreaudio/device.c): \
                    \n\tMacCoreaudio_getStreamVirtualFormat, err: 0x%x\
                    for device %s",
                ((int) err),
                deviceUID);
        return err;
    }

    return err;
}

/**
 * Returns the device format using deprecated property.
 *
 * @param deviceUID The device identifier.
 * @param isOutput True if the device is an output device.
 * @param deviceFormat The structure to fill in with the device format.
 *
 * @return noErr if everything works fine. Any other value for an error.
 */
OSStatus MacCoreaudio_getDeviceFormatDeprecated(
        const char * deviceUID,
        unsigned char isOutput,
        AudioStreamBasicDescription * deviceFormat)
{
    AudioDeviceID device;
    OSStatus err = noErr;
    AudioObjectPropertyAddress address;

    // Gets the correspoding device
    if((device = MacCoreaudio_getDevice(deviceUID)) == kAudioObjectUnknown)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceFormatDeprecated (coreaudio/device.c): \
                    \n\tgetDevice: %s",
                    deviceUID);
        return kAudioObjectUnknown;
    }

    UInt32 size = sizeof(AudioStreamBasicDescription);
    // This property ought to some day be deprecated.
    address.mSelector = kAudioDevicePropertyStreamFormat;
    if(isOutput)
        address.mScope = kAudioDevicePropertyScopeOutput;
    else
        address.mScope = kAudioDevicePropertyScopeInput;
    address.mElement = kAudioObjectPropertyElementMaster;

    if((err = AudioObjectGetPropertyData(
                    device,
                    &address,
                    0,
                    NULL,
                    &size,
                    deviceFormat))
            != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_getDeviceFormatDeprecated (coreaudio/device.c): \
                    \n\tAudioObjectGetPropertyData err: 0x%x for device %s",
                ((int) err),
                deviceUID);
    }

    return err;
}

/**
 * Returns the default format.
 *
 * @param deviceFormat The structure to fill in with the default format.
 */
void MacCoreaudio_getDefaultFormat(AudioStreamBasicDescription * deviceFormat)
{
    FillOutASBDForLPCM(
            deviceFormat,
            44100.0,
            2,
            8 * sizeof(AudioUnitSampleType),    // 32
            8 * sizeof(AudioUnitSampleType),    // 32
            true,
            false,
            false);
}

/**
 * Callback called by CoreAudio to feed in the input buffer before conversion.
 */
OSStatus MacCoreaudio_converterComplexInputDataProc(
        AudioConverterRef inAudioConverter,
        UInt32* ioNumberDataPackets,
        AudioBufferList* ioData,
        AudioStreamPacketDescription** ioDataPacketDescription,
        void* inUserData)
{
    if(ioDataPacketDescription)
    {
        MacCoreaudio_log("_converterComplexInputDataProc cannot \
                provide input data; it doesn't know how to \
                provide packet descriptions");
        *ioDataPacketDescription = NULL;
        *ioNumberDataPackets = 0;
        ioData->mNumberBuffers = 0;
        return 501;
    }

    MacCoreaudio_Stream * stream = (MacCoreaudio_Stream*) inUserData;

    UInt32 mBytesPerPacket = 0;
    if(stream->step == 0)
    {
        if(stream->isOutputStream)
            mBytesPerPacket = stream->javaFormat.mBytesPerPacket;
        else
            mBytesPerPacket = stream->deviceFormat.mBytesPerPacket;
    }
    else // if (stream->step == 1) only for AEC.
        mBytesPerPacket = stream->aecFormat.mBytesPerPacket;
    UInt32 nbPackets = stream->audioBuffer.mDataByteSize / mBytesPerPacket;
    UInt32 length = (nbPackets - *ioNumberDataPackets) * mBytesPerPacket;

    if(*ioNumberDataPackets <= nbPackets)
    {
        ioData->mNumberBuffers = 1;
        ioData->mBuffers[0] = stream->audioBuffer;
        ioData->mBuffers[0].mDataByteSize -= length;
    }
    else
    {
        *ioNumberDataPackets = 0;
        ioData->mBuffers[0].mData = NULL;
        ioData->mBuffers[0].mDataByteSize = 0;
    }

    return 0;
}

/**
 * Returns the rate used by the echo canceler for a given provided rate.
 *
 * @param rate The provided rate.
 *
 * @return the echo canceler selected rate.
 */
int MacCoreaudio_getAECCorrespondingRate(int rate)
{
    int aecRate = rate;
    switch(rate)
    {
        case 8000:
            aecRate = 8000;
            break;
        case 11025:
        case 16000:
            aecRate = 16000;
            break;
        default:
        //case 22050:
        //case 32000:
        //case 44100:
        //case 48000:
            aecRate = 32000;
            break;
    }

    return aecRate;
}

/**
 * Initializes the device format for a given device UID.
 *
 * @param deviceUID The device identifier.
 * @param stream The stream using the provided device.
 *
 * @return noErr if everything works correctly. Any other value otherwise.
 */
OSStatus MacCoreaudio_initDeviceFormat(
        const char * deviceUID,
        MacCoreaudio_Stream * stream)
{
    OSStatus err = noErr;
    if((err = MacCoreaudio_getDeviceFormat(
                    deviceUID,
                    stream->isOutputStream,
                    &stream->deviceFormat))
            != noErr)
    {
        MacCoreaudio_log(
                "MacCoreaudio_initDeviceFormat (coreaudio/device.c): \
                    \n\tMacCoreaudio_getDeviceFormat for device: %s",
                deviceUID);

        if((err = MacCoreaudio_getDeviceFormatDeprecated(
                        deviceUID,
                        stream->isOutputStream,
                        &stream->deviceFormat)) != noErr)
        {
            MacCoreaudio_log(
                    "MacCoreaudio_initDeviceFormat (coreaudio/device.c): \
                        \n\tMacCoreaudio_getDeviceFormatDeprecated \
                        for device: %s",
                    deviceUID);

            // Everything has failed to retrieve the device format, then try
            // with the default one.
            MacCoreaudio_getDefaultFormat(&stream->deviceFormat);
        }
    }

    return err;
}

/**
 * Updates the buffer size if necessary: when the new length is greater than
 * the actual one.
 *
 * @param buffer The pointer to the buffer to update.
 * @param bufferLength The actual size of the provided buffer.
 * @param newLength The new length of the buffer.
 */
void MacCoreaudio_updateBuffer(
        char** buffer,
        int* bufferLength,
        int newLength)
{
    if(*bufferLength < newLength)
    {
        if(*buffer != NULL)
            free(*buffer);
        if((*buffer = malloc(newLength * sizeof(char))) == NULL)
        {
            *bufferLength = 0;
            MacCoreaudio_log(
                    "%s: %s\n",
                    "MacCoreaudio_updateBuffer (coreaudio/device.c): \
                    \n\tmalloc",
                    strerror(errno));
            return;
        }
        *bufferLength = newLength;
    }
}

/**
 * Tells if the provided formats are identical.
 *
 * @param format1 The first format.
 * @param format2 The second format.
 *
 * @return 1 if the two formats are identical. 0 otherwise.
 */
int MacCoreaudio_isSameFormat(
        AudioStreamBasicDescription format1,
        AudioStreamBasicDescription format2)
{
    return (format1.mSampleRate == format2.mSampleRate
            && format1.mChannelsPerFrame == format2.mChannelsPerFrame
            && format1.mBitsPerChannel == format2.mBitsPerChannel
            && format1.mBytesPerPacket == format2.mBytesPerPacket
            && format1.mBytesPerFrame == format2.mBytesPerFrame);
}

/**
 * Converts data from input to output format.
 *
 * @param step The conversion number of the capture/render processes.
 * @param converter The converter for this step of process.
 * @param inBuffer The input buffer.
 * @param inBufferLength The length of the input buffer.
 * @param inFormat The input format.
 * @param outBuffer The output buffer.
 * @param outBufferLength The length of the output buffer.
 * @param outFormat The output format.
 *
 * @return noErr if everything works correctly. Any other value otherwise.
 */
OSStatus MacCoreaudio_convert(
        MacCoreaudio_Stream *stream,
        unsigned short step,
        AudioConverterRef converter,
        char *inBuffer,
        int inBufferLength,
        AudioStreamBasicDescription inFormat,
        char *outBuffer,
        int outBufferLength,
        AudioStreamBasicDescription outFormat)
{
    OSStatus err = noErr;

    if(inBufferLength > 0 && outBufferLength > 0)
    {
        stream->step = step;

        stream->audioBuffer.mNumberChannels = inFormat.mChannelsPerFrame;
        stream->audioBuffer.mDataByteSize = inBufferLength;
        stream->audioBuffer.mData = inBuffer;

        UInt32 outputDataPacketSize = outBufferLength / outFormat.mBytesPerPacket;

        AudioBufferList outBufferList;
        outBufferList.mNumberBuffers = 1;
        outBufferList.mBuffers[0].mNumberChannels = outFormat.mChannelsPerFrame;
        outBufferList.mBuffers[0].mDataByteSize = outBufferLength;
        outBufferList.mBuffers[0].mData = outBuffer;

        if((err = AudioConverterFillComplexBuffer(
                        converter,
                        MacCoreaudio_converterComplexInputDataProc,
                        stream, // corresponding to inUserData
                        &outputDataPacketSize,
                        &outBufferList,
                        NULL))
                != noErr)
        {
            MacCoreaudio_log(
                    "MacCoreaudio_convert (coreaudio/device.c): \
                        \n\tAudioConverterFillComplexBuffer: 0x%x",
                    (int) err);
            return err;
        }
    }

    return err;
}
