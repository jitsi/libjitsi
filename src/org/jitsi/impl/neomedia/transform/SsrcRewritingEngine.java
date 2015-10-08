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
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.util.*;

import java.util.*;
import java.util.concurrent.*;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.*;
import org.jitsi.util.function.*;

/**
 * Rewrites source SSRCs {A, B, C, ...} to target SSRC A'. Note that this also
 * includes sequence number rewriting and RTCP SSRC and sequence number
 * rewriting, RTX and FEC/RED rewriting. It is also responsible of "BYEing" the
 * SSRCs it rewrites to. This class is not thread-safe unless otherwise stated.
 *
 * TODO SSRCs should be Longs, like everywhere else in libjitsi.
 *
 * @author George Politis
 */
public class SsrcRewritingEngine implements TransformEngine
{
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
    private static final int INVALID_SEQNUM = -1;

    /**
     * An int const indicating an invalid payload type.
     */
    private static final int UNMAP_PT = -1;

    /**
     * An int const indicating an invalid SSRC.
     */
    private static final int UNMAP_SSRC = 0;

    /**
     * Generates <tt>RawPacket</tt>s from <tt>RTCPCompoundPacket</tt>s.
     */
    private final RTCPGenerator generator = new RTCPGenerator();

    /**
     * Parses <tt>RTCPCompoundPacket</tt>s from <tt>RawPacket</tt>s.
     */
    private final RTCPPacketParserEx parser = new RTCPPacketParserEx();

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
    private Map<Integer, SsrcGroupRewriter> origin2rewriter;

    /**
     * A <tt>Map</tt> that maps target SSRCs to <tt>SsrcGroupRewriter</tt>s. It
     * can be used to quickly find an <tt>SsrcGroupRewriter</tt> by its SSRC.
     * Each target SSRC is mapped to a different <tt>SsrcGroupRewriter</tt>, so
     * this is a 1-1 map. We're wrapping the <tt>SsrcGroupRewriter</tt> in a
     * <tt>Tracked</tt> class so the engine instance can count how many source
     * SSRCs a target SSRC is rewriting. The purpose of this is to BYE target
     * SSRCs that no longer have source SSRCs.
     */
    private Map<Integer, Tracked<SsrcGroupRewriter>> target2rewriter;

    /**
     * Maps RTX SSRCs to primary SSRCs.
     */
    private Map<Integer, Integer> rtx2primary;

    /**
     * Maps SSRCs to RED payload type. The RED payload type is typically going
     * to be 116 but having it as a constant seems like an open invitation for
     * headaches.
     */
    private Map<Integer, Byte> ssrc2red;

    /**
     * Maps SSRCs to FEC payload type. The FEC payload type is typically going
     * to be 117 but having it as a constant seems like an open invitation for
     * headaches.
     */
    private Map<Integer, Byte> ssrc2fec;

