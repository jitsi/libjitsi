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
package org.jitsi.impl.neomedia.codec.audio.g729;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.util.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class JavaDecoder
    extends AbstractCodec2
{
    private static final short BIT_0 = Ld8k.BIT_0;

    private static final short BIT_1 = Ld8k.BIT_1;

    private static final int L_FRAME = Ld8k.L_FRAME;

    private static final int SERIAL_SIZE = Ld8k.SERIAL_SIZE;

    private static final short SIZE_WORD = Ld8k.SIZE_WORD;

    private static final short SYNC_WORD = Ld8k.SYNC_WORD;

    private static final int INPUT_FRAME_SIZE_IN_BYTES = L_FRAME / 8;

    private static final int OUTPUT_FRAME_SIZE_IN_BYTES = 2 * L_FRAME;

    private Decoder decoder;

    private short[] serial;

    private short[] sp16;

    /**
     * Initializes a new <code>JavaDecoder</code> instance.
     */
    public JavaDecoder()
    {
        super(
            "G.729 Decoder",
            AudioFormat.class,
            new AudioFormat[]
                    {
                        new AudioFormat(
                                AudioFormat.LINEAR,
                                8000,
                                16,
                                1,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED,
                                /* frameSizeInBits */ Format.NOT_SPECIFIED,
                                /* frameRate */ Format.NOT_SPECIFIED,
                                Format.byteArray)
                    });

        inputFormats
            = new AudioFormat[]
                    {
                        new AudioFormat(
                                AudioFormat.G729_RTP,
                                8000,
                                AudioFormat.NOT_SPECIFIED,
                                1)
                    };
    }

    private void depacketize(byte[] inFrame, int inFrameOffset, short[] serial)
    {
        serial[0] = SYNC_WORD;
        serial[1] = SIZE_WORD;
        for (int s = 0; s < L_FRAME; s++)
        {
            int in = inFrame[inFrameOffset + s / 8];

            in &= 1 << (7 - (s % 8));
            serial[2 + s] = (0 != in) ? BIT_1 : BIT_0;
        }
    }

    /*
     * Implements AbstractCodecExt#doClose().
     */
    @Override
    protected void doClose()
    {
        serial = null;
        sp16 = null;
        decoder = null;
    }

    /**
     * Opens this <tt>Codec</tt> and acquires the resources that it needs to
     * operate. A call to {@link PlugIn#open()} on this instance will result in
     * a call to <tt>doOpen</tt> only if {@link AbstractCodec#opened} is
     * <tt>false</tt>. All required input and/or output formats are assumed to
     * have been set on this <tt>Codec</tt> before <tt>doOpen</tt> is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this
     * <tt>Codec</tt> needs to operate cannot be acquired
     * @see AbstractCodecExt#doOpen()
     */
    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
        serial = new short[SERIAL_SIZE];
        sp16 = new short[L_FRAME];
        decoder = new Decoder();
    }

    /*
     * Implements AbstractCodecExt#doProcess(Buffer, Buffer).
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        int inLength = inBuffer.getLength();
        /*
         * Decode as many G.729 frames as possible in one go in order to
         * mitigate an issue with sample rate conversion which leads to audio
         * glitches.
         */
        int frameCount = inLength / INPUT_FRAME_SIZE_IN_BYTES;

        if (frameCount < 1)
        {
            discardOutputBuffer(outBuffer);
            return BUFFER_PROCESSED_OK | OUTPUT_BUFFER_NOT_FILLED;
        }

        byte[] in = (byte[]) inBuffer.getData();
        int inOffset = inBuffer.getOffset();

        int outOffset = outBuffer.getOffset();
        int outLength = OUTPUT_FRAME_SIZE_IN_BYTES * frameCount;
        byte[] out
            = validateByteArraySize(outBuffer, outOffset + outLength, false);

        for (int i = 0; i < frameCount; i++)
        {
            depacketize(in, inOffset, serial);
            inLength -= INPUT_FRAME_SIZE_IN_BYTES;
            inOffset += INPUT_FRAME_SIZE_IN_BYTES;

            decoder.process(serial, sp16);

            writeShorts(sp16, out, outOffset);
            outOffset += OUTPUT_FRAME_SIZE_IN_BYTES;
        }
        inBuffer.setLength(inLength);
        inBuffer.setOffset(inOffset);
        outBuffer.setLength(outLength);

        return BUFFER_PROCESSED_OK;
    }

    private static void writeShorts(short[] in, byte[] out, int outOffset)
    {
        for (int i = 0, o = outOffset; i < in.length; i++, o += 2)
            ArrayIOUtils.writeShort(in[i], out, o);
    }
}
