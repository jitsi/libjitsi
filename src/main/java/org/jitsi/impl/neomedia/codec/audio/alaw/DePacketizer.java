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
package org.jitsi.impl.neomedia.codec.audio.alaw;

import javax.media.*;
import javax.media.format.*;

import com.sun.media.codec.audio.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * DePacketizer for ALAW codec
 * @author Damian Minkov
 */
public class DePacketizer
    extends AudioCodec
{
    /**
     * Creates DePacketizer
     */
    public DePacketizer()
    {
        inputFormats = new Format[]{new AudioFormat(Constants.ALAW_RTP)};
    }

    /**
     * Returns the name of the DePacketizer
     * @return String
     */
    public String getName()
    {
        return "ALAW DePacketizer";
    }

    /**
     * Returns the supported output formats
     * @param in Format
     * @return Format[]
     */
    public Format[] getSupportedOutputFormats(Format in)
    {

        if (in == null)
        {
            return new Format[]{new AudioFormat(AudioFormat.ALAW)};
        }

        if (matches(in, inputFormats) == null)
        {
            return new Format[1];
        }

        if (! (in instanceof AudioFormat))
        {
            return new Format[]{new AudioFormat(AudioFormat.ALAW)};
        }

        AudioFormat af = (AudioFormat) in;
        return new Format[]
        {
            new AudioFormat(
                AudioFormat.ALAW,
                af.getSampleRate(),
                af.getSampleSizeInBits(),
                af.getChannels())
        };
    }

    /**
     * Initializes the codec.
     */
    @Override
    public void open()
    {}

    /**
     * Clean up
     */
    @Override
    public void close()
    {}

    /**
     * decode the buffer
     * @param inputBuffer Buffer
     * @param outputBuffer Buffer
     * @return int
     */
    public int process(Buffer inputBuffer, Buffer outputBuffer)
    {

        if (!checkInputBuffer(inputBuffer))
        {
            return BUFFER_PROCESSED_FAILED;
        }

        if (isEOM(inputBuffer))
        {
            propagateEOM(outputBuffer);
            return BUFFER_PROCESSED_OK;
        }

        Object outData = outputBuffer.getData();
        outputBuffer.setData(inputBuffer.getData());
        inputBuffer.setData(outData);
        outputBuffer.setLength(inputBuffer.getLength());
        outputBuffer.setFormat(outputFormat);
        outputBuffer.setOffset(inputBuffer.getOffset());
        return BUFFER_PROCESSED_OK;
    }
}
