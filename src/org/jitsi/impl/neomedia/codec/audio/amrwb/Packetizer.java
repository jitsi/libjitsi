/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.amrwb;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements an RTP packetizer for Adaptive Multi-Rate Wideband (AMR-WB).
 *
 * @author Lyubomir Marinov
 */
public class Packetizer
    extends AbstractCodec2
{
    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>Packetizer</tt> instances.
     */
    static final AudioFormat[] SUPPORTED_INPUT_FORMATS
        = { new AudioFormat(Constants.AMR_WB) };

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>Packetizer</tt> instances.
     */
    static final AudioFormat[] SUPPORTED_OUTPUT_FORMATS
        = { new AudioFormat(Constants.AMR_WB_RTP) };

    /**
     * Initializes a new <tt>Packetizer</tt> instance.
     */
    public Packetizer()
    {
        super(
                "AMR-WB RTP Packetizer",
                AudioFormat.class,
                SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf)
    {
        // TODO Auto-generated method stub
        return 0;
    }
}
