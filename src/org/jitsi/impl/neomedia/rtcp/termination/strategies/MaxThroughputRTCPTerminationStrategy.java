/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.*;

import java.util.*;

/**
 * Maximizes endpoint throughput. It does that by sending REMB messages with the
 * largest possible exp and mantissa values. This strategy is only meant to be
 * used in tests.
 *
 * @author George Politis
 */
public class MaxThroughputRTCPTerminationStrategy
        implements RTCPTerminationStrategy, RTCPPacketTransformer
{
    private final RTCPReportBuilder reportBuilder
            // TODO(gp) create an RTCPReportBuilderImpl that reports feedback using the announced SSRC of the bridge
            = new DefaultRTCPReportBuilderImpl();

    public static final int MAX_MANTISSA = 262143;
    public static final int MAX_EXP = 63;

    @Override
    public RTCPCompoundPacket transformRTCPPacket(RTCPCompoundPacket inPacket)
    {
        if (inPacket == null
                || inPacket.packets == null || inPacket.packets.length == 0)
        {
            return inPacket;
        }

        Vector<RTCPPacket> outPackets = new Vector<RTCPPacket>();

        for (RTCPPacket p : inPacket.packets)
        {
            switch (p.type)
            {
                case RTCPPacket.RR:
                    // Mute RRs from the peers. We send our own.
                    break;
                case RTCPPacket.SR:
                    // Remove feedback information from the SR and forward.
                    RTCPSRPacket sr = (RTCPSRPacket) p;
                    outPackets.add(sr);
                    sr.reports = new RTCPReportBlock[0];
                    break;
                case RTCPFBPacket.PSFB:
                    RTCPFBPacket psfb = (RTCPFBPacket) p;
                    switch (psfb.fmt)
                    {
                        case RTCPREMBPacket.FMT:
                            // Mute REMBs.
                            RTCPREMBPacket remb = (RTCPREMBPacket)p;

                            remb.mantissa = MAX_MANTISSA;
                            remb.exp = MAX_EXP;
                            outPackets.add(remb);
                            break;
                        default:
                            // Pass through everything else, like PLIs and NACKs
                            outPackets.add(psfb);
                            break;
                    }
                    break;
                default:
                    // Pass through everything else, like PLIs and NACKs
                    outPackets.add(p);
                    break;
            }
        }

        RTCPPacket[] outarr = new RTCPPacket[outPackets.size()];
        outPackets.copyInto(outarr);

        RTCPCompoundPacket outPacket = new RTCPCompoundPacket(outarr);

        return outPacket;
    }

    @Override
    public RTCPPacketTransformer getRTCPPacketTransformer()
    {
        return this;
    }

    @Override
    public RTCPReportBuilder getRTCPReportBuilder()
    {
        return reportBuilder;
    }

    @Override
    public void setRTPTranslator(RTPTranslator translator) {
        // Nothing to do here.
    }

    @Override
    public RTPTranslator getRTPTranslator()
    {
        return null;
    }
}
