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

import java.util.*;
import java.util.concurrent.*;
import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.*;
import org.jitsi.util.function.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.vp8.*;
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
     * An int const indicating an invalid SSRC.
     */
    private static final int INVALID_SSRC = 0;

    /**
     * An int const indicating an invalid payload type.
     */
    private static final int UNMAP_PT = -1;

    /**
     * An int const indicating an invalid SSRC.
     */
    private static final int UNMAP_SSRC = 0;

    /**
     * The max value of an unsigned short (2^16).
     */
    private static final int MAX_UNSIGNED_SHORT = 65536;

    /**
     * The median value of an unsigned short (2^15).
     */
    private static final int MEDIAN_UNSIGNED_SHORT = 32768;

    /**
     * We assume that if RETRANSMISSIONS_FRONTIER_MS have passed since we first
     * saw a sequence number, then that sequence number won't be retransmitted.
     */
    private static final long RETRANSMISSIONS_FRONTIER_MS = 30 * 1000;

    /**
     * The <tt>Random</tt> that generates initial sequence numbers. Instances of
     * {@code java.util.Random} are thread-safe since Java 1.7.
     */
    private static final Random random = new Random();

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
    private Map<Integer, RefCount<SsrcGroupRewriter>> target2rewriter;

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
        if (ssrcGroup != null && !ssrcGroup.isEmpty())
        {
            for (Integer ssrcOrigPrimary : ssrcGroup)
            {
                map(ssrcOrigPrimary, ssrcTargetPrimary);
            }
        }

        // Take care of the RTX SSRCs.
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
        Iterator<Map.Entry<Integer, RefCount<SsrcGroupRewriter>>> iterator
            = target2rewriter.entrySet().iterator();

        while (iterator.hasNext())
        {
            Map.Entry<Integer, RefCount<SsrcGroupRewriter>> entry
                = iterator.next();
            RefCount<SsrcGroupRewriter> refCount = entry.getValue();

            if (refCount.get() < 1)
            {
                refCount.getReferent().close();
                iterator.remove();
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
            for (Map.Entry<Integer, Byte> entry : src.entrySet())
            {
                Integer ssrc = entry.getKey();
                Byte pt = entry.getValue();

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
     * Utility method that prepends the receiver identifier to the printed
     * debug message.
     *
     * @param msg the debug message to print.
     */
    private void logDebug(String msg)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug(
                    mediaStream.getProperty(
                            MediaStream.PNAME_RECEIVER_IDENTIFIER)
                        + ": " + msg);
        }
    }

    /**
     * Utility method that prepends the receiver identifier to the printed
     * warn message.
     *
     * @param msg the warning message to print.
     */
    private void logWarn(String msg)
    {
        if (logger.isWarnEnabled())
        {
            logger.warn(
                    mediaStream.getProperty(
                            MediaStream.PNAME_RECEIVER_IDENTIFIER)
                        + ": " + msg);
        }
    }

     /**
      * Utility method that prepends the receiver identifier to the printed
      * error message.
      *
      * @param msg the error message to print.
      * @param t the throwable that caused the error.
     */
    private void logError(String msg, Throwable t)
    {
        logger.error(
                mediaStream.getProperty(MediaStream.PNAME_RECEIVER_IDENTIFIER)
                    + ": " + msg, t);
    }

     /**
      * Utility method that prepends the receiver identifier to the printed
      * info message.
      *
      *  @param msg the info message to print.
      */
    private void logInfo(String msg)
    {
        if (logger.isInfoEnabled())
        {
            logger.info(
                    mediaStream.getProperty(
                            MediaStream.PNAME_RECEIVER_IDENTIFIER)
                        + ": " + msg);
        }
    }

    /**
     * Initializes some expensive ConcurrentHashMaps for this engine instance.
     */
    private synchronized void assertInitialized()
    {
        if (initialized)
        {
            return;
        }

        origin2rewriter = new ConcurrentHashMap<>();
        target2rewriter = new HashMap<>();
        rtx2primary = new ConcurrentHashMap<>();
        ssrc2red = new ConcurrentHashMap<>();
        ssrc2fec = new ConcurrentHashMap<>();

        initialized = true;
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
                target2rewriter.put(
                        ssrcTarget,
                        new RefCount<>(new SsrcGroupRewriter(ssrcTarget)));
            }

            RefCount<SsrcGroupRewriter> trackedSsrcGroupRewriter
                = target2rewriter.get(ssrcTarget);

            // Link the original SSRC to the appropriate
            // <tt>SsrcGroupRewriter</tt>
            SsrcGroupRewriter oldSsrcGroupRewriter = origin2rewriter.put(
                ssrcOrig, trackedSsrcGroupRewriter.getReferent());

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
                RefCount<SsrcGroupRewriter> trackedSsrcGroupRewriter =
                    target2rewriter.get(ssrcGroupRewriter.getSSRCTarget());

                trackedSsrcGroupRewriter.decrease();
            }
        }
    }

    /**
     * Reverse rewrites the target SSRC into an origin SSRC based on the
     * currently active SSRC rewriter for that target SSRC.
     * @param targetSSRC
     */
    private int reverseRewriteSSRC(int ssrc)
    {
        // If there is an <tt>SsrcGroupRewriter</tt>, rewrite
        // the packet, otherwise include it unaltered.
        RefCount<SsrcGroupRewriter> trackedSsrcGroupRewriter
            = target2rewriter.get(ssrc);

        SsrcGroupRewriter ssrcGroupRewriter;
        if (trackedSsrcGroupRewriter != null)
        {
            ssrcGroupRewriter
                = trackedSsrcGroupRewriter.getReferent();
        }
        else
        {
            return INVALID_SSRC;
        }

        if (ssrcGroupRewriter == null)
        {
            return INVALID_SSRC;
        }

        SsrcGroupRewriter.SsrcRewriter activeRewriter
            = ssrcGroupRewriter.getActiveRewriter();

        if (activeRewriter == null)
        {

            logDebug("Could not find an SsrcRewriter for " +
                    "the RTCP packet type: ");
            return INVALID_SSRC;
        }

        return activeRewriter.getSourceSSRC();
    }

    /**
     * The <tt>PacketTransformer</tt> that rewrites <tt>RawPacket</tt>s that
     * represent RTP packets. This <tt>PacketTransformer</tt> is an entry point
     * to this class.
     */
    class MyRTPSinglePacketTransformer
        extends SinglePacketTransformerAdapter
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
            // packet, otherwise return it unaltered.
            return (ssrcGroupRewriter == null)
                ? pkt : ssrcGroupRewriter.rewriteRTP(pkt);
        }
    };

    /**
     * The <tt>PacketTransformer</tt> that rewrites <tt>RawPacket</tt>s that
     * represent RTCP packets.
     */
    class MyRTCPSinglePacketTransformer
        extends SinglePacketTransformerAdapter
    {
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
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
                logError("Failed to rewrite an RTCP packet. " +
                    "Dropping packet.", e);
                return null;
            }

            if (inPacket == null || inPacket.packets == null
                || inPacket.packets.length == 0)
            {
                return pkt;
            }

            Collection<RTCPPacket> outRTCPPackets = new ArrayList<>();

            // XXX It turns out that all the simulcast layers share the same
            // RTP timestamp starting offset. They also share the same NTP clock
            // and the same clock rate, so we don't need to do any modifications
            // to the RTP/RTCP timestamps. BUT .. if this assumption stops being
            // true, everything will probably stop working. You have been
            // warned TAG(timestamp-uplifting).

            for (RTCPPacket inRTCPPacket : inPacket.packets)
            {
                // Use the SSRC of the RTCP packet to find which
                // <tt>SsrcGroupRewriter</tt> to use.

                // XXX we could move the RawPacket methods into a utils
                // class with static methods so that we don't have to create
                // new RawPacket's each time we want to use those methods.
                // For example, PacketBufferUtils sounds like an appropriate
                // name for this class.
                switch (inRTCPPacket.type)
                {
                case RTCPPacket.RR:
                case RTCPPacket.SR:
                case RTCPPacket.SDES:
                    // FIXME Is it a good thing to eat up thos (no!)? How
                    // is FMJ supposed to know what's going on?
                case RTCPPacket.BYE:
                    // RTCP termination takes care of all these, so we
                    // don't include them in the outPacket..

                    // XXX Well, ideally we would put the correct SR
                    // information, by reusing code from RTCP termination.
                    // We would also be able to reverse transform RTCP
                    // packets, like NACKs.
                    break;
                case RTCPFBPacket.PSFB:
                    // Get the synchronization source identifier of the
                    // media source that this piece of feedback information
                    // is related to.
                    RTCPFBPacket psfb = (RTCPFBPacket) inRTCPPacket;
                    int ssrc = (int) psfb.sourceSSRC;

                    if (ssrc != 0)
                    {
                        int reverseSSRC = reverseRewriteSSRC(ssrc);

                        if (reverseSSRC == INVALID_SSRC)
                        {
                            // We only really care if it's NOT a REMB.
                            logDebug("Could not find an " +
                                    "SsrcGroupRewriter for the RTCP packet: " +
                                    psfb.toString());
                        }
                        else
                        {
                            psfb.sourceSSRC = reverseSSRC & 0xffffffffl;
                        }
                    }

                    switch (psfb.type)
                    {
                    case RTCPREMBPacket.FMT:
                        // Special handling for REMB messages.
                        RTCPREMBPacket remb = (RTCPREMBPacket) psfb;
                        long[] dest = remb.getDest();
                        if (dest != null && dest.length != 0)
                        {
                            for (int i = 0; i < dest.length; i++)
                            {
                                int reverseSSRC = reverseRewriteSSRC((int) dest[i]);
                                if (reverseSSRC == INVALID_SSRC)
                                {
                                    logDebug("Could not find an " +
                                            "SsrcGroupRewriter for the RTCP packet: " +
                                            psfb.toString());
                                }
                                else
                                {
                                    dest[i] = reverseSSRC & 0xffffffffl;
                                }
                            }
                        }

                        remb.setDest(dest);
                        break;
                    }

                    outRTCPPackets.add(psfb);

                    break;
                case RTCPFBPacket.RTPFB:
                    RTCPFBPacket psfb1 = (RTCPFBPacket) inRTCPPacket;
                    if (psfb1.fmt != 1)
                    {
                        logWarn(
                                "Unhandled RTCP packet: " + inRTCPPacket.toString());
                    }
                    break;
                default:
                    logWarn(
                        "Unhandled RTCP packet: " + inRTCPPacket.toString());
                    break;
                }
            }

            if (!outRTCPPackets.isEmpty())
            {
                RTCPPacket rtcpPackets[] = outRTCPPackets.toArray(
                    new RTCPPacket[outRTCPPackets.size()]);

                RTCPCompoundPacket cp = new RTCPCompoundPacket(rtcpPackets);

                return generator.apply(cp);
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
    };

    /**
     * Does the actual work of rewriting a group of SSRCs to a target SSRC. This
     * class is not thread-safe.
     */
    public class SsrcGroupRewriter
    {
        /**
         * A map of SSRCs to <tt>SsrcRewriter</tt>. Each SSRC that we rewrite in
         * this group rewriter has its own rewriter.
         */
        private final Map<Integer, SsrcRewriter> rewriters = new HashMap<>();

        /**
         * The target SSRC that the rewritten RTP packets will have. This is
         * shared between all the "child" <tt>SsrcRewriter</tt>s.
         */
        private final int ssrcTarget;

        /**
         * The low 16 bits contain the base sequence number sent in RTP data
         * packet for {#ssrcTarget} for this cycle, and the most significant 16
         * bits extend that sequence number with the corresponding count of
         * sequence number cycles.
         */
        private int currentExtendedSeqnumBase;

        /**
         * Holds the max RTP timestamp that we've sent (to the endpoint).
         *
         * Ideally, what we should do is fully rewrite the timestamps, unless we
         * can take advantage of some knowledge of the system. We have observed
         * that in the Chromium simulcast implementation the initial timestamp
         * value is the same for all the simulcast streams. Since they also
         * share the same NTP clock, we can conclude that the RTP timestamps
         * advance at the same rate. This means that we don't have to rewrite
         * the RTP timestamps TAG(timestamp-uplifting).
         *
         * A small problem occurs when a stream switch happens: When a stream
         * switches we request a keyframe for the stream we want to switch into.
         * The problem is that the frames are sampled at different times, so
         * we might end up with a key frame that is one sampling cycle behind
         * what we were already streaming. We hack around this by implementing
         * "RTP timestamp uplifting".
         *
         * When a switch occurs, we store the maximum timestamp that we've sent
         * to an endpoint. If we observe new packets (NOT retransmissions) with
         * timestamp older than what the endpoint has already seen, we overwrite
         * the timestamp with maxTimestamp + 1.
         */
        private long maxTimestamp;

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
            this.currentExtendedSeqnumBase = random.nextInt(0x10000);
        }

        /**
         * Closes this instance and sends an RTCP BYE packet for the target
         * SSRC.
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
         * Gets the active <tt>SsrcRewriter</tt> of this instance.
         *
         * @return the active <tt>SsrcRewriter</tt> of this instance.
         */
        public SsrcRewriter getActiveRewriter()
        {
            return activeRewriter;
        }

        public Collection<SsrcRewriter> getRewriters()
        {
            return rewriters.values();
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
                logDebug("Now rewriting " + (pkt.getSSRC() & 0xffffffffl)
                    + " to " + (ssrcTarget & 0xffffffffl) + " (was rewriting "
                    + (activeRewriter.getSourceSSRC() & 0xffffffffl) + ").");

                // We don't have to worry about sequence number intervals that
                // span multiple sequence number cycles because the extended
                // sequence number interval length is 32 bits.
                SsrcRewriter.ExtendedSequenceNumberInterval currentInterval
                    = activeRewriter.getCurrentExtendedSequenceNumberInterval();

                int currentIntervalLength
                    = currentInterval == null ? 0 : currentInterval.length();

                // Pause the active rewriter (closes its current interval and
                // puts it in the interval tree).
                activeRewriter.pause();
                if (logger.isDebugEnabled())
                {
                    boolean isKeyFrame;
                    byte redPT = ssrc2red.get(sourceSSRC);
                    if (redPT == pkt.getPayloadType())
                    {
                        REDBlockIterator.REDBlock block
                            = REDBlockIterator.getPrimaryBlock(pkt);

                        if (block != null)
                        {
                            // FIXME What if we're not using VP8?
                            isKeyFrame = DePacketizer.isKeyFrame(
                                        pkt.getBuffer(),
                                        block.getBlockOffset(),
                                        block.getBlockLength());
                        }
                        else
                        {
                            isKeyFrame = false;
                        }
                    }
                    else
                    {
                        // FIXME What if we're not using VP8?
                        isKeyFrame = DePacketizer.isKeyFrame(
                                    pkt.getBuffer(),
                                    pkt.getPayloadOffset(),
                                    pkt.getPayloadLength());
                    }

                    if (!isKeyFrame)
                    {
                        logWarn("We're switching NOT on a key frame. Bad " +
                                "Stuff (tm) will happen to you!");
                    }
                }

                // Because {#currentExtendedSeqnumBase} is an extended sequence
                // number, if we keep increasing it, it will eventually result
                // in natural wrap around of the low 16 bits.
                currentExtendedSeqnumBase += (currentIntervalLength + 1);
                activeRewriter = rewriters.get(sourceSSRC);
            }

            if (activeRewriter == null)
            {
                logDebug("Now rewriting " + (pkt.getSSRC() & 0xffffffffl)
                    + " to " + (ssrcTarget & 0xffffffffl));
                // We haven't initialized yet.
                activeRewriter = rewriters.get(sourceSSRC);
            }

            if (activeRewriter == null)
            {
                logWarn("Don't know about this SSRC. This will never " +
                    "happen or somebody is messing with us.");
            }
        }

        /**
         * This method can be used when rewriting FEC and RTX packets.
         *
         * @param ssrcOrigin the SSRC of the packet whose sequence number we are
         * rewriting.
         * @param sequenceNumber the 16 bits sequence number that we want to
         * rewrite.
         *
         * @return an integer that's either {#INVALID_SEQNUM} or a 16 bits
         * sequence number.
         */
        private int rewriteSequenceNumber(int ssrcOrigin, short sequenceNumber)
        {
            SsrcRewriter rewriter = rewriters.get(ssrcOrigin);
            if (rewriter == null)
            {
                return INVALID_SEQNUM;
            }

            int origExtendedSequenceNumber
                = rewriter.extendOriginalSequenceNumber(sequenceNumber);

            SsrcRewriter.ExtendedSequenceNumberInterval retransmissionInterval
                = rewriter.findRetransmissionInterval(origExtendedSequenceNumber);

            if (retransmissionInterval == null)
            {
                return INVALID_SEQNUM;
            }
            else
            {
                int targetExtendedSequenceNumber = retransmissionInterval.rewriteExtendedSequenceNumber(
                    origExtendedSequenceNumber);

                // Take only the bits that contain the sequence number (the low
                // 16 bits).
                return targetExtendedSequenceNumber & 0x0000ffff;
            }
        }

        /**
         * Rewrites SSRCs and sequence numbers of a given source SSRC. This
         * class is not thread-safe.
         */
        public class SsrcRewriter
        {
            /**
             * The origin SSRC that this <tt>SsrcRewriter</tt> rewrites. The
             * target SSRC is managed by the parent <tt>SsrcGroupRewriter</tt>.
             */
            private final int sourceSSRC;

            /**
             * A <tt>NavigableMap</tt> that maps <tt>Integer</tt>s representing
             * interval maxes to <tt>ExtendedSequenceNumberInterval</tt>s. So,
             * when we receive an RTP packet with given sequence number, we can
             * easily find in which sequence number interval it belongs, if it
             * does.
             *
             * TODO we should not keep more intervals than what's enough to
             * cover the last 1000 (arbitrary value) sequence numbers (and even
             * that's way too much).
             */
            private final NavigableMap<Integer, ExtendedSequenceNumberInterval>
                intervals
                    = new TreeMap<>();

            /**
             * This is the current sequence number interval for this origin
             * SSRC. We can't have it in the intervals navigable map because
             * its max isn't determined yet. If this is null, then it means that
             * this original SSRC is paused (invariant).
             */
            private ExtendedSequenceNumberInterval currentExtendedSequenceNumberInterval;

            /**
             * Ctor.
             *
             * @param sourceSSRC
             */
            public SsrcRewriter(int sourceSSRC)
            {
                this.sourceSSRC = sourceSSRC;
            }

            public Collection<ExtendedSequenceNumberInterval> getExtendedSequenceNumberIntervals()
            {
                return intervals.values();
            }

            /**
             *
             * @return
             */
            public ExtendedSequenceNumberInterval
                getCurrentExtendedSequenceNumberInterval()
            {
                return currentExtendedSequenceNumberInterval;
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
                short seqnum = (short) pkt.getSequenceNumber();

                int origExtendedSequenceNumber
                    = extendOriginalSequenceNumber(seqnum);

                // first, check if this is a retransmission and rewrite using
                // an appropriate interval.
                ExtendedSequenceNumberInterval retransmissionInterval
                    = findRetransmissionInterval(origExtendedSequenceNumber);
                if (retransmissionInterval != null)
                {
                    logDebug("Retransmitting packet with SEQNUM "
                        + (seqnum & 0xffff) + " of SSRC "
                        + (pkt.getSSRC() & 0xffffffffl)
                        + " from the current interval.");

                    return retransmissionInterval.rewriteRTP(pkt);
                }

                // this is not a retransmission.

                if (currentExtendedSequenceNumberInterval == null)
                {
                    // the stream has resumed.
                    currentExtendedSequenceNumberInterval
                        = new ExtendedSequenceNumberInterval(
                            origExtendedSequenceNumber, currentExtendedSeqnumBase);
                    currentExtendedSequenceNumberInterval.lastSeen = System.currentTimeMillis();
                }
                else
                {
                    // more packets to the stream, increase the sequence number
                    // interval range.
                    currentExtendedSequenceNumberInterval.extendedMaxOrig = origExtendedSequenceNumber;
                    // the timestamp needs to be greater or equal to the
                    // maxTimestamp for the current extended sequence number
                    // interval.
                    currentExtendedSequenceNumberInterval.maxTimestamp = pkt.getTimestamp();
                    currentExtendedSequenceNumberInterval.lastSeen = System.currentTimeMillis();
                }

                if (logger.isDebugEnabled())
                {
                    // Please let me know when RTP timestamp uplifting happens,
                    // will ya?
                    if (pkt.getTimestamp() < maxTimestamp)
                    {
                        logDebug("RTP timestamp uplifting.");
                        pkt.setTimestamp(maxTimestamp + 1);
                    }
                }

                return currentExtendedSequenceNumberInterval.rewriteRTP(pkt);
            }

            /**
             * Moves the current sequence number interval, in the
             * {@link #intervals} tree. It is not to be updated anymore.
             *
             * @return the extended length of the sequence number interval that
             * got paused.
             */
            public void pause()
            {
                if (currentExtendedSequenceNumberInterval != null)
                {
                    intervals.put(currentExtendedSequenceNumberInterval.extendedMaxOrig,
                        currentExtendedSequenceNumberInterval);
                    // Store the max timestamp so that we can consult it when
                    // we rewrite the next packets of the next stream.
                    maxTimestamp = currentExtendedSequenceNumberInterval.maxTimestamp;
                    currentExtendedSequenceNumberInterval = null;

                    // TODO We don't need to keep track of more than 2 cycles,
                    // so we need to trim the intervals tree to accommodate just
                    // that.
                }
                else
                {
                    // this stream is already paused.
                    logInfo("The stream is already paused.");
                }
            }

            /**
             * @param origExtendedSeqnumOrig the original extended sequence
             * number.
             *
             * @return
             */
            public ExtendedSequenceNumberInterval findRetransmissionInterval(
                int origExtendedSeqnumOrig)
            {
                // first check in the current sequence number interval.
                if (currentExtendedSequenceNumberInterval != null
                    && currentExtendedSequenceNumberInterval.contains(
                    origExtendedSeqnumOrig))
                {
                    return currentExtendedSequenceNumberInterval;
                }

                // not there, try to find the sequence number in a previous
                // interval.
                Map.Entry<Integer, ExtendedSequenceNumberInterval> candidateInterval
                    = intervals.ceilingEntry(origExtendedSeqnumOrig);

                if (candidateInterval != null
                    && candidateInterval.getValue().contains(origExtendedSeqnumOrig))
                {
                    return candidateInterval.getValue();
                }

                return null;
            }

            /**
             *
             * @param ssOrigSeqnum
             * @return
             */
            private int extendOriginalSequenceNumber(short ssOrigSeqnum)
            {
                // XXX we're using hungarian notation here to distinguish
                // between signed short, unsigned short etc.

                // Find the most recent extended sequence number interval for
                // this SSRC.
                ExtendedSequenceNumberInterval mostRecentInterval
                    = currentExtendedSequenceNumberInterval;

                if (mostRecentInterval == null)
                {
                    Map.Entry<Integer, ExtendedSequenceNumberInterval>
                        entry = intervals.lastEntry();

                    if (entry != null)
                    {
                        mostRecentInterval = entry.getValue();
                    }
                }

                if (mostRecentInterval == null)
                {
                    // We don't have a most recent interval for this SSRC. This
                    // must be the very first RTP packet that we receive for
                    // this SSRC. The cycle is 0 and the extended sequence
                    // number is whatever the original sequence number is.
                    return ssOrigSeqnum & 0x0000ffff;
                }

                int usOriginalSequenceNumber = ssOrigSeqnum & 0x0000ffff;
                int usHighestSeenSeqnum = mostRecentInterval.extendedMaxOrig & 0x0000ffff;

                // There are two possible cases, either this is a
                // re-transmission, or it's a new sequence number that will
                // be used to either extend the current interval (if there
                // is a current interval) or start a new one.

                if (usOriginalSequenceNumber - usHighestSeenSeqnum > 0)
                {
                    // If the received sequence number (unsigned 16 bits) is
                    // bigger than the most recent max, then one of the
                    // following holds:
                    //
                    // 1. this is a new sequence number from this cycle.
                    //    For example, usOriginalSequenceNumber = 60001 and usHighestSeenSeqnum = 60000.
                    // 2. this is a new sequence number from a subsequent
                    //    cycle. For example, usOriginalSequenceNumber = 60001 and usHighestSeenSeqnum = 60000.
                    // 3. this is a retransmission from the previous cycle.
                    //    For example, usOriginalSequenceNumber = 65536 and usHighestSeenSeqnum = 1
                    //
                    // If this is a packet from a subsequent cycle, then
                    // this means that the sequence numbers have advanced
                    // at least one cycle. Assuming that a cycle takes at
                    // least 30 seconds to complete (atm it takes ~ 20
                    // mins), then the mostRecentInterval must have been
                    // last touched more than 30s ago.

                    if (System.currentTimeMillis() - mostRecentInterval.lastSeen - RETRANSMISSIONS_FRONTIER_MS < 0)
                    {
                        // the last sequence number is recent.
                        if (usOriginalSequenceNumber - (usHighestSeenSeqnum + MAX_UNSIGNED_SHORT) - MEDIAN_UNSIGNED_SHORT > 0)
                        {
                            // retransmission from the previous cycle.
                            return ((((usHighestSeenSeqnum & 0xffff0000) >> 4) - 1) << 4) | (usOriginalSequenceNumber & 0x0000ffff);
                        }
                        else
                        {
                            // new sequence number from this cycle.
                            return (usHighestSeenSeqnum & 0xffff0000) | (usOriginalSequenceNumber & 0x0000ffff);
                        }
                    }
                    else
                    {
                        // sequence number from _a_ subsequent cycle (not sure
                        // which one).
                        return ((((usHighestSeenSeqnum & 0xffff0000) >> 4) + 1) << 4) | (usOriginalSequenceNumber & 0x0000ffff);
                    }
                }
                else
                {
                    // Else, the received sequence number (unsigned 16 bits) is
                    // smaller than the most recent max, then one of the
                    // following holds:
                    //
                    // 1. this is a new sequence number from _a_ subsequent
                    //    cycle.
                    // 2. this is a retransmission from this cycle.

                    if (System.currentTimeMillis() - mostRecentInterval.lastSeen - RETRANSMISSIONS_FRONTIER_MS < 0)
                    {
                        // the last sequence number is recent
                        if ((usHighestSeenSeqnum - usOriginalSequenceNumber) - MEDIAN_UNSIGNED_SHORT > 0)
                        {
                            // if the distance to the last max is greater
                            // than 2^15, then the sequence numbers must
                            // have wrapped around (new cycle).
                            return ((((usHighestSeenSeqnum & 0xffff0000) >> 4) + 1) << 4) | (usOriginalSequenceNumber & 0x0000ffff);
                        }
                        else
                        {
                            // else, this is a retransmission from this cycle.
                            return (usHighestSeenSeqnum & 0xffff0000) | (usOriginalSequenceNumber & 0x0000ffff);
                        }
                    }
                    else
                    {
                        // this can't possibly be a retransmission as
                        // it would refer to something that's too old,
                        // so the sequence numbers must have wrapped
                        // around.
                        return ((((usHighestSeenSeqnum & 0xffff0000) >> 4) + 1) << 4) | (usOriginalSequenceNumber & 0x0000ffff);
                    }
                }
            }

            /**
             * Does the dirty job of rewriting SSRCs and sequence numbers of a
             * given extended sequence number interval of a given source SSRC.
             */
            public class ExtendedSequenceNumberInterval
            {
                /**
                 * The extended minimum sequence number of this interval.
                 */
                private final int extendedMinOrig;

                /**
                 * Holds the value of the extended sequence number of the target
                 * SSRC when this interval started.
                 */
                private final int extendedBaseTarget;

                /**
                 * The extended maximum sequence number of this interval.
                 */
                private int extendedMaxOrig;

                /**
                 * The time this interval has been closed.
                 */
                private long lastSeen;

                /**
                 * Holds the max RTP timestamp that we've sent (to the endpoint)
                 * in this interval.
                 */
                private long maxTimestamp;

                /**
                 * Ctor.
                 *
                 * @param extendedBaseOrig
                 * @param extendedBaseTarget
                 */
                public ExtendedSequenceNumberInterval(
                    int extendedBaseOrig, int extendedBaseTarget)
                {
                    this.extendedBaseTarget = extendedBaseTarget;

                    this.extendedMinOrig = extendedBaseOrig;
                    this.extendedMaxOrig = extendedBaseOrig;
                }

                public long getLastSeen()
                {
                    return lastSeen;
                }

                public int getExtendedMin()
                {
                    return extendedMinOrig;
                }

                public int getExtendedMax()
                {
                    return extendedMaxOrig;
                }

                /**
                 * Returns a boolean determining whether a sequence number
                 * is contained in this interval or not.
                 *
                 * @param extendedSequenceNumber the sequence number to
                 * determine whether it belongs in the interval or not.
                 * @return true if the sequence number is contained in the
                 * interval, otherwise false.
                 */
                public boolean contains(int extendedSequenceNumber)
                {
                    return extendedMinOrig >= extendedSequenceNumber
                        && extendedSequenceNumber <= extendedMaxOrig;
                }

                /**
                 *
                 * @param extendedSequenceNumber
                 * @return
                 */
                public int rewriteExtendedSequenceNumber(
                    int extendedSequenceNumber)
                {
                    int diff = extendedSequenceNumber - extendedMinOrig;
                    return extendedBaseTarget + diff;
                }

                /**
                 * @param pkt
                 */
                public RawPacket rewriteRTP(RawPacket pkt)
                {
                    // Rewrite the SSRC.
                    pkt.setSSRC(ssrcTarget);

                    // Rewrite the sequence number of the RTP packet.
                    short ssSeqnum = (short) pkt.getSequenceNumber();
                    int extendedSequenceNumber = extendOriginalSequenceNumber(ssSeqnum);
                    int rewriteSeqnum = rewriteExtendedSequenceNumber(extendedSequenceNumber);
                    // This will disregard the high 16 bits.
                    pkt.setSequenceNumber(rewriteSeqnum);

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
                    short snOrig = pkt.getOriginalSequenceNumber();

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
                 * Calculates and returns the length of this interval. Note that
                 * all 32 bits are used to represent the interval length because
                 * an interval can span multiple cycles.
                 *
                 * @return the length of this interval.
                 */
                public int length()
                {
                    return extendedMaxOrig - extendedMinOrig;
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
                        logWarn("The buffer is empty.");
                        return;
                    }

                    if (buf.length < off + len)
                    {
                        logWarn("The buffer is invalid.");
                        return;
                    }

                    // FIXME similar code can be found in the
                    // REDFilterTransformEngine and in the REDTransformEngine and
                    // in the SimulcastLayer.

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
                    for (int i = 0; i <= pktCount; i++)
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
                        logWarn("The buffer is empty.");
                        return false;
                    }

                    if (buf.length < off + len)
                    {
                        logWarn("The buffer is invalid.");
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
                    short snBase = (short) (buf[off + 2] << 8 | buf[off + 3]);

                    SsrcGroupRewriter rewriter
                        = origin2rewriter.get(sourceSSRC);

                    int snRewritenBase
                        = rewriter.rewriteSequenceNumber(sourceSSRC, snBase);


                    if (snRewritenBase == INVALID_SEQNUM)
                    {
                        logInfo("We could not find a sequence number " +
                            "interval for a FEC packet.");
                        return false;
                    }

                    buf[off + 2] = (byte) (snRewritenBase & 0xff00 >> 8);
                    buf[off + 3] = (byte) (snRewritenBase & 0x00ff);
                    return true;
                }
            }
        }
    }
}
