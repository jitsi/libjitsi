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
import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Rewrites SSRCs and sequence numbers of a given source SSRC. This
 * class is not thread-safe.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
class SsrcRewriter
{
    /**
     * The <tt>Logger</tt> used by the <tt>SsrcRewritingEngine</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger = Logger.getLogger(SsrcRewriter.class);

    /**
     * The value of {@link Logger#isDebugEnabled()} from the time of the
     * initialization of the class {@code SsrcRewriter} cached for the purposes
     * of performance.
     */
    private static final boolean DEBUG;

    /**
     * The value of {@link Logger#isTraceEnabled()} from the time of the
     * initialization of the class {@code SsrcRewriter} cached for the purposes
     * of performance.
     */
    private static final boolean TRACE;

    /**
     * The maximum number of entries in the timestamp history.
     */
    private static final int TS_HISTORY_MAX_ENTRIES = 100;

    /**
     * The origin SSRC that this <tt>SsrcRewriter</tt> rewrites. The
     * target SSRC is managed by the parent <tt>SsrcGroupRewriter</tt>.
     */
    private final int sourceSSRC;

    /**
     * The owner of this instance.
     */
    public final SsrcGroupRewriter ssrcGroupRewriter;

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
     * The MRU timestamp history.
     */
    private final Map<Long, Long> tsHistory = new LinkedHashMap<Long, Long>() {

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > TS_HISTORY_MAX_ENTRIES;
        }
    };

    /**
     * This is the current sequence number interval for this origin
     * SSRC. We can't have it in the intervals navigable map because
     * its max isn't determined yet. If this is null, then it means that
     * this original SSRC is paused (invariant).
     */
    private ExtendedSequenceNumberInterval currentExtendedSequenceNumberInterval;

    /**
     * Static init.
     */
    static
    {
        DEBUG = logger.isDebugEnabled();
        TRACE = logger.isTraceEnabled();
    }

    /**
     * Ctor.
     *
     * @param ssrcGroupRewriter
     * @param sourceSSRC
     */
    public SsrcRewriter(SsrcGroupRewriter ssrcGroupRewriter, int sourceSSRC)
    {
        this.ssrcGroupRewriter = ssrcGroupRewriter;
        this.sourceSSRC = sourceSSRC;
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
     * Rewrites (the SSRC, sequence number, timestamp, etc. of) a specific RTP
     * packet.
     *
     * @param pkt the {@code RawPacket} which represents the RTP packet to be
     * rewritten
     */
    public RawPacket rewriteRTP(RawPacket pkt)
    {
        int seqnum = pkt.getSequenceNumber();
        int extendedSeqnum = extendOriginalSequenceNumber(seqnum);

        // first, check if this is a retransmission and rewrite using
        // an appropriate interval.
        ExtendedSequenceNumberInterval retransmissionInterval
            = findRetransmissionInterval(extendedSeqnum);

        if (retransmissionInterval != null)
        {
            long ssrc = pkt.getSSRCAsLong();
            pkt = retransmissionInterval.rewriteRTP(pkt);

            if (DEBUG)
            {
                logger.debug(
                        "Retransmitting packet with SEQNUM " + seqnum
                            + " of SSRC " + ssrc
                            + " retran SSRC: " + pkt.getSSRCAsLong()
                            + " retran SEQNUM: " + pkt.getSequenceNumber());
            }
        }
        else
        {
            // this is not a retransmission.

            if (currentExtendedSequenceNumberInterval == null)
            {
                // the stream has resumed.
                currentExtendedSequenceNumberInterval
                    = new ExtendedSequenceNumberInterval(
                    this,
                    extendedSeqnum,
                    ssrcGroupRewriter.currentExtendedSeqnumBase);
            }
            else
            {
                // more packets to the stream, increase the sequence number
                // interval range.
                currentExtendedSequenceNumberInterval.extendedMaxOrig
                    = extendedSeqnum;
            }
            currentExtendedSequenceNumberInterval.lastSeen
                = System.currentTimeMillis();

            pkt = currentExtendedSequenceNumberInterval.rewriteRTP(pkt);
        }

        if (pkt != null)
        {
            rewriteTimestamp(pkt);
        }

        return pkt;
    }

    /**
     * Rewrites the RTP timestamp of a specific RTP packet.
     *
     * @param p the {@code RawPacket} which represents the RTP packet to rewrite
     * the RTP timestamp of
     */
    void rewriteTimestamp(RawPacket p)
    {
        // Provide rudimentary optimization and, more importantly, protection in
        // the case of RawPackets from the same frame/sampling instance.

        // Simply cache the last rewritten RTP timestamp and reuse it in
        // subsequent rewrites.

        long oldValue = p.getTimestamp();

        if (tsHistory.containsKey(oldValue))
        {
            long tsDest = tsHistory.get(oldValue);
            p.setTimestamp(tsDest);

            if (TRACE)
            {
                logger.trace("Rewriting (SSRC=" + p.getSSRCAsLong()
                    + ", seqnum=" + p.getSequenceNumber()
                    + ") timestamp using cached value "
                    + oldValue + " to " + p.getTimestamp());
            }
        }
        else
        {
            SsrcGroupRewriter ssrcGroupRewriter = this.ssrcGroupRewriter;
            long timestampSsrcAsLong = ssrcGroupRewriter.getTimestampSsrc();
            int sourceSsrc = getSourceSSRC();

            if (timestampSsrcAsLong == SsrcRewritingEngine.INVALID_SSRC)
            {
                // The first pkt to require RTP timestamp rewriting determines
                // the SSRC which will NOT undergo RTP timestamp rewriting.
                // Unless SsrcGroupRewriter decides to force the SSRC for RTP
                // timestamp rewriting, of course.
                ssrcGroupRewriter.setTimestampSsrc(sourceSsrc);
            }
            else
            {
                int timestampSsrc = (int) timestampSsrcAsLong;

                if (sourceSsrc != timestampSsrc)
                {
                    // Rewrite the RTP timestamp of pkt in accord with the
                    // wallclock of timestampSsrc.
                    rewriteTimestamp(p, sourceSsrc, timestampSsrc);
                }

                ssrcGroupRewriter.maybeUpliftTimestamp(p);
            }

            long newValue = p.getTimestamp();

            if (TRACE)
            {
                logger.trace("Fully rewriting (SSRC=" + p.getSSRCAsLong()
                    + ", seqnum=" + p.getSequenceNumber()
                    + ") timestamp " + oldValue
                    + " to " + newValue);
            }

            tsHistory.put(oldValue, newValue);
        }
    }

    /**
     * Rewrites the RTP timestamp of a specific RTP packet.
     *
     * @param p the {@code RawPacket} which represents the RTP packet to rewrite
     * the RTP timestamp of
     * @param sourceSsrc the SSRC of the source from which {@code p} originated
     * @param timestampSsrc the SSRC of the RTP stream which identifies the RTP
     * timestamp base into which the RTP timestamp of {@code p} is to be
     * rewritten
     */
    private void rewriteTimestamp(
            RawPacket p,
            int sourceSsrc, int timestampSsrc)
    {
        // TODO The only RTP timestamp rewriting supported at the time of this
        // writing depends on the availability of remote wallclocks.

        // Convert the SSRCs to RemoteClocks.
        int[] ssrcs = { sourceSsrc, timestampSsrc };
        RemoteClock[] clocks
            = RemoteClock.findRemoteClocks(ssrcGroupRewriter.ssrcRewritingEngine
                .getMediaStream(), ssrcs);

        // Require all/the two RemoteClocks to carry out the RTP timestamp
        // rewriting.
        for (int i = 0; i < ssrcs.length; ++i)
        {
            if (clocks[i] == null)
            {
                if (DEBUG)
                {
                    logger.debug(
                            "No remote wallclock available for SSRC "
                                + (ssrcs[i] & 0xffffffffL) + "!.");
                }
                return;
            }
        }

        rewriteTimestamp(p, clocks[0], clocks[1]);
    }

    /**
     * Rewrites the RTP timestamp of a specific RTP packet.
     *
     * @param p the {@code RawPacket} which represents the RTP packet to rewrite
     * the RTP timestamp of
     * @param srcClock the {@code RemoteClock} of the source from which
     * {@code p} originated
     * @param dstClock the {@code RemoteClock} which identifies the RTP
     * timestamp base into which the RTP timestamp of {@code p} is to be
     * rewritten
     */
    private void rewriteTimestamp(
            RawPacket p,
            RemoteClock srcClock, RemoteClock dstClock)
    {
        // XXX Presume that srcClock and dstClock represent the same wallclock
        // (in terms of system time in milliseconds/NTP time). Technically, this
        // presumption may be wrong. Practically, we are unlikely (at the time
        // of this writing) to hit a case in which this presumption is wrong.

        // Convert the RTP timestamp of p to system time in milliseconds using
        // srcClock.
        long srcRtpTimestamp = p.getTimestamp();
        Timestamp srcTs
            = srcClock.rtpTimestamp2remoteSystemTimeMs(srcRtpTimestamp);

        if (srcTs == null)
            return;

        // Convert the system time in milliseconds to an RTP timestamp using
        // dstClock.
        Timestamp dstTs
            = dstClock.remoteSystemTimeMs2rtpTimestamp(srcTs.getSystemTimeMs());

        if (dstTs == null)
            return;

        long dstRtpTimestamp = dstTs.getRtpTimestampAsLong();

        p.setTimestamp(dstRtpTimestamp);
    }

    /**
     * Moves the current sequence number interval in the {@link #intervals}
     * tree. It is not to be updated anymore.
     */
    public void pause()
    {
        if (currentExtendedSequenceNumberInterval != null)
        {
            intervals.put(
                    currentExtendedSequenceNumberInterval.extendedMaxOrig,
                    currentExtendedSequenceNumberInterval);

            currentExtendedSequenceNumberInterval = null;

            // TODO We don't need to keep track of more than 2 cycles, so we
            // need to trim the intervals tree to accommodate just that.
        }
        else
        {
            // this stream is already paused.
            logger.info("The stream is already paused.");
        }
    }

    /**
     *
     * @param origExtendedSeqnum the original extended sequence number.
     * @return
     */
    public ExtendedSequenceNumberInterval findRetransmissionInterval(
            int origExtendedSeqnum)
    {
        // first check in the current sequence number interval.
        if (currentExtendedSequenceNumberInterval != null
                && currentExtendedSequenceNumberInterval.contains(
                        origExtendedSeqnum))
        {
            return currentExtendedSequenceNumberInterval;
        }

        // not there, try to find the sequence number in a previous
        // interval.
        Map.Entry<Integer, ExtendedSequenceNumberInterval> candidateEntry
            = intervals.ceilingEntry(origExtendedSeqnum);

        if (candidateEntry != null)
        {
            ExtendedSequenceNumberInterval candidateInterval
                = candidateEntry.getValue();

            if (candidateInterval.contains(origExtendedSeqnum))
            {
                return candidateInterval;
            }
        }

        return null;
    }

    /**
     *
     * @param origSeqnum
     * @return
     */
    int extendOriginalSequenceNumber(int origSeqnum)
    {
        SSRCCache ssrcCache
            = ssrcGroupRewriter.ssrcRewritingEngine
                .getMediaStream().getStreamRTPManager().getSSRCCache();

        if (ssrcCache != null)
        {
            // XXX We make sure in BasicRTCPTerminationStrategy that the
            // SSRCCache exists so we do the same here.

            SSRCInfo sourceSSRCInfo = ssrcCache.cache.get(getSourceSSRC());

            if (sourceSSRCInfo != null)
                return sourceSSRCInfo.extendSequenceNumber(origSeqnum);
        }
        return origSeqnum;
    }
}
