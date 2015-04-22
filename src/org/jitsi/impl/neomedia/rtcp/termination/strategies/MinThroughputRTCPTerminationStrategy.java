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
 * Minimizes endpoint throughput. It does that by sending REMB messages with the
 * smallest possible exp and mantissa values. This strategy is only meant to be
 * used in tests.
 *
 * @author George Politis
 */
public class MinThroughputRTCPTerminationStrategy
        implements RTCPTerminationStrategy, Transformer<RTCPCompoundPacket>
{
    private final RTCPReportBuilder reportBuilder
            // TODO(gp) create an RTCPReportBuilderImpl that reports feedback using the announced SSRC of the bridge
            = new DefaultRTCPReportBuilderImpl();

    public static final int MIN_MANTISSA = 10;
    public static final int MIN_EXP = 1;

    @Override
    public RTCPCompoundPacket reverseTransform(RTCPCompoundPacket inPacket)
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
                            RTCPREMBPacket remb = (RTCPREMBPacket)p;

                            remb.mantissa = MIN_MANTISSA;
                            remb.exp = MIN_EXP;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        // nothing to be done here
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPCompoundPacket transform(RTCPCompoundPacket inPacket)
    {
        return inPacket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transformer<RTCPCompoundPacket> getRTCPCompoundPacketTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPReportBuilder getRTCPReportBuilder()
    {
        return reportBuilder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRTPTranslator(RTPTranslator translator)
    {
        // Nothing to do here.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTPTranslator getRTPTranslator()
    {
        return null;
    }
}
