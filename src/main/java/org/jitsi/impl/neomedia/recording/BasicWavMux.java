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
package org.jitsi.impl.neomedia.recording;

import net.sf.fmj.media.codec.*;
import net.sf.fmj.media.multiplexer.*;
import net.sf.fmj.media.renderer.audio.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.protocol.*;

/**
 * Implements a multiplexer for WAV files based on FMJ's <tt>BasicMux</tt>.
 *
 * @author Boris Grozev
 */
public class BasicWavMux
    extends BasicMux
{
    /**
     * The input formats supported by this <tt>BasicWavMux</tt>.
     */
    public static Format SUPPORTED_INPUT_FORMAT
        = new AudioFormat(AudioFormat.LINEAR);

    /**
     * Initializes a <tt>BasicWavMux</tt> instance.
     */
    public BasicWavMux()
    {
        supportedInputs = new Format[] { SUPPORTED_INPUT_FORMAT };
        supportedOutputs = new ContentDescriptor[1];
        supportedOutputs[0] = new FileTypeDescriptor(FileTypeDescriptor.WAVE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName()
    {
        return "libjitsi wav mux";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Format setInputFormat(Format format, int trackID)
    {
        if ( !(format instanceof AudioFormat))
            return null;

        if ( !AudioFormat.LINEAR.equals(format.getEncoding()))
            return null;

        return inputs[0] = format;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeHeader()
    {
        final javax.sound.sampled.AudioFormat javaSoundAudioFormat
            = JavaSoundUtils.convertFormat((AudioFormat) inputs[0]);
        byte[] header = JavaSoundCodec.createWavHeader(javaSoundAudioFormat);
        if (header != null)
            write(header, 0, header.length);
    }


}
