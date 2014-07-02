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
public class NullRTCPPacketTransformer implements RTCPPacketTransformer
{
    /**
     *
     * @param inPacket
     * @return
     */
    @Override
    public RTCPCompoundPacket transformRTCPPacket(RTCPCompoundPacket inPacket)
    {
        // This strategy does not perform any modifications to incoming RTCP
        // traffic.
        return inPacket;
    }
}
