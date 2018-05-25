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
package org.jitsi.impl.neomedia.rtp;

import org.ice4j.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;
import org.jitsi.util.Logger;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Keeps track of how many channels receive it, its subjective quality index,
 * its last stable bitrate and other useful things for adaptivity/routing.
 *
 * @author George Politis
 */
public class RTPEncodingDesc
{
    /**
     * The {@link Logger} used by the {@link RTPEncodingDesc} class to print
     * debug information.
     */
    private static final Logger logger
        = Logger.getLogger(RTPEncodingDesc.class);

    /**
     * A value used to designate the absence of height information.
     */
    private final static int NO_HEIGHT = -1;

    /**
     * A value used to designate the absence of frame rate information.
     */
    private final static double NO_FRAME_RATE = -1;

    /**
     * The default window size in ms for the bitrate estimation.
     *
     * TODO maybe make this configurable.
     */
    private static final int AVERAGE_BITRATE_WINDOW_MS = 5000;

    /**
     * The number of incoming frames to keep track of.
     */
    private static final int FRAMES_HISTORY_SZ = 60;

     /**
      * The maximum time interval (in millis) an encoding can be considered
      * active without new frames. This value corresponds to 4fps + 50 millis
      * to compensate for network noise. If the network is clogged and we don't
      * get a new frame within 300 millis, and if the encoding is being
      * received, then we will ask for a new key frame (this is done in the
      * JVB in SimulcastController).
      */
    private static final int SUSPENSION_THRESHOLD_MS = 300;

    /**
     * The primary SSRC for this layering/encoding.
     */
    private final long primarySSRC;

    /**
     * The ssrcs associated with this encoding (for example, RTX or FLEXFEC)
     * Maps ssrc -> type {@link Constants} (rtx, etc.)
     */
    private final Map<Long, String> secondarySsrcs = new HashMap<>();

    /**
     * The index of this instance in the track encodings array.
     */
    private final int idx;

    /**
     * The temporal layer ID of this instance.
     */
    private final int tid;

    /**
     * The spatial layer ID of this instance.
     */
    private final int sid;

    /**
     * The max height of the bitstream that this instance represents. The actual
     * height may be less due to bad network or system load.
     */
    private final int height;

    /**
     * The max frame rate (in fps) of the bitstream that this instance
     * represents. The actual frame rate may be less due to bad network or
     * system load.
     */
    private final double frameRate;

    /**
     * The root {@link RTPEncodingDesc} of the dependencies DAG. Useful for
     * simulcast handling.
     */
    private final RTPEncodingDesc base;

    /**
     * The {@link MediaStreamTrackDesc} that this {@link RTPEncodingDesc}
     * belongs to.
     */
    private final MediaStreamTrackDesc track;

    /**
     * The {@link RateStatistics} instance used to calculate the receiving
     * bitrate of this RTP encoding.
     */
    private final RateStatistics rateStatistics
        = new RateStatistics(AVERAGE_BITRATE_WINDOW_MS);

    /**
     * The {@link TreeMap} that holds the seen {@link FrameDesc}, keyed
     * by their RTP timestamps.
     */
    private final TreeMap<Long, FrameDesc> streamFrames
        = new TreeMap<Long, FrameDesc>()
    {
        /**
         * A helper {@link LinkedList} that is used to cleanup the map.
         */
        private LinkedList<Long> tsl = new LinkedList<>();

        /**
         * {@inheritDoc}
         *
         * It also removes the eldest entry each time a new one is added and the
         * total number of entries exceeds FRAMES_HISTORY_SZ.
         */
        @Override
        public FrameDesc put(Long key, FrameDesc value)
        {
            FrameDesc previous = super.put(key, value);
            if (tsl.add(key) && tsl.size() > FRAMES_HISTORY_SZ)
            {
                Long first = tsl.removeFirst();
                this.remove(first);
            }

            return previous;
        }
    };

    /**
     * The {@link RTPEncodingDesc} on which this layer depends.
     */
    private final RTPEncodingDesc[] dependencyEncodings;

    /**
     * The last "stable" bitrate (in bps) for this instance.
     */
    private long lastStableBitrateBps;

    /**
     * The last frame from this encoding that has been received.
     */
    private FrameDesc lastReceivedFrame;

    /**
     * The number of receivers for this encoding.
     */
    private AtomicInteger numOfReceivers = new AtomicInteger();

