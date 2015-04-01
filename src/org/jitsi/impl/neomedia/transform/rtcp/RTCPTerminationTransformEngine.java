/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.rtcp;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Uses the <tt>Transformer</tt> of the <tt>RTCPTerminationStrategy</tt> of the
 * <tt>RTPTranslator</tt> of the associated <tt>MediaStream</tt> to transform
 * incoming RTCP packets. Advanced RTCP termination strategies can drop incoming
 * RTCP packets.
 *
 * @author George Politis
 */
public class RTCPTerminationTransformEngine
    extends SinglePacketTransformer
    implements TransformEngine
{
    private static final Logger logger
        = Logger.getLogger(RTCPTerminationTransformEngine.class);

    /**
     * The associated <tt>MediaStream</tt> of this
     * <tt><RTCPTerminationTransformEngine/tt>.
     */
    private final MediaStream mediaStream;

    private final RTCPPacketParserEx parser;

    /**
     * Ctor.
     *
     * @param mediaStream
     */
    public RTCPTerminationTransformEngine(MediaStream mediaStream)
    {
        this.mediaStream = mediaStream;
        this.parser = new RTCPPacketParserEx();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        // Nothing to do here.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        // RTP packets need not be transformed.
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        // Do you think this is a game?
        if (pkt == null)
            return pkt;

        // Get the RTCP termination strategy from the assiciated media stream
        // translator.
        RTPTranslator rtpTranslator = mediaStream.getRTPTranslator();
        if (rtpTranslator == null)
            return pkt;

        RTCPTerminationStrategy rtcpTerminationStrategy
                = rtpTranslator.getRTCPTerminationStrategy();

        if (rtcpTerminationStrategy == null)
            return pkt;

        Transformer<RTCPCompoundPacket> rtcpPacketTransformer
                = rtcpTerminationStrategy.getRTCPCompoundPacketTransformer();

        if (rtcpPacketTransformer == null)
            return pkt;

        // Parse the RTCP packet.
        RTCPCompoundPacket inRTCPPacket;
        try
        {
            inRTCPPacket = (RTCPCompoundPacket) parser.parse(
                    pkt.getBuffer(),
                    pkt.getOffset(),
                    pkt.getLength());
        }
        catch (BadFormatException e)
        {
            // TODO(gp) decide what to do with malformed packets!
            logger.error("Could not parse RTCP packet.", e);
            return pkt;
        }

        logger.debug("Parsed : " + inRTCPPacket.toString());

        // Transform the RTCP packet.
        RTCPCompoundPacket outRTCPPacket = rtcpPacketTransformer
                .transform(inRTCPPacket);

        // If the outRTCPPacket is the same object as the inRTCPPacket,
        // return the pkt.
        if (inRTCPPacket == outRTCPPacket)
        {
            logger.debug("Did not perform any modifications to the received " +
                    "packet.");
            return pkt;
        }

        if (outRTCPPacket == null)
        {
            logger.debug("The RTCP termination strategy dropped the received " +
                    "packet from the transform engine chain.");
        }
        else
        {
            logger.debug("Transformed the received packet to : " +
                    outRTCPPacket.toString());
        }


        if (outRTCPPacket == null
                || outRTCPPacket.packets == null
                || outRTCPPacket.packets.length == 0)
            return null;

        // Assemble the RTCP packet.
        int len = outRTCPPacket.calcLength();
        outRTCPPacket.assemble(len, false);
        byte[] buf = outRTCPPacket.data;

        RawPacket pktOut = new RawPacket();

        pktOut.setBuffer(buf);
        pktOut.setLength(buf.length);
        pktOut.setOffset(0);

        return pktOut;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        // Outgoing RTCP packets need not be transformed.
        return pkt;
    }
}
