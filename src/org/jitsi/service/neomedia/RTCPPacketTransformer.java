/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import net.sf.fmj.media.rtp.*;

/**
 * Created by gp on 7/2/14.
 */
public interface RTCPPacketTransformer
{
    /**
     *
     * @param inPacket
     * @return
     */
    RTCPCompoundPacket transformRTCPPacket(RTCPCompoundPacket inPacket);
}
