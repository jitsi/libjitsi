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
package org.jitsi.impl.neomedia.transform.rewriting;

import java.util.*;
import java.util.concurrent.*;
import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.rtcp.termination.strategies.*;
import org.jitsi.util.function.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Rewrites source SSRCs {A, B, C, ...} to target SSRC A'. Note that this also
 * includes sequence number rewriting and RTCP SSRC and sequence number
 * rewriting, RTX and FEC/RED rewriting. It is also responsible of "BYEing" the
 * SSRCs it rewrites to. This class is not thread-safe unless otherwise stated.
 *
 * TODO we should be using Longs for SSRCs, because want a special value for
 * UNMAP_SSRC and also because it's like this everywhere else in libjitsi.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class SsrcRewritingEngine implements TransformEngine
{
    /**
     * The <tt>Random</tt> that generates initial sequence numbers. Instances of
     * {@code java.util.Random} are thread-safe since Java 1.7.
     */
    private static final Random RANDOM = new Random();

    /**
     * The <tt>Logger</tt> used by the <tt>SsrcRewritingEngine</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(SsrcRewritingEngine.class);

    /**
     * An int const indicating an invalid seqnum. One reason why we use integers
     * to represent sequence numbers is so that we can have this invalid
     * sequence number const.
     */
    static final int INVALID_SEQNUM = -1;

    /**
     * An int const indicating an invalid SSRC.
     *
     * FIXME 0 is actually a valid SSRC. That's one essential reason why we need
     * SSRCs to be represented by Longs and not Ints.
     */
    private static final int INVALID_SSRC = 0;

    /**
     * An int const indicating an unused SSRC. The usage of value 0 is also done
     * in RFCs.
     */
    private static final int UNUSED_SSRC = 0;

    /**
     * An int const indicating an invalid payload type.
     *
     * FIXME We could live with a short here.
     */
    private static final int UNMAP_PT = -1;

    /**
     * An int const indicating an invalid SSRC.
     *
     * FIXME 0 is actually a perfectly valid SSRC. That's one essential reason
     * why we need SSRCs to be represented by Longs and not Ints.
     */
    private static final int UNMAP_SSRC = 0;

    /**
     * The owner of this instance.
     */
    private final MediaStream mediaStream;

    /**
     * Generates <tt>RawPacket</tt>s from <tt>RTCPCompoundPacket</tt>s.
     */
    private final RTCPGenerator generator = new RTCPGenerator();

    /**
     * Parses <tt>RTCPCompoundPacket</tt>s from <tt>RawPacket</tt>s.
     */
    private final RTCPPacketParserEx parser = new RTCPPacketParserEx();

    /**
     * The <tt>SeqnumBaseKeeper</tt> of this instance.
     */
    private final SeqnumBaseKeeper seqnumBaseKeeper = new SeqnumBaseKeeper();

    /**
     * A <tt>Map</tt> that maps source SSRCs to <tt>SsrcGroupRewriter</tt>s. It
     * can be used to quickly find which <tt>SsrcGroupRewriter</tt> to use for
     * a specific RTP packet based on its SSRC. Multiple SSRCs can be mapped to
     * the same <tt>SsrcGroupRewriter</tt>, so this is not a 1-1 map.
     * <p/>
     * One other thing to note is that this map holds both primary and RTX
     * origin SSRCs and will hold RED/FEC SSRCs in the future as well, when
     * Chrome and/or other browsers implement it.
     * <p/>
     * We protect writing to this map with a synchronized method block but we
     * need a <tt>ConcurrentHashMap</tt> because there is potential race
     * condition when resizing a plain HashMap. An alternative solution would
     * be to use RWL or synchronized blocks. Not sure about the performance
     * diff, but locks for reading sound heavy.
     */
    Map<Integer, SsrcGroupRewriter> origin2rewriter;

    /**
     * A <tt>Map</tt> that maps target SSRCs to <tt>SsrcGroupRewriter</tt>s. It
     * can be used to quickly find an <tt>SsrcGroupRewriter</tt> by its SSRC.
     * Each target SSRC is mapped to a different <tt>SsrcGroupRewriter</tt>, so
     * this is a 1-1 map. We're wrapping the <tt>SsrcGroupRewriter</tt> in a
     * <tt>Tracked</tt> class so the engine instance can count how many source
     * SSRCs a target SSRC is rewriting. The purpose of this is to BYE target
     * SSRCs that no longer have source SSRCs.
     */
    private Map<Integer, RefCount<SsrcGroupRewriter>> target2rewriter;

    /**
     * Maps RTX SSRCs to primary SSRCs.
     *
     * FIXME On the other hand it seems wasteful to maintain this mapping here,
     * in the outbound direction. We should have an efficient way to find a
     * <tt>MediaStream</tt> by its RTX SSRC and extract the primary SSRC.
     */
    Map<Integer, Integer> rtx2primary;

    /**
     * Maps SSRCs to RED payload type. The RED payload type is typically going
     * to be 116 but having it as a constant seems like an open invitation for
     * headaches.
     *
     * FIXME On the other hand it seems wasteful to maintain this mapping here,
     * in the outbound direction. We should have an efficient way to find a
     * <tt>MediaStream</tt> by its SSRC and extract the RED PT.
     */
    Map<Integer, Byte> ssrc2red;

    /**
     * Maps SSRCs to FEC payload type. The FEC payload type is typically going
     * to be 117 but having it as a constant seems like an open invitation for
     * headaches.
     *
     * FIXME On the other hand it seems wasteful to maintain this mapping here,
     * in the outbound direction. We should have an efficient way to find a
     * <tt>MediaStream</tt> by its SSRC and extract the FEC PT.
     */
    Map<Integer, Byte> ssrc2fec;

    /**
     * The <tt>PacketTransformer</tt> that rewrites <tt>RawPacket</tt>s that
     * represent RTP packets. This <tt>PacketTransformer</tt> is an entry point
     * to this class.
     */
    private final SinglePacketTransformer rtpTransformer
        = new MyRTPSinglePacketTransformer();

    /**
     * The <tt>PacketTransformer</tt> that rewrites <tt>RawPacket</tt>s that
     * represent RTCP packets.
     */
    private final SinglePacketTransformer rtcpTransformer
        = new MyRTCPSinglePacketTransformer();

    /**
     * A boolean that indicates whether this transformer is enabled or not.
     */
    private boolean initialized = false;

    /**
     * Ctor.
     *
     * @param mediaStream the owner of this instance.
     */
    public SsrcRewritingEngine(MediaStream mediaStream)
    {
        this.mediaStream = mediaStream;
        logger.debug("Created a new SSRC rewriting engine.");
    }

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
     * Configures the {@code SsrcRewritingEngine} to rewrite an SSRC group to
     * the target SSRC. The method is thread-safe. Only one thread writes to the
     * maps at a time, but many can read.
     *
     * FIXME split this method in two for clarity: map and unmap.
     *
     * @param ssrcGroup the SSRC group to rewrite to the target SSRC.
     * @param ssrcTargetPrimary the target SSRC or {@code 0} to unmap.
     * @param ssrc2fec
     * @param ssrc2red
     * @param rtxGroups maps RTX SSRCs to SSRCs.
     * @param ssrcTargetRTX the target RTX SSRC.
     */
    public synchronized void map(
        final Set<Integer> ssrcGroup, final Integer ssrcTargetPrimary,
        final Map<Integer, Byte> ssrc2fec,
        final Map<Integer, Byte> ssrc2red,
        final Map<Integer, Integer> rtxGroups, final Integer ssrcTargetRTX)
    {
        // FIXME maps, again. What's wrong with simple arrays?
        if (!assertInitialized())
        {
            logger.warn("Failed to map/unmap because the SSRC rewriting engine is" +
                    "not initialized.");
            return;
        }

        // Map the primary SSRCs.
        if (ssrcGroup != null && !ssrcGroup.isEmpty())
        {
            for (Integer ssrcOrigPrimary : ssrcGroup)
            {
                map(ssrcOrigPrimary, ssrcTargetPrimary);
            }
        }

        // Map/unmap the RTX SSRCs to primary SSRCs.
        if (rtxGroups != null && !rtxGroups.isEmpty())
        {
            if (ssrcTargetRTX != null && ssrcTargetRTX != UNMAP_SSRC)
            {
                rtx2primary.putAll(rtxGroups);
            }
            else
            {
                rtx2primary.keySet().removeAll(rtxGroups.keySet());
            }

            for (Integer ssrcOrigRTX : rtxGroups.keySet())
            {
                map(ssrcOrigRTX, ssrcTargetRTX);
            }
        }

        // Take care of FEC PTs.
        putAll(ssrc2fec, this.ssrc2fec);

        // Take care of RED PTs.
        putAll(ssrc2red, this.ssrc2red);

        // BYE target SSRCs that no longer have source/original SSRCs.
        // TODO we need a way to garbage collect target SSRCs that should have
        // been unmapped.
        for (Iterator<RefCount<SsrcGroupRewriter>> i
                    = target2rewriter.values().iterator();
                i.hasNext();)
        {
            RefCount<SsrcGroupRewriter> refCount = i.next();

            if (refCount.get() < 1)
            {
                refCount.getReferent().close();
                i.remove();
            }
        }
    }

    /**
     * Copies all of the entries/mappings from {@code src} into {@code dst} in a
     * payload type-aware fashion i.e. values in {@code src} equal to
     * {@link #UNMAP_PT} are removed from {@code dst}.
     *
     * @param src the {@code Map} of synchronization source identifiers (SSRCs)
     * to payload types to copy from
     * @param dst the {@code Map} of SSRCs to payload types to copy into
     */
    private void putAll(Map<Integer, Byte> src, Map<Integer, Byte> dst)
    {
        if (src != null && !src.isEmpty())
        {
            for (Map.Entry<Integer, Byte> e : src.entrySet())
            {
                Integer ssrc = e.getKey();
                Byte pt = e.getValue();

                if (pt == UNMAP_PT)
                {
                    dst.remove(ssrc);
                }
                else
                {
                    dst.put(ssrc, pt);
                }
            }
        }
    }

    /**
     * Initializes some expensive ConcurrentHashMaps for this engine instance.
     */
    private synchronized boolean assertInitialized()
    {
        if (mediaStream == null)
        {
            logger.warn("This instance is not properly initialized because " +
                    "the stream is null.");
            return false;
        }

        if (initialized)
        {
            return true;
        }

        logger.debug("Initilizing the SSRC rewriting engine.");
        origin2rewriter = new ConcurrentHashMap<>();
        target2rewriter = new HashMap<>();
        rtx2primary = new ConcurrentHashMap<>();
        ssrc2red = new ConcurrentHashMap<>();
        ssrc2fec = new ConcurrentHashMap<>();

        initialized = true;

        return true;
    }

    /**
     * Sets up the engine so that ssrcOrig is rewritten to ssrcTarget.
     *
     * @param ssrcOrig
     * @param ssrcTarget
     */
    private synchronized void map(Integer ssrcOrig, Integer ssrcTarget)
    {
        if (ssrcOrig == null)
        {
            // Not so well played, caller.
            return;
        }

        if (logger.isDebugEnabled())
        {
            logger.debug(
                    "Configuring the SSRC rewriting engine to rewrite: "
                            + (ssrcOrig & 0xffffffffL) + " to "
                            + (ssrcTarget & 0xffffffffL));
        }

        if (ssrcTarget != null && ssrcTarget != UNMAP_SSRC)
        {
            RefCount<SsrcGroupRewriter> refCount
                = target2rewriter.get(ssrcTarget);

            if (refCount == null)
            {
                // Create an <tt>SsrcGroupRewriter</tt> for the target SSRC.
                refCount = new RefCount<>(
                    seqnumBaseKeeper.createSsrcGroupRewriter(this, ssrcTarget));
                target2rewriter.put(ssrcTarget, refCount);
            }

            // Link the original SSRC to the appropriate SsrcGroupRewriter.
            SsrcGroupRewriter oldSsrcGroupRewriter
                = origin2rewriter.put(ssrcOrig, refCount.getReferent());

            if (oldSsrcGroupRewriter == null)
            {
                // We put one and nothing was removed, so we must increase.
                refCount.increase();
            }
            else
            {
                // We put one but we removed one as well, so we're even.
            }
        }
        else
        {
            // Unmap the origin SSRC and the target SSRC.
            SsrcGroupRewriter ssrcGroupRewriter
                = origin2rewriter.remove(ssrcOrig);

            if (ssrcGroupRewriter != null)
            {
                RefCount<SsrcGroupRewriter> refCount
                    = target2rewriter.get(ssrcGroupRewriter.getSSRCTarget());

                refCount.decrease();
            }
        }
    }

    /**
     * Reverse rewrites the target SSRC into an origin SSRC based on the
     * currently active SSRC rewriter for that target SSRC.
     *
     * @param ssrc the target SSRC to rewrite into a source SSRC.
     */
    private int reverseRewriteSSRC(int ssrc)
    {
        // If there is an <tt>SsrcGroupRewriter</tt>, rewrite
        // the packet, otherwise include it unaltered.
        RefCount<SsrcGroupRewriter> refCount = target2rewriter.get(ssrc);
        SsrcGroupRewriter ssrcGroupRewriter;

        if (refCount != null)
        {
            ssrcGroupRewriter = refCount.getReferent();
        }
        else
        {
            return INVALID_SSRC;
        }
        if (ssrcGroupRewriter == null)
        {
            return INVALID_SSRC;
        }

        SsrcRewriter activeRewriter = ssrcGroupRewriter.getActiveRewriter();

        if (activeRewriter == null)
        {
            logger.debug(
                    "Could not find an SsrcRewriter for the RTCP packet type: ");
            return INVALID_SSRC;
        }

        return activeRewriter.getSourceSSRC();
    }

    /**
     * Gets the {@code MediaStream} which has initialized this instance and is
     * its owner.
     *
     * @return the {@code MediaStream} which has initialized this instance and
     * is its owner
     */
    public MediaStream getMediaStream()
    {
        return mediaStream;
    }

    /**
     * The <tt>PacketTransformer</tt> that rewrites <tt>RawPacket</tt>s that
     * represent RTP packets. This <tt>PacketTransformer</tt> is an entry point
     * to this class.
     */
    private class MyRTPSinglePacketTransformer
        extends SinglePacketTransformerAdapter
    {
        public MyRTPSinglePacketTransformer()
        {
            super(RTPPacketPredicate.INSTANCE);
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            if (pkt == null)
            {
                return pkt;
            }

            seqnumBaseKeeper.update(pkt);
            if (!initialized)
            {
                return pkt;
            }

            // Use the SSRC of the RTP packet to find which SsrcGroupRewriter to
            // use.
            int ssrc = pkt.getSSRC();
            SsrcGroupRewriter ssrcGroupRewriter = origin2rewriter.get(ssrc);

            // If there is an SsrcGroupRewriter, rewrite the packet; otherwise,
            // return it unaltered.
            if (ssrcGroupRewriter == null)
            {
                // We don't have a rewriter for this packet. Let's not freak
                // out about it, it's most probably a DTLS packet.
                return pkt;
            }
            else
            {
                return ssrcGroupRewriter.rewriteRTP(pkt);
            }
        }
    }

    /**
     * The <tt>PacketTransformer</tt> that rewrites <tt>RawPacket</tt>s that
     * represent RTCP packets.
     */
    private class MyRTCPSinglePacketTransformer
        extends SinglePacketTransformerAdapter
    {
        public MyRTCPSinglePacketTransformer()
        {
            super(RTCPPacketPredicate.INSTANCE);
        }

        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            if (!initialized)
            {
                return pkt;
            }

            // We want to rewrite each individual RTCP packet in an RTCP
            // compound packet. Furthermore, we want to do that with the
            // appropriate rewriter.
            RTCPPacket[] inPkts;

            try
            {
                RTCPCompoundPacket compound
                    = (RTCPCompoundPacket)
                        parser.parse(
                                pkt.getBuffer(),
                                pkt.getOffset(),
                                pkt.getLength());

                inPkts = (compound == null) ? null : compound.packets;
            }
            catch (BadFormatException e)
            {
                logger.error(
                        "Failed to rewrite an RTCP packet. Passing through.",
                        e);
                return pkt;
            }

            if (inPkts == null || inPkts.length == 0)
            {
                logger.warn(
                        "Weird! It seems we received an empty RTCP packet!");
                return pkt;
            }

            Collection<RTCPPacket> outPkts = new ArrayList<>();

            // XXX It turns out that all the simulcast layers share the same
            // RTP timestamp starting offset. They also share the same NTP clock
            // and the same clock rate, so we don't need to do any modifications
            // to the RTP/RTCP timestamps. BUT .. if this assumption stops being
            // true, everything will probably stop working. You have been
            // warned TAG(timestamp-uplifting).

            for (RTCPPacket inPkt : inPkts)
            {
                // Use the SSRC of the RTCP packet to find which
                // <tt>SsrcGroupRewriter</tt> to use.

                // XXX we could move the RawPacket methods into a utils
                // class with static methods so that we don't have to create
                // new RawPacket's each time we want to use those methods.
                // For example, PacketBufferUtils sounds like an appropriate
                // name for this class.
                switch (inPkt.type)
                {
                case RTCPPacket.RR:
                    break;
                case RTCPPacket.SR:
                    RTCPSRPacket sr = (RTCPSRPacket) inPkt;
                    sr.reports
                        = BasicRTCPTerminationStrategy
                            .MIN_RTCP_REPORT_BLOCKS_ARRAY;
                    outPkts.add(sr);
                    break;
                case RTCPPacket.SDES:
                case RTCPPacket.BYE:
                    // XXX Well, ideally we would put the correct SR
                    // information, by reusing code from RTCP termination.
                    // We would also be able to reverse transform RTCP
                    // packets, like NACKs.
                    outPkts.add(inPkt);
                    break;
                case RTCPFBPacket.PSFB:
                    // Get the synchronization source identifier of the
                    // media source that this piece of feedback information
                    // is related to.
                    RTCPFBPacket psfb = (RTCPFBPacket) inPkt;
                    int ssrc = (int) psfb.sourceSSRC;

                    if (ssrc != UNUSED_SSRC)
                    {
                        int reverseSSRC = reverseRewriteSSRC(ssrc);

                        if (reverseSSRC == INVALID_SSRC)
                        {
                            // We only really care if it's NOT a REMB.
                            logger.debug(
                                    "Could not find an SsrcGroupRewriter for"
                                        + " the RTCP packet: " + psfb);
                        }
                        else
                        {
                            psfb.sourceSSRC = reverseSSRC & 0xffffffffL;
                        }
                    }

                    switch (psfb.fmt)
                    {
                    case RTCPREMBPacket.FMT:
                        // Special handling for REMB messages.
                        RTCPREMBPacket remb = (RTCPREMBPacket) psfb;
                        long[] dest = remb.getDest();

                        if (dest != null && dest.length != 0)
                        {
                            for (int i = 0; i < dest.length; i++)
                            {
                                int reverseSSRC
                                    = reverseRewriteSSRC((int) dest[i]);
                                if (reverseSSRC == INVALID_SSRC)
                                {
                                    logger.debug(
                                            "Could not find an"
                                                + " SsrcGroupRewriter for the"
                                                + " RTCP packet: " + psfb);
                                }
                                else
                                {
                                    dest[i] = reverseSSRC & 0xffffffffL;
                                }
                            }
                        }
                        remb.setDest(dest);

                        if (logger.isTraceEnabled())
                        {
                            logger.trace(
                                    "Received estimated bitrate (bps): "
                                        + remb.getBitrate() + ", dest: "
                                        + Arrays.toString(dest)
                                        + ", time (ms): "
                                        + System.currentTimeMillis());
                        }
                        break;
                    }

                    outPkts.add(psfb);

                    break;
                case RTCPFBPacket.RTPFB:
                    RTCPFBPacket fb = (RTCPFBPacket) inPkt;
                    if (fb.fmt != NACKPacket.FMT)
                    {
                        logger.warn(
                                "Unhandled RTCP RTPFB packet (not a NACK): "
                                    + inPkt);
                    }
                    else
                    {
                        // NACK termination is taking care of NACKs.
                    }
                    break;
                default:
                    logger.warn(
                            "Unhandled RTCP (non RTPFB PSFB) packet: " + inPkt);
                    break;
                }
            }

            if (!outPkts.isEmpty())
            {
                return
                    generator.apply(
                            new RTCPCompoundPacket(
                                    outPkts.toArray(
                                            new RTCPPacket[outPkts.size()])));
            }
            else
            {
                return null;
            }
        }

        // We rely on RTCP termination and NACK termination so that we don't
        // have to do any reverse rewriting (for now). This has the disadvantage
        // of requiring RTCP termination even in the simple case of 1-1 calls
        // but then again, in this case we don't need simulcast (but we do need
        // SSRC collision detection and conflict resolution).
    }

    /**
     * This class holds the most recent outbound sequence number of the
     * associated <tt>MediaStream</tt>.
     *
     * FIXME We can get this information from FMJ. There's absolutely no need
     * for this class, but getting the information from FMJ needs some testing
     * first. This is a temporary fix.
     */
    private static class SeqnumBaseKeeper
    {
        /**
         * The <tt>Map</tt> that holds the latest and greatest sequence number
         * for a given SSRC.
         */
        private final Map<Integer, Integer> map = new HashMap<>();

        /**
         * The <tt>Comparator</tt> used to compare sequence numbers.
         */
        private static final SeqNumComparator seqNumComparator
            = new SeqNumComparator();

        /**
         *
         * @param pkt
         */
        public synchronized void update(RawPacket pkt)
        {
            int ssrc = pkt.getSSRC();
            int seqnum = pkt.getSequenceNumber();
            if (map.containsKey(ssrc))
            {
                int oldSeqnum = map.get(ssrc);
                if (seqNumComparator.compare(seqnum, oldSeqnum) == 1)
                {
                    map.put(ssrc, seqnum);
                }
            }
            else
            {
                map.put(ssrc, seqnum);
            }
        }

        /**
         *
         * @param ssrcRewritingEngine
         * @param ssrcTarget
         * @return
         */
        public synchronized SsrcGroupRewriter createSsrcGroupRewriter(
            SsrcRewritingEngine ssrcRewritingEngine, Integer ssrcTarget)
        {
            int seqnum;
            if (map.containsKey(ssrcTarget))
            {
                seqnum = map.get(ssrcTarget) + 1;
            }
            else
            {
                seqnum = RANDOM.nextInt(0x10000);
            }

            return
                new SsrcGroupRewriter(ssrcRewritingEngine, ssrcTarget, seqnum);
        }
    }
}
