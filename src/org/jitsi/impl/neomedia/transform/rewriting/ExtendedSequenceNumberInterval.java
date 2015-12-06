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
import org.jitsi.util.function.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;

/**
 * Does the dirty job of rewriting SSRCs and sequence numbers of a
 * given extended sequence number interval of a given source SSRC.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
class ExtendedSequenceNumberInterval
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
     * The owner of this instance.
     */
    public final SsrcRewriter ssrcRewriter;

    /**
     * The predicate used to match FEC <tt>REDBlock</tt>s.
     */
    private final Predicate<REDBlock> fecPredicate = new Predicate<REDBlock>()
    {
        public boolean test(REDBlock redBlock)
        {
            Map<Integer, Byte> ssrc2fec = getSsrcRewritingEngine().ssrc2fec;
            int sourceSSRC = ssrcRewriter.getSourceSSRC();
            return redBlock != null
                && ssrc2fec.get(sourceSSRC) == redBlock.getPayloadType();
        }
    };

    /**
     * The extended maximum sequence number of this interval.
     */
    int extendedMaxOrig;

    /**
     * The time this interval has been closed.
     */
    long lastSeen;

    /**
     * Holds the max RTP timestamp that we've sent (to the endpoint)
     * in this interval.
     */
    long maxTimestamp;

    private final long timestampOrig;

    private final long timestampTarget;

    /**
     * Ctor.
     *
     * @param ssrcRewriter
     * @param extendedBaseOrig
     * @param extendedBaseTarget
     * @param timestampOrig
     * @param timestampTarget
     */
    public ExtendedSequenceNumberInterval(
            SsrcRewriter ssrcRewriter,
            int extendedBaseOrig, int extendedBaseTarget,
            long timestampOrig, long timestampTarget)
    {
        this.ssrcRewriter = ssrcRewriter;
        this.extendedBaseTarget = extendedBaseTarget;

        this.extendedMinOrig = extendedBaseOrig;
        this.extendedMaxOrig = extendedBaseOrig;

        this.timestampOrig = timestampOrig;
        this.timestampTarget = timestampTarget;
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
     * Determines whether a sequence number is contained in this interval.
     *
     * @param extendedSeqnum the sequence number to determine whether it belongs
     * to this interval.
     * @return {@code true} if {@code extendedSeqnum} is contained in this
     * interval; otherwise, {@code false}.
     */
    public boolean contains(int extendedSeqnum)
    {
        return
            extendedMinOrig <= extendedSeqnum
                && extendedSeqnum <= extendedMaxOrig;
    }

    /**
     *
     * @param extendedSeqnum
     * @return
     */
    public int rewriteExtendedSequenceNumber(int extendedSeqnum)
    {
        int diff = extendedSeqnum - extendedMinOrig;
        return extendedBaseTarget + diff;
    }

    /**
     * Rewrites (the SSRC, sequence number, timestamp, etc. of) a specific RTP
     * packet.
     *
     * @param pkt the {@code RawPacket} which represents the RTP packet to be
     * rewritten
     */
    public RawPacket rewriteRTP(RawPacket pkt)
    {
        // SSRC
        SsrcGroupRewriter ssrcGroupRewriter = getSsrcGroupRewriter();
        int ssrcTarget = ssrcGroupRewriter.getSSRCTarget();

        pkt.setSSRC(ssrcTarget);

        // Sequence number
        short seqnum = (short) pkt.getSequenceNumber();
        int extendedSeqnum = ssrcRewriter.extendOriginalSequenceNumber(seqnum);
        int rewriteSeqnum = rewriteExtendedSequenceNumber(extendedSeqnum);

        pkt.setSequenceNumber(rewriteSeqnum);

        SsrcRewritingEngine ssrcRewritingEngine
            = ssrcGroupRewriter.ssrcRewritingEngine;
        Map<Integer, Integer> rtx2primary = ssrcRewritingEngine.rtx2primary;
        int sourceSSRC = ssrcRewriter.getSourceSSRC();
        Integer primarySSRC = rtx2primary.get(sourceSSRC);

        if (primarySSRC == null)
            primarySSRC = sourceSSRC;

        byte pt = pkt.getPayloadType();
        boolean rtx = rtx2primary.containsKey(sourceSSRC);

        // RED
        if (ssrcRewritingEngine.ssrc2red.get(sourceSSRC) == pt)
        {
            byte[] buf = pkt.getBuffer();
            int osnLen = rtx ? 2 : 0;
            int off = pkt.getPayloadOffset() + osnLen;
            int len = pkt.getPayloadLength() - osnLen;

            rewriteRED(primarySSRC, buf, off, len);
        }

        // FEC
        if (ssrcRewritingEngine.ssrc2fec.get(sourceSSRC) == pt)
        {
            byte[] buf = pkt.getBuffer();
            int osnLen = rtx ? 2 : 0;
            int off = pkt.getPayloadOffset() + osnLen;
            int len = pkt.getPayloadLength() - osnLen;

            // For the twisted case where we re-transmit a FEC
            // packet in an RTX packet..
            if (!rewriteFEC(primarySSRC, buf, off, len))
            {
                return null;
            }
        }

        // RTX
        if (rtx && !rewriteRTX(pkt))
            return null;

        // timestamp
        //
        // XXX Since we may be rewriting the RTP timestamp and, consequently, we
        // may be remembering timestamp-related state, it sounds better to do
        // these after FEC and RTX have not discarded pkt.
        rewriteTimestamp(pkt);

        return pkt;
    }

    /**
     * Rewrites the RTP timestamp of a specific RTP packet.
     *
     * @param pkt the {@code RawPacket} which represents the RTP packet to
     * rewrite the RTP timestamp of
     */
    private void rewriteTimestamp(RawPacket pkt)
    {
        long timestamp = pkt.getTimestamp();

        // Rewrite timestampOrig into timestampTarget.
        if (timestamp == timestampOrig)
        {
            timestamp = timestampTarget;
            pkt.setTimestamp(timestamp);
        }

        // Update the maximum RTP timestamp that we've sent to the endpoint (in
        // this interval).
        if (maxTimestamp < timestamp)
        {
            maxTimestamp = timestamp;
        }
    }

    /**
     *
     * @param pkt
     * @return
     */
    private boolean rewriteRTX(RawPacket pkt)
    {
        // This is an RTX packet. Replace RTX OSN field or drop.
        SsrcRewritingEngine ssrcRewritingEngine = getSsrcRewritingEngine();
        int sourceSSRC = ssrcRewriter.getSourceSSRC();
        int ssrcOrig = ssrcRewritingEngine.rtx2primary.get(sourceSSRC);
        short snOrig = pkt.getOriginalSequenceNumber();

        SsrcGroupRewriter rewriterPrimary
            = ssrcRewritingEngine.origin2rewriter.get(ssrcOrig);
        int sequenceNumber
            = rewriterPrimary.rewriteSequenceNumber(ssrcOrig, snOrig);

        if (sequenceNumber == SsrcRewritingEngine.INVALID_SEQNUM)
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
     * Calculates and returns the length of this interval. Note that all 32 bits
     * are used to represent the interval length because an interval can span
     * multiple cycles.
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
     * @return {@code true} if the RED was successfully rewritten;
     * {@code false}, otherwise
     */
    private boolean rewriteRED(int primarySSRC, byte[] buf, int off, int len)
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

        // XXX we assume that at most one FEC packet is inside RED.
        REDBlock b = REDBlockIterator.matchFirst(fecPredicate, buf, off, len);
        if (b != null)
        {
            if (!rewriteFEC(primarySSRC, buf, b.getOffset(), b.getLength()))
            {
                // TODO remove the FEC blocks that were not successfully
                // rewritten.
            }
        }

        return true;
    }

    /**
     * Rewrites the SN base in the FEC Header.
     *
     * TODO do we need to change any other fields? Look at the FECSender.
     *
     * @param buf
     * @param off
     * @param len
     * @return {@code true} if the FEC was successfully rewritten;
     * {@code false}, otherwise
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
            = getSsrcRewritingEngine().origin2rewriter.get(sourceSSRC);
        int snRewritenBase
            = rewriter.rewriteSequenceNumber(sourceSSRC, snBase);

        if (snRewritenBase == SsrcRewritingEngine.INVALID_SEQNUM)
        {
            logInfo(
                    "We could not find a sequence number interval for a FEC"
                        + " packet.");
            return false;
        }

        buf[off + 2] = (byte) (snRewritenBase & 0xff00 >> 8);
        buf[off + 3] = (byte) (snRewritenBase & 0x00ff);
        return true;
    }

    /**
     * Gets the {@code SsrcGroupRewriter} which has initialized this instance
     * and is its owner.
     *
     * @return the {@code SsrcGroupRewriter} which has initialized this instance
     * and is its owner
     */
    public SsrcGroupRewriter getSsrcGroupRewriter()
    {
        return ssrcRewriter.ssrcGroupRewriter;
    }

    /**
     * Gets the {@code SsrcRewritingEngine} associated with this instance i.e.
     * which owns the {@code SsrcGroupRewriter} which in turn owns this
     * instance.
     *
     * @return the {@code SsrcRewritingEngine} associated with this instance
     */
    public SsrcRewritingEngine getSsrcRewritingEngine()
    {
        return getSsrcGroupRewriter().ssrcRewritingEngine;
    }

    private void logDebug(String msg)
    {
        ssrcRewriter.logDebug(msg);
    }

    private void logInfo(String msg)
    {
        ssrcRewriter.logInfo(msg);
    }

    private void logWarn(String msg)
    {
        ssrcRewriter.logWarn(msg);
    }
}
