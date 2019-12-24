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
package org.jitsi.impl.neomedia.codec.audio.amrwb;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.audio.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements an Adaptive Multi-Rate Wideband (AMR-WB) encoder using FFmpeg.
 *
 * @author Lyubomir Marinov
 */
public class JNIEncoder
    extends FFmpegAudioEncoder
{
    static
    {
        assertFindAVCodec(FFmpeg.CODEC_ID_AMR_WB);
    }

    /**
     * The bit rates supported by Adaptive Multi-Rate Wideband (AMR-WB).
     */
    static final int[] BIT_RATES
        = {  6600,  8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850 };

    /**
     * The amount of bytes in the RTP frame, for each BIT_RATE and the
     * Silence Indicator (SID), see 3GPP TS 26.201 Table 2 and 3:
     * Total number of (speech) bits + 10, devided by 8 to get the octets.
     * 10 because of the added header, which is CMR=4, F=1, FT=4, Q=1.
     * Three examples:
     * Mode 0 ( 6600) has 132 speech bits and 10 header bits = 18 octets
     * Mode 8 (23850) has 477 speech bits and 10 header bits = 61 octets.
     * Mode 9 (  SID) has  40        bits and 10 header bits =  7 octets.
     */
    static final int[] OCTETS
        = {    18,    24,    33,    37,    41,    47,    51,    59,    61, 7 };

    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>JNIEncoder</tt> instances.
     */
    static final AudioFormat[] SUPPORTED_INPUT_FORMATS
        = {
            new AudioFormat(
                    AudioFormat.LINEAR,
                    16000,
                    16,
                    1,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    /* frameSizeInBits */ Format.NOT_SPECIFIED,
                    /* frameRate */ Format.NOT_SPECIFIED,
                    Format.byteArray)
        };

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>JNIEncoder</tt> instances.
     */
    static final AudioFormat[] SUPPORTED_OUTPUT_FORMATS
        = {
            new AudioFormat(
                    Constants.AMR_WB_RTP,
                    16000,
                    /* sampleSizeInBits */ Format.NOT_SPECIFIED,
                    1),
            new AudioFormat(
                    Constants.AMR_WB,
                    16000,
                    /* sampleSizeInBits */ Format.NOT_SPECIFIED,
                    1)
        };

    /**
     * The bit rate to be produced by this <tt>JNIEncoder</tt>.
     */
    private int bitRate = BIT_RATES[BIT_RATES.length - 1];

    /**
     * The indicator which determines whether this <tt>JNIEncoder</tt> is to
     * perform RTP packetization.
     */
    private boolean packetize = false;

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public JNIEncoder()
    {
        super(
                "AMR-WB JNI Encoder",
                FFmpeg.CODEC_ID_AMR_WB,
                SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureAVCodecContext(long avctx, AudioFormat format)
    {
        super.configureAVCodecContext(avctx, format);

        FFmpeg.avcodeccontext_set_bit_rate(avctx, bitRate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf)
    {
        int doProcess = super.doProcess(inBuf, outBuf);

        if (this.packetize
                && ((doProcess & BUFFER_PROCESSED_FAILED) == 0)
                && ((doProcess & OUTPUT_BUFFER_NOT_FILLED) == 0))
        {
            int packetize = packetize(outBuf);

            if ((packetize & BUFFER_PROCESSED_FAILED) != 0)
                doProcess |= BUFFER_PROCESSED_FAILED;
            if ((packetize & OUTPUT_BUFFER_NOT_FILLED) != 0)
                doProcess |= OUTPUT_BUFFER_NOT_FILLED;
        }

        return doProcess;
    }

    /**
     * Packetizes a specific <tt>Buffer</tt> for RTP.
     *
     * @param buf the <tt>Buffer</tt> to packetize for RTP
     * @return
     */
    private int packetize(Buffer buf)
    {
        byte[] src = (byte[]) buf.getData();
        int srcLen = buf.getLength();
        int srcOff = buf.getOffset();

        int dstLen = srcLen + 1;
        byte[] dst = new byte[dstLen];

        int cmr /* codec mode request */        = 15;
        int f   /* followed by another frame */ = (src[0] >>> 7) & 0x01;
        int ft  /* frame type index */          = (src[0] >>> 3) & 0x0F;
        int q   /* frame quality indicator */   = (src[0] >>> 2) & 0x01;

        if (ft > 9) {
            /**
             * speech lost or no data, in case of DTX because of VAD
             * In both cases, no RTP packet shall be sent = silence. The return
             * code "OK" (with zero length or zero duration) does this not. The
             * return code "FAILED" does this.
             */
            return BUFFER_PROCESSED_FAILED;
        }

        dst[0] = (byte) (((cmr & 0x0F) << 4) | ((f & 0x01) << 3) | ((ft & 0x0E) >>> 1));
        dst[1] = (byte) ((( ft & 0x01) << 7) | ((q & 0x01) << 6));

        for (int srcI = srcOff + 1, srcEnd = srcOff + srcLen, dstI = 1;
                srcI < srcEnd;
                srcI++)
        {
            int s = 0xFF & src[srcI];
            int d = 0xC0 & dst[dstI];

            d |= ((0xFC & s) >>> 2);
            dst[dstI] = (byte) d;

            dstI++;

            dst[dstI] = (byte) ((0x03 & s) << 6);
        }

        buf.setData(dst);
        buf.setDuration(
                20L /* milliseconds */
                    * 1000000L /* nanoseconds in a millisecond */);
        buf.setLength(OCTETS[ft]);
        buf.setOffset(0);

        return BUFFER_PROCESSED_OK;
    }

    /**
     * {@inheritDoc}
     *
     * Additionally, determines whether this <tt>JNIEncoder</tt> is to perform
     * RTP packetization.
     */
    @Override
    public Format setOutputFormat(Format format)
    {
        format = super.setOutputFormat(format);

        if (format != null)
        {
            String encoding = format.getEncoding();

            packetize
                = ((encoding != null) && encoding.endsWith(Constants._RTP));
        }
        return format;
    }
}
