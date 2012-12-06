/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.codec;

import org.jitsi.util.*;

/**
 * Allows star import of <tt>org.jitsi.service.neomedia.codec</tt>
 * in order to get the constants defined in
 * <tt>org.jitsi.service.neomedia.codec.Constants</tt> without star
 * import of <tt>org.jitsi.impl.neomedia.codec</tt>.
 *
 * @author Lubomir Marinov
 * @author Boris Grozev
 */
public class Constants
{
    /**
     * The ALAW/RTP constant.
     */
    public static final String ALAW_RTP = "ALAW/rtp";

    /**
     * The G722 constant.
     */
    public static final String G722 = "g722";

    /**
     * The G722/RTP constant.
     */
    public static final String G722_RTP = "g722/rtp";

    /**
     * The iLBC constant.
     */
    public static final String ILBC = "ilbc";

    /**
     * The iLBC/RTP constant.
     */
    public static final String ILBC_RTP = "ilbc/rtp";

    /**
     * The SILK constant.
     */
    public static final String SILK = "SILK";

    /**
     * The SILK/RTP constant.
     */
    public static final String SILK_RTP = "SILK/rtp";

    /**
     * The SPEEX constant.
     */
    public static final String SPEEX = "speex";

    /**
     * The SPEEX/RTP constant.
     */
    public static final String SPEEX_RTP = "speex/rtp";

    /**
     * The OPUS/RTP constant.
     */
    public static final String OPUS_RTP = "opus/rtp";

    /**
     * The H264 constant.
     */
    public static final String H264 = "h264";

    /**
     * The H264/RTP constant.
     */
    public static final String H264_RTP = "h264/rtp";

    /**
     * The H263+ constant.
     */
    public static final String H263P = "H263-1998";

    /**
     * The H263+/RTP constant.
     */
    public static final String H263P_RTP = "h263-1998/rtp";

    /**
     * Pseudo format representing DTMF tones sent over RTP.
     */
    public static final String TELEPHONE_EVENT = "telephone-event";

    /**
     * The list of well-known sample rates of audio data used throughout
     * neomedia.
     */
    public static final double[] AUDIO_SAMPLE_RATES
        = { 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000 };

    /**
     * mode    : Frame size for the encoding/decoding
     * 20 - 20 ms
     * 30 - 30 ms
     */
    public static int ILBC_MODE = 30;

    /**
     * Default video width.
     */
    public static final int VIDEO_WIDTH;

    /**
     * Default video height.
     */
    public static final int VIDEO_HEIGHT;

    /**
     * The name of the property used to control whether FEC is enabled for SILK
     */
    public static final String PROP_SILK_FEC
            = "net.java.sip.communicator.impl.neomedia.codec.audio.silk." +
            "encoder.USE_FEC";

    /**
     * The name of the property used to control the the
     * 'always assume packet loss' setting for SILK
     */
    public static final String PROP_SILK_ASSUME_PL
            = "net.java.sip.communicator.impl.neomedia.codec.audio.silk." +
            "encoder.AWLAYS_ASSUME_PACKET_LOSS";

    /**
     * The name of the property used to control the SILK
     * 'speech activity threshold'
     */
    public static final String PROP_SILK_FEC_SAT
            = "net.java.sip.communicator.impl.neomedia.codec.audio.silk." +
            "encoder.SPEECH_ACTIVITY_THRESHOLD";

    /**
     * The name of the property used to control whether FEC support is
     * advertised for SILK
     */
    public static final String PROP_SILK_ADVERSISE_FEC
            = "net.java.sip.communicator.impl.neomedia.codec.audio.silk." +
            "ADVERTISE_FEC";
    /**
     * The name of the property used to control the Opus encoder
     * "audio bandwidth" setting
     */
    public static final String PROP_OPUS_BANDWIDTH
            = "net.java.sip.communicator.impl.neomedia.codec.audio.opus." +
            "encoder.AUDIO_BANDWIDTH";

    /**
     * The name of the property used to control the Opus encoder bitrate setting
     */
    public static final String PROP_OPUS_BITRATE
            = "net.java.sip.communicator.impl.neomedia.codec.audio.opus." +
            "encoder.BITRATE";

    /**
     * The name of the property used to control the Opus encoder "DTX" setting
     */
    public static final String PROP_OPUS_DTX
            = "net.java.sip.communicator.impl.neomedia.codec.audio.opus." +
            "encoder.DTX";

    /**
     * The name of the property used to control whether FEC is enabled for the
     * Opus encoder
     */
    public static final String PROP_OPUS_FEC
            = "net.java.sip.communicator.impl.neomedia.codec.audio.opus." +
            "encoder.FEC";

    /**
     * The name of the property used to control the Opus encoder
     * "minimum expected packet loss" setting
     */
    public static final String PROP_OPUS_MIN_EXPECTED_PACKET_LOSS
            = "net.java.sip.communicator.impl.neomedia.codec.audio.opus." +
            "encoder.MIN_EXPECTED_PACKET_LOSS";

    /**
     * The name of the property used to control the Opus encoder 'complexity'
     * setting
     */
    public static final String PROP_OPUS_COMPLEXITY
            = "net.java.sip.communicator.impl.neomedia.codec.audio.opus." +
            "encoder.COMPLEXITY";


    static
    {

        /*
         * On Mac OS X, the Apple iSight camera reports two sizes 640x480 and
         * 320x240 if we use the default size 352x288 we must use source format
         * 640x480 in this situation we suffer from high cpu usage as every
         * frame is scaled, so we use the non-standard format 320x240.
         */
        if (OSUtils.IS_MAC)
        {
            VIDEO_WIDTH = 320;
            VIDEO_HEIGHT = 240;
        }
        else
        {
            VIDEO_WIDTH = 352;
            VIDEO_HEIGHT = 288;
        }
    }
}
