/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform;

import java.net.*;

import org.jitsi.impl.neomedia.*;

/**
 * Extends <tt>RTPConnectorInputStream</tt> with transform logic.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lubomir Marinov
 * @author Boris Grozev
 */
public abstract class TransformInputStream
        extends RTPConnectorInputStream
{
    /**
     * The user defined <tt>PacketTransformer</tt> which is used to reverse
     * transform packets.
     */
    private PacketTransformer transformer;

    /**
     * Creates a new <tt>RawPacket</tt> array from a specific
     * <tt>DatagramPacket</tt> in order to have this instance receive its
     * packet data through its {@link #read(byte[], int, int)} method.
     * Reverse-transforms the received packet.
     *
     * @param datagramPacket the <tt>DatagramPacket</tt> containing the packet
     * data
     * @return a new <tt>RawPacket</tt> array containing the packet data of the
     * specified <tt>DatagramPacket</tt> or possibly its modification;
     * <tt>null</tt> to ignore the packet data of the specified
     * <tt>DatagramPacket</tt> and not make it available to this instance
     * through its {@link #read(byte[], int, int)} method
     * @see RTPConnectorInputStream#createRawPacket(DatagramPacket)
     */
    @Override
    protected RawPacket[] createRawPacket(DatagramPacket datagramPacket)
    {
        PacketTransformer transformer = getTransformer();
        RawPacket pkts[] = super.createRawPacket(datagramPacket);

        /* Don't try to transform invalid packets (for ex. empty) */
        for (int i=0; i<pkts.length; i++)
            if(pkts[i] != null && pkts[i].isInvalid())
                pkts[i] = null; //null elements are just ignored

        return
                (transformer == null)
                ? pkts
                : transformer.reverseTransform(pkts);
    }

    /**
     * Gets the <tt>PacketTransformer</tt> which is used to reverse-transform
     * packets.
     *
     * @return the <tt>PacketTransformer</tt> which is used to reverse-transform
     * packets
     */
    public PacketTransformer getTransformer()
    {
        return transformer;
    }

    /**
     * Sets the <tt>PacketTransformer</tt> which is to be used to
     * reverse-transform packets. Set to <tt>null</tt> to disable
     * transformation.
     *
     * @param transformer the <tt>PacketTransformer</tt> which is to be used to
     * reverse-transform packets.
     */
    public void setTransformer(PacketTransformer transformer)
    {
        this.transformer = transformer;
    }
}
