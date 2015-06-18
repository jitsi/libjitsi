package org.jitsi.service.neomedia.rtp;

import net.sf.fmj.media.rtp.*;

/**
 * Created by gp on 6/11/15.
 */
public interface RTCPPacketTransformer
{
    RTCPCompoundPacket transform(RTCPCompoundPacket rtcpCompoundPacket);

    RTCPCompoundPacket reverseTransform(RTCPCompoundPacket rtcpCompoundPacket);

    /**
     * Closes this <tt>Transformer</tt> i.e. releases the resources
     * allocated by it and prepares it for garbage collection.
     */
    void close();
}
