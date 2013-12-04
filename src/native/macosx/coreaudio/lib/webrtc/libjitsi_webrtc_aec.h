/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#ifndef libjitsi_webrtc_aec_h
#define libjitsi_webrtc_aec_h

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
int libjitsi_webrtc_aec_process(
        int isCaptureStream,
        int16_t * data,
        int data_length,
        int sample_rate,
        int nb_channels);

int libjitsi_webrtc_aec_initAudioProcessing(
        int sample_rate,
        int nb_capture_channels,
        int nb_render_channels);

#ifdef __cplusplus
}
#endif

#endif
