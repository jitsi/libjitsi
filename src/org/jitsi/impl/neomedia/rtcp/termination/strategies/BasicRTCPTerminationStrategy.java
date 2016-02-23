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
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import java.util.*;
import javax.media.rtp.*;
import javax.media.rtp.rtcp.*;
import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;
import org.jitsi.util.function.*;

/**
 *
 * The <tt>BasicRTCPTerminationStrategy</tt> "gateways" PLIs, FIRs, NACKs,
 * etc, in the sense that it replaces the packet sender information in the
 * PLIs, FIRs, NACKs, etc and it generates its own SRs/RRs/REMBs based on
 * information that it collects and from information found in FMJ.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class BasicRTCPTerminationStrategy
    extends MediaStreamRTCPTerminationStrategy
{
    /**
     * The <tt>Logger</tt> used by the <tt>BasicRTCPTerminationStrategy</tt>
     * class and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(BasicRTCPTerminationStrategy.class);

    /**
     * The maximum number of RTCP report blocks that an RR or an SR can
     * contain.
     */
    private static final int MAX_RTCP_REPORT_BLOCKS = 31;

    /**
     * The maximuc number of SDES chunks that an SDES packet can have.
     */
    private static final int MAX_SDES_CHUNKS = 31;

    /**
     * The minimum number of RTCP report blocks that an RR or an SR can
     * contain.
     */
    private static final int MIN_RTCP_REPORT_BLOCKS = 0;

    /**
     * A reusable array that holds {@link #MIN_RTCP_REPORT_BLOCKS}
     * <tt>RTCPReportBlock</tt>s.
     *
     * FIXME this should be somewhere else, probably inside the
     * RTCPReportBlock class.
     */
    public static final RTCPReportBlock[] MIN_RTCP_REPORT_BLOCKS_ARRAY
        = new RTCPReportBlock[MIN_RTCP_REPORT_BLOCKS];

    /**
     * The maximum transmission unit (MTU) to be assumed by
     * {@code BasicRTCPTerminationStrategy}.
     */
    private static final int MTU = 1024 + 256;

    /**
     * The RTP stats map that holds RTP statistics about all the streams that
     * this <tt>BasicRTCPTerminationStrategy</tt> (as a
     * <tt>TransformEngine</tt>) has observed.
     */
    private final RTPStatsMap rtpStatsMap = new RTPStatsMap();

    /**
     * The <tt>CNameRegistry</tt> holds the CNAMEs that this RTCP termination,
     * seen as a TransformEngine, has seen.
     */
    private final CNAMERegistry cnameRegistry = new CNAMERegistry();

    /**
     * The parser that parses <tt>RawPacket</tt>s to
     * <tt>RTCPCompoundPacket</tt>s.
     */
    private final RTCPPacketParserEx parser = new RTCPPacketParserEx();

    /**
     * The generator that generates <tt>RawPacket</tt>s from
     * <tt>RTCPCompoundPacket</tt>s.
     */
    private final RTCPGenerator generator = new RTCPGenerator();

    /**
     * The RTCP feedback gateway responsible for dropping all the stuff that
     * we support in this RTCP termination strategy.
     */
    private final FeedbackGateway feedbackGateway = new FeedbackGateway();

    /**
     * The garbage collector that cleans-up the state of this RTCP termination
     * strategy.
     */
    private final GarbageCollector garbageCollector = new GarbageCollector();

    /**
     * Takes care of calling the report() method of this RTCP termination
     * strategy when it's appropriate time.
     */
    private final RTCPReporter rtcpReporter = new RTCPReporter();

    /**
     * The RTP <tt>PacketTransformer</tt> of this
     * <tt>BasicRTCPTerminationStrategy</tt>.
     */
    private final PacketTransformer rtpTransformer
        = new SinglePacketTransformer(RTPPacketPredicate.INSTANCE)
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            // Update our RTP stats map (packets/octet sent).
            rtpStatsMap.apply(pkt);
            rtcpReporter.maybeReport();

            return pkt;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            // Let everything pass through.
            rtcpReporter.maybeReport();
            return pkt;
        }
    };

    /**
     * The RTCP <tt>PacketTransformer</tt> of this
     * <tt>BasicRTCPTerminationStrategy</tt>.
     */
    private final PacketTransformer rtcpTransformer
        = new SinglePacketTransformerAdapter(RTCPPacketPredicate.INSTANCE)
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            RTCPCompoundPacket compound;

            try
            {
                compound
                    = (RTCPCompoundPacket)
                        parser.parse(
                                pkt.getBuffer(),
                                pkt.getOffset(),
                                pkt.getLength());
            }
            catch (BadFormatException e)
            {
                logger.warn(
                        "Failed to terminate an RTCP packet. Dropping packet.");
                return null;
            }

            cnameRegistry.update(compound);

            // Remove SRs and RRs from the RTCP packet.
            return feedbackGateway.gateway(compound);
        }
    };

    /**
     * A counter that counts the number of times we've sent "full-blown" SDES.
     */
    private int sdesCounter = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return rtpTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    /**
     * Runs in the reporting thread and it generates RTCP reports for the
     * associated {@code MediaStream}.
     *
     * @return a {@code List} of {@code RawPacket}s representing the RTCP
     * compound packets to inject into the {@code MediaStream}.
     */
    public List<RawPacket> report()
    {
        garbageCollector.cleanup();

        // TODO Compound RTCP packets should not exceed the MTU of the network
        // path.
        //
        // An individual RTP participant should send only one compound RTCP
        // packet per report interval in order for the RTCP bandwidth per
        // participant to be estimated correctly, except when the compound
        // RTCP packet is split for partial encryption.
        //
        // If there are too many sources to fit all the necessary RR packets
        // into one compound RTCP packet without exceeding the maximum
        // transmission unit (MTU) of the network path, then only the subset
        // that will fit into one MTU should be included in each interval. The
        // subsets should be selected round-robin across multiple intervals so
        // that all sources are reported.
        //
        // It is impossible to know in advance what the MTU of path will be.
        // There are various algorithms for experimenting to find out, but many
        // devices do not properly implement (or deliberately ignore) the
        // necessary standards so it all comes down to trial and error. For that
        // reason, we can just guess 1200 or 1500 bytes per message.
        long time = System.currentTimeMillis();

        // RRs
        List<RTCPRRPacket> rrs = makeRRs(time);
        // SRs
        List<RTCPSRPacket> srs = makeSRs(time);

        // Bail out (early) if we have nothing to report.
        if ((rrs == null || rrs.isEmpty()) && (srs == null || srs.isEmpty()))
        {
            return null;
        }

        // REMB
        RTCPREMBPacket remb = makeREMB();

        // SDES
        List<RTCPSDESPacket> sdes = makeSDES();

        // Build the RTCPCompoundPackets to return.
        List<RTCPCompoundPacket> compounds = compound(rrs, srs, sdes, remb);

        // Return the RawPackets to inject into the MediaStream.
        List<RawPacket> rawPkts;

        if (compounds.isEmpty())
        {
            rawPkts = null;
        }
        else
        {
            rawPkts = new ArrayList<>(compounds.size());
            for (RTCPCompoundPacket compound : compounds)
            {
                rawPkts.add(generator.apply(compound));
            }
        }
        return rawPkts;
    }

    /**
     * Constructs a new {@code RTCPCompoundPacket}s out of specific SRs, RRs,
     * SDES, and other RTCP packets.
     *
     * @param rrs
     * @param srs
     * @param sdess the {@code RTCPSDESPacket} to include in the new
     * {@code RTCPCompoundPacket}. An SDES packet containing a CNAME item MUST
     * be included in each compound RTCP packet.
     * @param others other {@code RTCPPacket}s to be included in the new
     * {@code RTCPCompoundPacket}
     * @return a new {@code RTCPCompoundPacket} consisting of the specified
     * {@code srs}, {@code rrs}, {@code sdes}, and {@code others}
     */
    private List<RTCPCompoundPacket> compound(
            List<RTCPRRPacket> rrs,
            List<RTCPSRPacket> srs,
            List<RTCPSDESPacket> sdess,
            RTCPPacket... others)
    {
        // SRs are capable of carrying the report blocks of the RRs and thus of
        // reducing the compound packets's size.
        if (srs != null && !srs.isEmpty() && rrs != null && !rrs.isEmpty())
        {
            moveReportBlocks(rrs, srs);
        }

        List<RTCPCompoundPacket> compounds = new ArrayList<>();
        RTCPCompoundPacket prevCompound = null;

        do
        {
            // SR or RR.
            RTCPPacket report;
            int ssrc;

            if (srs != null && !srs.isEmpty())
            {
                RTCPSRPacket sr = srs.remove(0);

                report = sr;
                ssrc = sr.ssrc;
            }
            else if (rrs != null && !rrs.isEmpty())
            {
                RTCPRRPacket rr = rrs.remove(0);

                report = rr;
                ssrc = rr.ssrc;
            }
            else
            {
                break;
            }

            List<RTCPPacket> rtcps = new ArrayList<>();

            rtcps.add(report);

            // RTCP packets other than SR, RR, and SDES such as REMB.
            if (others.length > 0)
            {
                for (int i = 0; i < others.length; ++i)
                {
                    RTCPPacket other = others[i];

                    if (other != null)
                    {
                        rtcps.add(other);
                        // We've included the current other in the current
                        // compound packet and we don't want to include it in
                        // subsequent compound packets.
                        others[i] = null;
                    }
                }
            }

            if (sdess != null && sdess.size() != 0)
            {
                for (RTCPSDESPacket sdes : sdess)
                {
                    // SDES with CNAME for the SSRC of the SR or RR.
                    RTCPSDESItem cnameItem = findCNAMEItem(sdes, ssrc);

                    if (cnameItem != null)
                    {
                        RTCPSDES sdesOfReport = new RTCPSDES();

                        sdesOfReport.items = new RTCPSDESItem[] { cnameItem };
                        sdesOfReport.ssrc = ssrc;
                        rtcps.add(new RTCPSDESPacket(new RTCPSDES[] { sdesOfReport }));
                        break;
                    }
                }
            }

            RTCPCompoundPacket compound
                = new RTCPCompoundPacket(
                        rtcps.toArray(new RTCPPacket[rtcps.size()]));

            // Try to merge the current compound packet into the previous
            // compound packet.
            if (prevCompound == null || !tryMerge(prevCompound, compound))
            {
                compounds.add(compound);
                prevCompound = compound;
            }
        }
        while (true);

        return compounds;
    }

    /**
     * Merges {@code src} into {@code dst} if the resulting compound packet's
     * size does not exceed {@link #MTU}.
     *
     * @param dst the {@code RTCPCompoundPacket} to merge {@code src} into
     * @param src the {@code RTCPCompoundPacket} to merge into {@code dst}
     * @return {@code true} if {@code src} was merged into {@code dst};
     * otherwise, {@code false}
     */
    private boolean tryMerge(RTCPCompoundPacket dst, RTCPCompoundPacket src)
    {
        // The SDES of src may be merged into the SDES of dst (if any) in order
        // to reduce the resulting compound packet's size.

        // XXX For the sake of performance, we assume that SDES is the last
        // packet in the compound packets.
        RTCPSDESPacket dstSDES = null;
        RTCPSDESPacket srcSDES = null;
        boolean mergeSDES = false;

        // dstSDES
        if (dst.packets.length != 0)
        {
            RTCPPacket last;

            last = dst.packets[dst.packets.length - 1];
            if (last instanceof RTCPSDESPacket)
            {
                dstSDES = (RTCPSDESPacket) last;
                // srcSDES
                if (src.packets.length != 0)
                {
                    last = src.packets[src.packets.length - 1];
                    if (last instanceof RTCPSDESPacket)
                    {
                        srcSDES = (RTCPSDESPacket) last;
                        // mergeSDES
                        mergeSDES = true;
                    }
                }
            }
        }

        int newDstLen = dst.calcLength() + src.calcLength();

        if (mergeSDES)
        {
            // The SDES of src will be merged into the SDES of dst in order to
            // reduce the resulting compound packet's size.
            newDstLen -= 4;
        }

        if (newDstLen > MTU)
        {
            // Cannot merge src into dst because the resulting compound packet's
            // size would exceeed the MTU.
            return false;
        }

        int dstPktCount = dst.packets.length;
        int srcPktCount = src.packets.length;

        if (dstSDES != null)
        {
            // We'll take the SDES of dst into account separately.
            --dstPktCount;
        }

        // Merge the SDES of src into the SDES of dst.
        if (mergeSDES)
        {
            RTCPSDES[] newDstChunks
                = new RTCPSDES[dstSDES.sdes.length + srcSDES.sdes.length];

            System.arraycopy(
                    dstSDES.sdes, 0,
                    newDstChunks, 0,
                    dstSDES.sdes.length);
            System.arraycopy(
                    srcSDES.sdes, 0,
                    newDstChunks, dstSDES.sdes.length,
                    srcSDES.sdes.length);
            dstSDES.sdes = newDstChunks;

            // We've merged the SDES of src into the SDES of dst.
            --srcPktCount;
        }

        // Merge the non-SDES packets of src into dst.
        int newDstPktCount = dstPktCount + srcPktCount;

        if (dstSDES != null)
        {
            ++newDstPktCount;
        }

        RTCPPacket[] newDstPkts = new RTCPPacket[newDstPktCount];

        System.arraycopy(dst.packets, 0, newDstPkts, 0, dstPktCount);
        System.arraycopy(src.packets, 0, newDstPkts, dstPktCount, srcPktCount);
        // Keep SDES at the end.
        if (dstSDES != null)
        {
            newDstPkts[newDstPkts.length - 1] = dstSDES;
        }
        dst.packets = newDstPkts;

        return true;
    }

    /**
     * Finds the first {@code RTCPSDESItem} within a specific
     * {@code RTCPSDESPacket} which specifies the CNAME of a specific
     * synchronization source identifier (SSRC).
     *
     * @param sdes the {@code RTCPSDESPacket} to search through
     * @param ssrc the synchronization source identifier (SSRC) to find the
     * first CNAME of
     * @return the first {@code RTCPSDESItem} within {@code sdes} which
     * specifies the CNAME of {@code ssrc} if any; otherwise, {@code null}
     */
    private RTCPSDESItem findCNAMEItem(RTCPSDESPacket sdes, int ssrc)
    {
        for (RTCPSDES chunk : sdes.sdes)
        {
            if (chunk.ssrc == ssrc)
            {
                for (RTCPSDESItem item : chunk.items)
                {
                    if (item.type == RTCPSDESItem.CNAME)
                        return item;
                }
            }
        }
        return null;
    }

    /**
     * Moves report blocks from specific RRs into specific SRs.
     *
     * @param rrs the {@code List} of RRs to move report blocks from. If an RR
     * remains with no report blocks (after a possible move), it is removed from
     * the {@code List}.
     * @param srs the {@code List} of SRs to move report blocks to
     */
    private void moveReportBlocks(
            List<RTCPRRPacket> rrs,
            List<RTCPSRPacket> srs)
    {
        for (RTCPSRPacket sr : srs)
        {
            if (rrs.isEmpty())
            {
                // There are no (more) RRs to replace with SRs.
                break;
            }

            int srReportBlockCapacity
                = MAX_RTCP_REPORT_BLOCKS - sr.reports.length;

            if (srReportBlockCapacity <= 0)
            {
                // The current SR does not have the capacity to carry the report
                // blocks of RRs.
                continue;
            }

            for (Iterator<RTCPRRPacket> rrI = rrs.iterator(); rrI.hasNext();)
            {
                RTCPRRPacket rr = rrI.next();
                int reportBlocksToMove
                    = Math.min(srReportBlockCapacity, rr.reports.length);

                if (reportBlocksToMove > 0)
                {
                    if (srReportBlockCapacity == MAX_RTCP_REPORT_BLOCKS
                            && reportBlocksToMove == rr.reports.length)
                    {
                        // Since the SR appears to have the capacity to carry
                        // MAX_RTCP_REPORT_BLOCKS number of report blocks, then
                        // we assume its reports array is empty.
                        RTCPReportBlock[] srReportBlocks = sr.reports;

                        sr.reports = rr.reports;
                        rr.reports = srReportBlocks;
                    }
                    else
                    {
                        // Copy report blocks from the RR to the SR.
                        RTCPReportBlock[] srReportBlocks
                            = new RTCPReportBlock[
                                    sr.reports.length + reportBlocksToMove];

                        System.arraycopy(
                                sr.reports, 0,
                                srReportBlocks, 0,
                                sr.reports.length);
                        System.arraycopy(
                                rr.reports, 0,
                                srReportBlocks, sr.reports.length,
                                reportBlocksToMove);
                        sr.reports = srReportBlocks;

                        // Remove the copied report blocks from the RR.
                        int rrReportBlockCount
                            = rr.reports.length - reportBlocksToMove;
                        RTCPReportBlock[] rrReportBlocks
                            = new RTCPReportBlock[rrReportBlockCount];

                        System.arraycopy(
                                rr.reports, reportBlocksToMove,
                                rrReportBlocks, 0,
                                rrReportBlockCount);
                        rr.reports = rrReportBlocks;
                    }
                }
                if (rr.reports.length == 0)
                {
                    // The report blocks of the current RR will be carried by
                    // the current SR so the current RR is no longer necessary.
                    rrI.remove();
                }
            }
        }
    }

    /**
     * (attempts) to get the local SSRC that will be used in the media sender
     * SSRC field of the RTCP reports. TAG(cat4-local-ssrc-hurricane)
     * @return
     */
    private long getLocalSSRC()
    {
        return getStream().getStreamRTPManager().getLocalSSRC();
    }

    /**
     * Makes <tt>RTCPRRPacket</tt>s using information in FMJ.
     *
     * @param time
     * @return A <tt>List</tt> of <tt>RTCPRRPacket</tt>s to inject into the
     * <tt>MediaStream</tt>.
     */
    private List<RTCPRRPacket> makeRRs(long time)
    {
        RTCPReportBlock[] reportBlocks = makeReportBlocks(time);
        if (reportBlocks == null || reportBlocks.length == 0)
        {
            return null;
        }

        List<RTCPRRPacket> rrs = new ArrayList<>();

        // We use the stream's local source ID (SSRC) as the SSRC of packet
        // sender.
        int streamSSRC = (int) getLocalSSRC();

        // Since a maximum of 31 reception report blocks will fit in an SR
        // or RR packet, additional RR packets SHOULD be stacked after the
        // initial SR or RR packet as needed to contain the reception
        // reports for all sources heard during the interval since the last
        // report.
        if (reportBlocks.length > MAX_RTCP_REPORT_BLOCKS)
        {
            for (int offset = 0;
                    offset < reportBlocks.length;
                    offset += MAX_RTCP_REPORT_BLOCKS)
            {
                int blockCount
                    = Math.min(
                            reportBlocks.length - offset,
                            MAX_RTCP_REPORT_BLOCKS);
                RTCPReportBlock[] blocks = new RTCPReportBlock[blockCount];

                System.arraycopy(
                        reportBlocks, offset,
                        blocks, 0,
                        blocks.length);

                RTCPRRPacket rr = new RTCPRRPacket(streamSSRC, blocks);
                rrs.add(rr);
            }
        }
        else
        {
            RTCPRRPacket rr = new RTCPRRPacket(streamSSRC, reportBlocks);
            rrs.add(rr);
        }

        return rrs;
    }

    /**
     * Iterate through all the <tt>ReceiveStream</tt>s that this
     * <tt>MediaStream</tt> has and make <tt>RTCPReportBlock</tt>s for all of
     * them.
     *
     * @param time
     * @return
     */
    private RTCPReportBlock[] makeReportBlocks(long time)
    {
        MediaStream stream = getStream();
        // State validation.
        if (stream == null)
        {
            logger.warn("stream is null.");
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }

        StreamRTPManager streamRTPManager = stream.getStreamRTPManager();
        if (streamRTPManager == null)
        {
            logger.warn("streamRTPManager is null.");
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }

        Collection<ReceiveStream> receiveStreams;

        // XXX MediaStreamImpl's implementation of #getReceiveStreams() says
        // that, unfortunately, it has been observed that sometimes there are
        // valid ReceiveStreams in MediaStreamImpl which are not returned by
        // FMJ's RTPManager. Since (1) MediaStreamImpl#getReceiveStreams() will
        // include the results of StreamRTPManager#getReceiveStreams() and (2)
        // we are going to check the results against SSRCCache, it should be
        // relatively safe to rely on MediaStreamImpl's implementation.
        if (stream instanceof MediaStreamImpl)
        {
            receiveStreams = ((MediaStreamImpl) stream).getReceiveStreams();
        }
        else
        {
            receiveStreams = streamRTPManager.getReceiveStreams();
        }
        if (receiveStreams == null || receiveStreams.isEmpty())
        {
            logger.info(
                    "There are no receive streams to build report blocks for.");
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }

        SSRCCache cache = streamRTPManager.getSSRCCache();
        if (cache == null)
        {
            logger.info("cache is null.");
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }

        // Create and populate the return object.
        Collection<RTCPReportBlock> reportBlocks = new ArrayList<>();

        for (ReceiveStream receiveStream : receiveStreams)
        {
            // Dig into the guts of FMJ and get the stats for the current
            // receiveStream.
            SSRCInfo info = cache.cache.get((int) receiveStream.getSSRC());

            if (info == null)
            {
                logger.warn("We have a ReceiveStream but not an SSRCInfo for " +
                    "that ReceiveStream.");
                continue;
            }
            if (!info.ours && info.sender)
            {
                RTCPReportBlock reportBlock = info.makeReceiverReport(time);
                reportBlocks.add(reportBlock);
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
    private RTCPREMBPacket makeREMB()
    {
        // TODO we should only make REMBs if REMB support has been advertised.
        // Destination
        RemoteBitrateEstimator remoteBitrateEstimator
            = ((VideoMediaStream) getStream()).getRemoteBitrateEstimator();

        Collection<Integer> ssrcs = remoteBitrateEstimator.getSsrcs();

        // TODO(gp) intersect with SSRCs from signaled simulcast layers
        // NOTE(gp) The Google Congestion Control algorithm (sender side)
        // doesn't seem to care about the SSRCs in the dest field.
        long[] dest = new long[ssrcs.size()];
        int i = 0;

        for (Integer ssrc : ssrcs)
            dest[i++] = ssrc & 0xFFFFFFFFL;

        // Create and return the packet.
        // We use the stream's local source ID (SSRC) as the SSRC of packet
        // sender.
        long streamSSRC = getLocalSSRC();

        return
            makeREMB(
                    remoteBitrateEstimator,
                    streamSSRC, /* mediaSSRC */ 0L, dest);
    }

    /**
     * Makes an <tt>RTCPREMBPacket</tt> that provides receiver feedback to the
     * endpoint from which we receive.
     *
     * @param remoteBitrateEstimator
     * @param senderSSRC
     * @param mediaSSRC
     * @param dest
     * @return an <tt>RTCPREMBPacket</tt> that provides receiver feedback to the
     * endpoint from which we receive.
     */
    protected RTCPREMBPacket makeREMB(
            RemoteBitrateEstimator remoteBitrateEstimator,
            long senderSSRC, long mediaSSRC, long[] dest)
    {
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
            return new RTCPREMBPacket(senderSSRC, mediaSSRC, bitrate, dest);
        }
    }

    /**
     * Makes <tt>RTCPSRPacket</tt>s for all the RTP streams that we're sending.
     *
     * @return a <tt>List</tt> of <tt>RTCPSRPacket</tt> for all the RTP streams
     * that we're sending.
     */
    private List<RTCPSRPacket> makeSRs(long time)
    {
        MediaStreamImpl mediaStream = getMediaStreamImpl();
        List<RTCPSRPacket> srs = new ArrayList<>();

        for (RTPStatsEntry rtpStatsEntry : rtpStatsMap.values())
        {
            int ssrc = rtpStatsEntry.getSsrc();
            RemoteClock remoteClock
                = RemoteClock.findRemoteClock(mediaStream, ssrc);
            Timestamp remoteTs;

            if (remoteClock == null
                    || (remoteTs = remoteClock.estimate(time)) == null)
            {
                // We're not going to go far without an estimate.
                continue;
            }

            RTCPSRPacket sr
                = new RTCPSRPacket(ssrc, MIN_RTCP_REPORT_BLOCKS_ARRAY);

            // Set the NTP timestamp for this SR.
            long remoteSystemTimeMs = remoteTs.getSystemTimeMs();
            long remoteNtpTime = TimeUtils.toNtpTime(remoteSystemTimeMs);
            sr.ntptimestampmsw = TimeUtils.getMsw(remoteNtpTime);
            sr.ntptimestamplsw = TimeUtils.getLsw(remoteNtpTime);

            // Set the RTP timestamp.
            sr.rtptimestamp = remoteTs.getRtpTimestamp();

            // Fill-in packet and octet send count.
            sr.packetcount = rtpStatsEntry.getPacketsSent();
            sr.octetcount = rtpStatsEntry.getBytesSent();

            srs.add(sr);
        }

        return srs;
    }

    /**
     * Makes <tt>RTCPSDES</tt> packets for all the RTP streams that we're
     * sending.
     *
     * @return a <tt>List</tt> of <tt>RTCPSDES</tt> packets for all the RTP
     * streams that we're sending.
     */
    private List<RTCPSDESPacket> makeSDES()
    {
        // Create an SDES for our own SSRC.
        SSRCInfo ourinfo
            = getStream().getStreamRTPManager().getSSRCCache().ourssrc;
        Collection<RTCPSDESItem> ownItems = new ArrayList<>();

        // CNAME
        ownItems.add(
                new RTCPSDESItem(
                        RTCPSDESItem.CNAME,
                        ourinfo.sourceInfo.getCNAME()));

        // Throttle the source description bandwidth. See RFC3550#6.3.9
        // Allocation of Source Description Bandwidth.
        if (sdesCounter % 3 == 0)
        {
            SourceDescription sd;
            String d;

            if ((sd = ourinfo.name) != null
                    && (d = sd.getDescription()) != null)
            {
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.NAME, d));
            }
            if ((sd = ourinfo.email) != null
                    && (d = sd.getDescription()) != null)
            {
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.EMAIL, d));
            }
            if ((sd = ourinfo.phone) != null
                    && (d = sd.getDescription()) != null)
            {
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.PHONE, d));
            }
            if ((sd = ourinfo.loc) != null
                    && (d = sd.getDescription()) != null)
            {
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.LOC, d));
            }
            if ((sd = ourinfo.tool) != null
                    && (d = sd.getDescription()) != null)
            {
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.TOOL, d));
            }
            if ((sd = ourinfo.note) != null
                    && (d = sd.getDescription()) != null)
            {
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.NOTE, d));
            }
        }
        sdesCounter++;

        RTCPSDES ownSDES = new RTCPSDES();

        ownSDES.items = ownItems.toArray(new RTCPSDESItem[ownItems.size()]);
        ownSDES.ssrc = (int) getLocalSSRC();

        Collection<RTCPSDES> chunks = new ArrayList<>();

        chunks.add(ownSDES);

        Set<Map.Entry<Integer, byte[]>> entries = cnameRegistry.entrySet();
        List<RTCPSDESPacket> sdesPackets = new ArrayList<>(
            (int) Math.ceil((double) entries.size() / MAX_SDES_CHUNKS));

        for (Map.Entry<Integer, byte[]> entry : entries)
        {
            if (chunks.size() >= MAX_SDES_CHUNKS)
            {
                // RTCP SDES packets can have a maximum of 31 SDES chunks.
                sdesPackets.add(new RTCPSDESPacket(
                        chunks.toArray(new RTCPSDES[chunks.size()])));
                chunks = new ArrayList<>();
            }

            RTCPSDES sdes = new RTCPSDES();

            sdes.items
                = new RTCPSDESItem[]
                {
                    new RTCPSDESItem(RTCPSDESItem.CNAME, entry.getValue())
                };
            sdes.ssrc = entry.getKey();
            chunks.add(sdes);
        }

        if (chunks.size() > 0)
        {
            // Add the remaining chunks in a new SDES packet.
            sdesPackets.add(new RTCPSDESPacket(
                    chunks.toArray(new RTCPSDES[chunks.size()])));
        }

        return sdesPackets;
    }

    /**
     * The garbage collector runs at each reporting interval and cleans up
     * the data structures of this RTCP termination strategy based on the
     * SSRCs that the owner <tt>MediaStream</tt> is still sending.
     */
    class GarbageCollector
    {
        public void cleanup()
        {
            // TODO We need to fix TAG(cat4-local-ssrc-hurricane) and
            // TAG(cat4-remote-ssrc-hurricane) first. The idea is to remove
            // from our data structures everything that is not listed in as
            // a remote SSRC.
        }
    }

    /**
     * Removes receiver and sender feedback from RTCP packets. Typically this
     * means dropping SRs, RR report blocks and REMBs. It needs to pass through
     * PLIs, FIRs, NACKs, etc.
     */
    class FeedbackGateway
    {
        /**
         * Removes receiver and sender feedback from RTCP packets.
         *
         * TODO We need to be selective when gatewaying/sending receiver
         * feedback and only forward RTCP packets that are targeted to the
         * owning MediaStream. We can determine this by looking at the SSRCs
         * that the owning MediaStream receives. For example, we don't want
         * to send PLIs or FIRs to endpoints that are not concerned.
         *
         * @param inPacket the <tt>RTCPCompoundPacket</tt> to filter.
         * @return the filtered <tt>RawPacket</tt>.
         */
        public RawPacket gateway(RTCPCompoundPacket inPacket)
        {
            RTCPPacket[] inPackets;

            if (inPacket == null
                    || (inPackets = inPacket.packets) == null
                    || inPackets.length == 0)
            {
                logger.info("Ignoring empty RTCP packet.");
                return null;
            }

            List<RTCPPacket> outPackets = new ArrayList<>(inPackets.length);

            for (RTCPPacket rtcp : inPackets)
            {
                switch (rtcp.type)
                {
                case RTCPPacket.RR:
                case RTCPPacket.SR:
                case RTCPPacket.SDES:
                    // We generate our own RR/SR/SDES packets. We only want to
                    // forward NACKs/PLIs/etc.
                    break;
                case RTCPFBPacket.PSFB:
                    RTCPFBPacket psfb = (RTCPFBPacket) rtcp;
                    switch (psfb.fmt)
                    {
                    case RTCPREMBPacket.FMT:
                        // We generate its own REMB packets.
                        break;
                    default:
                        // We let through everything else, like NACK packets.
                        outPackets.add(psfb);
                        break;
                    }
                    break;
                default:
                    // We let through everything else, like BYE and APP packets.
                    outPackets.add(rtcp);
                    break;
                }
            }

            if (outPackets.isEmpty())
            {
                return null;
            }

            // We have feedback messages to send. Pack them in a compound RR and
            // send them. TODO Use RFC5506 Reduced-Size RTCP, if the receiver
            // supports it.
            Collection<RTCPRRPacket> rrs = makeRRs(System.currentTimeMillis());

            if (rrs != null && !rrs.isEmpty())
            {
                outPackets.addAll(0, rrs);
            }
            else
            {
                logger.warn("We might be sending invalid RTCPs.");
            }

            RTCPCompoundPacket outPacket
                = new RTCPCompoundPacket(
                        outPackets.toArray(new RTCPPacket[outPackets.size()]));

            return generator.apply(outPacket);
        }
    }

    /**
     * Takes care of calling the report() method every RTCP_INTERVAL_VIDEO_MS.
     */
    class RTCPReporter
    {
        /**
         * For video we use 500ms interval.
         */
        private static final int RTCP_INTERVAL_VIDEO_MS = 500;

        /**
        */
        private long nextTimeToSendRTCP;

        /**
         */
        public void maybeReport()
        {
            if (!timeToSendRTCPReport())
            {
                return;
            }

            // Make the RTCP reports for the assoc. MediaStream.
            List<RawPacket> pkts = report();

            if (pkts == null || pkts.isEmpty())
            {
                // Nothing was generated.
                return;
            }

            for (RawPacket pkt : pkts)
            {
                try
                {
                    getStream().injectPacket(
                            pkt,
                            /* data */ false,
                            BasicRTCPTerminationStrategy.this);

                    // TODO update transmission stats.
                    /*if (ssrcInfo instanceof SendSSRCInfo)
                    {
                        ((SendSSRCInfo) ssrcInfo).stats.total_rtcp++;
                        cache.sm.transstats.rtcp_sent++;
                    }
                    cache.updateavgrtcpsize(pkt.getLength());
                    if (cache.initial)
                        cache.initial = false;
                    if (!cache.rtcpsent)
                        cache.rtcpsent = true;*/
                }
                catch (TransmissionFailedException e)
                {
                    logger.error(e);
                    /*cache.sm.defaultstats
                        .update(OverallStats.TRANSMITFAILED, 1);
                    cache.sm.transstats.transmit_failed++;*/
                }
            }
        }

        private boolean timeToSendRTCPReport()
        {
            final long now = System.currentTimeMillis();

            if (now >= nextTimeToSendRTCP)
            {
                nextTimeToSendRTCP = now + RTCP_INTERVAL_VIDEO_MS;
                return true;
            }

            return false;
        }
    }
}
