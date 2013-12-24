/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#ifndef device_h
#define device_h

#include <AudioToolbox/AudioConverter.h>
#include <CoreAudio/CoreAudio.h>
#include <CoreFoundation/CFString.h>
#include <stdio.h>

/**
 * Functions to list, access and modifies audio devices via coreaudio.
 * Look at correspondig ".c" file for documentation.
 *
 * @author Vincent Lucas
 */
typedef struct
{
    AudioDeviceIOProcID ioProcId;
    void* callbackFunction;
    void* callbackObject;
    void* callbackMethod;
    unsigned char isOutputStream;
    unsigned char isAECActivated;
    unsigned short step;
    AudioConverterRef aecConverter;
    AudioConverterRef outConverter;
    AudioStreamBasicDescription deviceFormat;
    AudioStreamBasicDescription aecFormat;
    AudioStreamBasicDescription javaFormat;
    AudioBuffer audioBuffer;
    pthread_mutex_t mutex;
    char * outBuffer;
    int outBufferLength;
} maccoreaudio_stream;

int maccoreaudio_initDevices(
        void);

void maccoreaudio_freeDevices(
        void);

int maccoreaudio_isInputDevice(
        const char * deviceUID);

int maccoreaudio_isOutputDevice(
        const char * deviceUID);

char* maccoreaudio_getDeviceName(
        const char * deviceUID);

char* maccoreaudio_getDeviceModelIdentifier(
        const char * deviceUID);

OSStatus maccoreaudio_setInputDeviceVolume(
        const char * deviceUID,
        Float32 volume);

OSStatus maccoreaudio_setOutputDeviceVolume(
        const char * deviceUID,
        Float32 volume);

Float32 maccoreaudio_getInputDeviceVolume(
        const char * deviceUID);

Float32 maccoreaudio_getOutputDeviceVolume(
        const char * deviceUID);

int maccoreaudio_getDeviceUIDList(
        char *** deviceUIDList);

const char* maccoreaudio_getTransportType(
        const char * deviceUID);

Float64 maccoreaudio_getNominalSampleRate(
        const char * deviceUID,
        unsigned char isOutputStream,
        unsigned char isEchoCancel);

OSStatus maccoreaudio_getAvailableNominalSampleRates(
        const char * deviceUID,
        Float64 * minRate,
        Float64 * maxRate,
        unsigned char isOutputStream,
        unsigned char isEchoCancel);

char* maccoreaudio_getDefaultInputDeviceUID(
        void);

char* maccoreaudio_getDefaultOutputDeviceUID(
        void);

int maccoreaudio_countInputChannels(
        const char * deviceUID);

int maccoreaudio_countOutputChannels(
        const char * deviceUID);

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
        unsigned char isNonInterleaved,
        unsigned char isEchoCancel);

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
        unsigned char isNonInterleaved,
        unsigned char isEchoCancel);

void maccoreaudio_stopStream(
        const char * deviceUID,
        maccoreaudio_stream * stream);

void maccoreaudio_initializeHotplug(
        void* callbackFunction);

void maccoreaudio_uninitializeHotplug();

#endif
