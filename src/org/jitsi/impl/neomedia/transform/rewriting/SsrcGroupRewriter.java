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
import org.jitsi.impl.neomedia.codec.video.vp8.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtcp.termination.strategies.*;
import org.jitsi.service.neomedia.*;
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
     * The value of {@link Logger#isTraceEnabled()} from the time of the
     * initialization of the class {@code SsrcGroupRewriter} cached for the
     * purposes of performance.
     */
    private static final boolean TRACE;

    /**
     * The value of {@link Logger#isDebugEnabled()} from the time of the
     * initialization of the class {@code SsrcGroupRewriter} cached for the
     * purposes of performance.
     */
    private static final boolean DEBUG;

    /**
     * The value of {@link Logger#isWarnEnabled()} from the time of the
     * initialization of the class {@code SsrcGroupRewriter} cached for the
     * purposes of performance.
     */
    private static final boolean WARN;

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
     * Holds the max RTP timestamp that we've sent out.
     * <p>
     * When there's a stream switch, we request a keyframe for the stream we
     * want to switch into (this is done elsewhere). The problem is that the
     * frames are sampled at different times, so we might end up with a key
     * frame that is one sampling cycle behind what we were already streaming.
     * We hack around this by implementing "RTP timestamp uplifting".
     * <p>
     * When a switch occurs, we store the maximum timestamp that we've sent
     * to an endpoint. If we observe new packets (NOT retransmissions) with
     * timestamp older than what the endpoint has already seen, we overwrite
     * the timestamp with maxTimestamp + 1.
     */
    public long maxTimestamp = -1;

    /**
     * The current <tt>SsrcRewriter</tt> that we use to rewrite source
     * SSRCs. The active rewriter is determined by the RTP packets that
     * we get.
     */
    private SsrcRewriter activeRewriter;

    /**
     * The SSRC of the RTP stream into whose RTP timestamp other RTP streams
     * are rewritten.
     */
    private long _timestampSsrc;

    /**
     * Static init.
     */
    static
    {
        TRACE = logger.isTraceEnabled();
        DEBUG = logger.isDebugEnabled();
        WARN = logger.isWarnEnabled();
    }

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

        // XXX At first it seemed like we could rewrite RTP timestamps to the
        // first SSRC which requires RTP timestamp rewriting. However, we are
        // sending RTCP SRs with the timestamp of ssrcTarget. Consequently, we
        // have to rewrite the RTP timestamps to ssrcTarget. Anyway, leave the
        // infrastructure around _timestampSsrc for the purposes of
        // experimenting.
        _timestampSsrc = ssrcTarget;
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
            MediaStream mediaStream = ssrcRewritingEngine.getMediaStream();

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
     * @param p the packet to rewrite
     * @return the rewritten <tt>RawPacket</tt>
     */
    public RawPacket rewriteRTP(RawPacket p)
    {
        if (p == null)
        {
            return p;
        }

        maybeSwitchActiveRewriter(p);

        if (activeRewriter == null)
        {
            logger.warn(
                    "Can't rewrite the RTP packet because there's no active"
                        + " rewriter.");
        }
        else
        {
            // For the purposes of debugging, trace the rewriting of SSRC,
            // sequence number, and RTP timestamp.
            long ssrc0;
            int seqnum0;
            long ts0;

            if (TRACE)
            {
                ssrc0 = p.getSSRCAsLong();
                seqnum0 = p.getSequenceNumber();
                ts0 = p.getTimestamp();
            }
            else
            {
                // Assign values so that the compiler does not complain that the
                // local variables may be used without being initializaed. Do
                // not read the actual values because the reads are not trivial.
                ssrc0 = SsrcRewritingEngine.INVALID_SSRC;
                seqnum0 = SsrcRewritingEngine.INVALID_SEQNUM;
                ts0 = 0;
            }

            p = activeRewriter.rewriteRTP(p);

            // For the purposes of debugging, trace the rewriting of SSRC,
            // sequence number, and RTP timestamp.
            if (TRACE && p != null)
            {
                boolean isKeyframe = isKeyFrame(p);
                long ssrc1 = p.getSSRCAsLong();
                int seqnum1 = p.getSequenceNumber();
                long ts1 = p.getTimestamp();

                logger.trace(
                    "rewriteRTP SSRC, seqnum, ts from: "
                        + ssrc0 + "," + seqnum0 + "," + ts0
                        + " to: "
                        + ssrc1 + "," + seqnum1 + "," + ts1
                        + ", isKeyframe: " + isKeyframe);
            }
        }
        return p;
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

        // This "if" block is not thread-safe but we don't expect multiple
        // threads to access this block all at the same time.
        if (!rewriters.containsKey(sourceSSRC))
        {
            if (DEBUG)
            {
                logger.debug(
                        "Creating an SSRC rewriter to rewrite "
                            + pkt.getSSRCAsLong() + " to "
                            + (ssrcTarget & 0xffffffffL));
            }
            rewriters.put(sourceSSRC, new SsrcRewriter(this, sourceSSRC));
        }

        if (activeRewriter != null
                && activeRewriter.getSourceSSRC() != sourceSSRC)
        {
            // Got a packet with a different SSRC from the one that the current
            // SsrcRewriter handles. Pause the current SsrcRewriter and switch
            // to the correct one.
            if (DEBUG)
            {
                logger.debug(
                        "Now rewriting " + pkt.getSSRCAsLong() + "/"
                            + pkt.getSequenceNumber() + " to "
                            + (ssrcTarget & 0xffffffffL) + " (was rewriting "
                            + (activeRewriter.getSourceSSRC() & 0xffffffffL)
                            + ").");
            }

            // We don't have to worry about sequence number intervals that span
            // multiple sequence number cycles because the extended sequence
            // number interval length is 32 bits.
            ExtendedSequenceNumberInterval currentInterval
                = activeRewriter.getCurrentExtendedSequenceNumberInterval();
            int currentIntervalLength
                = currentInterval == null ? 0 : currentInterval.length();

            if (WARN && currentIntervalLength < 1)
            {
                logger.warn(
                        "Pausing an interval of length 0. This doesn't look"
                            + " right.");
            }

            // Pause the active rewriter (closes its current interval and puts
            // it in the interval tree).
            activeRewriter.pause();

            if (WARN)
            {
                // We're only supposed to switch on key frames. Here we check if
                // that's the case.
                if (!isKeyFrame(pkt))
                {
                    logger.warn(
                            "We're switching NOT on a key frame (seq="
                                + pkt.getSequenceNumber() + "). Bad Stuff (tm)"
                                + " will happen to you!");
                }
            }

            // Because {#currentExtendedSeqnumBase} is an extended sequence
            // number, if we keep increasing it, it will eventually result in
            // natural wrap around of the low 16 bits.
            currentExtendedSeqnumBase += (currentIntervalLength + 1);
            activeRewriter = rewriters.get(sourceSSRC);
        }

        if (activeRewriter == null)
        {
            if (DEBUG)
            {
                logger.debug(
                        "Now rewriting " + pkt.getSSRCAsLong() + " to "
                            + (ssrcTarget & 0xffffffffL));
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
        Byte redPT = ssrcRewritingEngine.ssrc2red.get(sourceSSRC);
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
    int rewriteSequenceNumber(int ssrcOrigin, int seqnum)
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
            logger.warn(
                    "Could not find a retransmission interval for seqnum "
                        + seqnum + " from "
                        + (ssrcOrigin & 0xffffffffL));
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
     * Uplift the timestamp of a frame if we've already sent a larger
     * timestamp to the remote endpoint.
     *
     * @param p
     */
    public void maybeUpliftTimestamp(RawPacket p)
    {
        // XXX Why do we need this? : When there's a stream switch, we request a
        // keyframe for the stream we want to switch into (this is done
        // elsewhere). The {@link SsrcRewriter} rewrites the timestamps of the
        // "mixed" streams so that they all have the same timestamp offset. The
        // problem still remains tho, frames can be sampled at different times,
        // so we might end up with a key frame that is one sampling cycle behind
        // what we were already streaming. We hack around this by implementing
        // "RTP timestamp uplifting".

        // XXX(gp): The uplifting should not take place if the
        // timestamps have advanced "a lot" (i.e. > 3000 or 3000/90 = 33ms).

        long timestamp = p.getTimestamp();

        if (maxTimestamp == -1) // Initialize maxTimestamp.
        {
            maxTimestamp = timestamp - 1;
        }

        long minTimestamp = maxTimestamp + 1;
        long delta = TimeUtils.rtpDiff(timestamp, minTimestamp);

        if (delta < 0) /* minTimestamp is inclusive */
        {
            if (DEBUG)
            {
                logger.debug(
                    "Uplifting RTP timestamp " + timestamp
                        + " with SEQNUM " + p.getSequenceNumber()
                        + " from SSRC " + p.getSSRCAsLong()
                        + " because of delta " + delta + " to "
                        + minTimestamp);
            }

            if (delta < -300000 && WARN)
            {
                // Bail-out. This is not supposed to happen because it means
                // that more than one frame has to be uplifted, which means that
                // we might be mis-rewriting the timestamps (since we're
                // switching on neighboring frames and neighboring frames are
                // sampled at similar instances).

                logger.warn(
                    "Uplifting a HIGHLY suspicious RTP timestamp "
                        + timestamp + " with SEQNUM "
                        + p.getSequenceNumber() + " from SSRC "
                        + p.getSSRCAsLong() + " because of delta=" + delta +
                        " to " + minTimestamp);
            }

            p.setTimestamp(minTimestamp);
        }
        else
        {
            // FIXME If the delta is >>> 3000 it could mean problems as well.
        }

        if (TimeUtils.rtpDiff(maxTimestamp, timestamp) < 0)
        {
            maxTimestamp = timestamp;
        }
    }

    /**
     * Gets the SSRC of the RTP stream into whose RTP timestamp other RTP
     * streams are rewritten.
     *
     * @return the SSRC of the RTP stream into whose RTP timestamp other RTP
     * streams are rewritten
     */
    long getTimestampSsrc()
    {
        return _timestampSsrc;
    }

    /**
     * Sets the SSRC of the RTP stream into whose RTP timestamp other RTP
     * streams are to be rewritten.
     *
     * @param timestampSsrc the SSRC of the RTP stream into whose RTP timestamp
     * other RTP streams are to be rewritten
     */
    void setTimestampSsrc(int timestampSsrc)
    {
        setTimestampSsrc(timestampSsrc & 0xffffffffL);
    }

    /**
     * Sets the SSRC of the RTP stream into whose RTP timestamp other RTP
     * streams are to be rewritten.
     *
     * @param timestampSsrc the SSRC of the RTP stream into whose RTP timestamp
     * other RTP streams are to be rewritten
     */
    void setTimestampSsrc(long timestampSsrc)
    {
        _timestampSsrc = timestampSsrc;
    }
}
