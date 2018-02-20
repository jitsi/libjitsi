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
package org.jitsi.impl.neomedia.codec.video.h264;

import java.awt.*;
import java.util.*;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.util.*;

/**
 * Implements an FMJ H.264 encoder using FFmpeg (and x264).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class JNIEncoder
    extends AbstractCodec
    implements RTCPFeedbackMessageListener
{
    /**
     * The available presets we can use with the encoder.
     */
    public static final String[] AVAILABLE_PRESETS
        = {
            "ultrafast",
            "superfast",
            "veryfast",
            "faster",
            "fast",
            "medium",
            "slow",
            "slower",
            "veryslow"
        };

    /**
     * The name of the baseline H.264 (encoding) profile.
     */
    public static final String BASELINE_PROFILE = "baseline";

    /**
     * The default value of the {@link #DEFAULT_INTRA_REFRESH_PNAME}
     * <tt>ConfigurationService</tt> property.
     */
    public static final boolean DEFAULT_DEFAULT_INTRA_REFRESH = true;

    /**
     * The name of the main H.264 (encoding) profile.
     */
    public static final String MAIN_PROFILE = "main";

    /**
     * The default value of the {@link #DEFAULT_PROFILE_PNAME}
     * <tt>ConfigurationService</tt> property.
     */
    public static final String DEFAULT_DEFAULT_PROFILE = BASELINE_PROFILE;

    /**
     * The frame rate to be assumed by <tt>JNIEncoder</tt> instances in the
     * absence of any other frame rate indication.
     */
    public static final int DEFAULT_FRAME_RATE = 15;

    /**
     * The name of the boolean <tt>ConfigurationService</tt> property which
     * specifies whether Periodic Intra Refresh is to be used by default. The
     * default value is <tt>true</tt>. The value may be overridden by
     * {@link #setAdditionalCodecSettings(Map)}.
     */
    public static final String DEFAULT_INTRA_REFRESH_PNAME
        = "org.jitsi.impl.neomedia.codec.video.h264.defaultIntraRefresh";

    /**
     * The default maximum GOP (group of pictures) size i.e. the maximum
     * interval between keyframes. The x264 library defaults to 250.
     */
    public static final int DEFAULT_KEYINT = 150;

    /**
     * The default value of the {@link #PRESET_PNAME}
     * <tt>ConfigurationService</tt> property.
     */
    public static final String DEFAULT_PRESET = AVAILABLE_PRESETS[0];

    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the H.264 (encoding) profile to be used in the absence of negotiation.
     * Though it seems that RFC 3984 "RTP Payload Format for H.264 Video"
     * specifies the baseline profile as the default, we have till the time of
     * this writing defaulted to the main profile and we do not currently want
     * to change from the main to the base profile unless we really have to.
     */
    public static final String DEFAULT_PROFILE_PNAME
        = "net.java.sip.communicator.impl.neomedia.codec.video.h264."
            + "defaultProfile";

    /**
     * The name of the high H.264 (encoding) profile.
     */
    public static final String HIGH_PROFILE = "high";

    /**
     * The name of the integer <tt>ConfigurationService</tt> property which
     * specifies the maximum GOP (group of pictures) size i.e. the maximum
     * interval between keyframes. FFmpeg calls it <tt>gop_size</tt>, x264
     * refers to it as <tt>keyint</tt> or <tt>i_keyint_max</tt>.
     */
    public static final String KEYINT_PNAME
        = "org.jitsi.impl.neomedia.codec.video.h264.keyint";

    /**
     * The logger used by the <tt>JNIEncoder</tt> class and its instances for
     * logging output.
     */
    private static final Logger logger = Logger.getLogger(JNIEncoder.class);

    /**
     * Minimum interval between two PLI request processing (in milliseconds).
     */
    private static final long PLI_INTERVAL = 3000;

    /**
     * Name of the code.
     */
    private static final String PLUGIN_NAME = "H.264 Encoder";

    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the x264 preset to be used by <tt>JNIEncoder</tt>. A preset is a
     * collection of x264 options that will provide a certain encoding speed to
     * compression ratio. A slower preset will provide better compression i.e.
     * quality per size.
     */
    public static final String PRESET_PNAME
        = "org.jitsi.impl.neomedia.codec.video.h264.preset";

    /**
     * The list of <tt>Formats</tt> supported by <tt>JNIEncoder</tt> instances
     * as output.
     */
    static final Format[] SUPPORTED_OUTPUT_FORMATS
        = {
            new ParameterizedVideoFormat(
                    Constants.H264,
                    VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0"),
            new ParameterizedVideoFormat(
                    Constants.H264,
                    VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1")
        };

    public static final int X264_KEYINT_MAX_INFINITE = 1 << 30;

    public static final int X264_KEYINT_MIN_AUTO = 0;

    /**
     * Checks the configuration and returns the profile to use.
     * @param profile the profile setting.
     * @return the profile FFmpeg to use.
     */
    private static int getProfileForConfig(String profile)
    {
        if(BASELINE_PROFILE.equalsIgnoreCase(profile))
            return FFmpeg.FF_PROFILE_H264_BASELINE;
        else if(HIGH_PROFILE.equalsIgnoreCase(profile))
            return FFmpeg.FF_PROFILE_H264_HIGH;
        else
            return FFmpeg.FF_PROFILE_H264_MAIN;
    }

    /**
     * The additional settings of this <tt>Codec</tt>.
     */
    private Map<String, String> additionalCodecSettings;

    /**
     * The codec we will use.
     */
    private long avctx;

    /**
     * The encoded data is stored in avpicture.
     */
    private long avFrame;

    /**
     * The indicator which determines whether the generation of a keyframe is to
     * be forced during a subsequent execution of
     * {@link #process(Buffer, Buffer)}. The first frame to undergo encoding is
     * naturally a keyframe and, for the sake of clarity, the initial value is
     * <tt>true</tt>.
     */
    private boolean forceKeyFrame = true;

    /**
     * The <tt>KeyFrameControl</tt> used by this <tt>JNIEncoder</tt> to
     * control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    private KeyFrameControl.KeyFrameRequestee keyFrameRequestee;

    /**
     * The maximum GOP (group of pictures) size i.e. the maximum interval
     * between keyframes (with which {@link #open()} has been invoked without an
     * intervening {@link #close()}). FFmpeg calls it <tt>gop_size</tt>, x264
     * refers to it as <tt>keyint</tt> or <tt>i_keyint_max</tt>.
     */
    private int keyint;

    /**
     * The number of frames processed since the last keyframe.
     */
    private int lastKeyFrame;

    /**
     * The time in milliseconds of the last request for a key frame from the
     * remote peer to this local peer.
     */
    private long lastKeyFrameRequestTime = System.currentTimeMillis();

    /**
     * The packetization mode to be used for the H.264 RTP payload output by
     * this <tt>JNIEncoder</tt> and the associated packetizer. RFC 3984 "RTP
     * Payload Format for H.264 Video" says that "[w]hen the value of
     * packetization-mode is equal to 0 or packetization-mode is not present,
     * the single NAL mode, as defined in section 6.2 of RFC 3984, MUST be
     * used."
     */
    private String packetizationMode;

    /**
     * The raw frame buffer.
     */
    private long rawFrameBuffer;

    /**
     * Length of the raw frame buffer. Once the dimensions are known, this is
     * set to 3/2 * (height*width), which is the size needed for a YUV420 frame.
     */
    private int rawFrameLen;

    /**
     * The indicator which determines whether two consecutive frames at the
     * beginning of the video transmission have been encoded as keyframes. The
     * first frame is a keyframe but it is at the very beginning of the video
     * transmission and, consequently, there is a higher risk that pieces of it
     * will be lost on their way through the network. To mitigate possible
     * issues in the case of network loss, the second frame is also a keyframe.
     */
    private boolean secondKeyFrame = true;

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public JNIEncoder()
    {
        inputFormats
            = new Format[]
            {
                new YUVFormat(
                        /* size */ null,
                        /* maxDataLength */ Format.NOT_SPECIFIED,
                        Format.byteArray,
                        /* frameRate */ Format.NOT_SPECIFIED,
                        YUVFormat.YUV_420,
                        /* strideY */ Format.NOT_SPECIFIED,
                        /* strideUV */ Format.NOT_SPECIFIED,
                        /* offsetY */ Format.NOT_SPECIFIED,
                        /* offsetU */ Format.NOT_SPECIFIED,
                        /* offsetV */ Format.NOT_SPECIFIED)
            };

        inputFormat = null;
        outputFormat = null;
    }

    /**
     * Closes this <tt>Codec</tt>.
     */
    @Override
    public synchronized void close()
    {
        if (opened)
        {
            opened = false;
            super.close();

            if (avctx != 0)
            {
                FFmpeg.avcodec_close(avctx);
                FFmpeg.av_free(avctx);
                avctx = 0;
            }

            if (avFrame != 0)
            {
                FFmpeg.avcodec_free_frame(avFrame);
                avFrame = 0;
            }
            if (rawFrameBuffer != 0)
            {
                FFmpeg.av_free(rawFrameBuffer);
                rawFrameBuffer = 0;
            }

            if (keyFrameRequestee != null)
            {
                if (keyFrameControl != null)
                    keyFrameControl.removeKeyFrameRequestee(keyFrameRequestee);
                keyFrameRequestee = null;
            }
        }
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array for formats matching input format
     */
    private Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        String[] packetizationModes
            = (this.packetizationMode == null)
                ? new String[] { "0", "1" }
                : new String[] { this.packetizationMode };
        Format[] matchingOutputFormats = new Format[packetizationModes.length];
        Dimension size = inputVideoFormat.getSize();
        float frameRate = inputVideoFormat.getFrameRate();

        for (int index = packetizationModes.length - 1; index >= 0; index--)
        {
            matchingOutputFormats[index]
                = new ParameterizedVideoFormat(
                        Constants.H264,
                        size,
                        /* maxDataLength */ Format.NOT_SPECIFIED,
                        Format.byteArray,
                        frameRate,
                        ParameterizedVideoFormat.toMap(
                                VideoMediaFormatImpl
                                    .H264_PACKETIZATION_MODE_FMTP,
                                packetizationModes[index]));
        }
        return matchingOutputFormats;
    }

    /**
     * Gets the name of this <tt>Codec</tt>.
     *
     * @return codec name
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Returns the list of formats supported at the output.
     *
     * @param in input <tt>Format</tt> to determine corresponding output
     * <tt>Format</tt>s
     * @return array of formats supported at output
     */
    @Override
    public Format[] getSupportedOutputFormats(Format in)
    {
        Format[] supportedOutputFormats;

        // null input format
        if (in == null)
            supportedOutputFormats = SUPPORTED_OUTPUT_FORMATS;
        // mismatch input format
        else if (!(in instanceof VideoFormat)
                || (null == AbstractCodec2.matches(in, inputFormats)))
            supportedOutputFormats = new Format[0];
        else
            supportedOutputFormats = getMatchingOutputFormats(in);
        return supportedOutputFormats;
    }

    /**
     * Determines whether the encoding of {@link #avFrame} is to produce a
     * keyframe. The returned value will be set on <tt>avFrame</tt> via a call
     * to {@link FFmpeg#avframe_set_key_frame(long, boolean)}.
     *
     * @return <tt>true</tt> if the encoding of <tt>avFrame</tt> is to produce a
     * keyframe; otherwise, <tt>false</tt>
     */
    private boolean isKeyFrame()
    {
        boolean keyFrame;

        if (forceKeyFrame)
        {
            keyFrame = true;

            /*
             * The first frame is a keyframe but it is at the very beginning of
             * the video transmission and, consequently, there is a higher risk
             * that pieces of it will be lost on their way through the network.
             * To mitigate possible issues in the case of network loss, the
             * second frame is also a keyframe.
             */
            if (secondKeyFrame)
            {
                secondKeyFrame = false;
                forceKeyFrame = true;
            }
            else
                forceKeyFrame = false;
        }
        else
        {
            /*
             * In order to be sure that keyint will be respected, we will
             * implement it ourselves (regardless of the fact that we have told
             * FFmpeg and x264 about it). Otherwise, we may end up not
             * generating keyframes at all (apart from the two generated after
             * open).
             */
            keyFrame = (lastKeyFrame == keyint);
        }

        return keyFrame;
    }

    /**
     * Notifies this <tt>JNIEncoder</tt> that the remote peer has requested a
     * key frame from this local peer.
     *
     * @return <tt>true</tt> if this <tt>JNIEncoder</tt> has honored the request
     * for a key frame; otherwise, <tt>false</tt>
     */
    private boolean keyFrameRequest()
    {
        long now = System.currentTimeMillis();

        if (now > (lastKeyFrameRequestTime + PLI_INTERVAL))
        {
            lastKeyFrameRequestTime = now;
            forceKeyFrame = true;
        }
        return true;
    }

    /**
     * Opens this <tt>Codec</tt>.
     */
    @Override
    public synchronized void open()
        throws ResourceUnavailableException
    {
        if (opened)
            return;

        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;
        VideoFormat outputVideoFormat = (VideoFormat) outputFormat;

        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the
         * input.
         */
        Dimension size = null;

        if (inputVideoFormat != null)
            size = inputVideoFormat.getSize();
        if ((size == null) && (outputVideoFormat != null))
            size = outputVideoFormat.getSize();
        if (size == null)
        {
            throw new ResourceUnavailableException(
                    "The input video frame width and height are not set.");
        }

        int width = size.width, height = size.height;

        /*
         * XXX We do not currently negotiate the profile so, regardless of the
         * many AVCodecContext properties we have set above, force the default
         * profile configuration.
         */
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean intraRefresh = DEFAULT_DEFAULT_INTRA_REFRESH;
        int keyint = DEFAULT_KEYINT;
        String preset = DEFAULT_PRESET;
        String profile = DEFAULT_DEFAULT_PROFILE;

        if (cfg != null)
        {
            intraRefresh
                = cfg.getBoolean(DEFAULT_INTRA_REFRESH_PNAME, intraRefresh);
            keyint = cfg.getInt(KEYINT_PNAME, keyint);
            preset = cfg.getString(PRESET_PNAME, preset);
            profile = cfg.getString(DEFAULT_PROFILE_PNAME, profile);
        }

        if (additionalCodecSettings != null)
        {
            for (Map.Entry<String, String> e
                    : additionalCodecSettings.entrySet())
            {
                String k = e.getKey();
                String v = e.getValue();

                if ("h264.intrarefresh".equals(k))
                {
                    if("false".equals(v))
                        intraRefresh = false;
                }
                else if ("h264.profile".equals(k))
                {
                    if (BASELINE_PROFILE.equals(v)
                            || HIGH_PROFILE.equals(v)
                            || MAIN_PROFILE.equals(v))
                        profile = v;
                }
            }
        }

        long avcodec = FFmpeg.avcodec_find_encoder(FFmpeg.CODEC_ID_H264);

        if (avcodec == 0)
        {
            throw new ResourceUnavailableException(
                    "Could not find H.264 encoder.");
        }

        avctx = FFmpeg.avcodec_alloc_context3(avcodec);

        FFmpeg.avcodeccontext_set_pix_fmt(avctx, FFmpeg.PIX_FMT_YUV420P);
        FFmpeg.avcodeccontext_set_size(avctx, width, height);

        FFmpeg.avcodeccontext_set_qcompress(avctx, 0.6f);

        int bitRate
            = 1000
                * NeomediaServiceUtils
                    .getMediaServiceImpl()
                        .getDeviceConfiguration()
                            .getVideoBitrate();
        int frameRate = Format.NOT_SPECIFIED;

        // Allow the outputFormat to request a certain frameRate.
        if (outputVideoFormat != null)
            frameRate = (int) outputVideoFormat.getFrameRate();
        // Otherwise, output in the frameRate of the inputFormat.
        if ((frameRate == Format.NOT_SPECIFIED) && (inputVideoFormat != null))
            frameRate = (int) inputVideoFormat.getFrameRate();
        if (frameRate == Format.NOT_SPECIFIED)
            frameRate = DEFAULT_FRAME_RATE;

        // average bit rate
        FFmpeg.avcodeccontext_set_bit_rate(avctx, bitRate);
        // so to be 1 in x264
        FFmpeg.avcodeccontext_set_bit_rate_tolerance(avctx,
                (bitRate / frameRate));
        FFmpeg.avcodeccontext_set_rc_max_rate(avctx, bitRate);
        FFmpeg.avcodeccontext_set_sample_aspect_ratio(avctx, 0, 0);
        FFmpeg.avcodeccontext_set_thread_count(avctx, 1);

        // time_base should be 1 / frame rate
        FFmpeg.avcodeccontext_set_time_base(avctx, 1, frameRate);
        FFmpeg.avcodeccontext_set_ticks_per_frame(avctx, 2);
        FFmpeg.avcodeccontext_set_quantizer(avctx, 30, 31, 4);

        // avctx.chromaoffset = -2;

        FFmpeg.avcodeccontext_set_mb_decision(avctx,
            FFmpeg.FF_MB_DECISION_SIMPLE);

        FFmpeg.avcodeccontext_set_rc_eq(avctx, "blurCplx^(1-qComp)");

        FFmpeg.avcodeccontext_add_flags(avctx,
                FFmpeg.CODEC_FLAG_LOOP_FILTER);
        if (intraRefresh)
        {
            /*
             * The flag is ignored in newer FFmpeg versions and we set the x264
             * "intra-refresh" option for them. Anyway, the flag is set for the
             * older FFmpeg versions.
             */
            FFmpeg.avcodeccontext_add_flags2(avctx,
                FFmpeg.CODEC_FLAG2_INTRA_REFRESH);
        }
        FFmpeg.avcodeccontext_set_me_method(avctx, 7);
        FFmpeg.avcodeccontext_set_me_subpel_quality(avctx, 2);
        FFmpeg.avcodeccontext_set_me_range(avctx, 16);
        FFmpeg.avcodeccontext_set_me_cmp(avctx, FFmpeg.FF_CMP_CHROMA);
        FFmpeg.avcodeccontext_set_scenechange_threshold(avctx, 40);
        FFmpeg.avcodeccontext_set_rc_buffer_size(avctx, 10);
        FFmpeg.avcodeccontext_set_gop_size(avctx, keyint);
        FFmpeg.avcodeccontext_set_i_quant_factor(avctx, 1f / 1.4f);

        FFmpeg.avcodeccontext_set_refs(avctx, 1);
        // FFmpeg.avcodeccontext_set_trellis(avctx, 2);

        FFmpeg.avcodeccontext_set_keyint_min(avctx, X264_KEYINT_MIN_AUTO);

        if ((null == packetizationMode) || "0".equals(packetizationMode))
        {
            FFmpeg.avcodeccontext_set_rtp_payload_size(avctx,
                    Packetizer.MAX_PAYLOAD_SIZE);
        }

        try
        {
            FFmpeg.avcodeccontext_set_profile(avctx,
                getProfileForConfig(profile));
        }
        catch (UnsatisfiedLinkError ule)
        {
            logger.warn("The FFmpeg JNI library is out-of-date.");
        }

        if (FFmpeg.avcodec_open2(
                    avctx,
                    avcodec,
                    /*
                     * XXX crf=0 means lossless coding which is not supported by
                     * the baseline and main profiles. Consequently, we cannot
                     * specify it because we specify either the baseline or the
                     * main profile. Otherwise, x264 will detect the
                     * inconsistency in the specified parameters/options and
                     * FFmpeg will fail.
                     */
                    //"crf" /* constant quality mode, constant ratefactor */, "0",
                    "intra-refresh", intraRefresh ? "1" : "0",
                    "keyint", Integer.toString(keyint),
                    "partitions", "b8x8,i4x4,p8x8",
                    "preset", preset,
                    "thread_type", "slice",
                    "tune", "zerolatency")
                < 0)
        {
            throw new ResourceUnavailableException(
                    "Could not open H.264 encoder. (size= " + width + "x"
                            + height + ")");
        }

        rawFrameLen = (width * height * 3) / 2;
        rawFrameBuffer = FFmpeg.av_malloc(rawFrameLen);
        avFrame = FFmpeg.avcodec_alloc_frame();

        int sizeInBytes = width * height;

        FFmpeg.avframe_set_data(
                avFrame,
                rawFrameBuffer,
                sizeInBytes,
                sizeInBytes / 4);
        FFmpeg.avframe_set_linesize(avFrame, width, width / 2, width / 2);

        /*
         * In order to be sure that keyint will be respected, we will implement
         * it ourselves (regardless of the fact that we have told FFmpeg and
         * x264 about it). Otherwise, we may end up not generating keyframes at
         * all (apart from the two generated after open).
         */
        forceKeyFrame = true;
        this.keyint = keyint;
        lastKeyFrame = 0;

        /*
         * Implement the ability to have the remote peer request key frames from
         * this local peer.
         */
        if (keyFrameRequestee == null)
        {
            keyFrameRequestee
                = new KeyFrameControl.KeyFrameRequestee()
                        {
                            public boolean keyFrameRequest()
                            {
                                return JNIEncoder.this.keyFrameRequest();
                            }
                        };
        }
        if (keyFrameControl != null)
            keyFrameControl.addKeyFrameRequestee(-1, keyFrameRequestee);

        opened = true;
        super.open();
    }

    /**
     * Processes/encodes a buffer.
     *
     * @param inBuffer input buffer
     * @param outBuffer output buffer
     * @return <tt>BUFFER_PROCESSED_OK</tt> if buffer has been successfully
     * processed
     */
    @Override
    public synchronized int process(Buffer inBuffer, Buffer outBuffer)
    {
        if (isEOM(inBuffer))
        {
            propagateEOM(outBuffer);
            reset();
            return BUFFER_PROCESSED_OK;
        }
        if (inBuffer.isDiscard())
        {
            outBuffer.setDiscard(true);
            reset();
            return BUFFER_PROCESSED_OK;
        }

        Format inFormat = inBuffer.getFormat();

        if ((inFormat != inputFormat) && !inFormat.equals(inputFormat))
            setInputFormat(inFormat);

        if (inBuffer.getLength() < 10)
        {
            outBuffer.setDiscard(true);
            reset();
            return BUFFER_PROCESSED_OK;
        }

        // Copy the data of inBuffer into avFrame.
        FFmpeg.memcpy(
                rawFrameBuffer,
                (byte[]) inBuffer.getData(), inBuffer.getOffset(),
                rawFrameLen);

        boolean keyFrame = isKeyFrame();

        FFmpeg.avframe_set_key_frame(avFrame, keyFrame);
        /*
         * In order to be sure that keyint will be respected, we will implement
         * it ourselves (regardless of the fact that we have told FFmpeg and
         * x264 about it). Otherwise, we may end up not generating keyframes at
         * all (apart from the two generated after open).
         */
        if (keyFrame)
            lastKeyFrame = 0;
        else
            lastKeyFrame++;

        // Encode avFrame into the data of outBuffer.
        byte[] out
            = AbstractCodec2.validateByteArraySize(
                    outBuffer,
                    rawFrameLen,
                    false);
        int outLength
            = FFmpeg.avcodec_encode_video(avctx, out, out.length, avFrame);

        outBuffer.setLength(outLength);
        outBuffer.setOffset(0);
        outBuffer.setTimeStamp(inBuffer.getTimeStamp());
        return BUFFER_PROCESSED_OK;
    }

    /**
     * Notifies this <tt>RTCPFeedbackListener</tt> that an RTCP feedback message
     * has been received
     *
     * @param ev an <tt>RTCPFeedbackMessageEvent</tt> which specifies the
     * details of the notification event such as the feedback message type and
     * the payload type
     */
    @Override
    public void rtcpFeedbackMessageReceived(RTCPFeedbackMessageEvent ev)
    {
        /*
         * If RTCP message is a Picture Loss Indication (PLI) or a Full
         * Intra-frame Request (FIR) the encoder will force the next frame to be
         * a keyframe.
         */
        if (ev.getPayloadType() == RTCPFeedbackMessageEvent.PT_PS)
        {
            switch (ev.getFeedbackMessageType())
            {
                case RTCPFeedbackMessageEvent.FMT_PLI:
                case RTCPFeedbackMessageEvent.FMT_FIR:
                    if (logger.isTraceEnabled())
                    {
                        logger.trace(
                                "Scheduling a key-frame, because we received an"
                                    + " RTCP PLI or FIR.");
                    }
                    keyFrameRequest();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Sets additional settings on this <tt>Codec</tt>.
     *
     * @param additionalCodecSettings the additional settings to be set on this
     * <tt>Codec</tt>
     */
    public void setAdditionalCodecSettings(
            Map<String, String> additionalCodecSettings)
    {
        this.additionalCodecSettings = additionalCodecSettings;
    }

    /**
     * Sets the <tt>Format</tt> of the media data to be input to this
     * <tt>Codec</tt>.
     *
     * @param format the <tt>Format</tt> of media data to set on this
     * <tt>Codec</tt>
     * @return the <tt>Format</tt> of media data set on this <tt>Codec</tt> or
     * <tt>null</tt> if the specified <tt>format</tt> is not supported by this
     * <tt>Codec</tt>
     */
    @Override
    public Format setInputFormat(Format format)
    {
        // mismatch input format
        if (!(format instanceof VideoFormat)
                || (null == AbstractCodec2.matches(format, inputFormats)))
            return null;

        YUVFormat yuvFormat = (YUVFormat) format;

        if (yuvFormat.getOffsetU() > yuvFormat.getOffsetV())
            return null;

        inputFormat = AbstractCodec2.specialize(yuvFormat, Format.byteArray);

        // Return the selected inputFormat
        return inputFormat;
    }

    /**
     * Sets the <tt>KeyFrameControl</tt> to be used by this
     * <tt>JNIEncoder</tt> as a means of control over its key frame-related
     * logic.
     *
     * @param keyFrameControl the <tt>KeyFrameControl</tt> to be used by this
     * <tt>JNIEncoder</tt> as a means of control over its key frame-related
     * logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        if (this.keyFrameControl != keyFrameControl)
        {
            if ((this.keyFrameControl != null) && (keyFrameRequestee != null))
                this.keyFrameControl.removeKeyFrameRequestee(keyFrameRequestee);

            this.keyFrameControl = keyFrameControl;

            if ((this.keyFrameControl != null) && (keyFrameRequestee != null))
                this.keyFrameControl.addKeyFrameRequestee(-1, keyFrameRequestee);
        }
    }

    /**
     * Sets the <tt>Format</tt> in which this <tt>Codec</tt> is to output media
     * data.
     *
     * @param format the <tt>Format</tt> in which this <tt>Codec</tt> is to
     * output media data
     * @return the <tt>Format</tt> in which this <tt>Codec</tt> is currently
     * configured to output media data or <tt>null</tt> if <tt>format</tt> was
     * found to be incompatible with this <tt>Codec</tt>
     */
    @Override
    public Format setOutputFormat(Format format)
    {
        // mismatch output format
        if (!(format instanceof VideoFormat)
                || (null
                        == AbstractCodec2.matches(
                                format,
                                getMatchingOutputFormats(inputFormat))))
            return null;

        VideoFormat videoFormat = (VideoFormat) format;
        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the
         * input.
         */
        Dimension size = null;

        if (inputFormat != null)
            size = ((VideoFormat) inputFormat).getSize();
        if ((size == null) && format.matches(outputFormat))
            size = ((VideoFormat) outputFormat).getSize();

        Map<String, String> fmtps = null;

        if (format instanceof ParameterizedVideoFormat)
            fmtps = ((ParameterizedVideoFormat) format).getFormatParameters();
        if (fmtps == null)
            fmtps = new HashMap<String, String>();
        if (packetizationMode != null)
        {
            fmtps.put(
                    VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP,
                    packetizationMode);
        }

        outputFormat
            = new ParameterizedVideoFormat(
                    videoFormat.getEncoding(),
                    size,
                    /* maxDataLength */ Format.NOT_SPECIFIED,
                    Format.byteArray,
                    videoFormat.getFrameRate(),
                    fmtps);

        // Return the selected outputFormat
        return outputFormat;
    }

    /**
     * Sets the packetization mode to be used for the H.264 RTP payload output
     * by this <tt>JNIEncoder</tt> and the associated packetizer.
     *
     * @param packetizationMode the packetization mode to be used for the H.264
     * RTP payload output by this <tt>JNIEncoder</tt> and the associated
     * packetizer
     */
    public void setPacketizationMode(String packetizationMode)
    {
        /*
         * RFC 3984 "RTP Payload Format for H.264 Video" says that "[w]hen the
         * value of packetization-mode is equal to 0 or packetization-mode is
         * not present, the single NAL mode, as defined in section 6.2 of RFC
         * 3984, MUST be used."
         */
        if ((packetizationMode == null) || "0".equals(packetizationMode))
            this.packetizationMode = "0";
        else if ("1".equals(packetizationMode))
            this.packetizationMode = "1";
        else
            throw new IllegalArgumentException("packetizationMode");
    }
}
