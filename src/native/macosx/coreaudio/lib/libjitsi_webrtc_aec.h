/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#ifndef libjitsi_webrtc_aec_h
#define libjitsi_webrtc_aec_h

#include <CoreAudio/CoreAudio.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" 
{
#endif

/**
 * Functions to use acoustic echo cancelling with webrtc.
 *
 * @author Vincent Lucas
 */

void
libjitsi_webrtc_aec_start(
        unsigned char isOutputStream);

void
libjitsi_webrtc_aec_stop(
        unsigned char isOutputStream);

int
libjitsi_webrtc_aec_initAudioProcessing(
        int sample_rate,
        int nb_channels,
        AudioStreamBasicDescription format);

int
libjitsi_webrtc_aec_process(
        int isRenderStream,
        int sample_rate,
        int nb_channels);

void
libjitsi_webrtc_aec_completeProcess(
        int isRenderStream);

int16_t *
libjitsi_webrtc_aec_getData(
        int isRenderStream,
        int length);

int16_t *
libjitsi_webrtc_aec_getProcessedData(
        int isRenderStream);

int
libjitsi_webrtc_aec_getCaptureFormat(
        AudioStreamBasicDescription * format);

int
libjitsi_webrtc_aec_lock(
        int isRenderStream);

int
libjitsi_webrtc_aec_unlock(
        int isRenderStream);

#ifdef __cplusplus
}
#endif

#endif
