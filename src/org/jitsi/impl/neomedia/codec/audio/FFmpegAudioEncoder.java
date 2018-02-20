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
public class FFmpegAudioEncoder
    extends AbstractFFmpegAudioCodec
{
    /**
     * The <tt>Logger</tt> used by the <tt>FFmpegAudioEncoder</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(FFmpegAudioEncoder.class);

    /**
     * Asserts that an encoder with a specific <tt>AVCodecID</tt> is found by
     * FFmpeg.
     *
     * @param codecID the <tt>AVCodecID</tt> of the encoder to find
     * @throws RuntimeException if no encoder with the specified
     * <tt>codecID</tt> is found by FFmpeg
     */
    public static void assertFindAVCodec(int codecID)
    {
        if (FFmpeg.avcodec_find_encoder(codecID) == 0)
        {
            throw new RuntimeException(
                    "Could not find FFmpeg encoder " + codecIDToString(codecID)
                        + "!");
        }
    }

    /**
     * The audio data which was given to this <tt>AbstractFFmpegAudioCodec</tt>
     * in a previous call to {@link #doProcess(Buffer, Buffer)} but was less
     * than {@link #frameSizeInBytes} in length and was thus left to be
     * prepended to the audio data in a next call to <tt>doProcess</tt>.
     */
    private byte[] prevIn;

    /**
     * The length of the valid audio data in {@link #prevIn}.
     */
    protected int prevInLen;

    /**
     * Initializes a new <tt>FFmpegAudioEncoder</tt> instance with a specific
     * <tt>PlugIn</tt> name, a specific <tt>AVCodecID</tt>, and a specific list
     * of <tt>Format</tt>s supported as output.
     *
     * @param name the <tt>PlugIn</tt> name of the new instance
     * @param codecID the <tt>AVCodecID</tt> of the FFmpeg codec to be
     * represented by the new instance 
     * @param supportedOutputFormats the list of <tt>Format</tt>s supported by
     * the new instance as output
     */
    protected FFmpegAudioEncoder(
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
    protected void configureAVCodecContext(long avctx, AudioFormat format)
    {
        super.configureAVCodecContext(avctx, format);

        try
        {
            FFmpeg.avcodeccontext_set_sample_fmt(
                    avctx,
                    FFmpeg.AV_SAMPLE_FMT_S16P);
        }
        catch (UnsatisfiedLinkError ule)
        {
            logger.warn("The FFmpeg JNI library is out-of-date.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void doClose()
    {
        super.doClose();

        prevIn = null;
        prevInLen = 0;
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

        if ((prevInLen > 0) || (inLen < frameSizeInBytes))
        {
            int newPrevInLen = Math.min(frameSizeInBytes - prevInLen, inLen);

            if (newPrevInLen > 0)
            {
                if (prevIn == null)
                {
                    prevIn = new byte[frameSizeInBytes];
                    prevInLen = 0;
                }

                System.arraycopy(in, inOff, prevIn, prevInLen, newPrevInLen);

                inBuf.setLength(inLen - newPrevInLen);
                inBuf.setOffset(inOff + newPrevInLen);

                prevInLen += newPrevInLen;
                if (prevInLen == frameSizeInBytes)
                {
                    in = prevIn;
                    inLen = prevInLen;
                    inOff = 0;

                    prevInLen = 0;
                }
                else
                {
                    return OUTPUT_BUFFER_NOT_FILLED;
                }
            }
        }
        else
        {
            inBuf.setLength(inLen - frameSizeInBytes);
            inBuf.setOffset(inOff + frameSizeInBytes);
        }

        Object outData = outBuf.getData();
        byte[] out
            = (outData instanceof byte[]) ? (byte[]) outData : null;
        int outOff = outBuf.getOffset();
        int minOutLen = Math.max(FFmpeg.FF_MIN_BUFFER_SIZE, inLen);

        if ((out == null) || (out.length - outOff < minOutLen))
        {
            out = new byte[minOutLen];
            outBuf.setData(out);
            outOff = 0;
            outBuf.setOffset(outOff);
        }

        int outLen
            = FFmpeg.avcodec_encode_audio(
                    avctx,
                    out, outOff, out.length - outOff,
                    in, inOff);

        if (outLen < 0)
        {
            return BUFFER_PROCESSED_FAILED;
        }
        else
        {
            outBuf.setFormat(getOutputFormat());
            outBuf.setLength(outLen);

            if (inBuf.getLength() > 0)
                return INPUT_BUFFER_NOT_CONSUMED;
            else if (outLen == 0)
                return OUTPUT_BUFFER_NOT_FILLED;
            else
                return BUFFER_PROCESSED_OK;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected long findAVCodec(int codecID)
    {
        return FFmpeg.avcodec_find_encoder(codecID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AudioFormat getAVCodecContextFormat()
    {
        return (AudioFormat) getInputFormat();
    }
}
