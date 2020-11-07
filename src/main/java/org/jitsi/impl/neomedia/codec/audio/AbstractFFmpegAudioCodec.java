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
package org.jitsi.impl.neomedia.codec.audio;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;

/**
 * Implements an audio <tt>Codec</tt> using the FFmpeg library.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractFFmpegAudioCodec
    extends AbstractCodec2
{
    /**
     * Returns a <tt>String</tt> representation of a specific
     * <tt>AVCodecID</tt>.
     *
     * @param codecID the <tt>AVCodecID</tt> to represent as a <tt>String</tt>
     * @return a <tt>String</tt> representation of the specified
     * <tt>codecID</tt>
     */
    public static String codecIDToString(int codecID)
    {
        switch (codecID)
        {
        case FFmpeg.CODEC_ID_MP3:
            return "CODEC_ID_MP3";
        default:
            return "0x" + Long.toHexString(codecID & 0xFFFFFFFFL);
        }
    }

    /**
     * The <tt>AVCodecContext</tt> which performs the actual encoding/decoding
     * and which is the native counterpart of this open
     * <tt>AbstractFFmpegAudioCodec</tt>.
     */
    protected long avctx;

    /**
     * The <tt>AVCodecID</tt> of {@link #avctx}.
     */
    protected final int codecID;

    /**
     * The number of bytes of audio data to be encoded with a single call to
     * {@link FFmpeg#avcodec_encode_audio(long, byte[], int, int, byte[], int)}
     * based on the <tt>frame_size</tt> of {@link #avctx}.
     */
    protected int frameSizeInBytes;

    /**
     * Initializes a new <tt>AbstractFFmpegAudioCodec</tt> instance with a
     * specific <tt>PlugIn</tt> name, a specific <tt>AVCodecID</tt>, and a
     * specific list of <tt>Format</tt>s supported as output.
     *
     * @param name the <tt>PlugIn</tt> name of the new instance
     * @param codecID the <tt>AVCodecID</tt> of the FFmpeg codec to be
     * represented by the new instance 
     * @param supportedOutputFormats the list of <tt>Format</tt>s supported by
     * the new instance as output
     */
    protected AbstractFFmpegAudioCodec(
            String name,
            int codecID,
            Format[] supportedOutputFormats)
    {
        super(name, AudioFormat.class, supportedOutputFormats);

        this.codecID = codecID;
    }

    /**
     * Configures the <tt>AVCodecContext</tt> initialized in {@link #doOpen()}
     * prior to invoking one of the FFmpeg functions in the
     * <tt>avcodec_open</tt> family. Allows extenders to override and provide
     * additional, optional configuration.
     *
     * @param avctx the <tt>AVCodecContext</tt> to configure
     * @param format the <tt>AudioFormat</tt> with which <tt>avctx</tt> is being
     * configured
     */
    protected void configureAVCodecContext(long avctx, AudioFormat format)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void doClose()
    {
        if (avctx != 0)
        {
            FFmpeg.avcodec_close(avctx);
            FFmpeg.av_free(avctx);
            avctx = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void doOpen()
        throws ResourceUnavailableException
    {
        int codecID = this.codecID;
        long codec = findAVCodec(codecID);

        if (codec == 0)
        {
            throw new ResourceUnavailableException(
                    "Could not find FFmpeg codec " + codecIDToString(codecID)
                        + "!");
        }

        avctx = FFmpeg.avcodec_alloc_context3(codec);
        if (avctx == 0)
        {
            throw new ResourceUnavailableException(
                    "Could not allocate AVCodecContext for FFmpeg codec "
                        + codecIDToString(codecID) + "!");
        }

        int avcodec_open = -1;

        try
        {
            AudioFormat format = getAVCodecContextFormat();
            int channels = format.getChannels();
            int sampleRate = (int) format.getSampleRate();

            if (channels == Format.NOT_SPECIFIED)
                channels = 1;
            FFmpeg.avcodeccontext_set_channels(avctx, channels);
            if (channels == 1)
            {
                //mono
                FFmpeg.avcodeccontext_set_channel_layout(
                    avctx,
                    FFmpeg.AV_CH_LAYOUT_MONO);
            }
            else if (channels == 2)
            {
                //stereo
                FFmpeg.avcodeccontext_set_channel_layout(
                    avctx,
                    FFmpeg.AV_CH_LAYOUT_STEREO);
            }

            if (sampleRate != Format.NOT_SPECIFIED)
                FFmpeg.avcodeccontext_set_sample_rate(avctx, sampleRate);

            configureAVCodecContext(avctx, format);

            avcodec_open = FFmpeg.avcodec_open2(avctx, codec);

            // When encoding, set by libavcodec in avcodec_open2 and may be 0 to
            // indicate unrestricted frame size. When decoding, may be set by
            // some decoders to indicate constant frame size.
            int frameSize = FFmpeg.avcodeccontext_get_frame_size(avctx);

            frameSizeInBytes
                = frameSize
                    * (format.getSampleSizeInBits() / 8)
                    * channels;
        }
        finally
        {
            if (avcodec_open < 0)
            {
                FFmpeg.av_free(avctx);
                avctx = 0;
            }
        }
        if (avctx == 0)
        {
            throw new ResourceUnavailableException(
                    "Could not open FFmpeg codec " + codecIDToString(codecID)
                        + "!");
        }
    }

    /**
     * Finds an <tt>AVCodec</tt> with a specific <tt>AVCodecID</tt>. The method
     * is invoked by {@link #doOpen()} in order to (eventually) open a new
     * <tt>AVCodecContext</tt>.
     *
     * @param codecID the <tt>AVCodecID</tt> of the <tt>AVCodec</tt> to find
     * @return an <tt>AVCodec</tt> with the specified <tt>codecID</tt> or
     * <tt>0</tt>
     */
    protected abstract long findAVCodec(int codecID);

    /**
     * Gets the <tt>AudioFormat</tt> with which {@link #avctx} is to be
     * configured and opened by {@link #doOpen()}.
     *
     * @return the <tt>AudioFormat</tt> with which <tt>avctx</tt> is to be
     * configured and opened by <tt>doOpen()</tt>
     */
    protected abstract AudioFormat getAVCodecContextFormat();
}
