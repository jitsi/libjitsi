/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import java.util.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.*;

/**
 * Removes report blocks and REMB packets (collectively referred to as receiver
 * feedback) from incoming receiver/sender reports.
 *
 * Updates the feedback cache and makes its own reports based on the feedback
 * cache information.
 *
 * Serves as a base for other RTCP termination strategies.
 *
 * @author George Politis
 */
public class BasicRTCPTerminationStrategy
        implements RTCPTerminationStrategy,
            RTCPPacketTransformer,
            RTCPReportBuilder
{
    /**
     *
     */
    private RTCPTransmitter rtcpTransmitter;

    /**
     *
     */
    private final ReceiverReporting receiverReporting;

    /**
     * The <tt>RTPTranslator</tt> associated with this strategy.
     */
    private RTPTranslator translator;

    /**
     * A cache of media receiver feedback. It contains both receiver report
     * blocks and REMB packets.
     */
    private final FeedbackCache feedbackCache;

    /**
     *
     * @return
     */
    public FeedbackCache getFeedbackCache()
    {
        return feedbackCache;
    }

    /**
     *
     */
    public BasicRTCPTerminationStrategy()
    {
        this.feedbackCache = new FeedbackCache();
        this.receiverReporting = new ReceiverReporting(this);

        reset();
    }

    @Override
    public RTCPPacket[] makeReports()
    {
        return receiverReporting.makeReports();
    }

    @Override
    public void reset()
    {
        /* Nothing to do here */
    }

    @Override
    public void setRTCPTransmitter(RTCPTransmitter rtcpTransmitter)
    {
        if (rtcpTransmitter != this.rtcpTransmitter)
        {
            this.rtcpTransmitter = rtcpTransmitter;
            onRTCPTransmitterChanged();
        }
    }

    /**
     * Notifies this instance that the <tt>RTCPTransmitter</tt> has changed.
     */
    private void onRTCPTransmitterChanged()
    {
        RTCPTransmitter t;
        SSRCCache c;
        if ((t = this.rtcpTransmitter) != null
                && (c = t.cache) != null)
        {
            // Make the SSRCCache to "calculate" an RTCP reporting interval of 
            // 1s.
            c.audio = false;
        }
    }

    @Override
    public RTCPTransmitter getRTCPTransmitter()
    {
        return this.rtcpTransmitter;
    }

    @Override
    public RTCPPacketTransformer getRTCPPacketTransformer()
    {
        return this;
    }

    @Override
    public RTCPReportBuilder getRTCPReportBuilder()
    {
        return this;
    }

    @Override
    public void setRTPTranslator(RTPTranslator translator) {
        this.translator = translator;
    }

    @Override
    public RTPTranslator getRTPTranslator()
    {
        return this.translator;
    }

    /**
     * 1. Removes receiver report blocks from RRs and SRs and kills REMBs.
     * 2. Updates the receiver feedback cache.
     *
     * @param inPacket
     * @return
     */
    @Override
    public RTCPCompoundPacket transformRTCPPacket(
            RTCPCompoundPacket inPacket)
    {
        if (inPacket == null
                || inPacket.packets == null || inPacket.packets.length == 0)
        {
            return inPacket;
        }

        Vector<RTCPPacket> outPackets = new Vector<RTCPPacket>(
                inPacket.packets.length);

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
                    outPackets.add(sr);
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

        feedbackCache.update(ssrc, reports, remb);

        RTCPPacket[] outarr = new RTCPPacket[outPackets.size()];
        outPackets.copyInto(outarr);

        RTCPCompoundPacket outPacket = new RTCPCompoundPacket(outarr);

        return outPacket;
    }
}
