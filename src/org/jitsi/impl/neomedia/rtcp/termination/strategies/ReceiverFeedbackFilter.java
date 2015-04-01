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
 * @author George Politis
 */
public class ReceiverFeedbackFilter implements Transformer<RTCPCompoundPacket>
{
    @Override
    public RTCPCompoundPacket transform(RTCPCompoundPacket inPacket)
    {
        if (inPacket == null
                || inPacket.packets == null || inPacket.packets.length == 0)
        {
            return inPacket;
        }

        Vector<RTCPPacket> outPackets = new Vector<RTCPPacket>(
                inPacket.packets.length);

        // Modify the incoming RTCP packet and/or update the
        // <tt>feedbackCache</tt>.
        for (RTCPPacket p : inPacket.packets)
        {
            switch (p.type)
            {
                case RTCPPacket.RR:

                    // mute the receiver report blocks.

                    RTCPRRPacket rr = (RTCPRRPacket) p;
                    outPackets.add(rr);
                    rr.reports = new RTCPReportBlock[0];
                    break;
                case RTCPPacket.SR:

                    // mute the receiver report blocks.

                    RTCPSRPacket sr = (RTCPSRPacket) p;
                    outPackets.add(sr);
                    sr.reports = new RTCPReportBlock[0];
                    break;
                case RTCPFBPacket.PSFB:
                    RTCPFBPacket psfb = (RTCPFBPacket) p;
                    switch (psfb.fmt)
                    {
                        case RTCPREMBPacket.FMT:

                            // NOT adding the REMB in the outPacket as we mute
                            // REMBs from the peers.
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
}
