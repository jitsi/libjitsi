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
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.vp8.*;
import org.jitsi.util.*;


/**
 * Does the actual work of rewriting a group of SSRCs to a target SSRC. This
 * class is not thread-safe.
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
     * The <tt>Random</tt> that generates initial sequence numbers. Instances of
     * {@code java.util.Random} are thread-safe since Java 1.7.
     */
    private static final Random RANDOM = new Random();

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
    long maxTimestamp;

    /**
     * The current <tt>SsrcRewriter</tt> that we use to rewrite source
     * SSRCs. The active rewriter is determined by the RTP packets that
     * we get.
     */
    private SsrcRewriter activeRewriter;

    /**
     * Ctor.
     *
     * @param SsrcRewritingEngine the owner of this instance.
     * @param ssrcTarget the target SSRC for this <tt>SsrcGroupRewriter</tt>.
     */
    public SsrcGroupRewriter(
            SsrcRewritingEngine ssrcRewritingEngine,
            Integer ssrcTarget)
    {
        this.ssrcRewritingEngine = ssrcRewritingEngine;
        this.ssrcTarget = ssrcTarget;

        this.currentExtendedSeqnumBase = RANDOM.nextInt(0x10000);
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

    /**
     * Gets the target SSRC that the rewritten RTP packets will have.
     */
    public int getSSRCTarget()
    {
        return this.ssrcTarget;
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

        this.maybeSwitchActiveRewriter(pkt);

        if (activeRewriter == null)
        {
            logWarn("Can't rewrite the RTP packet because there's no active " +
                    "rewriter.");
            return pkt;
        }

        return activeRewriter.rewriteRTP(pkt);
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
            logDebug("Creating an SSRC rewriter to rewrite " + (sourceSSRC
                        & 0xffffffffl) + " into " + (ssrcTarget & 0xffffffffl));
            rewriters.put(sourceSSRC, new SsrcRewriter(this, sourceSSRC));
        }

        if (activeRewriter != null
            && activeRewriter.getSourceSSRC() != sourceSSRC)
        {
            // Got a packet with a different SSRC from the one that the
            // current SsrcRewriter handles. Pause the current SsrcRewriter
            // and switch to the correct one.
            logDebug("Now rewriting " + pkt.getSSRCAsLong()
                + " to " + (ssrcTarget & 0xffffffffl) + " (was rewriting "
                + (activeRewriter.getSourceSSRC() & 0xffffffffl) + ").");

            // We don't have to worry about sequence number intervals that
            // span multiple sequence number cycles because the extended
            // sequence number interval length is 32 bits.
            ExtendedSequenceNumberInterval currentInterval
                = activeRewriter.getCurrentExtendedSequenceNumberInterval();

            int currentIntervalLength
                = currentInterval == null ? 0 : currentInterval.length();

            if (currentIntervalLength < 1)
            {
                logDebug("Pausing an interval of length 0. This doesn't look " +
                        "right");
            }

            // Pause the active rewriter (closes its current interval and
            // puts it in the interval tree).
            activeRewriter.pause();

            if (logger.isDebugEnabled())
            {
                // We're only supposed to switch on key frames. Here we check
                // if that's the case.
                if (!isKeyFrame(pkt))
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
            logDebug("Now rewriting " + pkt.getSSRCAsLong()
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
     * Utility method that determines whether or not a packet is a key frame.
     * This is used only for debugging purposes.
     */
    private boolean isKeyFrame(RawPacket pkt)
    {
        final int sourceSSRC = pkt.getSSRC();
        boolean isKeyFrame;
        byte redPT = ssrcRewritingEngine.ssrc2red.get(sourceSSRC);
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

        return isKeyFrame;
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
    int rewriteSequenceNumber(int ssrcOrigin, short sequenceNumber)
    {
        SsrcRewriter rewriter = rewriters.get(ssrcOrigin);
        if (rewriter == null)
        {
            logWarn("An SSRC rewriter was not found for SSRC : " + (ssrcOrigin
                        & 0xffffffffl));
            return SsrcRewritingEngine.INVALID_SEQNUM;
        }

        int origExtendedSequenceNumber
            = rewriter.extendOriginalSequenceNumber(sequenceNumber);

        ExtendedSequenceNumberInterval retransmissionInterval
            = rewriter.findRetransmissionInterval(origExtendedSequenceNumber);

        if (retransmissionInterval == null)
        {
            logWarn("Could not find a retransmission interval.");
            return SsrcRewritingEngine.INVALID_SEQNUM;
        }
        else
        {
            int targetExtendedSequenceNumber = retransmissionInterval
                .rewriteExtendedSequenceNumber(origExtendedSequenceNumber);

            // Take only the bits that contain the sequence number (the low
            // 16 bits).
            return targetExtendedSequenceNumber & 0x0000ffff;
        }
    }

    void logDebug(String msg)
    {
        ssrcRewritingEngine.logDebug(msg);
    }

    void logInfo(String msg)
    {
        ssrcRewritingEngine.logInfo(msg);
    }

    void logWarn(String msg)
    {
        ssrcRewritingEngine.logWarn(msg);
    }
}
