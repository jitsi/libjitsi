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
package org.jitsi.impl.neomedia.codec.audio.ilbc;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;

import java.awt.*;
import java.util.*;

/**
 * Implements an iLBC encoder and RTP packetizer as a {@link Codec}.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class JavaEncoder
    extends AbstractCodec2
    implements FormatParametersAwareCodec
{
    /**
     * The duration an output <tt>Buffer</tt> produced by this <tt>Codec</tt>.
     */
    private int duration = 0;

    /**
     * The <tt>ilbc_encoder</tt> adapted to <tt>Codec</tt> by this instance.
     */
    private ilbc_encoder enc = null;

    /**
     * The input length in bytes with which {@link #enc} has been initialized.
     */
    private int inLen;

    /**
     * The output length in bytes with which {@link #enc} has been initialized.
     */
    private int outLen;

    /**
     * The input from previous calls to {@link #doProcess(Buffer, Buffer)} which
     * has not been consumed yet.
     */
    private byte[] prevIn;

    /**
     * The number of bytes in {@link #prevIn} which have not been consumed yet.
     */
    private int prevInLen;

    /**
     * Initializes a new iLBC <tt>JavaEncoder</tt> instance.
     */
    public JavaEncoder()
    {
        super(
                "iLBC Encoder",
                AudioFormat.class,
                new Format[]
                        {
                            new AudioFormat(
                                    Constants.ILBC_RTP,
                                    8000,
                                    16,
                                    1,
                                    AudioFormat.LITTLE_ENDIAN,
                                    AudioFormat.SIGNED)
                        });

        inputFormats
            = new Format[]
                    {
                        new AudioFormat(
                                AudioFormat.LINEAR,
                                8000,
                                16,
                                1,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED)
                    };

        addControl(
                new com.sun.media.controls.SilenceSuppressionAdapter(
                        this,
                        false,
                        false));

        addControl(this);
    }

    /**
     * Implements {@link AbstractCodec2#doClose()}.
     *
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected void doClose()
    {
        enc = null;
        outLen = 0;
        inLen = 0;
        prevIn = null;
        prevInLen = 0;
        duration = 0;
    }

    /**
     * Implements {@link AbstractCodec2#doOpen()}.
     *
     * @see AbstractCodec2#doOpen()
     */
    @Override
    protected void doOpen()
    {
        // if not already initialised, use the default value (30).
        if(enc == null)
            initEncoder(Constants.ILBC_MODE);
    }

    /**
     * Implements {@link AbstractCodec2#doProcess(Buffer, Buffer)}.
     *
     * @param inBuffer the input buffer
     * @param outBuffer the output buffer
     * @return the status of the processing, whether buffer is consumed/filled..
     * @see AbstractCodec2#doProcess(Buffer, Buffer)
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        int inLen = inBuffer.getLength();
        byte[] in = (byte[]) inBuffer.getData();
        int inOff = inBuffer.getOffset();

        if ((prevInLen != 0) || (inLen < this.inLen))
        {
            int bytesToCopy = this.inLen - prevInLen;

            if (bytesToCopy > inLen)
                bytesToCopy = inLen;
            System.arraycopy(in, inOff, prevIn, prevInLen, bytesToCopy);
            prevInLen += bytesToCopy;

            inBuffer.setLength(inLen - bytesToCopy);
            inBuffer.setOffset(inOff + bytesToCopy);

            inLen = prevInLen;
            in = prevIn;
            inOff = 0;
        }
        else
        {
            inBuffer.setLength(inLen - this.inLen);
            inBuffer.setOffset(inOff + this.inLen);
        }

        int ret;

        if (inLen >= this.inLen)
        {
            /*
             * If we are about to encode from prevInput, we already have
             * prevInputLength taken into consideration by using prevInput in
             * the first place and we have to make sure that we will not use the
             * same prevInput more than once.
             */
            prevInLen = 0;

            int outOff = 0;
            byte[] out
                = validateByteArraySize(outBuffer, outOff + outLen, true);

            enc.encode(out, outOff, in, inOff);

            updateOutput(outBuffer, getOutputFormat(), outLen, outOff);
            outBuffer.setDuration(duration);
            ret = BUFFER_PROCESSED_OK;
        }
        else
        {
            ret = OUTPUT_BUFFER_NOT_FILLED;
        }

        if (inBuffer.getLength() > 0)
            ret |= INPUT_BUFFER_NOT_CONSUMED;
        return ret;
    }

    /**
     * Implements {@link javax.media.Control#getControlComponent()}.
     */
    @Override
    public Component getControlComponent()
    {
        return null;
    }

    /**
     * Get the output format.
     *
     * @return output format
     * @see net.sf.fmj.media.AbstractCodec#getOutputFormat()
     */
    @Override
    @SuppressWarnings("serial")
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
                                    @Override
                                    public long computeDuration(long length)
                                    {
                                        return JavaEncoder.this.duration;
                                    }
                                });
        }
        return f;
    }

    /**
     * Init encoder with specified mode.
     * @param mode the mode to use.
     */
    private void initEncoder(int mode)
    {
        enc = new ilbc_encoder(mode);

        switch (mode)
        {
        case 20:
            outLen = ilbc_constants.NO_OF_BYTES_20MS;
            break;
        case 30:
            outLen = ilbc_constants.NO_OF_BYTES_30MS;
            break;
        default:
            throw new IllegalStateException("mode");
        }
        /* mode is 20 or 30 ms, duration must be in nanoseconds */
        duration = mode * 1000000;
        inLen = enc.ULP_inst.blockl * 2;
        prevIn = new byte[inLen];
        prevInLen = 0;
    }

    /**
     * Sets the format parameters to <tt>fmtps</tt>
     *
     * @param fmtps The format parameters to set
     */
    @Override
    public void setFormatParameters(Map<String, String> fmtps)
    {
        String modeStr = fmtps.get("mode");

        if(modeStr != null)
        {
            try
            {
                int mode = Integer.parseInt(modeStr);

                // supports only mode 20 or 30
                if(mode == 20 || mode == 30)
                    initEncoder(mode);
            }
            catch(Throwable t)
            {
            }
        }
    }
}
