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
 * Created by gp on 16/07/14.
 */
public class BasicRTCPTerminationStrategy
        implements RTCPTerminationStrategy,
            RTCPPacketTransformer,
            RTCPReportBuilder
{
    protected RTCPTransmitter rtcpTransmitter;

    /**
     * A cache of media receiver feedback. It contains both receiver report
     * blocks and REMB packets.
     */
    protected final FeedbackCache feedbackCache;

    /**
     * The cache processor that will be making the RTCP reports coming from
     * the bridge.
     */
    private FeedbackCacheProcessor feedbackCacheProcessor;

    public BasicRTCPTerminationStrategy()
    {
        this.feedbackCache = new FeedbackCache();

        reset();
    }

    /**
     * The <tt>RTPTranslator</tt> associated with this strategy.
     */
    protected RTPTranslator translator;

    @Override
    public RTCPPacket[] makeReports()
    {
        if (rtcpTransmitter == null)
            throw new IllegalStateException("rtcpTransmitter is not set");

        RTPTranslator t = this.translator;
        if (t == null || !(t instanceof RTPTranslatorImpl))
            return new RTCPPacket[0];

        // Use the SSRC of the bridge that is announced and the endpoints won't
        // drop the packet.
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

        // TODO(gp) build REMB packets from scratch.
        if (this.feedbackCacheProcessor == null)
        {
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

    private RTCPReportBlock[] makeReceiverReports(long time)
    {
        Vector<RTCPReportBlock> reports = new Vector<RTCPReportBlock>();

        // Make receiver reports for all known SSRCs.
        for (Enumeration<SSRCInfo> elements = rtcpTransmitter.cache.cache.elements();
             elements.hasMoreElements();)
        {
            SSRCInfo info = elements.nextElement();
            if (!info.ours && info.sender)
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

                int fractionlost  =(int) (frac * 256D);
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
                reports.addElement(receiverReport);
            }
        }

        // Copy into an array and return.
        RTCPReportBlock res[] = new RTCPReportBlock[reports.size()];
        reports.copyInto(res);
        return res;
    }

    @Override
    public void reset()
    {
        /* Nothing to do here */
    }

    @Override
    public void setRTCPTransmitter(RTCPTransmitter rtcpTransmitter)
    {
        this.rtcpTransmitter = rtcpTransmitter;
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