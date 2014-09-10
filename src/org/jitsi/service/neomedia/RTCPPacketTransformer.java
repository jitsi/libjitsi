/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import net.sf.fmj.media.rtp.*;

/**
 * @author George Politis
 */
public interface RTCPPacketTransformer
{
    /**
     * Transforms an incoming RTCP packet.
     *
     * @param inPacket the incoming RTCP packet to transform.
     * @return the transformed RTCP packet. If no transformations were made,
     * the method returns the input packet. If the packet is to be dropped,
     * the method returns null.
     */
    RTCPCompoundPacket transformRTCPPacket(RTCPCompoundPacket inPacket);
}
