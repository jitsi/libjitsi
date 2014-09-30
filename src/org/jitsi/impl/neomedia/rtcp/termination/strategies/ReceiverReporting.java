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
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.service.neomedia.*;

/**
 * @author George Politis
 */
public class ReceiverReporting
{
    private final BasicRTCPTerminationStrategy strategy;

    /**
     * The cache processor that will be making the RTCP reports coming from
     * the bridge.
     */
    private FeedbackCacheProcessor feedbackCacheProcessor;

    public ReceiverReporting(BasicRTCPTerminationStrategy strategy)
    {
        this.strategy = strategy;
    }

    public RTCPPacket[] makeReports()
    {
        RTCPTransmitter rtcpTransmitter
                = this.strategy.getRTCPReportBuilder().getRTCPTransmitter();

        if (rtcpTransmitter == null)
            throw new IllegalStateException("rtcpTransmitter is not set");

        RTPTranslator t = this.strategy.getRTPTranslator();
        if (t == null || !(t instanceof RTPTranslatorImpl))
            return new RTCPPacket[0];

        // Use the SSRC of the bridge that is announced through signaling so
        // that the endpoints won't drop the packet.
        int localSSRC = (int) ((RTPTranslatorImpl)t).getLocalSSRC(null);

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

        // TODO(gp) build REMB packets from scratch, like we do in the bridge.
        if (this.feedbackCacheProcessor == null)
        {
            FeedbackCache feedbackCache = strategy.getFeedbackCache();
            this.feedbackCacheProcessor
                    = new FeedbackCacheProcessor(feedbackCache);

            // TODO(gp) make percentile configurable.
            this.feedbackCacheProcessor.setPercentile(70);
        }

        RTCPPacket[] bestReceiverFeedback = feedbackCacheProcessor.makeReports(
                localSSRC);
        if (bestReceiverFeedback != null && bestReceiverFeedback.length != 0)
        {
            for (RTCPPacket packet : bestReceiverFeedback)
            {
                if (packet.type == RTCPFBPacket.PSFB
                        && packet instanceof RTCPFBPacket
                        && ((RTCPFBPacket)packet).fmt == RTCPREMBPacket.FMT)
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

    public Map<Integer, RTCPReportBlock> makeReceiverReportsMap(long time)
    {
        Map<Integer, RTCPReportBlock> reports = new HashMap<Integer, RTCPReportBlock>();

        RTCPTransmitter rtcpTransmitter
                = this.strategy.getRTCPReportBuilder().getRTCPTransmitter();

        if (rtcpTransmitter == null)
            throw new IllegalStateException("rtcpTransmitter is not set");

        // Make receiver reports for all known SSRCs.
        for (Enumeration<SSRCInfo> elements = rtcpTransmitter.cache.cache.elements();
             elements.hasMoreElements();)
        {
            SSRCInfo info = elements.nextElement();
            synchronized (info)
            {
                if (!info.ours)
                {

                    int ssrc = info.ssrc;
                    long lastseq = info.maxseq + info.cycles;
                    int jitter = (int) info.jitter;
                    long lsr = (int) ((info.lastSRntptimestamp & 0x0000ffffffff0000L) >> 16);
                    long dlsr = (int) ((time - info.lastSRreceiptTime) * 65.536000000000001D);
                    int packetslost = (int) (((lastseq - info.baseseq) + 1L) - info.received);

                    if (packetslost < 0)
                        packetslost = 0;
                    double frac = (double) (packetslost - info.prevlost)
                            / (double) (lastseq - info.prevmaxseq);
                    if (frac < 0.0D)
                        frac = 0.0D;

                    int fractionlost = (int) (frac * 256D);
                    RTCPReportBlock receiverReport = new RTCPReportBlock(
                            ssrc,
                            fractionlost,
                            packetslost,
                            lastseq,
                            jitter,
                            lsr,
                            dlsr
                    );


                    info.prevmaxseq = (int) lastseq;
                    info.prevlost = packetslost;
                    reports.put(ssrc, receiverReport);
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
