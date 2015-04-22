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
    private static RTCPReportBlock[] NO_RTCP_REPORT_BLOCKS
        = new RTCPReportBlock[0];

    private FeedbackCache feedbackCache;

    public FeedbackCacheUpdater(FeedbackCache feedbackCache)
    {
        this.feedbackCache = feedbackCache;
    }

    @Override
    public RTCPCompoundPacket reverseTransform(RTCPCompoundPacket inPacket)
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
        int ssrc = 0;

        // Modify the incoming RTCP packet and/or update the
        // <tt>feedbackCache</tt>.
        for (RTCPPacket p : inPacket.packets)
        {
            switch (p.type)
            {
                case RTCPPacket.RR:

                    // Grab the receiver report blocks to put them into the
                    // cache after the loop is done.

                    RTCPRRPacket rr = (RTCPRRPacket) p;
                    reports = rr.reports;
                    ssrc = rr.ssrc;

                    break;
                case RTCPPacket.SR:

                    // Grab the receiver report blocks to put them into the
                    // cache after the loop is done; mute the receiver report
                    // blocks.

                    RTCPSRPacket sr = (RTCPSRPacket) p;
                    reports = sr.reports;
                    ssrc = sr.ssrc;
                    sr.reports = NO_RTCP_REPORT_BLOCKS;
                    break;
                case RTCPFBPacket.PSFB:
                    RTCPFBPacket psfb = (RTCPFBPacket) p;
                    switch (psfb.fmt)
                    {
                        case RTCPREMBPacket.FMT:
                            remb = (RTCPREMBPacket) p;
                            ssrc = (int) remb.senderSSRC;

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
}
