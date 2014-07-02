package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.*;

import java.util.*;

/**
 * Created by gp on 7/1/14.
 */
public class MinimizingThroughputRTCPTerminationStrategy
        implements RTCPTerminationStrategy, RTCPPacketTransformer
{
    private final RTCPReportBuilder reportBuilder
            = new DefaultRTCPReportBuilderImpl();

    private static final int mantissa = 10;
    private static final int exp = 1;

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
                case RTCPPacketType.RR:
                    // Mute RRs from the peers. We send our own.
                    break;
                case RTCPPacketType.SR:
                    // Remove feedback information from the SR and forward.
                    RTCPSRPacket sr = (RTCPSRPacket) p;
                    outPackets.add(sr);
                    sr.reports = new RTCPReportBlock[0];
                    break;
                case RTCPPacketType.PSFB:
                    RTCPFBPacket psfb = (RTCPFBPacket) p;
                    switch (psfb.fmt)
                    {
                        case RTCPPSFBFormat.REMB:
                            // Mute REMBs.
                            RTCPREMBPacket remb = (RTCPREMBPacket)p;

                            remb.mantissa = mantissa;
                            remb.exp = exp;
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
}
