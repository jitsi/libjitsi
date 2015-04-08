/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.rtcp;

import net.sf.fmj.media.rtp.*;
import org.jitsi.service.neomedia.*;

/**
 * Created by gp on 7/2/14.
 */
public class NullRTCPPacketTransformer implements Transformer<RTCPCompoundPacket>
{
    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPCompoundPacket reverseTransform(RTCPCompoundPacket inPacket)
    {
        // This strategy does not perform any modifications to incoming RTCP
        // traffic.
        return inPacket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        // nothing to do here
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPCompoundPacket transform(RTCPCompoundPacket inPacket)
    {
        return inPacket;
    }
}
