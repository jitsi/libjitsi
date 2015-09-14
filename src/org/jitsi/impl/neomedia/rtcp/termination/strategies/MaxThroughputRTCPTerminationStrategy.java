/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;

import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.function.*;

/**
 * Maximizes endpoint throughput. It does that by sending REMB messages with the
 * largest possible exp and mantissa values. This strategy is only meant to be
 * used in tests.
 *
 * @author George Politis
 */
public class MaxThroughputRTCPTerminationStrategy
    implements RTCPTerminationStrategy
{
    /**
     * The RTCP <tt>PacketTransformer</tt> of this
     * <tt>MaxThorughputRTCPTerminationStrategy</tt>.
     */
    private final PacketTransformer rtcpTransformer
        = new SinglePacketTransformer()
    {
        /**
         *
         */
        public static final int MAX_MANTISSA = 262143;

        /**
         *
         */
        public static final int MAX_EXP = 63;

        /**
         * The parser that parses <tt>RawPacket</tt>s to
         * <tt>RTCPCompoundPacket</tt>s.
         */
        private final RTCPPacketParserEx parser = new RTCPPacketParserEx();

        /**
         * The generator that generates <tt>RawPacket</tt>s from
         * <tt>RTCPCompoundPacket</tt>s.
         */
        private final RTCPGenerator generator = new RTCPGenerator();

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            if (pkt == null)
            {
                return null;
            }

            RTCPCompoundPacket inPacket = null;
            try
            {
                inPacket = (RTCPCompoundPacket) parser.parse(
                    pkt.getBuffer(),
                    pkt.getOffset(),
                    pkt.getLength());
            }
            catch (BadFormatException e)
            {
                return null;
            }

            if (inPacket == null
                || inPacket.packets == null || inPacket.packets.length == 0)
            {
                return pkt;
            }

            for (RTCPPacket p : inPacket.packets)
            {
                switch (p.type)
                {
                    case RTCPFBPacket.PSFB:
                        RTCPFBPacket psfb = (RTCPFBPacket) p;
                        switch (psfb.fmt)
                        {
                            case RTCPREMBPacket.FMT:
                                RTCPREMBPacket remb = (RTCPREMBPacket)p;

                                remb.mantissa = MAX_MANTISSA;
                                remb.exp = MAX_EXP;
                                break;
                        }
                        break;
                }
            }

            return generator.apply(inPacket);
        }

        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            return pkt;
        }
    };

    @Override
    public PacketTransformer getRTPTransformer()
    {
        return null;
    }

    @Override
    public PacketTransformer getRTCPTransformer()
    {
        // Replace the mantissa and the exponent in the REMB
        // packets.
        return rtcpTransformer;
    }

    public RawPacket report()
    {
        return null;
    }
}