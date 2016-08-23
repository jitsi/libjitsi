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

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.util.*;
import org.jitsi.util.function.*;

/**
 * Does the dirty job of rewriting SSRCs and sequence numbers of a given
 * extended sequence number interval of a given source SSRC. This class is not
 * thread safe.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
class ExtendedSequenceNumberInterval
{
    /**
     * The <tt>Logger</tt> used by the <tt>ExtendedSequenceNumberInterval</tt>
     * class and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(ExtendedSequenceNumberInterval.class);

    /**
     * The value of {@link Logger#isDebugEnabled()} from the time of the
     * initialization of the class {@code ExtendedSequenceNumberInterval} cached
     * for the purposes of performance.
     */
    private static final boolean DEBUG;

    /**
     * The value of {@link Logger#isWarnEnabled()} from the time of the
     * initialization of the class {@code ExtendedSequenceNumberInterval} cached
     * for the purposes of performance.
     */
    private static final boolean WARN;

    /**
     * The value of {@link Logger#isTraceEnabled()} from the time of the
     * initialization of the class {@code ExtendedSequenceNumberInterval} cached
     * for the purposes of performance.
     */
    private static final boolean TRACE;

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
     * Static init.
     */
    static
    {
        DEBUG = logger.isDebugEnabled();
        WARN = logger.isWarnEnabled();
        TRACE = logger.isTraceEnabled();
    }
    /**
     * The predicate used to match FEC <tt>REDBlock</tt>s.
     */
    private final Predicate<REDBlock> fecPredicate = new Predicate<REDBlock>()
    {
        public boolean test(REDBlock redBlock)
        {
            Map<Long, Byte> ssrc2fec = getSsrcRewritingEngine().ssrc2fec;
            long sourceSSRC = ssrcRewriter.getSourceSSRC();
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
     * Ctor.
     *
     * @param ssrcRewriter
     * @param extendedBaseOrig
     * @param extendedBaseTarget
     */
    public ExtendedSequenceNumberInterval(
            SsrcRewriter ssrcRewriter,
            int extendedBaseOrig, int extendedBaseTarget)
    {
        this.ssrcRewriter = ssrcRewriter;
        this.extendedBaseTarget = extendedBaseTarget;

        this.extendedMinOrig = extendedBaseOrig;
        this.extendedMaxOrig = extendedBaseOrig;
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
        long ssrcTarget = ssrcGroupRewriter.getSSRCTarget();

        pkt.setSSRC((int) ssrcTarget);

        // Sequence number
        int seqnum = pkt.getSequenceNumber();
        int extendedSeqnum = ssrcRewriter.extendOriginalSequenceNumber(seqnum);
        if (extendedSeqnum < extendedMinOrig)
        {
            // This is expected to happen if we just switched simulcast streams,
            // and we received retransmissions for packets before the switch.
            // Drop these, because they are not supposed to be sent to the
            // received (their sequence number has been used by packets from
            // the previous stream).
            if (DEBUG)
            {
                logger.debug(
                    "Dropping a packet outside this interval: " + pkt
                        + ", streamHashCode=" + ssrcGroupRewriter
                        .ssrcRewritingEngine.getMediaStream().hashCode());
            }
            return null;
        }

        int rewriteSeqnum = rewriteExtendedSequenceNumber(extendedSeqnum);

        pkt.setSequenceNumber(rewriteSeqnum);

        SsrcRewritingEngine ssrcRewritingEngine
            = ssrcGroupRewriter.ssrcRewritingEngine;
        Map<Long, Long> rtx2primary = ssrcRewritingEngine.rtx2primary;
        long sourceSSRC = ssrcRewriter.getSourceSSRC();
        Long primarySSRC = rtx2primary.get(sourceSSRC);

        if (primarySSRC == null)
            primarySSRC = sourceSSRC;

        byte pt = pkt.getPayloadType();
        boolean rtx = rtx2primary.containsKey(sourceSSRC);

        // RED
        Byte red = ssrcRewritingEngine.ssrc2red.get(sourceSSRC);
        if (red != null && red == pt)
        {
            byte[] buf = pkt.getBuffer();
            int osnLen = rtx ? 2 : 0;
            int off = pkt.getPayloadOffset() + osnLen;
            int len = pkt.getPayloadLength() - osnLen;

            rewriteRED(primarySSRC, buf, off, len);
        }

        // FEC
        Byte fec = ssrcRewritingEngine.ssrc2fec.get(sourceSSRC);
        if (fec != null && fec == pt)
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

        return pkt;
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
        long sourceSSRC = ssrcRewriter.getSourceSSRC();
        long ssrcOrig = ssrcRewritingEngine.rtx2primary.get(sourceSSRC);
        int snOrig = pkt.getOriginalSequenceNumber();

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
            pkt.setOriginalSequenceNumber(sequenceNumber);
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
    private boolean rewriteRED(long primarySSRC, byte[] buf, int off, int len)
    {
        if (buf == null || buf.length == 0)
        {
            logger.warn("The buffer is empty."
                + ", streamHashCode=" + ssrcRewriter.ssrcGroupRewriter
                .ssrcRewritingEngine.getMediaStream().hashCode());
            return false;
        }

        if (buf.length < off + len)
        {
            logger.warn("The buffer is invalid."
                + ", streamHashCode=" + ssrcRewriter.ssrcGroupRewriter
                .ssrcRewritingEngine.getMediaStream().hashCode());
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
    private boolean rewriteFEC(long sourceSSRC, byte[] buf, int off, int len)
    {
        if (buf == null || buf.length == 0)
        {
            logger.warn("The buffer is empty."
                + ", streamHashCode=" + ssrcRewriter.ssrcGroupRewriter
                .ssrcRewritingEngine.getMediaStream().hashCode());
            return false;
        }
        if ((buf.length < off + len) || (len < 4))
        {
            logger.warn("The buffer is invalid."
                + ", streamHashCode=" + ssrcRewriter.ssrcGroupRewriter
                .ssrcRewritingEngine.getMediaStream().hashCode());
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
        int snBase = (buf[off + 2] & 0xff) << 8 | (buf[off + 3] & 0xff);

        SsrcGroupRewriter rewriter
            = getSsrcRewritingEngine().origin2rewriter.get(sourceSSRC);
        int snRewritenBase
            = rewriter.rewriteSequenceNumber(sourceSSRC, snBase);

        if (snRewritenBase == SsrcRewritingEngine.INVALID_SEQNUM)
        {
            logger.info(
                    "We could not find a sequence number interval for a FEC"
                        + " packet." +  ", streamHashCode=" + ssrcRewriter
                        .ssrcGroupRewriter.ssrcRewritingEngine
                        .getMediaStream().hashCode());
            return false;
        }

        if (TRACE)
        {
            logger.trace("Rewriting FEC packet SN base "
                + snBase + " to " + snRewritenBase +  ", streamHashCode="
                + ssrcRewriter.ssrcGroupRewriter.ssrcRewritingEngine
                .getMediaStream().hashCode());
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
}
