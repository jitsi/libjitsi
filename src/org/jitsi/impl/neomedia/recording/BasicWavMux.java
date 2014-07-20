/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
