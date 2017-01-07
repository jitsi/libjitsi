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
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * It merges multiple {@code RTPEncoding}s of a {@code MediaStreamTrack} into a
 * single RTP encoding. This class is not thread-safe. If multiple threads
 * access the engine concurrently, it must be synchronized externally.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class SsrcRewritingEngine
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
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
     * 10 seconds old. This is 300 frames for a 30fps video.
     */
    private static final int TS_EXPIRE_MS = 10000;

    /**
     * The maximum number of sequence numbers to store in the extended sequence
     * number intervals for a given SSRC.
     */
    private static final int SEQNUM_HISTORY_MAX_ENTRIES = 2000;

    /**
     * The <tt>Logger</tt> used by the <tt>SsrcRewritingEngine</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(SsrcRewritingEngine.class);

    /**
     * The owner of this instance.
     */
    private final MediaStream stream;

    /**
     * Maps primary SSRC (the primary SSRC of the primary RTP encoding) to
     * a {@code Rewriter}.
     */
    private final Map<Long, Rewriter> rewritersBySSRC = new TreeMap<>();

    /**
     * Ctor.
     *
     * @param stream The {@code MediaStream} that owns this instance.
     */
    public SsrcRewritingEngine(MediaStream stream)
    {
        super(RTPPacketPredicate.INSTANCE);
        this.stream = stream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null; // RTCP is handled by RTCP termination.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        if (pkt == null)
        {
            return pkt;
        }

        long encodingSSRC = pkt.getSSRCAsLong();

        Rewriter rewriter = rewritersBySSRC.get(encodingSSRC);
        if (rewriter == null)
        {
            // Find the RTPEncoding that corresponds to this SSRC.
            StreamRTPManager receiveRTPManager = stream.getRTPTranslator()
                .findStreamRTPManagerByReceiveSSRC((int) encodingSSRC);

            MediaStreamTrackReceiver receiver = null;
            if (receiveRTPManager != null)
            {
                MediaStream receiveStream = receiveRTPManager.getMediaStream();
                if (receiveStream != null)
                {
                    receiver = receiveStream.getMediaStreamTrackReceiver();
                }
            }

            if (receiver == null)
            {
                return pkt;
            }

            RTPEncoding encoding = receiver.resolveRTPEncoding(pkt);

            if (encoding == null)
            {
                // Maybe signaling hasn't propagated yet, or maybe this track
                // only has a single RTPEncoding.

                // Maybe signaling hasn't propagated yet.
                if (logger.isDebugEnabled())
                {
                    logger.debug("encoding_not_found"
                        + ",stream_hash=" + stream.hashCode()
                        + ",encodingSSRC=" + encodingSSRC
                        + " seqNum=" + pkt.getSequenceNumber());
                }

                return pkt;
            }

            MediaStreamTrack track = encoding.getMediaStreamTrack();
            RTPEncoding[] encodings = track.getRTPEncodings();

            if (encodings == null || encodings.length < 2)
            {
                // no simulcast.
                return pkt;
            }

            long trackSSRC = encodings[0].getPrimarySSRC();

            rewriter = rewritersBySSRC.get(trackSSRC);
            if (rewriter == null)
            {
                // Maybe signaling hasn't propagated yet.
                if (logger.isDebugEnabled())
                {
                    logger.debug("new_rewriter"
                        + ",stream_hash=" + stream.hashCode()
                        + ",encodingSSRC=" + encodingSSRC
                        + ",trackSSRC=" + trackSSRC
                        + " seqNum=" + pkt.getSequenceNumber());
                }

                rewriter = new Rewriter(trackSSRC);
                rewritersBySSRC.put(trackSSRC, rewriter);
                rewritersBySSRC.put(encodingSSRC, rewriter);
            }
        }

        long nowMs = System.currentTimeMillis();

        boolean res = rewriter.rewrite(pkt, nowMs);

        return res ? pkt : null;
    }

    /**
     * Merges together into a single {@code RTPEncoding} packets from multiple
     * {@code RTPEncoding}s of the same {@code MediaStreamTrack}.
     */
    class Rewriter
    {
        /**
         * The primary SSRC of the primary encoding of the
         * {@code MediaStreamTrack} that this {@code Rewriter} is rewriting.
         */
        private final long trackSSRC;

        /**
         * The {@code SSRCState}s that this {@code Rewriter} manages, indexed by
         * SSRC.
         */
        private final Map<Long, SSRCState> ssrcStateBySSRC = new TreeMap<>();

        /**
         * The {@code ExtendedSequenceNumberInterval} that is currently used for
         * rewriting RTP packets.
         */
        private ExtendedSequenceNumberInterval curInterval;

        /**
         * Ctor.
         *
         * @param trackSSRC the SSRC of the track that this rewriter manages.
         */
        public Rewriter(long trackSSRC)
        {
            this.trackSSRC = trackSSRC;
        }

        /**
         * Maps the given sequence number from the SSRC sequence
         * number space of ssrcState.ssrc to the track sequence number space.
         *
         * @param srcSeqNum the sequence number of the sequence number space of
         * SSRC.
         * @param ssrcState
         *
         * @return the sequence number mapped to the track sequence number
         * space.
         */
        private int rewriteSequenceNumber(
            int srcSeqNum, SSRCState ssrcState)
        {
            // First, extend the sequence number. This is necessary because blah
            // blah.
            int srcExtSeqNum = stream.getStreamRTPManager()
                .getResumableStreamRewriter(ssrcState.encodingSSRC)
                .extendSequenceNumber(srcSeqNum);

            // Try to translate using the current interval.
            if (curInterval != null
                && curInterval.ssrc == ssrcState.encodingSSRC)
            {
                int dstExtSeqNum = curInterval.rewrite(
                    srcExtSeqNum, true /* canExtend */);
                if (dstExtSeqNum != -1)
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("cur_interval"
                            + ",stream_hash=" + stream.hashCode()
                            + ",srcSSRC=" + ssrcState.encodingSSRC
                            + ",dstSSRC=" + trackSSRC
                            + " srcSeqnum=" + srcExtSeqNum
                            + ",dstSeqnum=" + dstExtSeqNum);
                    }
                    return dstExtSeqNum;
                }
            }

            // The packet is either old (in which case it should belong to
            // a closed interval), or it's new from a different encoding
            // than the one we are currently rewriting (in which case it's
            // time to replace the current interval).

            // Check if this is an old packet.
            Map.Entry<Integer, ExtendedSequenceNumberInterval> ceilingEntry
                = ssrcState.intervals.ceilingEntry(srcExtSeqNum);

            if (ceilingEntry != null)
            {
                ExtendedSequenceNumberInterval ceiling = ceilingEntry.getValue();
                // This is an old packet. Rewrite, if possible, drop
                // otherwise.
                int dstExtSeqNum = ceiling.rewrite(
                    srcExtSeqNum, false /* canExtend */);
                if (dstExtSeqNum == -1)
                {
                    logger.warn("packet_out_of_bounds"
                        + ",stream_hash=" + stream.hashCode()
                        + ",srcSSRC=" + ssrcState.encodingSSRC
                        + ",dstSSRC=" + trackSSRC
                        + " srcSeqnum=" + srcExtSeqNum
                        + ",dstSeqnum=" + dstExtSeqNum
                        + ",min=" + ceiling.min
                        + ",max=" + ceiling.max);
                }

                return dstExtSeqNum;
            }

            // Find the highest sequence number that we have sent out for
            // the track SSRC.
            int highestSent;
            if (curInterval != null)
            {
                highestSent = curInterval.rewrite(curInterval.max, false);
            }
            else
            {
                highestSent = stream.getMediaStreamStats()
                    .getSendStats(trackSSRC).getHighestSent();

                if (highestSent == -1)
                {
                    // Pretend we've sent the previous packet.
                    highestSent = srcExtSeqNum - 1;
                }
            }

            int dstExtSeqNum = highestSent + 1;

            if (curInterval != null)
            {
                // Store the current interval.
                SSRCState s = ssrcStateBySSRC.get(curInterval.ssrc);
                s.intervals.put(curInterval.max, curInterval);

                // Cleanup the intervals map.
                int numOfPackets = 0;
                Iterator<Map.Entry<Integer, ExtendedSequenceNumberInterval>>
                    it = s.intervals.descendingMap().entrySet().iterator();

                while (it.hasNext())
                {
                    Map.Entry<Integer, ExtendedSequenceNumberInterval>
                        next = it.next();
                    numOfPackets += next.getValue().length();

                    if (numOfPackets > SEQNUM_HISTORY_MAX_ENTRIES)
                    {
                        if (logger.isDebugEnabled())
                        {
                            logger.debug("interval_cleanup"
                                + " min=" + next.getValue().min
                                + ",max=" + next.getValue().max);
                        }

                        it.remove();
                    }
                }
            }

            int delta = dstExtSeqNum - srcExtSeqNum;
            curInterval = new ExtendedSequenceNumberInterval(
                ssrcState.encodingSSRC, delta, srcExtSeqNum);

            if (logger.isDebugEnabled())
            {
                logger.debug("new_interval"
                    + ",stream_hash=" + stream.hashCode()
                    + ",srcSSRC=" + ssrcState.encodingSSRC
                    + ",dstSSRC=" + trackSSRC
                    + " srcSeqnum=" + srcExtSeqNum
                    + ",dstSeqnum=" + dstExtSeqNum
                    + ",delta=" + delta);
            }

            return dstExtSeqNum;
        }

        /**
         * Rewrites the timestamp of a frame of an RTP encoding to a timestamp
         * of the track SSRC.
         *
         * @param srcTs the RTP timestamp from the source SSRC.
         * @param ssrcState the state of the source SSRC.
         * @param nowMs the current time in milliseconds.
         */
        private long rewriteTimestamp(long srcTs, SSRCState ssrcState,
                ExtendedSequenceNumberInterval maxInterval, long nowMs)
        {
            // Check if this timestamp is in the timestamp history.
            TimestampEntry oldTsEntry = ssrcState.tsHistory.get(srcTs);
            if (oldTsEntry != null && oldTsEntry.isFresh(nowMs))
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("rewrite_old_frame_success"
                        + ",stream_hash=" + stream.hashCode()
                        + ",srcSSRC=" + ssrcState.encodingSSRC
                        + ",dstSSRC=" + trackSSRC
                        + " srcTs=" + srcTs
                        + ",dstTs=" + oldTsEntry.dstTs);
                }

                return oldTsEntry.dstTs;
            }

            long dstTs;
            if (ssrcState.encodingSSRC != trackSSRC)
            {
                // Rewrite it in accord with the wallclock of trackSSRC.

                // Convert the SSRCs to RemoteClocks.
                long[] ssrcs = { ssrcState.encodingSSRC, trackSSRC};
                RemoteClock[] clocks
                    = stream.getStreamRTPManager().findRemoteClocks(ssrcs);

                // Require all/the two RemoteClocks to carry out the RTP
                // timestamp rewriting.
                if (clocks.length != 2 || clocks[0] == null || clocks[1] == null)
                {
                    logger.warn("clock_not_found"
                        + ",stream_hash=" + stream.hashCode()
                        + ",srcSSRC=" + ssrcState.encodingSSRC
                        + ",dstSSRC=" + trackSSRC);
                    return -1;
                }


                // XXX Presume that srcClock and dstClock represent the same
                // wallclock (in terms of system time in milliseconds/NTP time).
                // Technically, this presumption may be wrong. Practically, we
                // are unlikely (at the time of this writing) to hit a case in
                // which this presumption is wrong.

                // Convert the RTP timestamp of p to system time in milliseconds
                // using srcClock.
                Timestamp srcTimestamp = clocks[0]
                    .rtpTimestamp2remoteSystemTimeMs(srcTs);

                if (srcTimestamp == null)
                {
                    logger.warn("rtp_to_system_failed"
                        + ",stream_hash=" + stream.hashCode()
                        + ",srcSSRC=" + ssrcState.encodingSSRC
                        + ",dstSSRC=" + trackSSRC);
                    return -1;
                }

                // Convert the system time in milliseconds to an RTP timestamp
                // using dstClock.
                Timestamp dstTimestamp = clocks[1]
                    .remoteSystemTimeMs2rtpTimestamp(srcTimestamp.getSystemTimeMs());

                if (dstTimestamp == null)
                {
                    logger.warn("system_to_rtp_failed"
                        + ",stream_hash=" + stream.hashCode()
                        + ",srcSSRC=" + ssrcState.encodingSSRC
                        + ",dstSSRC=" + trackSSRC);
                    return -1;
                }

                dstTs = dstTimestamp.getRtpTimestampAsLong();
            }
            else
            {
                dstTs = srcTs;
            }

            if (ssrcState.maxTsEntry != null
                && ssrcState.maxTsEntry.isFresh(nowMs)
                && TimeUtils.rtpDiff(srcTs, ssrcState.maxTsEntry.srcTs) < 0)
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

                if (TimeUtils.rtpDiff(ssrcState.maxTsEntry.dstTs, dstTs) <= 0)
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.warn("downlifting"
                            + ",stream_hash=" + stream.hashCode()
                            + ",srcSSRC=" + ssrcState.encodingSSRC
                            + ",dstSSRC=" + trackSSRC
                            + " ts=" + dstTs
                            + ",newTs=" + (ssrcState.maxTsEntry.dstTs - 1));
                    }

                    // It seems that rewriting using the wallclocks resulted in
                    // a frame timestamp that is bigger than what we expect.
                    // Downlifting.
                    dstTs = ssrcState.maxTsEntry.dstTs - 1;
                }

                ssrcState.tsHistory.put(
                    srcTs, new TimestampEntry(nowMs, srcTs, dstTs));

                if (logger.isDebugEnabled())
                {
                    logger.debug("rewrite_old_frame_success"
                        + ",stream_hash=" + stream.hashCode()
                        + ",srcSSRC=" + ssrcState.encodingSSRC
                        + ",dstSSRC=" + trackSSRC
                        + " srcTs=" + srcTs
                        + ",dstTs=" + dstTs);
                }

                return dstTs;
            }

            // XXX Why do we need this? : When there's a stream switch, we
            // request a keyframe for the stream we want to switch into (this is
            // done elsewhere). The {@link RTPEncodingRewriter} rewrites the
            // timestamps of the "mixed" streams so that they all have the same
            // timestamp offset. The problem still remains tho, frames can be
            // sampled at different times, so we might end up with a key frame
            // that is one sampling cycle behind what we were already streaming.
            // We hack around this by implementing "RTP timestamp uplifting".

            // XXX(gp): The uplifting should not take place if the
            // timestamps have advanced "a lot" (i.e. > 3000 or 3000/90 = 33ms).

            long maxTs = -1;

            if (maxInterval != null)
            {
                SSRCState s = ssrcStateBySSRC.get(maxInterval.ssrc);
                if (s != null)
                {
                    maxTs = s.maxTsEntry.dstTs;
                }
            }

            if (maxTs == -1) // Initialize maxTimestamp.
            {
                // Pretend we've sent the previous frame.
                maxTs = dstTs - 1;
            }

            long minTimestamp = maxTs + 1; /* minTimestamp is inclusive */

            if (TimeUtils.rtpDiff(dstTs, minTimestamp) < 0)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("uplifting"
                        + ",stream_hash=" + stream.hashCode()
                        + ",srcSSRC=" + ssrcState.encodingSSRC
                        + ",dstSSRC=" + trackSSRC
                        + " ts=" + dstTs
                        + ",newTs=" + minTimestamp);
                }

                // The calculated dstTs is smaller than the required minimum
                // timestamp. Replace dstTs with minTimestamp.
                dstTs = minTimestamp;
            }

            TimestampEntry maxTsEntry = new TimestampEntry(nowMs, srcTs, dstTs);
            ssrcState.tsHistory.put(srcTs, maxTsEntry);
            ssrcState.maxTsEntry = maxTsEntry;

            logger.debug("rewrite_new_frame_success"
                + ",stream_hash=" + stream.hashCode()
                + ",srcSSRC=" + ssrcState.encodingSSRC
                + ",dstSSRC=" + trackSSRC
                + " srcTs=" + srcTs
                + ",dstTs=" + dstTs);
            return dstTs;
        }

        /**
         * Rewrites the sequence number, timestamp and SSRC of the packet in the
         * pkt param.
         *
         * @param pkt
         * @param nowMs
         * @return
         */
        public boolean rewrite(RawPacket pkt, long nowMs)
        {
            long encodingSSRC = pkt.getSSRCAsLong();
            SSRCState ssrcState = ssrcStateBySSRC.get(encodingSSRC);
            if (ssrcState == null)
            {
                ssrcState = new SSRCState(encodingSSRC);
                ssrcStateBySSRC.put(encodingSSRC, ssrcState);
            }

            ExtendedSequenceNumberInterval maxInterval = curInterval;

            int srcSn = pkt.getSequenceNumber();
            int dstSn = rewriteSequenceNumber(srcSn, ssrcState);
            if (dstSn == -1)
            {
                return false;
            }

            long srcTs = pkt.getTimestamp();
            long dstTs = rewriteTimestamp(srcTs, ssrcState, maxInterval, nowMs);
            if (dstTs == -1)
            {
                return false;
            }

            pkt.setSequenceNumber(dstSn);
            pkt.setTimestamp(dstTs);
            pkt.setSSRC((int) trackSSRC);

            return true;
        }
    }

    /**
     * A structure that is holding the state of an SSRC that is being rewritten.
     */
    class SSRCState
    {
        /**
         * Ctor.
         *
         * @param encodingSSRC The SSRC that this instance pertains to.
         */
        public SSRCState(long encodingSSRC)
        {
            this.encodingSSRC = encodingSSRC;
        }

        /**
         * The SSRC that this instance pertains to.
         */
        private final long encodingSSRC;

        /**
         * The extended sequence number intervals that have been forwarded for
         * this SSRC.
         */
        private final NavigableMap<Integer, ExtendedSequenceNumberInterval>
            intervals = new TreeMap<>();

        /**
         * The maximum {@code TimestampEntry} that has been forwarded for this
         * SSRC.
         */
        public TimestampEntry maxTsEntry;

        /**
         * The MRU target timestamp history.
         */
        private final Map<Long, TimestampEntry> tsHistory
            = new LinkedHashMap<Long, TimestampEntry>()
        {

            @Override
            protected boolean removeEldestEntry(Map.Entry eldest)
            {
                return size() > TS_HISTORY_MAX_ENTRIES;
            }
        };
    }

    /**
     * Holds a timestamp (long) and records the time when it was first seen.
     */
    class TimestampEntry
    {
        /**
         * The time (in ms) that this instance was created.
         */
        private final long added;

        /**
         * The source RTP timestamp that this timestamp entry represents.
         */
        private final long srcTs;

        /**
         * The timestamp we rewrote the source into.
         */
        private final long dstTs;

        /**
         * Ctor.
         */
        public TimestampEntry(long now, long srcTs, long dstTs)
        {
            this.srcTs = srcTs;
            this.dstTs = dstTs;
            this.added = now;
        }

        /**
         * Gets a boolean indicating whether or not the timestamp is less
         * than {@code TS_EXPIRE_MS}
         *
         * @return true if the timestamp was added less than 10 seconds ago,
         * false otherwise.
         */
        public boolean isFresh(long now)
        {
            return (now - added) < TS_EXPIRE_MS;
        }
    }
}
