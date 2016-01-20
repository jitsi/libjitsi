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

import java.io.*;
import java.util.*;
import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtcp.termination.strategies.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.util.*;


/**
 * Does the actual work of rewriting a group of SSRCs to a target SSRC. This
 * class is not thread-safe.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
class SsrcGroupRewriter
{
    /**
     * The <tt>Logger</tt> used by the <tt>SsrcGroupRewriter</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(SsrcGroupRewriter.class);

    /**
     * A map of SSRCs to <tt>SsrcRewriter</tt>. Each SSRC that we rewrite in
     * this group rewriter has its own rewriter.
     */
    private final Map<Integer, SsrcRewriter> rewriters = new HashMap<>();

    /**
     * The owner of this instance.
     */
    public final SsrcRewritingEngine ssrcRewritingEngine;

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
    int currentExtendedSeqnumBase;

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
     * @param ssrcRewritingEngine the owner of this instance.
     * @param ssrcTarget the target SSRC for this <tt>SsrcGroupRewriter</tt>.
     */
    public SsrcGroupRewriter(
            SsrcRewritingEngine ssrcRewritingEngine,
            Integer ssrcTarget,
            int seqnumBase)
    {
        this.ssrcRewritingEngine = ssrcRewritingEngine;
        this.ssrcTarget = ssrcTarget;

        this.currentExtendedSeqnumBase = seqnumBase;
    }

    /**
     * Closes this instance and sends an RTCP BYE packet for the target
     * SSRC.
     */
    public void close()
    {
        // According to RFC 3550, a participant who never sent an RTP or RTCP
        // packet MUST NOT send a BYE packet when they leave the group.
        if (getActiveRewriter() != null)
        {
            MediaStream mediaStream = getMediaStream();

            if (mediaStream != null)
            {
                int ssrcTarget = getSSRCTarget();

                // A BYE is to be sent in a compound RTCP packet and the latter
                // is to start with either an SR or an RR. We do not have enough
                // information here to build either an SR or an RR with report
                // blocks. Additionally, the report blocks have to carry
                // information in agreement with RTCP termination. On top of
                // these, the compound RTCP packet has to go through the RTCP
                // transmitter so that it updates the RTCP transmission stats.
                // We can leave the last two to the RTCP termination strategy in
                // place so we'll just construct an RR with no report blocks so
                // that we can form a seemingly legal compound RTCP packet.
                RTCPRRPacket rr
                    = new RTCPRRPacket(
                            ssrcTarget,
                            BasicRTCPTerminationStrategy
                                .MIN_RTCP_REPORT_BLOCKS_ARRAY);
                RTCPBYEPacket bye
                    = new RTCPBYEPacket(
                            new int[] { ssrcTarget },
                            /* reason */ null);
                RTCPCompoundPacket compound
                    = new RTCPCompoundPacket(new RTCPPacket[] { rr, bye });

                // Turn the RTCPCompoundPacket into a RawPacket and inject it
                // into the associated MediaStream.
                RawPacket raw;

                try
                {
                    raw = RTCPPacketParserEx.toRawPacket(compound);
                }
                catch (IOException ioe)
                {
                    raw = null;
                    // TODO error handling
                }
                if (raw != null)
                {
                    try
                    {
                        mediaStream.injectPacket(
                                raw,
                                /* data */ false,
                                /* after */ null);
                    }
                    catch (TransmissionFailedException tfe)
                    {
                        // TODO error handling
                    }
                }
            }
        }
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

    /**
     * Gets the target SSRC that the rewritten RTP packets will have.
     */
    public int getSSRCTarget()
    {
        return ssrcTarget;
    }

    /**
     * Determines whether a switch took place and if so it switches the active
     * rewriter appropriately. It then rewrites the <tt>RawPacket</tt> that is
     * passed in as a parameter using the active rewriter.
     *
     * @param pkt the packet to rewrite
     * @return the rewritten <tt>RawPacket</tt>
     */
    public RawPacket rewriteRTP(final RawPacket pkt)
    {
        if (pkt == null)
        {
            return pkt;
        }

        maybeSwitchActiveRewriter(pkt);

        if (activeRewriter == null)
        {
            logger.warn(
                    "Can't rewrite the RTP packet because there's no active"
                        + " rewriter.");
            return pkt;
        }
        else
        {
            return activeRewriter.rewriteRTP(pkt);
        }
    }

    /**
     * Maybe switches the {@link #activeRewriter}.
     *
     * @param pkt the received packet that will determine the
     * {@code activeRewriter}.
     */
    private void maybeSwitchActiveRewriter(final RawPacket pkt)
    {
        final int sourceSSRC = pkt.getSSRC();
        boolean debug = logger.isDebugEnabled();

        // This "if" block is not thread-safe but we don't expect multiple
        // threads to access this block all at the same time.
        if (!rewriters.containsKey(sourceSSRC))
        {
            if (debug)
            {
                logger.debug(
                        "Creating an SSRC rewriter to rewrite "
                            + pkt.getSSRCAsLong() + " to "
                            + (ssrcTarget & 0xffffffffl));
            }
            rewriters.put(sourceSSRC, new SsrcRewriter(this, sourceSSRC));
        }

        if (activeRewriter != null
                && activeRewriter.getSourceSSRC() != sourceSSRC)
        {
            // Got a packet with a different SSRC from the one that the
            // current SsrcRewriter handles. Pause the current SsrcRewriter
            // and switch to the correct one.
            if (debug)
            {
                logger.debug("Now rewriting " + pkt.getSSRCAsLong() + "/"
                        + pkt.getSequenceNumber() + " to "
                        + (ssrcTarget & 0xffffffffl) + " (was rewriting "
                        + (activeRewriter.getSourceSSRC() & 0xffffffffl)
                        + ").");
            }

            // We don't have to worry about sequence number intervals that
            // span multiple sequence number cycles because the extended
            // sequence number interval length is 32 bits.
            ExtendedSequenceNumberInterval currentInterval
                = activeRewriter.getCurrentExtendedSequenceNumberInterval();
            int currentIntervalLength
                = currentInterval == null ? 0 : currentInterval.length();

            if (debug && currentIntervalLength < 1)
            {
                logger.debug(
                        "Pausing an interval of length 0. This doesn't look"
                            + " right.");
            }

            // Pause the active rewriter (closes its current interval and
            // puts it in the interval tree).
            activeRewriter.pause();

            // FIXME We're using logger.warn under the condition of debug bellow.
            if (debug)
            {
                // We're only supposed to switch on key frames. Here we check
                // if that's the case.
                if (!isKeyFrame(pkt))
                {
                    logger.warn(
                            "We're switching NOT on a key frame. Bad Stuff (tm)"
                                + " will happen to you!");
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
            if (debug)
            {
                logger.debug(
                        "Now rewriting " + pkt.getSSRCAsLong() + " to "
                            + (ssrcTarget & 0xffffffffl));
            }
            // We haven't initialized yet.
            activeRewriter = rewriters.get(sourceSSRC);
        }

        if (activeRewriter == null)
        {
            logger.warn(
                    "Don't know about SSRC " + pkt.getSSRCAsLong()
                        + "! Somebody is messing with us!");
        }
    }

    /**
     * Determines whether a specific packet is a key frame.
     *
     * @param pkt the {@code RawPacket} to be determined whether it is a key
     * frame
     * @return {@code true} if {@pkt} is a key frame; otherwise, {@code false}
     */
    boolean isKeyFrame(RawPacket pkt)
    {
        int sourceSSRC = pkt.getSSRC();
        byte redPT = ssrcRewritingEngine.ssrc2red.get(sourceSSRC);
        byte vp8PT = 0x64;

        return Utils.isKeyFrame(pkt, redPT, vp8PT);
    }

    /**
     * This method can be used when rewriting FEC and RTX packets.
     *
     * @param ssrcOrigin the SSRC of the packet whose sequence number we are
     * rewriting.
     * @param seqnum the 16 bits sequence number that we want to
     * rewrite.
     *
     * @return an integer that's either {#INVALID_SEQNUM} or a 16 bits
     * sequence number.
     */
    int rewriteSequenceNumber(int ssrcOrigin, short seqnum)
    {
        SsrcRewriter rewriter = rewriters.get(ssrcOrigin);
        if (rewriter == null)
        {
            logger.warn(
                    "An SSRC rewriter was not found for SSRC : "
                        + (ssrcOrigin & 0xffffffffl));
            return SsrcRewritingEngine.INVALID_SEQNUM;
        }

        int origExtendedSeqnum
            = rewriter.extendOriginalSequenceNumber(seqnum);
        ExtendedSequenceNumberInterval retransmissionInterval
            = rewriter.findRetransmissionInterval(origExtendedSeqnum);

        if (retransmissionInterval == null)
        {
            logger.warn("Could not find a retransmission interval for seqnum " +
                    (seqnum & 0x0000ffff) + " from " + (ssrcOrigin & 0xffffffffl));
            return SsrcRewritingEngine.INVALID_SEQNUM;
        }
        else
        {
            int targetExtendedSeqnum
                = retransmissionInterval.rewriteExtendedSequenceNumber(
                        origExtendedSeqnum);

            // Take only the bits that contain the sequence number (the low
            // 16 bits).
            return targetExtendedSeqnum & 0x0000ffff;
        }
    }

    /**
     * Gets the {@code MediaStream} associated with this instance.
     *
     * @return the {@code MediaStream} associated with this instance
     */
    public MediaStream getMediaStream()
    {
        return ssrcRewritingEngine.getMediaStream();
    }

    /**
     * Gets the maximum RTP timestamp that we've sent to the remote endpoint.
     *
     * @return the maximum RTP timestamp that we've sent to the remote endpoint
     */
    public long getMaxTimestamp()
    {
        return maxTimestamp;
    }

    /**
     * Sets the maximum RTP timestamp that we've sent to the remote endpoint.
     *
     * @param maxTimestamp the maximum RTP timestamp that we've sent to the
     * remote endpoint
     */
    public void setMaxTimestamp(long maxTimestamp)
    {
        if (this.maxTimestamp < maxTimestamp)
        {
            this.maxTimestamp = maxTimestamp;
        }
    }
}
