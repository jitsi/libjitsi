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

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.audio.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements an Adaptive Multi-Rate Wideband (AMR-WB) decoder using FFmpeg.
 *
 * @author Lyubomir Marinov
 */
public class JNIDecoder
    extends FFmpegAudioDecoder
{
    static
    {
        assertFindAVCodec(FFmpeg.CODEC_ID_AMR_WB);
    }

    /**
     * The indicator which determines whether this <tt>JNIDecoder</tt> is to
     * perform RTP depacketization.
     */
    private boolean depacketize = false;

    /**
     * Initializes a new <tt>JNIDecoder</tt> instance.
     */
    public JNIDecoder()
    {
        super(
                "AMR-WB JNI Decoder",
                FFmpeg.CODEC_ID_AMR_WB,
                JNIEncoder.SUPPORTED_INPUT_FORMATS);

        inputFormats = JNIEncoder.SUPPORTED_OUTPUT_FORMATS;
    }

    private int depacketize(Buffer buf)
    {
        int inLen = buf.getLength();

        // The payload header and payload table of contents together span at
        // least two bytes in both bandwidth-efficient mode and octet-aligned
        // mode.
        if (inLen < 2)
            return BUFFER_PROCESSED_FAILED;

        byte[] in = (byte[]) buf.getData();
        int inOff = buf.getOffset();

        int in0 = in[inOff] & 0xFF;
        // F (1 bit): If set to 1, indicates that this frame is followed by
        // another speech frame in this payload; if set to 0, indicates that
        // this frame is the last frame in this payload.
        int f = (0x08 & in0) >>> 3;

        // TODO Add support for multiple frames in a single payload.
        if (f == 1)
            return BUFFER_PROCESSED_FAILED;

        int in1 = in[inOff + 1] & 0xFF;
        // Q (1 bit): Frame quality indicator. If set to 0, indicates the
        // corresponding frame is severely damaged.
        int q = (0x40 & in1) >>> 6;

        if (q == 0)
            return BUFFER_PROCESSED_FAILED;

        // FT (4 bits): Frame type index, indicating either the AMR-WB speech
        // coding mode or comfort noise (SID) mode of the corresponding frame
        // carried in this payload.
        int ft = ((0x07 & in0) << 1) | ((0x80 & in1) >>> 7);

        if (ft > 8)
            return OUTPUT_BUFFER_NOT_FILLED;

        int outLen = inLen;
        byte[] out = in;

        int out0 = ((ft & 0x0F) << 3) | ((q & 0x01) << 2);

        out[0] = (byte) out0;

        for (int inI = inOff + 1, inEnd = inOff + inLen, outI = 1;
                inI < inEnd;
                outI++)
        {
            int i = (0x3F & in[inI]) << 2;

            out[outI] = (byte) i;

            inI++;

            if (inI < inEnd)
            {
                int o = 0xFC & out[outI];

                i = (0xC0 & in[inI]) >>> 6;
                out[outI] = (byte) (o | i);
            }
        }

        buf.setData(out);
        buf.setDuration(
                20L /* milliseconds */
                    * 1000000L /* nanoseconds in a millisecond */);
        buf.setLength(outLen);
        buf.setOffset(0);

        return BUFFER_PROCESSED_OK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf)
    {
        if (this.depacketize)
        {
            int depacketize = depacketize(inBuf);

            if (((depacketize & BUFFER_PROCESSED_FAILED) != 0)
                    || ((depacketize & OUTPUT_BUFFER_NOT_FILLED) != 0))
            {
                return depacketize;
            }
        }

        return super.doProcess(inBuf, outBuf);
    }

    /**
     * {@inheritDoc}
     *
     * Additionally, determines whether this <tt>JNIDecoder</tt> is to perform
     * RTP depacketization.
     */
    @Override
    public Format setInputFormat(Format format)
    {
        format = super.setInputFormat(format);

        if (format != null)
        {
            String encoding = format.getEncoding();

            depacketize
                = ((encoding != null) && encoding.endsWith(Constants._RTP));
        }
        return format;
    }
}
