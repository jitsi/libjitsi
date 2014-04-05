/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#ifndef device_h
#define device_h

#include <mmdeviceapi.h>

/**
 * Functions to list, access and modifies audio devices via coreaudio.
 * Look at correspondig ".c" file for documentation.
 *
 * @author Vincent Lucas
 */
int initDevices(void);

void freeDevices(void);

IMMDevice * getDevice(
        const char * deviceUID);

void freeDevice(
        IMMDevice * device);

char* getDeviceName(
        const char * deviceUID);

char* getDeviceModelIdentifier(
        const char * deviceUID);

int setInputDeviceVolume(
        const char * deviceUID,
        float volume);

int setOutputDeviceVolume(
        const char * deviceUID,
        float volume);

float getInputDeviceVolume(
        const char * deviceUID);

float getOutputDeviceVolume(
        const char * deviceUID);
#endif
