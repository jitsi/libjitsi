/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;

/**
 * Extends the <tt>PacketTransformer</tt> interface with methods which allow
 * the transformation of a single packet into a single packet.
 *
 * Eases the implementation of <tt>PacketTransformer<tt>-s which transform each
 * packet into a single transformed packet (as opposed to an array of possibly
 * more than one packet).
 *
 * @author Boris Grozev
 */
public abstract class SinglePacketTransformer
    implements PacketTransformer
{
    /**
     * Transforms a specific packet.
     *
     * @param pkt the packet to be transformed.
     * @return the transformed packet.
     */
    public abstract RawPacket transform(RawPacket pkt);

    /**
     * Reverse-transforms a specific packet.
     *
     * @param pkt the transformed packet to be restored.
     * @return the restored packet.
     */
    public abstract RawPacket reverseTransform(RawPacket pkt);

    /**
     * {@inheritDoc}
     *
     * Transforms an array of packets by calling <tt>transform(RawPacket)</tt>
     * on each one.
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts)
    {
        if (pkts != null)
        {
            for (int i = 0; i < pkts.length; i++)
            {
                RawPacket pkt = pkts[i];

                if (pkt != null)
                    pkts[i] = transform(pkt);
            }
        }

        return pkts;
    }

    /**
     * {@inheritDoc}
     * Reverse-transforms an array of packets by calling
     * <tt>reverseTransform(RawPacket)</tt> on each one.
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        if (pkts != null)
        {
            for (int i = 0; i < pkts.length; i++)
            {
                RawPacket pkt = pkts[i];

                if (pkt != null)
                    pkts[i] = reverseTransform(pkt);
            }
        }

        return pkts;
    }
}
