/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

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

import javax.media.rtp.*;
import java.util.*;
import java.util.concurrent.*;

/**
 *
 * The <tt>BasicRTCPTerminationStrategy</tt> "gateways" PLIs, FIRs, NACKs,
 * etc, in the sense that it replaces the packet sender information in the
 * PLIs, FIRs, NACKs, etc and it generates its own SRs/RRs/REMBs based on
 * information that it collects and from information found in FMJ.
 *
 * @author George Politis
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
     * The minimum number of RTCP report blocks that an RR or an SR can
     * contain.
     */
    private static final int MIN_RTCP_REPORT_BLOCKS = 0;

    /**
     * A reusable array that can be used to hold up to
     * <tt>MAX_RTCP_REPORT_BLOCKS</tt> <tt>RTCPReportBlock</tt>s. It is assumed
     * that a single thread is accessing this field at a given time.
     */
    private final RTCPReportBlock[] MAX_RTCP_REPORT_BLOCKS_ARRAY
        = new RTCPReportBlock[MAX_RTCP_REPORT_BLOCKS];

    /**
     * A reusable array that holds 0 <tt>RTCPReportBlock</tt>s.
     */
    private static final RTCPReportBlock[] MIN_RTCP_REPORTS_BLOCKS_ARRAY
        = new RTCPReportBlock[MIN_RTCP_REPORT_BLOCKS];

    /**
     * The RTP stats map that holds RTP statistics about all the streams that
     * this <tt>BasicRTCPTerminationStrategy</tt> (as a
     * <tt>TransformEngine</tt>) has observed.
     */
    private final RTPStatsMap rtpStatsMap = new RTPStatsMap();

    /**
     * The RTCP stats map that holds RTCP statistics about all the streams that
     * this <tt>BasicRTCPTerminationStrategy</tt> (as a
     * <tt>TransformEngine</tt>) has observed.
     */
    private final RemoteClockEstimator remoteClockEstimator =
        new RemoteClockEstimator();

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
     * The RTP <tt>PacketTransformer</tt> of this
     * <tt>BasicRTCPTerminationStrategy</tt>.
     */
    private final PacketTransformer rtpTransformer
        = new SinglePacketTransformer()
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            // Update our RTP stats map (packets/octet sent).
            rtpStatsMap.apply(pkt);

            return pkt;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            // Let everything pass through.
            return pkt;
        }
    };

    /**
     * The RTCP <tt>PacketTransformer</tt> of this
     * <tt>BasicRTCPTerminationStrategy</tt>.
     */
    private final PacketTransformer rtcpTransformer
        = new SinglePacketTransformer()
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            if (pkt == null)
            {
                return pkt;
            }

            RTCPCompoundPacket inPacket;
            try
            {
                inPacket = (RTCPCompoundPacket) parser.parse(
                    pkt.getBuffer(),
                    pkt.getOffset(),
                    pkt.getLength());
            }
            catch (BadFormatException e)
            {
                logger.warn("Failed to terminate an RTCP packet. " +
                    "Dropping packet.");
                return null;
            }

            // Update our RTCP stats map (timestamps). This operation is
            // read-only.
            remoteClockEstimator.apply(inPacket);

            cnameRegistry.update(inPacket);

            // Remove SRs and RRs from the RTCP packet.
            pkt = feedbackGateway.gateway(inPacket);

            return pkt;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            // Let everything pass through.
            return pkt;
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
     * {@inheritDoc}
     */
    @Override
    public RawPacket report()
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

        Collection<RTCPPacket> packets = new ArrayList<RTCPPacket>();

        // First, we build the RRs.
        Collection<RTCPRRPacket> rrPackets = makeRTCPRRPackets(time);
        if (rrPackets != null && rrPackets.size() != 0)
        {
            packets.addAll(rrPackets);
        }

        // Next, we build the SRs.
        Collection<RTCPSRPacket> srPackets = makeRTCPSRPackets(time);
        if (srPackets != null && srPackets.size() != 0)
        {
            packets.addAll(srPackets);
        }

        // Bail out if we have nothing to report.
        if (packets.size() == 0)
        {
            return null;
        }

        // Next, we build the REMB.
        RTCPREMBPacket rembPacket = makeRTCPREMBPacket();
        if (rembPacket != null)
        {
            packets.add(rembPacket);
        }

        // Finally, we add an SDES packet.
        RTCPSDESPacket sdesPacket = makeSDESPacket();
        if (sdesPacket != null)
        {
            packets.add(sdesPacket);
        }

        // Prepare the <tt>RTCPCompoundPacket</tt> to return.
        RTCPPacket rtcpPackets[]
            = packets.toArray(new RTCPPacket[packets.size()]);

        RTCPCompoundPacket cp = new RTCPCompoundPacket(rtcpPackets);

        // Build the <tt>RTCPCompoundPacket</tt> and return the
        // <tt>RawPacket</tt> to inject to the <tt>MediaStream</tt>.
        return generator.apply(cp);
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
     *
     * @return A <tt>Collection</tt> of <tt>RTCPRRPacket</tt>s to inject to the
     * <tt>MediaStream</tt>.
     */
    private Collection<RTCPRRPacket> makeRTCPRRPackets(long time)
    {
        RTCPReportBlock[] reportBlocks = makeRTCPReportBlocks(time);
        if (reportBlocks == null || reportBlocks.length == 0)
        {
            return null;
        }

        Collection<RTCPRRPacket> rrPackets = new ArrayList<RTCPRRPacket>();

        // We use the stream's local source ID (SSRC) as the SSRC of packet
        // sender.
        long streamSSRC = getLocalSSRC();

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
                RTCPReportBlock[] blocks
                    = (reportBlocks.length - offset < MAX_RTCP_REPORT_BLOCKS)
                    ? new RTCPReportBlock[reportBlocks.length - offset]
                    : MAX_RTCP_REPORT_BLOCKS_ARRAY;

                System.arraycopy(
                    reportBlocks, offset, blocks, 0, blocks.length);

                RTCPRRPacket rr
                    = new RTCPRRPacket((int) streamSSRC, blocks);
                rrPackets.add(rr);
            }
        }
        else
        {
            RTCPRRPacket rr
                = new RTCPRRPacket((int) streamSSRC, reportBlocks);
            rrPackets.add(rr);
        }

        return rrPackets;
    }

    /**
     * Iterate through all the <tt>ReceiveStream</tt>s that this
     * <tt>MediaStream</tt> has and make <tt>RTCPReportBlock</tt>s for all of
     * them.
     *
     * @param time
     * @return
     */
    private RTCPReportBlock[] makeRTCPReportBlocks(long time)
    {
        MediaStream stream = getStream();
        // State validation.
        if (stream == null)
        {
            logger.warn("stream is null.");
            return MIN_RTCP_REPORTS_BLOCKS_ARRAY;
        }

        StreamRTPManager streamRTPManager = stream.getStreamRTPManager();
        if (streamRTPManager == null)
        {
            logger.warn("streamRTPManager is null.");
            return MIN_RTCP_REPORTS_BLOCKS_ARRAY;
        }

        Collection<ReceiveStream> receiveStreams
            = streamRTPManager.getReceiveStreams();

        if (receiveStreams == null || receiveStreams.size() == 0)
        {
            logger.info("There are no receive streams to build report " +
                "blocks for.");
            return MIN_RTCP_REPORTS_BLOCKS_ARRAY;
        }

        SSRCCache cache = streamRTPManager.getSSRCCache();
        if (cache == null)
        {
            logger.info("cache is null.");
            return MIN_RTCP_REPORTS_BLOCKS_ARRAY;
        }

        // Create the return object.
        Collection<RTCPReportBlock> rtcpReportBlocks
            = new ArrayList<RTCPReportBlock>();

        // Populate the return object.
        for (ReceiveStream receiveStream : receiveStreams)
        {
            // Dig into the guts of FMJ and get the stats for the current
            // receiveStream.
            SSRCInfo info = cache.cache.get((int) receiveStream.getSSRC());

            if (!info.ours && info.sender)
            {
                RTCPReportBlock rtcpReportBlock = info.makeReceiverReport(time);
                rtcpReportBlocks.add(rtcpReportBlock);
            }
        }

        return rtcpReportBlocks.toArray(
            new RTCPReportBlock[rtcpReportBlocks.size()]);
    }

    /**
     * Makes an <tt>RTCPREMBPacket</tt> that provides receiver feedback to the
     * endpoint from which we receive.
     *
     * @return an <tt>RTCPREMBPacket</tt> that provides receiver feedback to the
     * endpoint from which we receive.
     */
    private RTCPREMBPacket makeRTCPREMBPacket()
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

        // Exp & mantissa
        long bitrate = remoteBitrateEstimator.getLatestEstimate();

        if (bitrate == -1)
            return null;

        if (logger.isDebugEnabled())
            logger.debug("Estimated bitrate: " + bitrate);

        // Create and return the packet.
        // We use the stream's local source ID (SSRC) as the SSRC of packet
        // sender.
        long streamSSRC = getLocalSSRC();

        return new RTCPREMBPacket(
            streamSSRC, /* mediaSSRC */ 0L, bitrate, dest);
    }

    /**
     * Makes <tt>RTCPSRPacket</tt>s for all the RTP streams that we're sending.
     *
     * @return a <tt>List</tt> of <tt>RTCPSRPacket</tt> for all the RTP streams
     * that we're sending.
     */
    private Collection<RTCPSRPacket> makeRTCPSRPackets(long time)
    {
        Collection<RTCPSRPacket> srPackets = new ArrayList<RTCPSRPacket>();

        for (RTPStatsEntry rtpStatsEntry : rtpStatsMap.values())
        {
            int ssrc = rtpStatsEntry.getSsrc();
            RemoteClock estimate = remoteClockEstimator.estimate(ssrc, time);
            if (estimate == null)
            {
                // We're not going to go far without an estimate..
                continue;
            }

            RTCPSRPacket srPacket
                = new RTCPSRPacket(ssrc, MIN_RTCP_REPORTS_BLOCKS_ARRAY);

            // Set the NTP timestamp for this SR.
            long estimatedRemoteTime = estimate.getRemoteTime();
            long secs = estimatedRemoteTime / 1000L;
            double fraction = (estimatedRemoteTime - secs * 1000L) / 1000D;
            srPacket.ntptimestamplsw = (int) (fraction * 4294967296D);
            srPacket.ntptimestampmsw = secs;

            // Set the RTP timestamp.
            srPacket.rtptimestamp = estimate.getRtpTimestamp();

            // Fill-in packet and octet send count.
            srPacket.packetcount = rtpStatsEntry.getPacketsSent();
            srPacket.octetcount = rtpStatsEntry.getBytesSent();

            srPackets.add(srPacket);
        }

        return srPackets;
    }

    /**
     * Makes <tt>RTCPSDES</tt> packets for all the RTP streams that we're
     * sending.
     *
     * @return a <tt>List</tt> of <tt>RTCPSDES</tt> packets for all the RTP
     * streams that we're sending.
     */
    private RTCPSDESPacket makeSDESPacket()
    {
        Collection<RTCPSDES> sdesChunks = new ArrayList<RTCPSDES>();

        // Create an SDES for our own SSRC.
        RTCPSDES ownSDES = new RTCPSDES();

        SSRCInfo ourinfo
            = getStream().getStreamRTPManager().getSSRCCache().ourssrc;
        ownSDES.ssrc = (int) getLocalSSRC();
        Collection<RTCPSDESItem> ownItems = new ArrayList<RTCPSDESItem>();
        ownItems.add(new RTCPSDESItem(
            RTCPSDESItem.CNAME, ourinfo.sourceInfo.getCNAME()));

        // Throttle the source description bandwidth. See RFC3550#6.3.9
        // Allocation of Source Description Bandwidth.

        if (sdesCounter % 3 == 0)
        {
            if (ourinfo.name != null && ourinfo.name.getDescription() != null)
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.NAME, ourinfo.name
                    .getDescription()));
            if (ourinfo.email != null && ourinfo.email.getDescription() != null)
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.EMAIL, ourinfo.email
                    .getDescription()));
            if (ourinfo.phone != null && ourinfo.phone.getDescription() != null)
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.PHONE, ourinfo.phone
                    .getDescription()));
            if (ourinfo.loc != null && ourinfo.loc.getDescription() != null)
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.LOC, ourinfo.loc
                    .getDescription()));
            if (ourinfo.tool != null && ourinfo.tool.getDescription() != null)
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.TOOL, ourinfo.tool
                    .getDescription()));
            if (ourinfo.note != null && ourinfo.note.getDescription() != null)
                ownItems.add(new RTCPSDESItem(RTCPSDESItem.NOTE, ourinfo.note
                    .getDescription()));
        }

        sdesCounter++;

        ownSDES.items = ownItems.toArray(new RTCPSDESItem[ownItems.size()]);

        sdesChunks.add(ownSDES);

        for (Map.Entry<Integer, byte[]> entry : cnameRegistry.entrySet())
        {
            RTCPSDES sdes = new RTCPSDES();
            sdes.ssrc = entry.getKey();
            sdes.items = new RTCPSDESItem[]
                {
                    new RTCPSDESItem(RTCPSDESItem.CNAME, entry.getValue())
                };
        }

        RTCPSDES[] sps = sdesChunks.toArray(new RTCPSDES[sdesChunks.size()]);
        RTCPSDESPacket sp = new RTCPSDESPacket(sps);

        return sp;
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
         * @param inPacket the <tt>RTCPCompoundPacket</tt> to filter.
         * @return the filtered <tt>RawPacket</tt>.
         */
        public RawPacket gateway(RTCPCompoundPacket inPacket)
        {
            if (inPacket == null
                || inPacket.packets == null || inPacket.packets.length == 0)
            {
                logger.info("Ignoring empty RTCP packet.");
                return null;
            }

            ArrayList<RTCPPacket> outPackets = new ArrayList<RTCPPacket>(
                inPacket.packets.length);

            for (RTCPPacket p : inPacket.packets)
            {
                switch (p.type)
                {
                    case RTCPPacket.RR:
                    case RTCPPacket.SR:
                    case RTCPPacket.SDES:
                        // We generate our own RR/SR/SDES packets. We only want
                        // to forward NACKs/PLIs/etc.
                        break;
                    case RTCPFBPacket.PSFB:
                        RTCPFBPacket psfb = (RTCPFBPacket) p;
                        switch (psfb.fmt)
                        {
                            case RTCPREMBPacket.FMT:
                                // We generate its own REMB packets.
                                break;
                            default:
                                // We let through everything else, like NACK
                                // packets.
                                outPackets.add(psfb);
                                break;
                        }
                        break;
                    default:
                        // We let through everything else, like BYE and APP
                        // packets.
                        outPackets.add(p);
                        break;
                }
            }

            if (outPackets.size() == 0)
            {
                return null;
            }

            // We have feedback messages to send. Pack them in a compound
            // RR and send them. TODO Use RFC5506 Reduced-Size RTCP, if the
            // receiver supports it.
            Collection<RTCPRRPacket> rrPackets
                = makeRTCPRRPackets(System.currentTimeMillis());

            if (rrPackets != null && rrPackets.size() != 0)
            {
                outPackets.addAll(0, rrPackets);
            }
            else
            {
                logger.warn("We might be sending invalid RTCPs.");
            }

            RTCPPacket[] pkts
                = outPackets.toArray(new RTCPPacket[outPackets.size()]);
            RTCPCompoundPacket outPacket = new RTCPCompoundPacket(pkts);

            return generator.apply(outPacket);
        }
    }

    /**
     * Holds the NTP timestamp and the associated RTP timestamp for a given
     * RTP stream.
     */
    class RemoteClock
    {
        /**
         * Ctor.
         *
         * @param remoteTime
         * @param rtpTimestamp
         */
        public RemoteClock(long remoteTime, int rtpTimestamp)
        {
            this.remoteTime = remoteTime;
            this.rtpTimestamp = rtpTimestamp;
        }

        /**
         * The last NTP timestamp that we received for {@link this.ssrc}
         * expressed in millis. Should be treated a signed long.
         */
        private final long remoteTime;

        /**
         * The RTP timestamp associated to {@link this.ntpTimestamp}. The RTP
         * timestamp is an unsigned int.
         */
        private final int rtpTimestamp;

        /**
         *
         * @return
         */
        public int getRtpTimestamp()
        {
            return rtpTimestamp;
        }

        /**
         *
         * @return
         */
        public long getRemoteTime()
        {
            return remoteTime;
        }
    }

    /**
     *
     */
    class ReceivedRemoteClock
    {
        /**
         * The SSRC.
         */
        private final int ssrc;

        /**
         * The <tt>RemoteClock</tt> which was received at
         * {@link this.receivedTime} for this RTP stream.
         */
        private final RemoteClock remoteClock;

        /**
         * The local time in millis when we received the RTCP report with the
         * RTP/NTP timestamps. It's a signed long.
         */
        private final long receivedTime;

        /**
         * The clock rate for {@link.ssrc}. We need to have received at least
         * two SRs in order to be able to calculate this. Unsigned short.
         */
        private final int frequencyHz;

        /**
         * Ctor.
         *
         * @param ssrc
         * @param remoteTime
         * @param rtpTimestamp
         * @param frequencyHz
         */
        ReceivedRemoteClock(int ssrc,
                            long remoteTime,
                            int rtpTimestamp,
                            int frequencyHz)
        {
            this.ssrc = ssrc;
            this.remoteClock = new RemoteClock(remoteTime, rtpTimestamp);
            this.frequencyHz = frequencyHz;
            this.receivedTime = System.currentTimeMillis();
        }

        /**
         *
         * @return
         */
        public RemoteClock getRemoteClock()
        {
            return remoteClock;
        }

        /**
         *
         * @return
         */
        public long getReceivedTime()
        {
            return receivedTime;
        }

        /**
         *
         * @return
         */
        public int getSsrc()
        {
            return ssrc;
        }

        /**
         *
         * @return
         */
        public int getFrequencyHz()
        {
            return frequencyHz;
        }
    }

    /**
     * The <tt>RTPStatsEntry</tt> class contains information about an outgoing
     * SSRC.
     */
    class RTPStatsEntry
    {
        /**
         * The SSRC of the stream that this instance tracks.
         */
        private final int ssrc;

        /**
         * The total number of _payload_ octets (i.e., not including header or
         * padding) transmitted in RTP data packets by the sender since
         * starting transmission up until the time this SR packet was
         * generated. This should be treated as an unsigned int.
         */
        private final int bytesSent;

        /**
         * The total number of RTP data packets transmitted by the sender
         * (including re-transmissions) since starting transmission up until
         * the time this SR packet was generated. Re-transmissions using an RTX
         * stream are tracked in the RTX SSRC. This should be treated as an
         * unsigned int.
         */
        private final int packetsSent;

        /**
         *
         * @return
         */
        public int getSsrc()
        {
            return ssrc;
        }

        /**
         *
         * @return
         */
        public int getBytesSent()
        {
            return bytesSent;
        }

        /**
         *
         * @return
         */
        public int getPacketsSent()
        {
            return packetsSent;
        }

        /**
         * Ctor.
         *
         * @param ssrc
         * @param bytesSent
         */
        RTPStatsEntry(int ssrc, int bytesSent, int packetsSent)
        {
            this.ssrc = ssrc;
            this.bytesSent = bytesSent;
            this.packetsSent = packetsSent;
        }
    }

    /**
     * The <tt>RtpStatsMap</tt> gathers stats from RTP packets that the
     * <tt>RTCPReportBuilder</tt> uses to build its reports.
     */
    class RTPStatsMap
            extends ConcurrentHashMap<Integer, RTPStatsEntry>
    {
        /**
         * Updates this <tt>RTPStatsMap</tt> with information it gets from the
         * <tt>RawPacket</tt>.
         *
         * @param pkt the <tt>RawPacket</tt> that is being transmitted.
         */
        public void apply(RawPacket pkt)
        {
            int ssrc = pkt.getSSRC();
            if (this.containsKey(ssrc))
            {
                RTPStatsEntry oldRtpStatsEntry = this.get(ssrc);

                // Replace whatever was in there before. A feature of the two's
                // complement encoding (which is used by Java integers) is that
                // the bitwise results for add, subtract, and multiply are the
                // same if both inputs are interpreted as signed values or both
                // inputs are interpreted as unsigned values. (Other encodings
                // like one's complement and signed magnitude don't have this
                // properly.)
                this.put(ssrc, new RTPStatsEntry(
                    ssrc, oldRtpStatsEntry.getBytesSent() + pkt.getLength()
                    - pkt.getHeaderLength() - pkt.getPaddingSize(),
                    oldRtpStatsEntry.getPacketsSent() + 1));
            }
            else
            {
                // Add a new <tt>RTPStatsEntry</tt> in this map.
                this.put(ssrc, new RTPStatsEntry(
                    ssrc, pkt.getLength()
                    - pkt.getHeaderLength() - pkt.getPaddingSize(),
                    1));
            }
        }
    }

    /**
     * A class that can be used to estimate the remote time at a given local
     * time.
     */
    class RemoteClockEstimator
    {
        /**
         * base: 7-Feb-2036 @ 06:28:16 UTC
         */
        private static final long msb0baseTime = 2085978496000L;

        /**
         * base: 1-Jan-1900 @ 01:00:00 UTC
         */
        private static final long msb1baseTime = -2208988800000L;

        /**
         * A map holding the received remote clocks.
         */
        private Map<Integer, ReceivedRemoteClock> receivedClocks
            = new ConcurrentHashMap<Integer, ReceivedRemoteClock>();

        /**
         * Inspect an <tt>RTCPCompoundPacket</tt> and build-up the state for
         * future estimations.
         *
         * @param pkt
         */
        public void apply(RTCPCompoundPacket pkt)
        {
            if (pkt == null || pkt.packets == null || pkt.packets.length == 0)
            {
                return;
            }

            for (RTCPPacket rtcpPacket : pkt.packets)
            {
                switch (rtcpPacket.type)
                {
                    case RTCPPacket.SR:
                        RTCPSRPacket srPacket = (RTCPSRPacket) rtcpPacket;

                        // The media sender SSRC.
                        int ssrc = srPacket.ssrc;

                        // Convert 64-bit NTP timestamp to Java standard time.
                        // Note that java time (milliseconds) by definition has
                        // less precision then NTP time (picoseconds) so
                        // converting NTP timestamp to java time and back to NTP
                        // timestamp loses precision. For example, Tue, Dec 17
                        // 2002 09:07:24.810 EST is represented by a single
                        // Java-based time value of f22cd1fc8a, but its NTP
                        // equivalent are all values ranging from
                        // c1a9ae1c.cf5c28f5 to c1a9ae1c.cf9db22c.

                        // Use round-off on fractional part to preserve going to
                        // lower precision
                        long fraction = Math.round(
                            1000D * srPacket.ntptimestamplsw / 0x100000000L);
                        /*
                         * If the most significant bit (MSB) on the seconds
                         * field is set we use a different time base. The
                         * following text is a quote from RFC-2030 (SNTP v4):
                         *
                         * If bit 0 is set, the UTC time is in the range
                         * 1968-2036 and UTC time is reckoned from 0h 0m 0s UTC
                         * on 1 January 1900. If bit 0 is not set, the time is
                         * in the range 2036-2104 and UTC time is reckoned from
                         * 6h 28m 16s UTC on 7 February 2036.
                         */
                        long msb = srPacket.ntptimestampmsw & 0x80000000L;
                        long remoteTime = (msb == 0)
                            // use base: 7-Feb-2036 @ 06:28:16 UTC
                            ? msb0baseTime
                                + (srPacket.ntptimestampmsw * 1000) + fraction
                            // use base: 1-Jan-1900 @ 01:00:00 UTC
                            : msb1baseTime
                                + (srPacket.ntptimestampmsw * 1000) + fraction;

                        // Estimate the clock rate of the sender.
                        int frequencyHz = -1;
                        if (receivedClocks.containsKey(ssrc))
                        {
                            // Calculate the clock rate.
                            ReceivedRemoteClock oldStats
                                = receivedClocks.get(ssrc);
                            RemoteClock oldRemoteClock
                                = oldStats.getRemoteClock();
                            frequencyHz = Math.round((float)
                                (((int) srPacket.rtptimestamp
                                    - oldRemoteClock.getRtpTimestamp())
                                        & 0xffffffffl)
                                / (remoteTime
                                    - oldRemoteClock.getRemoteTime()));
                        }

                        // Replace whatever was in there before.
                        receivedClocks.put(ssrc, new ReceivedRemoteClock(ssrc,
                            remoteTime, (int) srPacket.rtptimestamp,
                            frequencyHz));
                        break;
                    case RTCPPacket.SDES:
                        break;
                }
            }
        }

        /**
         * Estimate the <tt>RemoteClock</tt> of a given RTP stream (identified
         * by its SSRC) at a given time.
         *
         * @param ssrc the SSRC of the RTP stream whose <tt>RemoteClock</tt> we
         * want to estimate.
         * @param time the local time that will be mapped to a remote time.
         * @return An estimation of the <tt>RemoteClock</tt> at time "time".
         */
        public RemoteClock estimate(int ssrc, long time)
        {
            ReceivedRemoteClock receivedRemoteClock = receivedClocks.get(ssrc);
            if (receivedRemoteClock == null
                || receivedRemoteClock.getFrequencyHz() == -1)
            {
                // We can't continue if we don't have NTP and RTP timestamps
                // and/or the original sender frequency, so move to the next
                // one.
                return null;
            }

            long delayMillis = time - receivedRemoteClock.getReceivedTime();

            // Estimate the remote wall clock.
            long remoteTime
                = receivedRemoteClock.getRemoteClock().getRemoteTime();
            long estimatedRemoteTime = remoteTime + delayMillis;

            // Drift the RTP timestamp.
            int rtpTimestamp
                = receivedRemoteClock.getRemoteClock().getRtpTimestamp()
                + ((int) delayMillis) * (receivedRemoteClock.getFrequencyHz()
                    / 1000);
            return new RemoteClock(estimatedRemoteTime, rtpTimestamp);
        }
    }

    /**
     * Keeps track of the CNAMEs of the RTP streams that we've seen.
     */
    class CNAMERegistry
        extends ConcurrentHashMap<Integer, byte[]>
    {
        /**
         * @param inPacket
         */
        public void update(RTCPCompoundPacket inPacket)
        {
            // Update CNAMEs.
            if (inPacket == null || inPacket.packets == null
                || inPacket.packets.length == 0)
            {
                return;
            }

            for (RTCPPacket p : inPacket.packets)
            {
                switch (p.type)
                {
                    case RTCPPacket.SDES:
                        RTCPSDESPacket sdesPacket = (RTCPSDESPacket) p;
                        if (sdesPacket.sdes == null
                            || sdesPacket.sdes.length == 0)
                        {
                            continue;
                        }

                        for (RTCPSDES chunk : sdesPacket.sdes)
                        {
                            if (chunk.items == null || chunk.items.length == 0)
                            {
                                continue;
                            }

                            for (RTCPSDESItem sdesItm : chunk.items)
                            {
                                if (sdesItm.type != RTCPSDESItem.CNAME)
                                {
                                    continue;
                                }

                                this.put(chunk.ssrc, sdesItm.data);
                            }
                        }
                        break;
                }
            }
        }
    }
}
