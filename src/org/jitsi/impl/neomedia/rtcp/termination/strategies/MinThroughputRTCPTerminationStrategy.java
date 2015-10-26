/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * Minimizes endpoint throughput. It does that by sending REMB messages with the
 * smallest possible exp and mantissa values. This strategy is only meant to be
 * used in tests.
 *
 * @author George Politis
 */
public class MinThroughputRTCPTerminationStrategy
    implements RTCPTerminationStrategy
{
    private final PacketTransformer rtcpTransformer
        = new SinglePacketTransformer()
    {
        /**
         * The minimum value of the mantissa in the REMB calculation.
         */
        private static final int MIN_MANTISSA = 10;

        /**
         * The minimum value of the exponent in the REMB calculation.
         */
        private static final int MIN_EXP = 1;

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

            RTCPCompoundPacket compoundPkt;

            try
            {
                compoundPkt
                    = (RTCPCompoundPacket)
                        parser.parse(
                                pkt.getBuffer(),
                                pkt.getOffset(),
                                pkt.getLength());
            }
            catch (BadFormatException e)
            {
                return null;
            }

            if (compoundPkt == null
                    || compoundPkt.packets == null
                    || compoundPkt.packets.length == 0)
            {
                return pkt;
            }

            for (RTCPPacket p : compoundPkt.packets)
            {
                switch (p.type)
                {
                case RTCPFBPacket.PSFB:
                    RTCPFBPacket psfb = (RTCPFBPacket) p;

                    switch (psfb.fmt)
                    {
                    case RTCPREMBPacket.FMT:
                        RTCPREMBPacket remb = (RTCPREMBPacket) p;

                        remb.mantissa = MIN_MANTISSA;
                        remb.exp = MIN_EXP;
                        break;
                    }
                    break;
                }
            }

            // We've modified compoundPkt so we have to reassemble it only.
            return generator.apply(compoundPkt);
        }

        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            return pkt;
        }
    };

    @Override
    public PacketTransformer getRTCPTransformer()
    {
        // Replace the mantissa and the exponent in the REMB packets.
        return rtcpTransformer;
    }

    @Override
    public PacketTransformer getRTPTransformer()
    {
        // We don't touch the RTP traffic.
        return null;
    }
}
