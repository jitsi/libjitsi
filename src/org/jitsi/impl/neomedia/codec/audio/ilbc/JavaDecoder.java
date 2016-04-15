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

import java.util.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements an iLBC decoder and RTP depacketizer as a {@link Codec}.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class JavaDecoder
    extends AbstractCodec2
{

    /**
     * The <tt>ilbc_decoder</tt> adapted to <tt>Codec</tt> by this instance.
     */
    private ilbc_decoder dec;

    /**
     * The input length in bytes with which {@link #dec} has been initialized.
     */
    private int inputLength;

    /**
     * List of offsets for a "more than one" iLBC frame per RTP packet.
     */
    private List<Integer> offsets = new ArrayList<Integer>();

    /**
     * Initializes a new iLBC <tt>JavaDecoder</tt> instance.
     */
    public JavaDecoder()
    {
        super(
                "iLBC Decoder",
                AudioFormat.class,
                new Format[] { new AudioFormat(AudioFormat.LINEAR) });

        inputFormats
            = new Format[]
                    {
                        new AudioFormat(
                                Constants.ILBC_RTP,
                                8000,
                                16,
                                1,
                                Format.NOT_SPECIFIED /* endian */,
                                Format.NOT_SPECIFIED /* signed */)
                    };

        addControl(
                new com.sun.media.controls.SilenceSuppressionAdapter(
                        this,
                        false,
                        false));
    }

    /**
     * Implements {@link AbstractCodecExt#doClose()}.
     *
     * @see AbstractCodecExt#doClose()
     */
    @Override
    protected void doClose()
    {
        dec = null;
        inputLength = 0;
    }

    /**
     * Implements {@link AbstractCodecExt#doOpen()}.
     *
     * @see AbstractCodecExt#doOpen()
     */
    @Override
    protected void doOpen()
    {
    }

    /**
     * Implements {@link AbstractCodecExt#doProcess(Buffer, Buffer)}.
     *
     * @param inputBuffer
     * @param outputBuffer
     * @return
     * @see AbstractCodecExt#doProcess(Buffer, Buffer)
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        byte[] input = (byte[]) inputBuffer.getData();
        int inputLength = inputBuffer.getLength();

        if(offsets.size() == 0 &&
            ((inputLength > ilbc_constants.NO_OF_BYTES_20MS &&
                inputLength != ilbc_constants.NO_OF_BYTES_30MS) ||
            inputLength > ilbc_constants.NO_OF_BYTES_30MS))
        {
            int nb = 0;
            int len = 0;

            if((inputLength % ilbc_constants.NO_OF_BYTES_20MS) == 0)
            {
                nb = (inputLength % ilbc_constants.NO_OF_BYTES_20MS);
                len = ilbc_constants.NO_OF_BYTES_20MS;
            }
            else if((inputLength % ilbc_constants.NO_OF_BYTES_30MS) == 0)
            {
                nb = (inputLength % ilbc_constants.NO_OF_BYTES_30MS);
                len = ilbc_constants.NO_OF_BYTES_30MS;
            }

            if (this.inputLength != len)
                initDec(len);

            for(int i = 0 ; i < nb ; i++)
            {
                offsets.add((Integer)(inputLength + (i * len)));
            }
        }
        else if (this.inputLength != inputLength)
            initDec(inputLength);

        int outputLength = dec.ULP_inst.blockl * 2;
        byte[] output
            = validateByteArraySize(outputBuffer, outputLength, false);
        int outputOffset = 0;

        int offsetToAdd = 0;

        if(offsets.size() > 0)
            offsetToAdd = offsets.remove(0).intValue();

        dec.decode(
                output, outputOffset,
                input, inputBuffer.getOffset() + offsetToAdd,
                (short) 1);

        updateOutput(
                outputBuffer,
                getOutputFormat(), outputLength, outputOffset);
        int flags = BUFFER_PROCESSED_OK;

        if(offsets.size() > 0)
            flags |= INPUT_BUFFER_NOT_CONSUMED;

        return flags;
    }

    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        AudioFormat inputAudioFormat = (AudioFormat) inputFormat;

        return
            new AudioFormat[]
                    {
                        new AudioFormat(
                                AudioFormat.LINEAR,
                                inputAudioFormat.getSampleRate(),
                                16,
                                1,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED)
                    };
    }

    /**
     * Initializes {@link #dec} so that it processes a specific number of bytes
     * as input.
     *
     * @param inputLength the number of bytes of input to be processed by
     * {@link #dec}
     */
    private void initDec(int inputLength)
    {
        int mode;

        switch (inputLength)
        {
        case ilbc_constants.NO_OF_BYTES_20MS:
            mode = 20;
            break;
        case ilbc_constants.NO_OF_BYTES_30MS:
            mode = 30;
            break;
        default:
            throw new IllegalArgumentException("inputLength");
        }

        dec = new ilbc_decoder(mode, 1);
        this.inputLength = inputLength;
    }
}