    /**
     * Ctor.
     *
     * @param track the {@link MediaStreamTrackDesc} that this instance
     * belongs to.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     */
    public RTPEncodingDesc(
        MediaStreamTrackDesc track, long primarySSRC)
    {
        this(track, 0, primarySSRC, -1 /* tid */, -1 /* sid */,
            NO_HEIGHT /* height */, NO_FRAME_RATE /* frame rate */,
            null /* dependencies */);
    }

    /**
     * Ctor.
     *
     * @param track the {@link MediaStreamTrackDesc} that this instance belongs
     * to.
     * @param idx the subjective quality index for this
     * layering/encoding.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     * @param tid temporal layer ID for this layering/encoding.
     * @param sid spatial layer ID for this layering/encoding.
     * @param height the max height of this encoding
     * @param frameRate the max frame rate (in fps) of this encoding
     * @param dependencyEncodings  The {@link RTPEncodingDesc} on which this
     * layer depends.
     */
    public RTPEncodingDesc(
        MediaStreamTrackDesc track, int idx,
        long primarySSRC,
        int tid, int sid,
        int height,
        double frameRate,
        RTPEncodingDesc[] dependencyEncodings)
    {
        // XXX we should be able to snif the actual height from the RTP
        // packets.
        this.height = height;
        this.frameRate = frameRate;
        this.primarySSRC = primarySSRC;
        this.track = track;
        this.idx = idx;
        this.tid = tid;
        this.sid = sid;
        this.dependencyEncodings = dependencyEncodings;
        if (ArrayUtils.isNullOrEmpty(dependencyEncodings))
        {
            this.base = this;
        }
        else
        {
            this.base = dependencyEncodings[0].getBaseLayer();
        }
    }

    public void addSecondarySsrc(long ssrc, String type)
    {
        secondarySsrcs.put(ssrc, type);
    }

