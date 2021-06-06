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
import org.jitsi.util.*;

/**
 * Implements an audio <tt>Codec</tt> using the FFmpeg library.
 *
 * @author Lyubomir Marinov
 */
public class FFmpegAudioDecoder
    extends AbstractFFmpegAudioCodec
{
    /**
     * Asserts that an decoder with a specific <tt>AVCodecID</tt> is found by
     * FFmpeg.
     *
     * @param codecID the <tt>AVCodecID</tt> of the decoder to find
     * @throws RuntimeException if no decoder with the specified
     * <tt>codecID</tt> is found by FFmpeg
     */
    public static void assertFindAVCodec(int codecID)
    {
        if (FFmpeg.avcodec_find_decoder(codecID) == 0)
        {
            throw new RuntimeException(
                    "Could not find FFmpeg decoder " + codecIDToString(codecID)
                        + "!");
        }
    }

    private long avpkt;

    private final boolean[] got_frame = new boolean[1];

    private long frame;

    /**
     * Initializes a new <tt>FFmpegAudioDecoder</tt> instance with a specific
     * <tt>PlugIn</tt> name, a specific <tt>AVCodecID</tt>, and a specific list
     * of <tt>Format</tt>s supported as output.
     *
     * @param name the <tt>PlugIn</tt> name of the new instance
     * @param codecID the <tt>AVCodecID</tt> of the FFmpeg codec to be
     * represented by the new instance 
     * @param supportedOutputFormats the list of <tt>Format</tt>s supported by
     * the new instance as output
     */
    protected FFmpegAudioDecoder(
            String name,
            int codecID,
            Format[] supportedOutputFormats)
    {
        super(name, codecID, supportedOutputFormats);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void doClose()
    {
        super.doClose();

        // avpkt
        long avpkt = this.avpkt;

        if (avpkt != 0)
        {
            this.avpkt = 0;
            FFmpeg.avcodec_free_packet(avpkt);
        }

        // frame
        long frame = this.frame;

        if (frame != 0)
        {
            this.frame = 0;
            FFmpeg.avcodec_free_frame(frame);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void doOpen()
        throws ResourceUnavailableException
    {
        super.doOpen();

        // avpkt
        long avpkt = this.avpkt;

        if (avpkt != 0)
        {
            this.avpkt = 0;
            FFmpeg.avcodec_free_packet(avpkt);
        }
        avpkt = FFmpeg.avcodec_alloc_packet(0);
        if (avpkt == 0)
        {
            doClose();
            throw new ResourceUnavailableException(
                    "Failed to allocate a new AVPacket for FFmpeg codec "
                        + codecIDToString(codecID) + "!");
        }
        else
        {
            this.avpkt = avpkt;
        }

        // frame
        long frame = this.frame;

        if (frame != 0)
        {
            this.frame = 0;
            FFmpeg.avcodec_free_frame(frame);
        }
        frame = FFmpeg.avcodec_alloc_frame();
        if (frame == 0)
        {
            doClose();
            throw new ResourceUnavailableException(
                    "Failed to allocate a new AVFrame for FFmpeg codec "
                        + codecIDToString(codecID) + "!");
        }
        else
        {
            this.frame = frame;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized int doProcess(Buffer inBuf, Buffer outBuf)
    {
        byte[] in = (byte[]) inBuf.getData();
        int inLen = inBuf.getLength();
        int inOff = inBuf.getOffset();

        long avpkt = this.avpkt;
        long frame = this.frame;

        FFmpeg.avpacket_set_data(avpkt, in, inOff, inLen);

        int consumedInLen
            = FFmpeg.avcodec_decode_audio4(avctx, frame, got_frame, avpkt);

        if ((consumedInLen < 0) || (consumedInLen > inLen))
        {
            return BUFFER_PROCESSED_FAILED;
        }
        else
        {
            int doProcess = BUFFER_PROCESSED_OK;

            inLen -= consumedInLen;
            inBuf.setLength(inLen);
            if (inLen > 0)
                doProcess |= INPUT_BUFFER_NOT_CONSUMED;

            if (got_frame[0])
            {
                long data0 = FFmpeg.avframe_get_data0(frame);
                int linesize0 = FFmpeg.avframe_get_linesize0(frame);

                if (data0 == 0)
                {
                    doProcess = BUFFER_PROCESSED_FAILED;
                }
                else
                {
                    byte[] bytes = new byte[linesize0];

                    FFmpeg.memcpy(bytes, 0, bytes.length, data0);

                    java.nio.FloatBuffer floats
                        = java.nio.ByteBuffer
                            .wrap(bytes)
                                .order(java.nio.ByteOrder.nativeOrder())
                                    .asFloatBuffer();
                    int outLen = floats.limit() * 2;
                    byte[] out = validateByteArraySize(outBuf, outLen, false);

                    outLen = 0;
                    for (int floatI = 0, floatEnd = floats.limit();
                            floatI < floatEnd;
                            ++floatI)
                    {
                        int s16
                            = Math.round(floats.get(floatI) * Short.MAX_VALUE);

                        if (s16 < Short.MIN_VALUE)
                            s16 = Short.MIN_VALUE;
                        else if (s16 > Short.MAX_VALUE)
                            s16 = Short.MAX_VALUE;
                        ArrayIOUtils.writeInt16(s16, out, outLen);
                        outLen += 2;
                    }

                    outBuf.setDuration(
                            20L /* milliseconds */
                                * 1000000L /* nanoseconds in a millisecond */);
                    outBuf.setFormat(getOutputFormat());
                    outBuf.setLength(outLen);
                    outBuf.setOffset(0);
                }
            }
            else
            {
                doProcess |= OUTPUT_BUFFER_NOT_FILLED;
            }

            return doProcess;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected long findAVCodec(int codecID)
    {
        return FFmpeg.avcodec_find_decoder(codecID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AudioFormat getAVCodecContextFormat()
    {
        return (AudioFormat) getOutputFormat();
    }
}