    /**
     * The <tt>PacketTransformer</tt> that rewrites <tt>RawPacket</tt>s that
     * represent RTP packets. This <tt>PacketTransformer</tt> is an entry point
     * to this class.
     */
    private final SinglePacketTransformer rtpTransformer
        = new SinglePacketTransformer()
    {
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            if (!initialized || pkt == null)
            {
                return pkt;
            }

            // Use the SSRC of the RTP packet to find which
            // <tt>SsrcGroupRewriter</tt> to use.
            int ssrc = pkt.getSSRC();
            SsrcGroupRewriter ssrcGroupRewriter
                = origin2rewriter.get(ssrc);

            // If there is an <tt>SsrcGroupRewriter</tt>, rewrite the
            // package, otherwise return it unaltered.
            return (ssrcGroupRewriter == null)
                ? pkt : ssrcGroupRewriter.rewriteRTP(pkt);
        }

        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            // Just pass through, nothing to do here.
            return pkt;
        }
    };

    /**
     * The <tt>PacketTransformer</tt> that rewrites <tt>RawPacket</tt>s that
     * represent RTCP packets.
     */
    private final SinglePacketTransformer rtcpTransformer
        = new SinglePacketTransformer()
    {
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            if (!initialized || pkt == null)
            {
                return pkt;
            }

            // We want to rewrite each individual RTCP packet in an RTCP
            // compound packet. Furthermore, we want to do that with the
            // appropriate rewriter.
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
                logger.warn("Failed to rewrite an RTCP packet. " +
                    "Dropping packet.");
                return null;
            }

            if (inPacket == null || inPacket.packets == null
                || inPacket.packets.length == 0)
            {
                return pkt;
            }

            Collection<RTCPPacket> outRTCPPackets
                = new ArrayList<RTCPPacket>();

            // XXX It turns out that all the simulcast layers share the same
            // RTP timestamp starting offset. They also share the same NTP clock
            // and the same clock rate, so we don't need to do any modifications
            // to the RTP/RTCP timestamps. BUT .. if this assumption stops being
            // true, everything will probably stop working. You have been
            // warned.

            for (RTCPPacket inRTCPPacket : inPacket.packets)
            {
                // Use the SSRC of the RTCP packet to find which
                // <tt>SsrcGroupRewriter</tt> to use.

                // XXX we could move the RawPacket methods into a utils
                // class with static methods so that we don't have to create
                // new RawPacket's each time we want to use those methods.
                // For example, PacketBufferUtils sounds like an appropriate
                // name for this class.
                RawPacket p = new RawPacket(
                    inRTCPPacket.data,
                    inRTCPPacket.offset,
                    inRTCPPacket.length);

                int ssrc = p.getRTCPSSRC();

                SsrcGroupRewriter ssrcGroupRewriter
                    = origin2rewriter.get(ssrc);

                // If there is an <tt>SsrcGroupRewriter</tt>, rewrite
                // the package, otherwise include it unaltered.
                RTCPPacket outRTCPPacket = (ssrcGroupRewriter == null)
                    ? inRTCPPacket
                    : ssrcGroupRewriter.rewriteRTCP(inRTCPPacket);

                outRTCPPackets.add(outRTCPPacket);
            }

            RTCPPacket rtcpPackets[] = outRTCPPackets.toArray(
                new RTCPPacket[outRTCPPackets.size()]);

            RTCPCompoundPacket cp = new RTCPCompoundPacket(rtcpPackets);

            return generator.apply(cp);
        }

        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            // Just pass through, nothing to do here. We rely on RTCP
            // termination and NACK termination so that we don't have to
            // do any reverse rewriting (for now). This has the disadvantage
            // of.. requiring RTCP termination even in the simple case of
            // 1-1 calls but then again, in this case we don't need simulcast
            // (but we do need SSRC collision detection and conflict resolution).
            return pkt;
        }
    };

    /**
     * A boolean that indicates whether this transformer is enabled or not.
     */
    private boolean initialized = false;

    /**
     * Initializes some expensive ConcurrentHashMaps for this engine instance.
     */
    private synchronized void assertInitialized()
    {
        if (initialized)
        {
            return;
        }

        origin2rewriter
            = new ConcurrentHashMap<Integer, SsrcGroupRewriter>();
        target2rewriter
            = new HashMap<Integer, Tracked<SsrcGroupRewriter>>();

        rtx2primary = new ConcurrentHashMap<Integer, Integer>();

        ssrc2red = new ConcurrentHashMap<Integer, Byte>();

        ssrc2fec = new ConcurrentHashMap<Integer, Byte>();

        initialized = true;
    }

    /**
     */
    public PacketTransformer getRTPTransformer()
    {
        return rtpTransformer;
    }

    /**
     */
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    /**
     * Configures the <tt>SsrcRewritingEngine</tt> to rewrite an SSRC group
     * to the target SSRC. This method is thread-safe. Only one thread writes
     * to the maps at a time, but many can read.
     *
     * @param ssrcGroup the SSRC group to rewrite to the target SSRC.
     * @param rtxGroups maps RTX SSRCs to SSRCs.
     * @param ssrcTargetPrimary the target SSRC or 0 to unmap.
     * @param ssrcTargetRTX the target RTX SSRC.
     */
    public synchronized void map(
        final Set<Integer> ssrcGroup, final Integer ssrcTargetPrimary,
        final Map<Integer, Byte> ssrc2fec,
        final Map<Integer, Byte> ssrc2red,
        final Map<Integer, Integer> rtxGroups, final Integer ssrcTargetRTX)
    {
        assertInitialized();

        // Take care of the primary SSRCs.
        if (ssrcGroup != null && ssrcGroup.size() != 0)
        {
            for (Integer ssrcOrigPrimary : ssrcGroup)
            {
                map(ssrcOrigPrimary, ssrcTargetPrimary);
            }
        }

        // Take care of the RTX SSRCs.
        if (rtxGroups != null && rtxGroups.size() != 0)
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
        if (ssrc2fec != null && ssrc2fec.size() != 0)
        {
            for (Map.Entry<Integer, Byte> entry : ssrc2fec.entrySet())
            {
                if (entry.getValue() == UNMAP_PT)
                {
                    this.ssrc2fec.remove(entry.getKey());
                }
                else
                {
                    this.ssrc2fec.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Take care of RED PTs.
        if (ssrc2red != null && ssrc2red.size() != 0)
        {
            for (Map.Entry<Integer, Byte> entry : ssrc2red.entrySet())
            {
                if (entry.getValue() == UNMAP_PT)
                {
                    this.ssrc2red.remove(entry.getKey());
                }
                else
                {
                    this.ssrc2red.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // BYE target SSRCs that no longer have source/original SSRCs.
        // TODO we need a way to garbage collect target SSRCs that should have
        // been unmapped.
        Iterator<Map.Entry<Integer, Tracked<SsrcGroupRewriter>>> iterator
            = target2rewriter.entrySet().iterator();

        while (iterator.hasNext())
        {
            Map.Entry<Integer, Tracked<SsrcGroupRewriter>> entry
                = iterator.next();

            if (entry.getValue().getCounter() < 1)
            {
                entry.getValue().getTracked().close();
                iterator.remove();
            }
        }
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

        if (ssrcTarget != null && ssrcTarget != UNMAP_SSRC)
        {
            // Create an <tt>SsrcGroupRewriter</tt> for the target SSRC.
            if (!target2rewriter.containsKey(ssrcTarget))
            {
                target2rewriter.put(ssrcTarget,
                    new Tracked<SsrcGroupRewriter>(
                        new SsrcGroupRewriter(ssrcTarget)));
            }

            Tracked<SsrcGroupRewriter> trackedSsrcGroupRewriter
                = target2rewriter.get(ssrcTarget);

            // Link the original SSRC to the appropriate
            // <tt>SsrcGroupRewriter</tt>
            SsrcGroupRewriter oldSsrcGroupRewriter = origin2rewriter.put(
                ssrcOrig, trackedSsrcGroupRewriter.getTracked());

            if (oldSsrcGroupRewriter == null)
            {
                // We put one and nothing was removed, so we must increase.
                trackedSsrcGroupRewriter.increase();
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
                Tracked<SsrcGroupRewriter> trackedSsrcGroupRewriter =
                    target2rewriter.get(ssrcGroupRewriter.getSSRCTarget());

                trackedSsrcGroupRewriter.decrease();
            }
        }
    }

    /**
     * Does the actual work of rewriting a group of SSRCs to a target SSRC. This
     * class is not thread-safe.
     */
    class SsrcGroupRewriter
    {
        /**
         * A map of SSRCs to <tt>SsrcRewriter</tt>. Each SSRC that we rewrite in
         * this group rewriter has its own rewriter.
         */
        private final Map<Integer, SsrcRewriter> rewriters
            = new HashMap<Integer, SsrcRewriter>();

        /**
         * The target SSRC that the rewritten RTP packets will have. This is
         * shared between all the "child" <tt>SsrcRewriter</tt>s.
         */
        private final int ssrcTarget;

        /**
         * The sequence number is 16 bits it increments by one for each RTP
         * data packet sent, and may be used by the receiver to detect packet
         * loss and to restore packet sequence.
         */
        private int currentSeqnumBase;

        /**
         * The current <tt>SsrcRewriter</tt> that we use to rewrite source
         * SSRCs. The active rewriter is determined by the RTP packets that
         * we get.
         */
        private SsrcRewriter activeRewriter;

        /**
         * Ctor.
         *
         * @param ssrcTarget the target SSRC for this
         * <tt>SsrcGroupRewriter</tt>.
         */
        public SsrcGroupRewriter(Integer ssrcTarget)
        {
            this.ssrcTarget = ssrcTarget;
            this.currentSeqnumBase = new Random().nextInt(0x10000);
        }

        /**
         */
        public void close()
        {
            // TODO this means we need to BYE the targetSSRC. we need to include
            // sender and receiver reports in the compound packet. This needs to
            // blend-in nicely with RTCP termination. This actually needs to go
            // through the RTCP transmitter so that it can update the RTCP
            // transmission stats.
        }

        /**
         * Gets the target SSRC that the rewritten RTP packets will have.
         */
        public int getSSRCTarget()
        {
            return this.ssrcTarget;
        }

        /**
         *
         * @param pkt
         * @return
         */
        public RawPacket rewriteRTP(final RawPacket pkt)
        {
            if (pkt == null)
            {
                return pkt;
            }

            this.maybeSwitchActiveRewriter(pkt);

            return (activeRewriter == null)
                ? pkt : activeRewriter.rewriteRTP(pkt);
        }

        /**
         *
         * @param pkt
         * @return
         */
        public RTCPPacket rewriteRTCP(RTCPPacket pkt)
        {
            if (pkt == null)
            {
                return pkt;
            }

            return (activeRewriter == null)
                ? pkt : activeRewriter.rewriteRTCP(pkt);
        }

        /**
         * Maybe switches the {@link this.activeRewriter}.
         *
         * @param pkt the received packet that will determine the active
         * rewriter.
         */
        private void maybeSwitchActiveRewriter(final RawPacket pkt)
        {
            final int sourceSSRC = pkt.getSSRC();

            // This "if" block is not thread-safe but we don't expect multiple
            // threads to access this block all at the same time.
            if (!rewriters.containsKey(sourceSSRC))
            {
                rewriters.put(sourceSSRC, new SsrcRewriter(sourceSSRC));
            }

            if (activeRewriter != null
                && activeRewriter.getSourceSSRC() != sourceSSRC)
            {
                // Got a packet with a different SSRC from the one that the
                // current SsrcRewriter handles. Pause the current SsrcRewriter
                // and switch to the correct one.
                int len = activeRewriter.pause();
                currentSeqnumBase += (len + 1);
                activeRewriter = rewriters.get(sourceSSRC);
            }

            if (activeRewriter == null)
            {
                // We haven't initialized yet.
                activeRewriter = rewriters.get(sourceSSRC);
            }

            if (activeRewriter == null)
            {
                logger.warn("Don't know about this SSRC. This will never " +
                    "happen or somebody is messing with us.");
            }
        }

        /**
         * @param ssrcOrigin
         * @param sequenceNumber
         * @return
         */
        private int rewriteSequenceNumber(int ssrcOrigin, int sequenceNumber)
        {
            SsrcRewriter rewriter = rewriters.get(ssrcOrigin);
            if (rewriter == null)
            {
                return INVALID_SEQNUM;
            }

            SsrcRewriter.SequenceNumberInterval retransmissionInterval
                = rewriter.findRetransmissionInterval(sequenceNumber);

            if (retransmissionInterval == null)
            {
                return INVALID_SEQNUM;
            }
            else
            {
                int sn = retransmissionInterval.rewriteSequenceNumber(
                    sequenceNumber);
                return sn;
            }
        }

        /**
         * Rewrites SSRCs and sequence numbers of a given source SSRC. This
         * class is not thread-safe.
         */
        class SsrcRewriter
        {
            /**
             * The origin SSRC that this <tt>SsrcRewriter</tt> rewrites. The
             * target SSRC is managed by the parent <tt>SsrcGroupRewriter</tt>.
             */
            private final int sourceSSRC;

            /**
             * A <tt>NavigableMap</tt> that maps <tt>Integer</tt>s representing
             * interval maxes to <tt>SequenceNumberInterval</tt>s. So, when
             * we receive an RTP packet with given sequence number, we can
             * easily find in which sequence number interval it belongs, if it
             * does.
             */
            private final NavigableMap<Integer, SequenceNumberInterval>
                intervals = new TreeMap<Integer, SequenceNumberInterval>();

            /**
             * This is the current sequence number interval for this origin
             * SSRC. We can't have it in the intervals navigable map because
             * its max isn't determined yet. If this is null, then it means that
             * this original SSRC is paused (invariant).
             */
            private SequenceNumberInterval currentSequenceNumberInterval;

            /**
             * Ctor.
             *
             * @param sourceSSRC
             */
            public SsrcRewriter(int sourceSSRC)
            {
                this.sourceSSRC = sourceSSRC;
            }

            /**
             * Gets the source SSRC for this <tt>SsrcRewriter</tt>.
             */
            public int getSourceSSRC()
            {
                return this.sourceSSRC;
            }

            /**
             */
            public RawPacket rewriteRTP(RawPacket pkt)
            {
                // TODO take care of sequence number roll-overs. We can easily
                // do that by keeping track of NTP timestamps and by keeping
                // two interval sets, the current one and the previous one. It
                // doesn't make any sense to go any further behind the timeline.
                int seqnum = pkt.getSequenceNumber();

                // first, check if this is a retransmission and rewrite using
                // an appropriate interval.
                SequenceNumberInterval retransmissionInterval
                    = findRetransmissionInterval(seqnum);
                if (retransmissionInterval != null)
                {
                    logger.debug("Retransmitting packet with SEQNUM "
                        + seqnum + " of SSRC "
                        + (pkt.getSSRC() & 0xffffffffl)
                        + " from the current interval.");

                    return retransmissionInterval.rewriteRTP(pkt);
                }

                // this is not a retransmission.

                if (currentSequenceNumberInterval == null)
                {
                    // the stream has resumed.
                    logger.debug("SSRC " + (pkt.getSSRC() & 0xffffffffl)
                        + " has resumed.");
                    currentSequenceNumberInterval = new SequenceNumberInterval(
                        seqnum, currentSeqnumBase);
                }
                else
                {
                    // more packets to the stream, increase the sequence number
                    // interval range.
                    currentSequenceNumberInterval.max = seqnum;
                }

                return currentSequenceNumberInterval.rewriteRTP(pkt);
            }

            /**
             */
            public RTCPPacket rewriteRTCP(RTCPPacket pkt)
            {
                if (pkt == null)
                {
                    return pkt;
                }

                // 1. Change the media sender SSRC in all RTCP packets.
                // 2. Put the correct SR information. Reuse code from RTCP
                // termination.
                // 3. Be able to reverse transform RTCP packets, like NACKs.


                return pkt;
            }

            /**
             * Moves the current sequence number interval, in the
             * {@link this.intervals} tree. It is not to be updated anymore.
             *
             * @return the length of the sequence number interval that got
             * paused.
             */
            public int pause()
            {
                // TODO take into account roll-overs. introduce the notion of
                // cycles.
                int ret = 0;
                if (currentSequenceNumberInterval != null)
                {
                    logger.debug("Pausing SSRC "
                        + (this.sourceSSRC & 0xffffffffl) + ".");
                    ret = currentSequenceNumberInterval.length();
                    intervals.put(currentSequenceNumberInterval.max, currentSequenceNumberInterval);
                    currentSequenceNumberInterval = null;
                    return ret;
                }
                else
                {
                    // this stream is already paused.
                    logger.info("The stream is already paused.");
                }

                return ret;
            }

            /**
             * @param seqnumOrig
             * @return
             */
            public SequenceNumberInterval findRetransmissionInterval(
                int seqnumOrig)
            {
                // first check in the current sequence number interval.
                if (currentSequenceNumberInterval != null
                    && currentSequenceNumberInterval.contains(seqnumOrig))
                {
                    return currentSequenceNumberInterval;
                }

                // not there, try to find the sequence number in a previous
                // interval.
                Map.Entry<Integer, SequenceNumberInterval> candidateInterval
                    = intervals.higherEntry(seqnumOrig);

                if (candidateInterval != null
                    && candidateInterval.getValue().contains(seqnumOrig))
                {
                    return candidateInterval.getValue();
                }

                return null;
            }

            /**
             * Does the dirty job of rewriting SSRCs and sequence numbers of a
             * given sequence number interval of a given source SSRC.
             */
            class SequenceNumberInterval
            {
                /**
                 * The minimum sequence number of this interval.
                 */
                private final int min;

                /**
                 * When did this interval started wrt the target SSRC?
                 */
                private final int base;

                /**
                 * The maximum sequence number of this interval, potentially
                 * updated when we receive an RTP packet.
                 */
                private int max;

                /**
                 * Ctor.
                 *
                 * @param baseOrig
                 * @param baseTarget
                 */
                public SequenceNumberInterval(int baseOrig, int baseTarget)
                {
                    this.min = baseOrig;
                    this.max = baseOrig;
                    this.base = baseTarget;
                }

                /**
                 * Returns a boolean determining whether a sequence number
                 * is contained in this interval or not.
                 *
                 * @param x the sequence number to determine whether it belongs
                 * in the interval or not.
                 * @return true if the sequence number is contained in the
                 * interval, otherwise false.
                 */
                public boolean contains(int x)
                {
                    return min >= x && x <= max;
                }

                public String toString()
                {
                    return "[" + min + ", " + max + "]";
                }

                public int rewriteSequenceNumber(int sequenceNumber)
                {
                    int diff = sequenceNumber - min;
                    return base + diff;
                }

                /**
                 * @param pkt
                 */
                public RawPacket rewriteRTP(RawPacket pkt)
                {
                    // Rewrite the SSRC.
                    pkt.setSSRC(ssrcTarget);

                    // Rewrite the sequence number of the RTP packet.
                    int diff = pkt.getSequenceNumber() - min;
                    pkt.setSequenceNumber(base + diff);

                    Integer primarySSRC = rtx2primary.get(sourceSSRC);
                    if (primarySSRC == null)
                    {
                        primarySSRC = sourceSSRC;
                    }

                    boolean isRTX = rtx2primary.containsKey(sourceSSRC);

                    // Take care of RED.
                    byte pt = pkt.getPayloadType();
                    if (ssrc2red.get(sourceSSRC) == pt)
                    {
                        byte[] buf = pkt.getBuffer();
                        int off = pkt.getPayloadOffset() + ((isRTX) ? 2 : 0);
                        int len = pkt.getPayloadLength() - ((isRTX) ? 2 : 0);
                        this.rewriteRED(primarySSRC, buf, off, len);
                    }

                    // Take care of FEC.
                    if (ssrc2fec.get(sourceSSRC) == pt)
                    {
                        byte[] buf = pkt.getBuffer();
                        int off = pkt.getPayloadOffset() + ((isRTX) ? 2 : 0);
                        int len = pkt.getPayloadLength() - ((isRTX) ? 2 : 0);
                        // For the twisted case where we re-transmit a FEC
                        // packet in an RTX packet..
                        if (!this.rewriteFEC(primarySSRC, buf, off, len))
                        {
                            return null;
                        }
                    }

                    // Take care of RTX and return the packet.
                    return (!isRTX || this.rewriteRTX(pkt)) ? pkt : null;
                }

                /**
                 *
                 * @param pkt
                 * @return
                 */
                public boolean rewriteRTX(RawPacket pkt)
                {
                    // This is an RTX packet. Replace RTX OSN field or drop.
                    int ssrcOrig = rtx2primary.get(sourceSSRC);
                    int snOrig = pkt.getOriginalSequenceNumber() & 0xffff;

                    SsrcGroupRewriter rewriterPrimary = origin2rewriter.get(ssrcOrig);

                    int sequenceNumber
                        = rewriterPrimary.rewriteSequenceNumber(ssrcOrig, snOrig);
                    if (sequenceNumber == INVALID_SEQNUM)
                    {
                        // Translation did not return anything useful. Dropping.
                        return false;
                    }
                    else
                    {
                        pkt.setOriginalSequenceNumber((short) sequenceNumber);
                        return true;
                    }
                }

                /**
                 * Calculates and returns the length of this interval.
                 *
                 * @return the length of this interval.
                 */
                public int length()
                {
                    return max - min;
                }

                /**
                 *
                 * @param primarySSRC
                 * @param buf
                 * @param off
                 * @param len
                 */
                private void rewriteRED(int primarySSRC, byte[] buf, int off, int len)
                {
                    if (buf == null || buf.length == 0)
                    {
                        logger.warn("The buffer is empty.");
                        return;
                    }

                    if (buf.length < off + len)
                    {
                        logger.warn("The buffer is invalid.");
                        return;
                    }

                    // FIXME similar code can be found in the REDFilterTransformEngine
                    // and in REDTransformEngine.

                    int idx = off; //beginning of RTP payload
                    int pktCount = 0; //number of packets inside RED

                    // 0                   1                   2                   3
                    // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                    //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    //|F|   block PT  |  timestamp offset         |   block length    |
                    //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    while ((buf[idx] & 0x80) != 0)
                    {
                        pktCount++;
                        idx += 4;
                    }

                    idx = off; //back to beginning of RTP payload

                    int payloadOffset = idx + pktCount * 4 + 1 /* RED headers */;
                    for (int i = 0; i < pktCount; i++)
                    {
                        byte blockPT = (byte) (buf[idx] & 0x7f);
                        int blockLen = (buf[idx + 2] & 0x03) << 8 | (buf[idx + 3]);

                        if (ssrc2fec.get(sourceSSRC) == blockPT)
                        {
                            // TODO include only the FEC blocks that were
                            // successfully rewritten.
                            rewriteFEC(primarySSRC, buf, payloadOffset, blockLen);
                        }

                        idx += 4; // next RED header
                        payloadOffset += blockLen;
                    }
                }

                /**
                 * Rewrites the SN base in the FEC Header.
                 *
                 * TODO do we need to change any other fields? Look at the
                 * FECSender.
                 *
                 * @param buf
                 * @param off
                 * @param len
                 * @return true if the FEC was successfully rewritten, false
                 * otherwise
                 */
                private boolean rewriteFEC(int sourceSSRC, byte[] buf, int off, int len)
                {
                    if (buf == null || buf.length == 0)
                    {
                        logger.warn("The buffer is empty.");
                        return false;
                    }

                    if (buf.length < off + len)
                    {
                        logger.warn("The buffer is invalid.");
                        return false;
                    }

                    //  0                   1                   2                   3
                    //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    // |E|L|P|X|  CC   |M| PT recovery |            SN base            |
                    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    // |                          TS recovery                          |
                    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    // |        length recovery        |
                    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    int snBase = buf[off + 2] << 8 | buf[off + 3];

                    SsrcGroupRewriter rewriter
                        = origin2rewriter.get(sourceSSRC);

                    int snRewritenBase
                        = rewriter.rewriteSequenceNumber(sourceSSRC, snBase);


                    if (snRewritenBase == INVALID_SEQNUM)
                    {
                        logger.info("We could not find a sequence number " +
                            "interval for a FEC packet.");
                        return false;
                    }

                    buf[off + 2] = (byte) (snRewritenBase & 0xf0 >> 8);
                    buf[off + 3] = (byte) (snRewritenBase & 0x0f);
                    return true;
                }
            }
        }
    }

    /**
     * A helper class that can be used to track references to an object. This
     * class is not thread-safe.
     *
     * TODO find a more meaningful name, like TrackedReference, for example.
     */
    static class Tracked<T>
    {
        private final T tracked;

        private int counter;

        public Tracked(T tracked)
        {
            this.tracked = tracked;
            this.counter = 0;
        }

        public void increase()
        {
            counter++;
        }

        public void decrease()
        {
            counter--;
        }

        public int getCounter()
        {
            return this.counter;
        }

        public T getTracked()
        {
            return this.tracked;
        }
    }
}
