/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#ifndef device_h
#define device_h

#include <CoreAudio/CoreAudio.h>
#include <CoreFoundation/CFString.h>
#include <stdio.h>

/**
 * Functions to list, access and modifies audio devices via coreaudio.
 * Look at correspondig ".c" file for documentation.
 *
 * @author Vincent Lucas
 */
AudioDeviceID getDevice(
        const char * deviceUID);

char* getDeviceName(
        const char * deviceUID);

OSStatus setInputDeviceVolume(
        const char * deviceUID,
        Float32 volume);

OSStatus setOutputDeviceVolume(
        const char * deviceUID,
        Float32 volume);

Float32 getInputDeviceVolume(
        const char * deviceUID);

Float32 getOutputDeviceVolume(
        const char * deviceUID);
#endif
