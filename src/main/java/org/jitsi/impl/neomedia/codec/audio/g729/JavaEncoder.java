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

import java.awt.*;
import java.util.*;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.util.*;

/**
 *
 * @author Lubomir Marinov
 */
public class JavaEncoder
    extends AbstractCodec2
    implements AdvancedAttributesAwareCodec
{
    private static final short BIT_1 = Ld8k.BIT_1;

    private static final int L_FRAME = Ld8k.L_FRAME;

    private static final int SERIAL_SIZE = Ld8k.SERIAL_SIZE;

    private static final int INPUT_FRAME_SIZE_IN_BYTES = 2 * L_FRAME;

    private static final int OUTPUT_FRAME_SIZE_IN_BYTES = L_FRAME / 8;

    /**
     * The count of the output frames to packetize. By default we packetize
     * 2 audio frames in one G729 packet.
     */
    private int OUTPUT_FRAMES_COUNT = 2;

    private Coder coder;

    private int outFrameCount;

    /**
     * The previous input if it was less than the input frame size and which is
     * to be prepended to the next input in order to form a complete input
     * frame.
     */
    private byte[] prevIn;

    /**
     * The length of the previous input if it was less than the input frame size
     * and which is to be prepended to the next input in order to form a
     * complete input frame.
     */
    private int prevInLength;

    private short[] serial;

    private short[] sp16;

    /**
     * The duration an output <tt>Buffer</tt> produced by this <tt>Codec</tt>
     * in nanosecond. We packetize 2 audio frames in one G729 packet by default.
     */
    private int duration
        = OUTPUT_FRAME_SIZE_IN_BYTES * OUTPUT_FRAMES_COUNT * 1000000;

    /**
     * Initializes a new <code>JavaEncoder</code> instance.
     */
    public JavaEncoder()
    {
        super(
            "G.729 Encoder",
            AudioFormat.class,
            new AudioFormat[]
                    {
                        new AudioFormat(
                                AudioFormat.G729_RTP,
                                8000,
                                AudioFormat.NOT_SPECIFIED,
                                1)
                    });

        inputFormats
            = new AudioFormat[]
                    {
                        new AudioFormat(
                                AudioFormat.LINEAR,
                                8000,
                                16,
                                1,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED)
                    };

        addControl(this);
    }

    /**
     * Get the output format.
     *
     * @return output format
     * @see net.sf.fmj.media.AbstractCodec#getOutputFormat()
     */
    @Override
    public Format getOutputFormat()
    {
        Format f = super.getOutputFormat();

        if ((f != null) && (f.getClass() == AudioFormat.class))
        {
            AudioFormat af = (AudioFormat) f;

            f
                = setOutputFormat(
                        new AudioFormat(
                                af.getEncoding(),
                                af.getSampleRate(),
                                af.getSampleSizeInBits(),
                                af.getChannels(),
                                af.getEndian(),
                                af.getSigned(),
                                af.getFrameSizeInBits(),
                                af.getFrameRate(),
                                af.getDataType())
                        {
                            private static final long serialVersionUID = 0L;

                            @Override
                            public long computeDuration(long length)
                            {
                                return JavaEncoder.this.duration;
                            }
                        });
        }
        return f;
    }

    @Override
    protected void discardOutputBuffer(Buffer outputBuffer)
    {
        super.discardOutputBuffer(outputBuffer);

        outFrameCount = 0;
    }

    /*
     * Implements AbstractCodecExt#doClose().
     */
    @Override
    protected void doClose()
    {
        prevIn = null;
        prevInLength = 0;

        sp16 = null;
        serial = null;
        coder = null;
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
     * @see AbstractCodec2#doOpen()
     */
    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
        prevIn = new byte[INPUT_FRAME_SIZE_IN_BYTES];
        prevInLength = 0;

        sp16 = new short[L_FRAME];
        serial = new short[SERIAL_SIZE];
        coder = new Coder();

        outFrameCount = 0;
    }

    /*
     * Implements AbstractCodecExt#doProcess(Buffer, Buffer).
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        byte[] in = (byte[]) inBuffer.getData();

        int inLength = inBuffer.getLength();
        int inOffset = inBuffer.getOffset();

        if ((prevInLength + inLength) < INPUT_FRAME_SIZE_IN_BYTES)
        {
            System.arraycopy(
                    in, inOffset,
                    prevIn, prevInLength,
                    inLength);
            prevInLength += inLength;
            return BUFFER_PROCESSED_OK | OUTPUT_BUFFER_NOT_FILLED;
        }

        int readShorts = 0;

        if (prevInLength > 0)
        {
            readShorts += readShorts(prevIn, 0, sp16, 0, prevInLength / 2);
            prevInLength = 0;
        }
        readShorts
            = readShorts(
                    in, inOffset,
                    sp16, readShorts, sp16.length - readShorts);

        int readBytes = 2 * readShorts;

        inLength -= readBytes;
        inBuffer.setLength(inLength);
        inOffset += readBytes;
        inBuffer.setOffset(inOffset);

        coder.process(sp16, serial);

        byte[] output
            = validateByteArraySize(
                    outBuffer,
                    outBuffer.getOffset()
                        + OUTPUT_FRAMES_COUNT * OUTPUT_FRAME_SIZE_IN_BYTES,
                    true);

        packetize(
                serial,
                output,
                outBuffer.getOffset()
                    + OUTPUT_FRAME_SIZE_IN_BYTES * outFrameCount);
        outBuffer.setLength(outBuffer.getLength() + OUTPUT_FRAME_SIZE_IN_BYTES);

        outBuffer.setFormat(outputFormat);

        int ret = BUFFER_PROCESSED_OK;

        if (outFrameCount == (OUTPUT_FRAMES_COUNT - 1))
            outFrameCount = 0;
        else
        {
            outFrameCount++;
            ret |= OUTPUT_BUFFER_NOT_FILLED;
        }
        if (inLength > 0)
            ret |= INPUT_BUFFER_NOT_CONSUMED;

        if(ret == BUFFER_PROCESSED_OK)
        {
            updateOutput(
                    outBuffer,
                    getOutputFormat(),
                    outBuffer.getLength(),
                    outBuffer.getOffset());
            outBuffer.setDuration(duration);
        }
        return ret;
    }

    private void packetize(short[] serial, byte[] outFrame, int outFrameOffset)
    {
        Arrays.fill(
                outFrame, outFrameOffset, outFrameOffset + L_FRAME / 8,
                (byte) 0);

        for (int s = 0; s < L_FRAME; s++)
        {
            if (BIT_1 == serial[2 + s])
            {
                int o = outFrameOffset + s / 8;
                int out = outFrame[o];

                out |= 1 << (7 - (s % 8));
                outFrame[o] = (byte) (out & 0xFF);
            }
        }
    }

    private static int readShorts(
            byte[] in, int inOffset,
            short[] out, int outOffset, int outLength)
    {
        for (int o=outOffset, i=inOffset; o<outLength; o++, i+=2)
            out[o] = ArrayIOUtils.readShort(in, i);
        return outLength;
    }

    /**
     * Sets the additional attributes to <tt>attributes</tt>
     *
     * @param attributes The additional attributes to set
     */
    @Override
    public void setAdvancedAttributes(Map<String, String> attributes)
    {
        try
        {
            String s = attributes.get("ptime");

            if ((s != null) && (s.length() != 0))
            {
                int ptime = Integer.parseInt(s);

                OUTPUT_FRAMES_COUNT = ptime / OUTPUT_FRAME_SIZE_IN_BYTES;
                duration =
                    OUTPUT_FRAME_SIZE_IN_BYTES * OUTPUT_FRAMES_COUNT * 1000000;
            }
        }
        catch (Exception e)
        {
            // Ignore
        }
    }

    /**
     * Not used.
     * @return null as it is not used.
     */
    @Override
    public Component getControlComponent()
    {
        return null;
    }
}
