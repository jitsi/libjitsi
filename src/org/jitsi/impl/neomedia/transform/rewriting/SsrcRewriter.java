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
import org.jitsi.impl.neomedia.rtp.*;
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
     * The maximum number of entries in the timestamp/frame history. The purpose
     * of the timestamp history is to make sure that we always rewrite the RTP
     * timestamp of a frame to the same value. The reason for doing this is
     * two-fold: 
     *
     * 1) The clock at the sender can drift or the sender might be buggy and
     * send RTCP SRs that indicate drift. So, if we get an SR that indicates
     * drift in between same-frame RTP packets and if we're fully rewritting
     * the RTP timestamps, we might end-up with RTP packets from the same frame
     * having different timestamps.
     *
     * 2) Performance. We don't have to re-run the same computation over and
     * over again.
     *
     * Assuming a 30fps video, 300 sounds like a reasonable value (which is
     * equivalent to roughly 10 seconds). There should be no practical case
     * where we get an RTP packet from a frame that does not fit in that.
     */
    private static final int TS_HISTORY_MAX_ENTRIES = 300;

    /**
     * The origin SSRC that this <tt>SsrcRewriter</tt> rewrites. The
     * target SSRC is managed by the parent <tt>SsrcGroupRewriter</tt>.
     */
    private final long sourceSSRC;

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
     */
    private final NavigableMap<Integer, ExtendedSequenceNumberInterval>
        intervals
            = new TreeMap<>();

    /**
     * The MRU target timestamp history.
     */
    private final Map<Long, TimestampEntry> tsHistory
        = new LinkedHashMap<Long, TimestampEntry>() {

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > TS_HISTORY_MAX_ENTRIES;
        }
    };

    /*
     * Keeps the last item added in the {@link tsHistory}.
     */
    private TimestampEntry maxSourceTsEntry;

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
    public SsrcRewriter(SsrcGroupRewriter ssrcGroupRewriter, long sourceSSRC)
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
    public long getSourceSSRC()
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
                logger.debug("Retransmitting packet seqnum=" + seqnum
                        + ", ssrc=" + ssrc
                        + ", retran_ssrc=" + pkt.getSSRCAsLong()
                        + ", retran_seqnum=" + pkt.getSequenceNumber()
                        + ", streamHashCode=" + ssrcGroupRewriter
                            .ssrcRewritingEngine.getMediaStream().hashCode());
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

        long now = System.currentTimeMillis();
        TimestampEntry tsEntry = tsHistory.get(oldValue);
        boolean rewritten = false;
        if (tsEntry != null && tsEntry.isFresh(now))
        {
            long tsDest = tsEntry.dest;
            p.setTimestamp(tsDest);

            if (TRACE)
            {
                RemoteClock srcClock = ssrcGroupRewriter.ssrcRewritingEngine
                    .getMediaStream().getStreamRTPManager()
                    .findRemoteClock(sourceSSRC);

                RemoteClock dstClock =  ssrcGroupRewriter.ssrcRewritingEngine
                    .getMediaStream().getStreamRTPManager()
                    .findRemoteClock(p.getSSRCAsLong());

                long srcMs = (srcClock != null)
                    ? srcClock.rtpTimestamp2remoteSystemTimeMs(oldValue)
                        .getSystemTimeMs()
                    : -1;


                long dstMs = (dstClock != null)
                    ? dstClock.rtpTimestamp2remoteSystemTimeMs(oldValue)
                        .getSystemTimeMs()
                    : -1;

                logger.trace("Rewriting timestamp from the cache "
                    + "ssrc=" + p.getSSRCAsLong()
                    + ", seqnum=" + p.getSequenceNumber()
                    + ", srcTs=" + oldValue
                    + ", srcTime=" + new Date(srcMs)
                    + ", srcTimeMs=" + srcMs
                    + ", newTs=" + p.getTimestamp()
                    + ", newTime=" + new Date(dstMs)
                    + ", newTimeMs=" + dstMs
                    + ", streamHashCode=" + ssrcGroupRewriter
                        .ssrcRewritingEngine.getMediaStream().hashCode());
            }

            rewritten = true;
        }
        else if (maxSourceTsEntry != null
            && maxSourceTsEntry.isFresh(now))
        {
            long delta = TimeUtils.rtpDiff(oldValue, maxSourceTsEntry.src);
            if (delta < 0)
            {
                // The current packet belongs to a frame F of an RTP stream S.
                // Reaching this point means this is the first time we see
                // frame F, and we've already seen F' such that F' > F (because
                // delta < 0). This is a frame re-ordering.
                //
                // This case needs special treatment. We must not perform RTP
                // timestamp uplifting and we must not rewrite to something
                // that's bigger than dest(F').
                //
                // Note that we only correctly handle the case where F' = F + 1,
                // i.e. frame re-orderings where the distance between the frames
                // is -1. It shouldn't be difficult to handle the general case
                // where F' = F + n, but it requires a different data structure
                // for keeping the timestamp history (a NavigableMap that can
                // also be used as an MRU).

                long timestampSsrc = ssrcGroupRewriter.getTimestampSsrc();

                // First, try to rewrite the RTP timestamp of pkt in accord with
                // the wallclock of timestampSsrc.
                long sourceSsrc = getSourceSSRC();
                if (sourceSsrc != timestampSsrc)
                {
                    // Rewrite the RTP timestamp of pkt in accord with the
                    // wallclock of timestampSsrc.
                    rewriteTimestamp(p, sourceSsrc, timestampSsrc);
                }

                long newValue = p.getTimestamp();
                long delta$1 = TimeUtils.rtpDiff(
                    maxSourceTsEntry.dest, newValue);

                if (delta$1 <= 0)
                {
                    // It seems that rewriting using the wallclocks resulted in
                    // a frame timestamp that is bigger than what we expect.
                    // Downlifting.
                    newValue = maxSourceTsEntry.dest - 1;
                    p.setTimestamp(newValue);

                    if (TRACE)
                    {
                        logger.trace("Downlifting re-ordered frame with "
                            + " ssrc=" + p.getSSRCAsLong()
                            + ", seqnum=" + p.getSequenceNumber()
                            + ") timestamp using cached value "
                            + oldValue + " to " + newValue);
                    }
                }
                else if (TRACE)
                {
                    RemoteClock srcClock = ssrcGroupRewriter.ssrcRewritingEngine
                        .getMediaStream().getStreamRTPManager()
                        .findRemoteClock(sourceSSRC);

                    RemoteClock dstClock =  ssrcGroupRewriter
                        .ssrcRewritingEngine.getMediaStream()
                        .getStreamRTPManager().findRemoteClock(
                            p.getSSRCAsLong());

                    long srcMs = (srcClock != null)
                        ? srcClock.rtpTimestamp2remoteSystemTimeMs(oldValue)
                            .getSystemTimeMs()
                        : -1;


                    long dstMs = (dstClock != null)
                        ? dstClock.rtpTimestamp2remoteSystemTimeMs(oldValue)
                            .getSystemTimeMs()
                        : -1;

                    logger.trace("Rewriting re-ordered frame "
                        + "ssrc=" + p.getSSRCAsLong()
                        + ", seqnum=" + p.getSequenceNumber()
                        + ", srcTs=" + oldValue
                        + ", srcTime=" + new Date(srcMs)
                        + ", srcTimeMs=" + srcMs
                        + ", newTs=" + p.getTimestamp()
                        + ", newTime=" + new Date(dstMs)
                        + ", newTimeMs=" + dstMs
                        + ", streamHashCode=" + ssrcGroupRewriter
                        .ssrcRewritingEngine.getMediaStream().hashCode());
                }

                tsHistory.put(
                    oldValue, new TimestampEntry(now, oldValue, newValue));
                rewritten = true;
            }
        }

        if (!rewritten)
        {
            SsrcGroupRewriter ssrcGroupRewriter = this.ssrcGroupRewriter;
            long timestampSsrcAsLong = ssrcGroupRewriter.getTimestampSsrc();
            long sourceSsrc = getSourceSSRC();

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
                if (sourceSsrc != timestampSsrcAsLong)
                {
                    // Rewrite the RTP timestamp of pkt in accord with the
                    // wallclock of timestampSsrc.
                    rewriteTimestamp(p, sourceSsrc, timestampSsrcAsLong);
                }

                ssrcGroupRewriter.maybeUpliftTimestamp(p);
            }

            long newValue = p.getTimestamp();

            if (TRACE)
            {
                RemoteClock srcClock = ssrcGroupRewriter.ssrcRewritingEngine
                    .getMediaStream().getStreamRTPManager()
                    .findRemoteClock(sourceSSRC);

                RemoteClock dstClock = ssrcGroupRewriter
                    .ssrcRewritingEngine.getMediaStream()
                    .getStreamRTPManager().findRemoteClock(
                        p.getSSRCAsLong());

                long srcMs = (srcClock != null)
                    ? srcClock.rtpTimestamp2remoteSystemTimeMs(oldValue)
                        .getSystemTimeMs()
                    : -1;

                long dstMs = (dstClock != null)
                    ? dstClock.rtpTimestamp2remoteSystemTimeMs(oldValue)
                        .getSystemTimeMs()
                    : -1;

                logger.trace("Fully rewriting RTP timestamp "
                    + "ssrc=" + p.getSSRCAsLong()
                    + ", seqnum=" + p.getSequenceNumber()
                    + ", timestamp=" + oldValue
                    + ", time=" + new Date(srcMs)
                    + ", newTimestamp=" + p.getTimestamp()
                    + ", newTime=" + new Date(dstMs)
                    + ", streamHashCode=" + ssrcGroupRewriter
                    .ssrcRewritingEngine.getMediaStream().hashCode());
            }

            TimestampEntry newTsEntry
                = new TimestampEntry(now, oldValue, newValue);

            tsHistory.put(oldValue, newTsEntry);
            maxSourceTsEntry = newTsEntry;
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
            long sourceSsrc, long timestampSsrc)
    {
        // TODO The only RTP timestamp rewriting supported at the time of this
        // writing depends on the availability of remote wallclocks.

        // Convert the SSRCs to RemoteClocks.
        long[] ssrcs = { sourceSsrc, timestampSsrc};
        RemoteClock[] clocks
            = ssrcGroupRewriter.ssrcRewritingEngine
            .getMediaStream().getStreamRTPManager().findRemoteClocks(ssrcs);

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
                                + (ssrcs[i]) + "!.");
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
        ResumableStreamRewriter rewriter
            = ssrcGroupRewriter.ssrcRewritingEngine.getMediaStream()
                .getStreamRTPManager().getResumableStreamRewriter(sourceSSRC);

        return rewriter.extendSequenceNumber(origSeqnum);
    }

    /**
     * Holds a timestamp (long) and records the time when it was first seen.
     */
    class TimestampEntry
    {
        private final long added;

        private final long src;

        private final long dest;

        /**
         * Ctor.
         */
        public TimestampEntry(long now, long src, long dest)
        {
            this.src = src;
            this.dest = dest;
            this.added = now;
        }

        /**
         * Gets a boolean indicating whether or not the timestamp is less than
         * 10 seconds old. This is 300 frames for a 30fps video.
         *
         * @return true if the timestamp was added less than 10 seconds ago,
         * false otherwise.
         */
        public boolean isFresh(long now)
        {
            return (now - added) < 10000;
        }
    }
}
