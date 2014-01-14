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
 * Extends <tt>RTPConnectorOutputStream</tt> with transform logic for TCP.
 *
 * In this implementation, TCP socket is used to send the data out. When a
 * normal RTP/RTCP packet is passed down from RTPManager, we first transform
 * the packet using user define PacketTransformer and then send it out through
 * network to all the stream targets.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lubomir Marinov
 */
public class TransformTCPOutputStream
    extends RTPConnectorTCPOutputStream
{
    /**
     * The <tt>PacketTransformer</tt> used to transform RTP/RTCP packets.
     */
    private PacketTransformer transformer;

    /**
     * Initializes a new <tt>TransformOutputStream</tt> which is to send packet
     * data out through a specific TCP socket.
     *
     * @param socket the TCP socket used to send packet data out
     */
    public TransformTCPOutputStream(Socket socket)
    {
        super(socket);
    }

    /**
     * Creates a new array of <tt>RawPacket</tt> from a specific <tt>byte[]</tt>
     * buffer in order to have this instance send its packet data through its
     * {@link #write(byte[], int, int)} method. Transforms the array of packets
     * using a <tt>PacketTransformer</tt>.
     *
     * @param buffer the packet data to be sent to the targets of this instance
     * @param offset the offset of the packet data in <tt>buffer</tt>
     * @param length the length of the packet data in <tt>buffer</tt>
     * @return an array of <tt>RawPacket</tt> containing the packet data of the
     * specified <tt>byte[]</tt> buffer or possibly its modification;
     */
    @Override
    protected RawPacket[] createRawPacket(byte[] buffer, int offset, int length)
    {
        RawPacket[] pkts = super.createRawPacket(buffer, offset, length);
        PacketTransformer transformer = getTransformer();

        if (transformer != null)
        {
            pkts = transformer.transform(pkts);

            /*
             * This is for the case when the ZRTP engine stops the media stream
             * allowing only ZRTP packets.
             */
            // XXX: Do we need to throw here if all elements of pkts are null?
            // They should be ignored in write().
            // TODO Comment in order to use the GoClear feature.
            if ((pkts == null) && (targets.size() > 0))
                throw new NullPointerException("pkts");
        }
        return pkts;
    }

    /**
     * Gets the <tt>PacketTransformer</tt> which is used to transform packets.
     *
     * @return the <tt>PacketTransformer</tt> which is used to transform packets
     */
    public PacketTransformer getTransformer()
    {
        return transformer;
    }

    /**
     * Sets the <tt>PacketTransformer</tt> which is to be used to transform
     * packets.
     *
     * @param transformer the <tt>PacketTransformer</tt> which is to be used to
     * transform packets
     */
    public void setTransformer(PacketTransformer transformer)
    {
        this.transformer = transformer;
    }
}
