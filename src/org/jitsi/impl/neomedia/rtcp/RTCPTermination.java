/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.impl.neomedia.rtcp;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.*;
import org.jitsi.util.function.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 *
 * @author George Politis
 * @author Boris Grozev
 */
public class RTCPTermination
    extends RTCPPacketListenerAdapter
    implements RecurringRunnable
{
    /**
     * The maximum number of RTCP report blocks that an RR can contain.
     */
    private static final int MAX_RTCP_REPORT_BLOCKS = 31;

    /**
     * The minimum number of RTCP report blocks that an RR can contain.
     */
    private static final int MIN_RTCP_REPORT_BLOCKS = 0;

    /**
     * The reporting period for RRs and REMBs.
     */
    private static final long REPORT_PERIOD_MS = 500;

    /**
     * The name of the property used to disable NACK termination.
     */
    public static final String DISABLE_NACK_TERMINATION_PNAME
        = " org.jitsi.impl.neomedia.rtcp.DISABLE_NACK_TERMINATION";

    /**
     * The generator that generates <tt>RawPacket</tt>s from
     * <tt>RTCPCompoundPacket</tt>s.
     */
    private final RTCPGenerator generator = new RTCPGenerator();

    /**
     * The instance which holds statistics for this {@link RTCPTermination}
     * instance.
     */
    private final Statistics statistics = new Statistics();

    /**
     * A reusable array that holds {@link #MIN_RTCP_REPORT_BLOCKS}
     * <tt>RTCPReportBlock</tt>s.
     */
    private static final RTCPReportBlock[] MIN_RTCP_REPORT_BLOCKS_ARRAY
        = new RTCPReportBlock[MIN_RTCP_REPORT_BLOCKS];

    /**
     * The {@link Logger} used by the {@link RTCPTermination} class to print
     * debug information.
     */
    private static final Logger logger
        = Logger.getLogger(RTCPTermination.class);

    /**
     * The {@link MediaStream} that owns this instance.
     */
    private final MediaStreamImpl stream;

    /**
     * The time (in millis) that this instance was last "run".
     */
    private long lastRunMs = -1;

    /**
     * Ctor.
     *
     * @param stream the {@link MediaStream} that owns this instance.
     */
    public RTCPTermination(MediaStreamImpl stream)
    {
        this.stream = stream;

        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg == null)
        {
            logger.warn("NOT initializing RTCP n' NACK termination because "
                + "the configuration service was not found.");
            return;
        }

        boolean enableNackTermination
            = !cfg.getBoolean(DISABLE_NACK_TERMINATION_PNAME, false);

        if (enableNackTermination)
        {
            CachingTransformer cache = stream.getCachingTransformer();
            if (cache != null)
            {
                cache.setEnabled(true);
            }
            else
            {
                logger.warn("NACK termination is enabled, but we don't have" +
                    " a packet cache.");
            }
        }

        stream.getMediaStreamStats().addRTCPPacketListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void firReceived(FIRPacket firPacket)
    {
        ((RTPTranslatorImpl) stream.getRTPTranslator())
                .getRtcpFeedbackMessageSender().sendFIR( (int) firPacket.senderSSRC );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pliReceived(PLIPacket pliPacket)
    {
        ((RTPTranslatorImpl) stream.getRTPTranslator())
            .getRtcpFeedbackMessageSender().sendFIR( (int) pliPacket.senderSSRC );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nackReceived(NACKPacket nackPacket)
    {
        long ssrc = nackPacket.sourceSSRC;
        Set<Integer> lostPackets = new TreeSet<>(nackPacket.getLostPackets());

        if (logger.isDebugEnabled())
        {
            logger.debug(Logger.Category.STATISTICS,
                "nack_received,stream=" + stream.hashCode()
                    + " ssrc=" + ssrc
                    + ",lost_packets=" + lostPackets);
        }

        RawPacketCache cache;
        RtxTransformer rtxTransformer;

        if (stream != null
            && (cache = stream.getCachingTransformer().getOutgoingRawPacketCache()) != null
            && (rtxTransformer = stream.getRtxTransformer())
            != null)
        {
            // XXX The retransmission of packets MUST take into account SSRC
            // rewriting. Which it may do by injecting retransmitted packets
            // AFTER the SsrcRewritingEngine.
            // Also, the cache MUST be notified of packets being retransmitted,
            // in order for it to update their timestamp. We do this here by
            // simply letting retransmitted packets pass through the cache again.
            // We use the retransmission requester here simply because it is
            // the transformer right before the cache, not because of anything
            // intrinsic to it.
            RetransmissionRequester rr = stream.getRetransmissionRequester();
            TransformEngine after
                = (rr instanceof TransformEngine) ? (TransformEngine) rr : null;

            long rtt = stream.getMediaStreamStats().getSendStats().getRtt();
            long now = System.currentTimeMillis();

            for (Iterator<Integer> i = lostPackets.iterator(); i.hasNext();)
            {
                int seq = i.next();
                RawPacketCache.Container container
                    = cache.getContainer(ssrc, seq);


                if (container != null)
                {
                    // Cache hit.
                    long delay = now - container.timeAdded;
                    boolean send = (rtt == -1) ||
                        (delay >= Math.min(rtt * 0.9, rtt - 5));

                    if (logger.isDebugEnabled())
                    {
                        logger.debug(Logger.Category.STATISTICS,
                            "retransmitting,stream=" + stream.hashCode()
                                + " ssrc=" + ssrc
                                + ",seq=" + seq
                                + ",send=" + send);
                    }

                    if (send && rtxTransformer.retransmit(container.pkt, after))
                    {
                        statistics.packetsRetransmitted.incrementAndGet();
                        statistics.bytesRetransmitted.addAndGet(
                            container.pkt.getLength());
                        i.remove();
                    }

                    if (!send)
                    {
                        statistics.packetsNotRetransmitted.incrementAndGet();
                        statistics.bytesNotRetransmitted.addAndGet(
                            container.pkt.getLength());
                        i.remove();
                    }

                }
                else
                {
                    statistics.packetsMissingFromCache.incrementAndGet();
                }
            }
        }

        if (!lostPackets.isEmpty())
        {
            // If retransmission requests are enabled, videobridge assumes
            // the responsibility of requesting missing packets.
            logger.debug("Packets missing from the cache.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimeUntilNextRun()
    {
        return (lastRunMs + REPORT_PERIOD_MS) - System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
        lastRunMs = System.currentTimeMillis();

        // Create and return the packet.
        // We use the stream's local source ID (SSRC) as the SSRC of packet
        // sender.
        long streamSSRC = getLocalSSRC();
        if (streamSSRC == -1)
        {
            return;
        }

        // RRs
        RTCPRRPacket[] rrs = makeRRs(streamSSRC);

        // Bail out (early) if we have nothing to report.
        if (ArrayUtils.isNullOrEmpty(rrs))
        {
            return;
        }

        // REMB
        RTCPREMBPacket remb = makeREMB(streamSSRC);

        // Build the RTCP compound packet to return.

        RTCPPacket[] rtcpPackets
            = new RTCPPacket[rrs.length + (remb == null ? 0 : 1)];

        System.arraycopy(rrs, 0, rtcpPackets, 0, rrs.length);
        if (remb != null)
        {
            rtcpPackets[rrs.length] = remb;
        }

        RTCPCompoundPacket compound = new RTCPCompoundPacket(rtcpPackets);

        // inject the packets into the MediaStream.
        RawPacket pkt = generator.apply(compound);

        try
        {
            stream.injectPacket(pkt, false, null);
        }
        catch (TransmissionFailedException e)
        {
            logger.error("transmission of an RTCP packet failed.", e);
        }
    }

    /**
     * Gets the instance which holds statistics for this {@link RTCPTermination}
     * instance.
     *
     * @return the instance which holds statistics for this
     * {@link RTCPTermination} instance.
     */
    public Statistics getStatistics()
    {
        return statistics;
    }

    /**
     * (attempts) to get the local SSRC that will be used in the media sender
     * SSRC field of the RTCP reports. TAG(cat4-local-ssrc-hurricane)
     *
     * @return
     */
    private long getLocalSSRC()
    {
        StreamRTPManager streamRTPManager = stream.getStreamRTPManager();
        if (streamRTPManager == null)
        {
            return -1;
        }

        return stream.getStreamRTPManager().getLocalSSRC();
    }


    /**
     * Makes <tt>RTCPRRPacket</tt>s using information in FMJ.
     *
     * @return A <tt>List</tt> of <tt>RTCPRRPacket</tt>s to inject into the
     * <tt>MediaStream</tt>.
     */
    private RTCPRRPacket[] makeRRs(long streamSSRC)
    {
        RTCPReportBlock[] reportBlocks = makeReportBlocks();
        if (ArrayUtils.isNullOrEmpty(reportBlocks))
        {
            return null;
        }

        int mod = reportBlocks.length % MAX_RTCP_REPORT_BLOCKS;
        int div = reportBlocks.length / MAX_RTCP_REPORT_BLOCKS;

        RTCPRRPacket[] rrs = new RTCPRRPacket[mod == 0 ? div : div + 1];

        // Since a maximum of 31 reception report blocks will fit in an SR
        // or RR packet, additional RR packets SHOULD be stacked after the
        // initial SR or RR packet as needed to contain the reception
        // reports for all sources heard during the interval since the last
        // report.
        if (reportBlocks.length > MAX_RTCP_REPORT_BLOCKS)
        {
            int rrIdx = 0;
            for (int off = 0;
                 off < reportBlocks.length; off += MAX_RTCP_REPORT_BLOCKS)
            {
                int blockCount = Math.min(
                    reportBlocks.length - off, MAX_RTCP_REPORT_BLOCKS);

                RTCPReportBlock[] blocks = new RTCPReportBlock[blockCount];

                System.arraycopy(reportBlocks, off, blocks, 0, blocks.length);

                rrs[rrIdx++] = new RTCPRRPacket((int) streamSSRC, blocks);
            }
        }
        else
        {
            rrs[0] = new RTCPRRPacket((int) streamSSRC, reportBlocks);
        }

        return rrs;
    }

    /**
     * Iterate through all the <tt>ReceiveStream</tt>s that this
     * <tt>MediaStream</tt> has and make <tt>RTCPReportBlock</tt>s for all of
     * them.
     *
     * @return
     */
    private RTCPReportBlock[] makeReportBlocks()
    {
        MediaStreamTrackDesc[] tracks
            = stream.getMediaStreamTrackReceiver().getMediaStreamTracks();

        if (ArrayUtils.isNullOrEmpty(tracks))
        {
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }

        List<RTCPReportBlock> reportBlocks = new ArrayList<>();

        for (int i = 0; i < tracks.length; i++)
        {
            List<RTCPReportBlock> trackReportBlocks
                = tracks[i].makeReceiverReport(lastRunMs);

            if (!trackReportBlocks.isEmpty())
            {
                reportBlocks.addAll(trackReportBlocks);
            }
        }

        return reportBlocks.toArray(new RTCPReportBlock[reportBlocks.size()]);
    }

    /**
     * Makes an <tt>RTCPREMBPacket</tt> that provides receiver feedback to the
     * endpoint from which we receive.
     *
     * @return an <tt>RTCPREMBPacket</tt> that provides receiver feedback to the
     * endpoint from which we receive.
     */
    private RTCPREMBPacket makeREMB(long streamSSRC)
    {
        // TODO we should only make REMBs if REMB support has been advertised.
        // Destination
        RemoteBitrateEstimator remoteBitrateEstimator
            = ((VideoMediaStream) stream).getRemoteBitrateEstimator();

        Collection<Integer> ssrcs = remoteBitrateEstimator.getSsrcs();

        // TODO(gp) intersect with SSRCs from signaled simulcast layers
        // NOTE(gp) The Google Congestion Control algorithm (sender side)
        // doesn't seem to care about the SSRCs in the dest field.
        long[] dest = new long[ssrcs.size()];
        int i = 0;

        for (Integer ssrc : ssrcs)
            dest[i++] = ssrc & 0xFFFFFFFFL;

        // Exp & mantissa
        long bitrate = remoteBitrateEstimator.getLatestEstimate();

        if (logger.isDebugEnabled())
        {
            logger.debug(
                "Estimated bitrate (bps): " + bitrate + ", dest: "
                    + Arrays.toString(dest) + ", time (ms): "
                    + System.currentTimeMillis());
        }
        if (bitrate == -1)
        {
            return null;
        }
        else
        {
            return new RTCPREMBPacket(streamSSRC, 0L, bitrate, dest);
        }
    }

    /**
     * Holds statistics for this {@link RTCPTermination} instance.
     *
     * TODO(gp) we should think about separating stats collection and stats
     * retrieval (stats snapshots)
     */
    public class Statistics
    {
        /**
         * Number of bytes retransmitted.
         */
        private final AtomicLong bytesRetransmitted = new AtomicLong();

        /**
         * Number of bytes for packets which were requested and found in the
         * cache, but were intentionally not retransmitted.
         */
        private final AtomicLong bytesNotRetransmitted = new AtomicLong();

        /**
         * Number of packets retransmitted.
         */
        private final AtomicLong packetsRetransmitted = new AtomicLong();

        /**
         * Number of packets which were requested and found in the cache, but
         * were intentionally not retransmitted.
         */
        private AtomicLong packetsNotRetransmitted = new AtomicLong();

        /**
         * The number of packets for which retransmission was requested, but
         * they were missing from the cache.
         */
        private AtomicLong packetsMissingFromCache = new AtomicLong();

        /**
         * Gets the number of bytes retransmitted.
         *
         * @return the number of bytes retransmitted.
         */
        public long getBytesRetransmitted()
        {
            return bytesRetransmitted.get();
        }

        /**
         * Gets the number of bytes for packets which were requested and found
         * in the cache, but were intentionally not retransmitted.
         *
         * @return the number of bytes for packets which were requested and
         * found in the cache, but were intentionally not retransmitted.
         */
        public long getBytesNotRetransmitted()
        {
            return bytesNotRetransmitted.get();
        }

        /**
         * Gets the number of packets retransmitted.
         *
         * @return the number of packets retransmitted.
         */
        public long getPacketsRetransmitted()
        {
            return packetsRetransmitted.get();
        }

        /**
         * Gets the number of packets which were requested and found in the
         * cache, but were intentionally not retransmitted.
         *
         * @return the number of packets which were requested and found in the
         * cache, but were intentionally not retransmitted.
         */
        public long getPacketsNotRetransmitted()
        {
            return packetsNotRetransmitted.get();
        }

        /**
         * Gets the number of packets for which retransmission was requested,
         * but they were missing from the cache.
         * @return the number of packets for which retransmission was requested,
         * but they were missing from the cache.
         */
        public long getPacketsMissingFromCache()
        {
            return packetsMissingFromCache.get();
        }
    }
}
