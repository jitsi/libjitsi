/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;

/**
 * Encapsulate the concept of packet transformation. Given an array of packets,
 * <tt>PacketTransformer</tt> can either "transform" each one of them, or
 * "reverse transform" (e.g. restore) each one of them.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Boris Grozev
 */
public interface PacketTransformer
{
    /**
     * Closes this <tt>PacketTransformer</tt> i.e. releases the resources
     * allocated by it and prepares it for garbage collection.
     */
    public void close();

    /**
     * Reverse-transforms each packet in an array of packets. Null values
     * must be ignored.
     *
     * @param pkts the transformed packets to be restored.
     * @return the restored packets.
     */
    public RawPacket[] reverseTransform(RawPacket[] pkts);

    /**
     * Transforms each packet in an array of packets. Null values must be
     * ignored.
     *
     * @param pkts the packets to be transformed
     * @return the transformed packets
     */
    public RawPacket[] transform(RawPacket[] pkts);
}
