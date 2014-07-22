/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.service.neomedia.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by gp on 7/4/14.
 */
public class HighestQualityRTCPTerminationStrategy
        implements RTCPTerminationStrategy,
        RTCPPacketTransformer,
        RTCPReportBuilder
{
    /**
     * A cache of media receiver feedback. It contains both receiver report
     * blocks and REMB packets.
     */
    private final FeedbackCache feedbackCache;

    /**
     * The cache processor that will be making the RTCP reports coming from
     * the bridge.
     */
    private FeedbackCacheProcessor feedbackCacheProcessor;

    public HighestQualityRTCPTerminationStrategy()
    {
        this.feedbackCache
                = new FeedbackCache();
    }

    /**
     * The <tt>RTPTranslator</tt> associated with this strategy.
     */
    private RTPTranslator translator;

    @Override
    public RTCPPacket[] makeReports()
    {
        // Uses the cache processor to make the RTCP reports.

        RTPTranslator t = this.translator;
        if (t == null || !(t instanceof RTPTranslatorImpl))
            return new RTCPPacket[0];

        long localSSRC = ((RTPTranslatorImpl)t).getLocalSSRC(null);

        if (this.feedbackCacheProcessor == null)
        {
            this.feedbackCacheProcessor
                    = new FeedbackCacheProcessor(feedbackCache);

            // TODO(gp) make percentile configurable.
            this.feedbackCacheProcessor.setPercentile(70);
        }

        RTCPPacket[] packets = feedbackCacheProcessor.makeReports(
                (int) localSSRC);

        return packets;
    }

    @Override
    public void reset()
    {
        // Nothing to do here.
    }

    @Override
    public void setRTCPTransmitter(RTCPTransmitter rtcpTransmitter)
    {
        // Nothing to do here.
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
    public RTCPCompoundPacket transformRTCPPacket(
            RTCPCompoundPacket inPacket)
    {
        // Removes receiver report blocks from RRs and SRs and kills REMBs.

        if (inPacket == null
                || inPacket.packets == null || inPacket.packets.length == 0)
        {
            return inPacket;
        }

        Vector<RTCPPacket> outPackets = new Vector<RTCPPacket>(inPacket.packets.length);

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
                case RTCPPacketType.RR:

                    // Grab the receiver report blocks to put them into the
                    // cache after the loop is done and mute the RR.

                    RTCPRRPacket rr = (RTCPRRPacket) p;
                    reports = rr.reports;
                    ssrc = Integer.valueOf(rr.ssrc);

                    break;
                case RTCPPacketType.SR:

                    // Grab the receiver report blocks to put them into the
                    // cache after the loop is done; mute the receiver report
                    // blocks.

                    RTCPSRPacket sr = (RTCPSRPacket) p;
                    outPackets.add(sr);
                    reports = sr.reports;
                    ssrc = Integer.valueOf(sr.ssrc);
                    sr.reports = new RTCPReportBlock[0];
                    break;
                case RTCPPacketType.PSFB:
                    RTCPFBPacket psfb = (RTCPFBPacket) p;
                    switch (psfb.fmt)
                    {
                        case RTCPPSFBFormat.REMB:

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