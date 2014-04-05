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

#include "libjitsi_webrtc_aec.h"


/**
 * Functions to list, access and modifies audio devices via coreaudio.
 * Look at corresponding ".c" file for documentation.
 *
 * @author Vincent Lucas
 */

typedef struct _MacCoreaudio_Stream
{
    AudioDeviceIOProcID ioProcId;
    void *callbackFunction;
    void *callbackObject;
    void *callbackMethod;
    unsigned char isOutputStream;
    unsigned short step;
    AudioConverterRef aecConverter;
    AudioConverterRef outConverter;
    AudioStreamBasicDescription deviceFormat;
    AudioStreamBasicDescription aecFormat;
    AudioStreamBasicDescription javaFormat;
    AudioBuffer audioBuffer;
    pthread_mutex_t mutex;

    char *outBuffer;
    int outBufferLength;

    /* Input streams only. */
    LibJitsi_WebRTC_AEC *aec;
    unsigned char isEchoCancel;
} MacCoreaudio_Stream;

int MacCoreaudio_isInputDevice(const char *deviceUID);

int MacCoreaudio_isOutputDevice(const char *deviceUID);

char* MacCoreaudio_getDeviceName(const char *deviceUID);

char* MacCoreaudio_getDeviceModelIdentifier(const char * deviceUID);

OSStatus MacCoreaudio_setInputDeviceVolume(
        const char *deviceUID,
        Float32 volume);

OSStatus MacCoreaudio_setOutputDeviceVolume(
        const char *deviceUID,
        Float32 volume);

Float32 MacCoreaudio_getInputDeviceVolume(const char *deviceUID);

Float32 MacCoreaudio_getOutputDeviceVolume(const char *deviceUID);

int MacCoreaudio_getDeviceUIDList(char ***deviceUIDList);

const char* MacCoreaudio_getTransportType(const char *deviceUID);

Float64 MacCoreaudio_getNominalSampleRate(
        const char *deviceUID,
        unsigned char isOutputStream,
        unsigned char isEchoCancel);

OSStatus MacCoreaudio_getAvailableNominalSampleRates(
        const char *deviceUID,
        Float64 *minRate,
        Float64 *maxRate,
        unsigned char isOutputStream,
        unsigned char isEchoCancel);

char* MacCoreaudio_getDefaultInputDeviceUID();

char* MacCoreaudio_getDefaultOutputDeviceUID();

int MacCoreaudio_countInputChannels(const char *deviceUID);

int MacCoreaudio_countOutputChannels(const char *deviceUID);

MacCoreaudio_Stream * MacCoreaudio_startInputStream(
        const char *deviceUID,
        void *callbackFunction,
        void *callbackObject,
        void *callbackMethod,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved,
        unsigned char isEchoCancel);

MacCoreaudio_Stream * MacCoreaudio_startOutputStream(
        const char *deviceUID,
        void *callbackFunction,
        void *callbackObject,
        void *callbackMethod,
        float sampleRate,
        UInt32 nbChannels,
        UInt32 bitsPerChannel,
        unsigned char isFloat,
        unsigned char isBigEndian,
        unsigned char isNonInterleaved,
        unsigned char isEchoCancel);

void MacCoreaudio_stopStream(
        const char *deviceUID,
        MacCoreaudio_Stream *stream);

void MacCoreaudio_initializeHotplug(void *callbackFunction);

void MacCoreaudio_uninitializeHotplug();

#endif
