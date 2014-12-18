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

/**
 * Implements an RTP depacketizer for Adaptive Multi-Rate Wideband (AMR-WB).
 *
 * @author Lyubomir Marinov
 */
public class DePacketizer
    extends AbstractCodec2
{
    /**
     * Initializes a new <tt>DePacketizer</tt> instance.
     */
    public DePacketizer()
    {
        super(
                "AMR-WB RTP DePacketizer",
                AudioFormat.class,
                Packetizer.SUPPORTED_INPUT_FORMATS);

        inputFormats = Packetizer.SUPPORTED_OUTPUT_FORMATS;
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
