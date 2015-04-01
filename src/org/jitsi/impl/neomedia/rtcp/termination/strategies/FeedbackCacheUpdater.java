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

/**
 * @author George Politis
 */
public class FeedbackCacheUpdater implements Transformer<RTCPCompoundPacket>
{
    private FeedbackCache feedbackCache;

    public FeedbackCacheUpdater(FeedbackCache feedbackCache)
    {
        this.feedbackCache = feedbackCache;
    }

    @Override
    public RTCPCompoundPacket transform(RTCPCompoundPacket inPacket)
    {
        if (inPacket == null
                || inPacket.packets == null || inPacket.packets.length == 0)
        {
            return inPacket;
        }

        // These are the data that are of interest to us : RR report blocks and
        // REMBs. We'll also need the SSRC of the RTCP report sender.

        RTCPReportBlock[] reports = null;
        RTCPREMBPacket remb = null;
        Integer ssrc = 0;

        // Modify the incoming RTCP packet and/or update the
        // <tt>feedbackCache</tt>.
        for (RTCPPacket p : inPacket.packets)
        {
            switch (p.type)
            {
                case RTCPPacket.RR:

                    // Grab the receiver report blocks to put them into the
                    // cache after the loop is done and mute the RR.

                    RTCPRRPacket rr = (RTCPRRPacket) p;
                    reports = rr.reports;
                    ssrc = Integer.valueOf(rr.ssrc);

                    break;
                case RTCPPacket.SR:

                    // Grab the receiver report blocks to put them into the
                    // cache after the loop is done; mute the receiver report
                    // blocks.

                    RTCPSRPacket sr = (RTCPSRPacket) p;
                    reports = sr.reports;
                    ssrc = Integer.valueOf(sr.ssrc);
                    sr.reports = new RTCPReportBlock[0];
                    break;
                case RTCPFBPacket.PSFB:
                    RTCPFBPacket psfb = (RTCPFBPacket) p;
                    switch (psfb.fmt)
                    {
                        case RTCPREMBPacket.FMT:

                            // NOT adding the REMB in the outPacket as we mute
                            // REMBs from the peers.
                            //
                            // We put it into the feedback cache instead.
                            remb = (RTCPREMBPacket) p;
                            ssrc = Integer.valueOf((int) remb.senderSSRC);

                            break;
                    }
                    break;
                default:
                    break;
            }
        }

        feedbackCache.update(ssrc, reports, remb);

        return inPacket;
    }
}