    /**
     * Applies frame boundaries heuristics to frames olderFrame and
     * newerFrame, assuming olderFrame predates/is older than newerFrame.
     * Depending on the relationship of olderFrame and newerFrame, and what
     * we know about olderFrame and newerFrame, we may be able to deduce
     * the last expected sequence number for olderFrame and/or the first
     * expected sequence number of newerFrame.
     *
     * @param olderFrame the {@link FrameDesc} that comes before newerFrame.
     * @param newerFrame the {@link FrameDesc} that comes after olderFrame.
     */
    private static void applyFrameBoundsHeuristics(
            FrameDesc olderFrame,
            FrameDesc newerFrame)
    {
        if (olderFrame.lastSequenceNumberKnown()
                && newerFrame.lastSequenceNumberKnown())
        {
            // We already know the last sequence number of olderFrame and the
            // first sequence number of newerFrame, no need for further
            // heuristics.
            return;
        }

        if (!TimestampUtils.isNewerTimestamp(
                newerFrame.getTimestamp(), olderFrame.getTimestamp()))
        {
            // newerFrame isn't newer than olderFrame, bail
            return;
        }
        int lowestSeenSeqNumOfNewerFrame = newerFrame.getMinSeen();
        int highestSeenSeqNumOfOlderFrame = olderFrame.getMaxSeen();
        int seqNumDiff = RTPUtils.getSequenceNumberDelta(
                lowestSeenSeqNumOfNewerFrame, highestSeenSeqNumOfOlderFrame);

        boolean guessed = false;

        // For a stream that supports frame marking, we will conclusively know
        // the start and end packets of a frame via the marking.  If those
        // packets have been received, the start/end of the frame will already
        // be conclusively known at this point.  Because of this, we can still
        // make a guess even when the sequence number gap is bigger (see
        // further comments for each scenario below)

        boolean framesSupportFrameBoundaries =
            olderFrame.supportsFrameBoundaries()
            && newerFrame.supportsFrameBoundaries();

        if (framesSupportFrameBoundaries)
        {
            if (olderFrame.lastSequenceNumberKnown()
                    || newerFrame.firstSequenceNumberKnown())
            {

                // XXX(bgrozev): for VPX codecs with PictureID we could find
                // the start/end even with diff>2 (if PictureIDDiff == 1)

                // XXX(gp): we don't have the picture ID in FrameDesc and I
                // feel it doesn't belong there. We may need to subclass it
                // into VPXFrameDesc and H264FrameDesc and move the
                // heuristics logic in there.
                if (seqNumDiff == 2)
                {
                    if (!olderFrame.lastSequenceNumberKnown())
                    {
                        // If we haven't yet seen the last sequence number of
                        // this frame, we know it must be the packet in the
                        // 'gap' here (since, had the biggest one we've seen
                        // for that frame so far been the last one, it would've
                        // been marked)

                        olderFrame.setEnd(RTPUtils.as16Bits(
                                    highestSeenSeqNumOfOlderFrame + 1));
                    }
                    else
                    {
                        newerFrame.setStart(RTPUtils.as16Bits(
                                    lowestSeenSeqNumOfNewerFrame - 1));
                    }
                    guessed = true;
                }
            }
            else
            {
                // Neither the last packet of the older frame nor the first
                // packet of the newer frame has been seen, so we know the
                // start/end packets must be held within this gap

                if (seqNumDiff == 3)
                {
                    olderFrame.setEnd(RTPUtils.as16Bits(
                                highestSeenSeqNumOfOlderFrame + 1));

                    newerFrame.setStart(RTPUtils.as16Bits(
                                lowestSeenSeqNumOfNewerFrame - 1));
                    guessed = true;
                }
            }
        }
        else
        {
            if (olderFrame.lastSequenceNumberKnown()
                    || newerFrame.firstSequenceNumberKnown())
            {
                if (seqNumDiff == 1)
                {
                    if (!olderFrame.lastSequenceNumberKnown())
                    {
                        olderFrame.setEnd(RTPUtils.as16Bits(
                                    highestSeenSeqNumOfOlderFrame));
                    }
                    else
                    {
                        newerFrame.setStart(RTPUtils.as16Bits(
                                    lowestSeenSeqNumOfNewerFrame));
                    }

                    guessed = true;
                }
            }
            else
            {
                // XXX(bgrozev): Can't do much here. If diff==2 and we don't
                // know either the first sequence number of the newer frame or
                // the last sequence number of the older frame, then there is 1
                // packet between olderFrameLastSeen and newerFrameFirstSeen.
                // And we don't know whether this packet belongs to olderFrame
                // or to newerFrame, or is olderFrame separate frame of its own
                // (since in this if branch there is no support for frame
                // boundaries, which means that e.g.  olderFrameLastSeen could
                // be the end of olderFrame even if lastSeqNumOfOlderFrame ==
                // -1).
            }
        }

        if (guessed)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(
                    "Guessed frame boundaries ts=" + olderFrame.getTimestamp()
                        + ",start=" + olderFrame.getStart()
                        + ",end=" + olderFrame.getEnd()
                        + ",ts=" + newerFrame.getTimestamp()
                        + ",start=" + newerFrame.getStart()
                        + ",end=" + newerFrame.getEnd());
            }
        }
    }

    /**
     * Gets the last stable bitrate (in bps) for this instance.
     *
     * @return The last stable bitrate (in bps) for this instance.
     */
    public long getLastStableBitrateBps()
    {
        return lastStableBitrateBps;
    }

    /**
     * Gets the primary SSRC for this layering/encoding.
     *
     * @return the primary SSRC for this layering/encoding.
     */
    public long getPrimarySSRC()
    {
        return primarySSRC;
    }

    /**
     * Get the secondary ssrc for this stream that corresponds to the given
     * type
     * @param type the type of the secondary ssrc (e.g. RTX)
     * @return the ssrc for the stream that corresponds to the given type,
     * if it exists; otherwise -1
     */
    public long getSecondarySsrc(String type)
    {
        for (Map.Entry<Long, String> e : secondarySsrcs.entrySet())
        {
            if (e.getValue().equals(type))
            {
                return e.getKey();
            }
        }
        return -1;
    }

    /**
     * Gets a boolean value indicating whether or not this instance is
     * streaming.
     *
     * @return true if this instance is streaming, false otherwise.
     */
    public boolean isActive(long nowMs)
    {
        if (lastReceivedFrame == null)
        {
            return false;
        }
        else
        {
            RTPEncodingDesc[] encodings = track.getRTPEncodings();
            boolean nextIsActive = encodings != null
                && encodings.length > idx + 1
                && encodings[idx + 1].isActive(nowMs);

            if (nextIsActive)
            {
                return true;
            }

            long timeSinceLastReceivedFrameMs
                = nowMs - lastReceivedFrame.getReceivedMs();

            return timeSinceLastReceivedFrameMs <= SUSPENSION_THRESHOLD_MS;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return "subjective_quality=" + idx +
            ",primary_ssrc=" + getPrimarySSRC() +
            ",secondary_ssrcs=" + secondarySsrcs +
            ",temporal_id=" + tid +
            ",spatial_id=" + sid +
            ",last_stable_bitrate_bps=" + lastStableBitrateBps;
    }

    /**
     * Gets the {@link MediaStreamTrackDesc} that this instance belongs to.
     *
     * @return the {@link MediaStreamTrackDesc} that this instance belongs to.
     */
    public MediaStreamTrackDesc getMediaStreamTrack()
    {
        return track;
    }

    /**
     * Gets the subjective quality index of this instance.
     *
     * @return the subjective quality index of this instance.
     */
    public int getIndex()
    {
        return idx;
    }

    /**
     * Returns a boolean that indicates whether or not this
     * {@link RTPEncodingDesc} depends on the subjective quality index that is
     * passed as an argument.
     *
     * @param idx the index of this instance in the track encodings array.
     * @return true if this {@link RTPEncodingDesc} depends on the subjective
     * quality index that is passed as an argument, false otherwise.
     */
    public boolean requires(int idx)
    {
        if (idx < 0)
        {
            return false;
        }

        if (idx == this.idx)
        {
            return true;
        }


        boolean requires = false;

        if (!ArrayUtils.isNullOrEmpty(dependencyEncodings))
        {
            for (RTPEncodingDesc enc : dependencyEncodings)
            {
                if (enc.requires(idx))
                {
                    requires = true;
                    break;
                }
            }
        }

        return requires;
    }

    /**
     * Gets a boolean indicating whether or not the specified packet matches
     * this encoding or not. Assumes that the packet is valid.
     *
     * @param pkt the RTP packet.
     */
    boolean matches(RawPacket pkt)
    {
        long ssrc = pkt.getSSRCAsLong();

        if (!matches(ssrc))
        {
            return false;
        }

        if (tid == -1 && sid == -1)
        {
            return true;
        }

        int tid
            = this.tid != -1
                    ? track.getMediaStreamTrackReceiver()
                            .getStream().getTemporalID(pkt)
                    : -1;
        int sid
            = this.sid != -1
                    ? track.getMediaStreamTrackReceiver()
                            .getStream().getSpatialID(pkt)
                    : -1;

        return (tid == -1 && sid == -1 && idx == 0)
            || (tid == this.tid && sid == this.sid);
    }

    /**
     * Gets a boolean indicating whether or not the SSRC specified in the
     * arguments matches this encoding or not.
     *
     * @param ssrc the SSRC to match.
     */
    public boolean matches(long ssrc)
    {
        if (primarySSRC == ssrc)
        {
            return true;
        }
        return secondarySsrcs.containsKey(ssrc);
    }

    /**
     *
     * @param pkt
     * @param nowMs
     */
    void update(RawPacket pkt, long nowMs)
    {
        // Update rate stats (this should run after padding termination).
        rateStatistics.update(pkt.getLength(), nowMs);

        long ts = pkt.getTimestamp();
        FrameDesc frame = base.streamFrames.get(ts);

        boolean isPacketOfNewFrame;
        if (frame == null)
        {
            isPacketOfNewFrame = true;
            synchronized (base.streamFrames)
            {
                base.streamFrames.put(
                    ts, frame = new FrameDesc(this, pkt, nowMs));
            }

            // We measure the stable bitrate on every new frame.
            lastStableBitrateBps = getBitrateBps(nowMs);

            if (lastReceivedFrame == null
                || RTPUtils.isNewerTimestampThan(
                        ts, lastReceivedFrame.getTimestamp()))
            {
                lastReceivedFrame = frame;
            }
        }
        else
        {
            isPacketOfNewFrame = false;
        }

        // Update the frame description.
        boolean frameChanged = frame.update(pkt);
        if (frameChanged)
        {
            // Frame boundaries heuristics.

            // Find the closest next frame.
            Map.Entry<Long, FrameDesc> ceilingEntry
                = base.streamFrames.ceilingEntry((ts + 1) & 0xFFFFFFFFL);

            if (ceilingEntry != null)
            {
                applyFrameBoundsHeuristics(frame, ceilingEntry.getValue());
            }

            // Find the closest previous frame.
            Map.Entry<Long, FrameDesc> floorEntry
                = base.streamFrames.floorEntry((ts - 1) & 0xFFFFFFFFL);

            if (floorEntry != null)
            {
                applyFrameBoundsHeuristics(floorEntry.getValue(), frame);
            }
        }
    }


    /**
     * Gets the cumulative bitrate (in bps) of this {@link RTPEncodingDesc} and
     * its dependencies.
     *
     * @param nowMs
     * @return the cumulative bitrate (in bps) of this {@link RTPEncodingDesc}
     * and its dependencies.
     */
    private long getBitrateBps(long nowMs)
    {
        RTPEncodingDesc[] encodings = track.getRTPEncodings();
        if (ArrayUtils.isNullOrEmpty(encodings))
        {
            return 0;
        }

        long[] rates = new long[encodings.length];
        getBitrateBps(nowMs, rates);

        long bitrate = 0;
        for (int i = 0; i < rates.length; i++)
        {
            bitrate += rates[i];
        }

        return bitrate;
    }

    /**
     * Recursively adds the bitrate (in bps) of this {@link RTPEncodingDesc} and
     * its dependencies in the array passed in as an argument.
     *
     * @param nowMs
     */
    private void getBitrateBps(long nowMs, long[] rates)
    {
        if (rates[idx] == 0)
        {
            rates[idx] = rateStatistics.getRate(nowMs);
        }

        if (!ArrayUtils.isNullOrEmpty(dependencyEncodings))
        {
            for (RTPEncodingDesc dependency : dependencyEncodings)
            {
                dependency.getBitrateBps(nowMs, rates);
            }
        }
    }

    /**
     * Finds the {@link FrameDesc} that matches the RTP packet specified
     * in the buffer passed in as an argument.
     *
     * @param buf the <tt>byte</tt> array that contains the RTP packet data.
     * @param off the offset in <tt>buf</tt> at which the actual data starts.
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> which
     * constitute the actual data.
     *
     * @return the {@link FrameDesc} that matches the RTP packet specified
     * in the buffer passed in as a parameter, or null if there is no matching
     * {@link FrameDesc}.
     */
//    FrameDesc findFrameDesc(byte[] buf, int off, int len)
//    {
//        long ts = RawPacket.getTimestamp(buf, off, len);
//        synchronized (base.streamFrames)
//        {
//            return base.streamFrames.get(ts);
//        }
//    }

    /**
     * Finds the {@link FrameDesc} that matches the RTP packet specified
     * in the buffer passed in as an argument.
     *
     * @param timestamp the timestamp of the desired {@link FrameDesc}
     *
     * @return the {@link FrameDesc} that matches the RTP timestamp given,
     * or null if there is no matching frame {@link FrameDesc}.
     */
    FrameDesc findFrameDesc(long timestamp)
    {
        synchronized (base.streamFrames)
        {
            return base.streamFrames.get(timestamp);
        }
    }

    /**
     * Gets the last frame from this encoding that has been received.
     * @return last frame from this encoding that has been received, otherwise
     * null.
     */
    FrameDesc getLastReceivedFrame()
    {
        return lastReceivedFrame;
    }

    /**
     * Gets the root {@link RTPEncodingDesc} of the dependencies DAG. Useful for
     * simulcast handling.
     *
     * @return the root {@link RTPEncodingDesc} of the dependencies DAG. Useful for
     * simulcast handling.
     */
    public RTPEncodingDesc getBaseLayer()
    {
        return base;
    }

    /**
     * Gets the {@link RTPEncodingDesc} on which this layer depends.
     *
     * @return the {@link RTPEncodingDesc} on which this layer depends.
     */
    public RTPEncodingDesc[] getDependencyEncodings()
    {
        return dependencyEncodings;
    }

    /**
     * Gets the max height of the bitstream that this instance represents.
     *
     * @return the max height of the bitstream that this instance represents.
     */
    public int getHeight()
    {
        return height;
    }

    /**
     * Gets the max frame rate (in fps) of the bitstream that this instance
     * represents.
     *
     * @return the max frame rate (in fps) of the bitstream that this instance
     * represents.
     */
    public double getFrameRate()
    {
        return frameRate;
    }

    /**
     * Gets the number of receivers for this encoding.
     *
     * @return the number of receivers for this encoding.
     */
    public boolean isReceived()
    {
        return numOfReceivers.get() > 0;
    }

    /**
     * Atomically increments the number of receivers of this encoding.
     */
    public void incrReceivers()
    {
        numOfReceivers.incrementAndGet();
        if (logger.isTraceEnabled())
        {
            logger.trace("increment_receivers,hash="
                + track.getMediaStreamTrackReceiver().getStream().hashCode()
                + ",idx=" + idx
                + ",receivers=" + numOfReceivers);
        }
    }

    /**
     * Atomically decrements the number of receivers of this encoding.
     */
    public void decrReceivers()
    {
        numOfReceivers.decrementAndGet();
        if (logger.isTraceEnabled())
        {
            logger.trace("decrement_receivers,hash="
                + track.getMediaStreamTrackReceiver().getStream().hashCode()
                + ",idx=" + idx
                + ",receivers=" + numOfReceivers);
        }
    }
}
