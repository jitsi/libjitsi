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

/**
 * Removes report blocks and REMB packets (collectively referred to as receiver
 * feedback) from incoming receiver/sender reports.
 *
 * Updates the feedback cache and makes its own reports based on information
 * from FMJ and the feedback cache.
 *
 * @author George Politis
 */
public class BasicRTCPTerminationStrategy
        extends AbstractRTCPTerminationStrategy
{
    /**
     * The cache processor that will be making the RTCP reports coming from
     * the bridge.
     */
    private final FeedbackCacheProcessor feedbackCacheProcessor;

    /**
     * A cache of media receiver feedback. It contains both receiver report
     * blocks and REMB packets.
     */
    private final FeedbackCache feedbackCache;

    /**
     * Ctor.
     */
    public BasicRTCPTerminationStrategy()
    {
        this.feedbackCache = new FeedbackCache();
        this.feedbackCacheProcessor
                = new FeedbackCacheProcessor(feedbackCache);

        // TODO(gp) make percentile configurable.
        this.feedbackCacheProcessor.setPercentile(70);

        setTransformerChain(new Transformer[]{
                new FeedbackCacheUpdater(feedbackCache),
                new ReceiverFeedbackFilter()
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPPacket[] makeReports()
    {
        RTCPTransmitter rtcpTransmitter
                = this.getRTCPReportBuilder().getRTCPTransmitter();

        if (rtcpTransmitter == null)
            throw new IllegalStateException("rtcpTransmitter is not set");

        RTPTranslator t = this.getRTPTranslator();
        if (t == null || !(t instanceof RTPTranslatorImpl))
            return new RTCPPacket[0];

        // Use the SSRC of the bridge that is announced through signaling so
        // that the endpoints won't drop the packet.
        int localSSRC = (int) ((RTPTranslatorImpl) t).getLocalSSRC(null);

        Vector<RTCPPacket> packets = new Vector<RTCPPacket>();

        long time = System.currentTimeMillis();
        RTCPReportBlock reports[] = makeReceiverReports(time);
        RTCPReportBlock firstrep[] = reports;

        // If the number of sources for which reception statistics are being
        // reported exceeds 31, the number that will fit into one SR or RR
        // packet, then additional RR packets SHOULD follow the initial report
        // packet.
        if (reports.length > 31)
        {
            firstrep = new RTCPReportBlock[31];
            System.arraycopy(reports, 0, firstrep, 0, 31);
        }

        packets.addElement(new RTCPRRPacket(localSSRC, firstrep));

        if (firstrep != reports)
        {
            for (int offset = 31; offset < reports.length; offset += 31)
            {
                if (reports.length - offset < 31)
                    firstrep = new RTCPReportBlock[reports.length - offset];
                System.arraycopy(reports, offset, firstrep, 0, firstrep.length);
                RTCPRRPacket rrp = new RTCPRRPacket(localSSRC, firstrep);
                packets.addElement(rrp);
            }

        }

        // Include REMB.

        // TODO(gp) build REMB packets from scratch instead of relying on the
        // feedback cache processor, just like we do in the JVB.

        RTCPPacket[] bestReceiverFeedback = feedbackCacheProcessor.makeReports(
                localSSRC);
        if (bestReceiverFeedback != null && bestReceiverFeedback.length != 0)
        {
            for (RTCPPacket packet : bestReceiverFeedback)
            {
                if (packet.type == RTCPFBPacket.PSFB
                        && packet instanceof RTCPFBPacket
                        && ((RTCPFBPacket) packet).fmt == RTCPREMBPacket.FMT)
                {
                    packets.add(packet);
                }
            }
        }

        // TODO(gp) for RTCP compound packets MUST contain an SDES packet.

        // Copy into an array and return.
        RTCPPacket[] res = new RTCPPacket[packets.size()];
        packets.copyInto(res);
        return res;
    }

    private Map<Integer, RTCPReportBlock> makeReceiverReportsMap(long time)
    {
        Map<Integer, RTCPReportBlock> reports = new HashMap<Integer, RTCPReportBlock>();

        RTCPTransmitter rtcpTransmitter
                = this.getRTCPReportBuilder().getRTCPTransmitter();

        if (rtcpTransmitter == null)
            throw new IllegalStateException("rtcpTransmitter is not set");

        // Make receiver reports for all known SSRCs.
        for (Enumeration<SSRCInfo> elements = rtcpTransmitter.cache.cache.elements();
             elements.hasMoreElements(); )
        {
            SSRCInfo info = elements.nextElement();
            synchronized (info)
            {
                if (!info.ours)
                {
                    RTCPReportBlock receiverReport
                            = info.makeReceiverReport(time);

                    reports.put(info.ssrc, receiverReport);
                }
            }
        }

        return reports;
    }

    private RTCPReportBlock[] makeReceiverReports(long time)
    {
        Collection<RTCPReportBlock> reports
                = makeReceiverReportsMap(time).values();

        return reports.toArray(new RTCPReportBlock[reports.size()]);
    }
}
