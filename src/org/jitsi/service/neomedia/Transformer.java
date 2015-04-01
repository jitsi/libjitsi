/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

/**
 * @author George Politis
 */
public interface Transformer<T>
{
    /**
     * Transforms an incoming RTCP packet.
     *
     * @param inPacket the incoming RTCP packet to transform.
     * @return the transformed RTCP packet. If no transformations were made,
     * the method returns the input packet. If the packet is to be dropped,
     * the method returns null.
     */
    T transform(T inPacket);
}
