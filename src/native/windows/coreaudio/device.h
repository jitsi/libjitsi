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
