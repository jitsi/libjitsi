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
package org.jitsi.impl.neomedia.codec.audio.gsm;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;

/**
 *
 * DePacketizer for GSM/RTP.  Doesn't have to do much, just copies input
 * to output.  Uses buffer-swapping observed in debugging
 * and seen in other open-source DePacketizer implementations.
 * @author Martin Harvan
 * @author Damian Minkov
 */
public class DePacketizer
    extends AbstractDePacketizer
{
    @Override
    public String getName()
    {
        return "GSM DePacketizer";
    }

    /**
     * Constructs a new <tt>DePacketizer</tt>.
     */
    public DePacketizer()
    {
        super();
        this.inputFormats = new Format[] {
            new AudioFormat(
                    AudioFormat.GSM_RTP,
                    8000,
                    8,
                    1,
                    Format.NOT_SPECIFIED,
                    AudioFormat.SIGNED,
                    264,
                    Format.NOT_SPECIFIED,
                    Format.byteArray)};
    }

    // TODO: move to base class?
    protected Format[] outputFormats = new Format[] {
        new AudioFormat(
            AudioFormat.GSM,
            8000,
            8,
            1,
            -1,
            AudioFormat.SIGNED,
            264,
            -1.0,
            Format.byteArray)};

    @Override
    public Format[] getSupportedOutputFormats(Format input)
    {
        if (input == null)
            return outputFormats;
        else
        {
            if (!(input instanceof AudioFormat))
            {
                return new Format[] {null};
            }
            final AudioFormat inputCast = (AudioFormat) input;
            if (!inputCast.getEncoding().equals(AudioFormat.GSM_RTP))
            {
                return new Format[] {null};
            }
            final AudioFormat result =
                    new AudioFormat(
                            AudioFormat.GSM,
                            inputCast.getSampleRate(),
                            inputCast.getSampleSizeInBits(),
                            inputCast.getChannels(),
                            inputCast.getEndian(),
                            inputCast.getSigned(),
                            inputCast.getFrameSizeInBits(),
                            inputCast.getFrameRate(),
                            inputCast.getDataType());

            return new Format[] {result};
        }
    }

    @Override
    public void open()
    {
    }

    @Override
    public void close()
    {
    }

    @Override
    public Format setInputFormat(Format f)
    {
        return super.setInputFormat(f);
    }

    @Override
    public Format setOutputFormat(Format f)
    {
        return super.setOutputFormat(f);
    }


}
