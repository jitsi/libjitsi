/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
