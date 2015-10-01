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
#ifndef LibJitsi_WebRTC_AEC_h
#define LibJitsi_WebRTC_AEC_h

#include <CoreAudio/CoreAudio.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" 
{
#endif

/**
 * Functions to use Acoustic Echo Cancellation (AEC) with WebRTC.
 *
 * @author Vincent Lucas
 */

typedef struct _LibJitsi_WebRTC_AEC LibJitsi_WebRTC_AEC;

LibJitsi_WebRTC_AEC *LibJitsi_WebRTC_AEC_init();

void LibJitsi_WebRTC_AEC_free(LibJitsi_WebRTC_AEC *aec);

void LibJitsi_WebRTC_AEC_start(LibJitsi_WebRTC_AEC *aec);

void LibJitsi_WebRTC_AEC_stop(LibJitsi_WebRTC_AEC *aec);

int
LibJitsi_WebRTC_AEC_initAudioProcessing(
        LibJitsi_WebRTC_AEC *aec,
        int sample_rate,
        int nb_channels,
        AudioStreamBasicDescription format);

int LibJitsi_WebRTC_AEC_process(LibJitsi_WebRTC_AEC *aec);

void
LibJitsi_WebRTC_AEC_completeProcess(
        LibJitsi_WebRTC_AEC *aec,
        int isRenderStream);

int16_t *
LibJitsi_WebRTC_AEC_getData(
        LibJitsi_WebRTC_AEC *aec,
        int isRenderStream,
        int length);

int16_t *
LibJitsi_WebRTC_AEC_getProcessedData(LibJitsi_WebRTC_AEC *aec);

int
LibJitsi_WebRTC_AEC_getCaptureFormat(
        LibJitsi_WebRTC_AEC *aec,
        AudioStreamBasicDescription *format);

int LibJitsi_WebRTC_AEC_lock(LibJitsi_WebRTC_AEC *aec, int isRenderStream);

int LibJitsi_WebRTC_AEC_unlock(LibJitsi_WebRTC_AEC *aec, int isRenderStream);

#ifdef __cplusplus
}
#endif

#endif
