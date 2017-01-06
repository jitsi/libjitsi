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
package org.jitsi.impl.neomedia;

import org.ice4j.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.util.*;

/**
 * Keeps track of how many channels receive it, its subjective quality index,
 * its last stable bitrate and other useful things for adaptivity/routing.
 *
 * @author George Politis
 */
public class RTPEncodingImpl
    implements RTPEncoding
{
    /**
     * The default window size in ms for the bitrate estimation.
     *
     * TODO maybe make this configurable.
     */
    private static final int AVERAGE_BITRATE_WINDOW_MS = 5000;

    /**
     * The primary SSRC for this layering/encoding.
     */
    private final long primarySSRC;

    /**
     * The RTX SSRC for this layering/encoding.
     */
    private final long rtxSSRC;

    /**
     * The index of this instance in the track encodings array.
     */
    private final int subjectiveQualityIdx;

    /**
     * The temporal ID of this instance.
     */
    private final int temporalId;

    /**
     * The {@link MediaStreamTrackImpl} that this {@link RTPEncodingImpl}
     * belongs to.
     */
    private final MediaStreamTrackImpl track;

    /**
     * The {@link RateStatistics} instance used to calculate the receiving
     * bitrate of this RTP encoding.
     */
    private final RateStatistics rateStatistics
        = new RateStatistics(AVERAGE_BITRATE_WINDOW_MS);

    /**
     * The {@link TreeMap} that holds the seen {@link SourceFrameDesc}.
     */
    private final TreeMap<Long, SourceFrameDesc> frames
        = new TreeMap<Long, SourceFrameDesc>()
    {
        /**
         * A helper {@link LinkedList} that is used to cleanup the map.
         */
        private LinkedList<Long> tsl = new LinkedList<>();

        /**
         * {@inheritDoc}
         */
        @Override
        public SourceFrameDesc put(Long key, SourceFrameDesc value)
        {
            SourceFrameDesc previous = super.put(key, value);
            if (tsl.add(key) && tsl.size() > 300)
            {
                Long first = tsl.removeFirst();
                this.remove(first);
            }

            return previous;
        }
    };

    /**
     * The {@link RTPEncodingImpl} on which this layer depends.
     */
    private final RTPEncodingImpl[] dependencyEncodings;

    /**
     * A cache of the dependencies of this {@link RTPEncoding}.
     */
    private final Map<Integer, Boolean> dependencyCache = new TreeMap<>();

    /**
     * A boolean flag that indicates whether or not this instance is streaming
     * or if it's suspended.
     */
    private boolean active = false;

    /**
     * The last stable bitrate (in bps) for this instance.
     */
    private long lastStableBitrateBps;

    /**
     * Ctor.
     *
     * @param track the {@link MediaStreamTrack} that this instance belongs to.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     */
    public RTPEncodingImpl(MediaStreamTrackImpl track, long primarySSRC)
    {
        this(track, primarySSRC, -1 /* rtxSSRC */);
    }

    /**
     * Ctor.
     *
     * @param track the {@link MediaStreamTrack} that this instance belongs to.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     * @param rtxSSRC The RTX SSRC for this layering/encoding.
     */
    public RTPEncodingImpl(
        MediaStreamTrackImpl track, long primarySSRC, long rtxSSRC)
    {
        this(track, 0, primarySSRC, rtxSSRC,
            0 /* temporalId */, null /* dependencies */);
    }

    /**
     * Ctor.
     *
     * @param track the {@link MediaStreamTrack} that this instance belongs to.
     * @param subjectiveQualityIdx the subjective quality index for this
     * layering/encoding.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     * @param rtxSSRC The RTX SSRC for this layering/encoding.
     * @param temporalId The inverse of the input framerate fraction to be
     * encoded.
     * @param dependencyEncodings  The {@link RTPEncodingImpl} on which this
     * layer depends.
     */
    public RTPEncodingImpl(
        MediaStreamTrackImpl track, int subjectiveQualityIdx,
        long primarySSRC, long rtxSSRC,
        int temporalId,
        RTPEncodingImpl[] dependencyEncodings)
    {
        this.primarySSRC = primarySSRC;
        this.rtxSSRC = rtxSSRC;
        this.track = track;
        this.subjectiveQualityIdx = subjectiveQualityIdx;
        this.temporalId = temporalId;
        this.dependencyEncodings = dependencyEncodings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getPrimarySSRC()
    {
        return primarySSRC;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRTXSSRC()
    {
        return rtxSSRC;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActive()
    {
        return active;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return "subjective_quality=" + subjectiveQualityIdx +
            ",primary_ssrc=" + getPrimarySSRC() +
            ",rtx_ssrc=" + getRTXSSRC() +
            ",temporal_id=" + temporalId +
            ",active=" + active +
            ",last_stable_bitrate_bps=" + lastStableBitrateBps;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaStreamTrackImpl getMediaStreamTrack()
    {
        return track;
    }

    /**
     * Gets the subjective quality index of this instance.
     *
     * @return the subjective quality index of this instance.
     */
    int getIndex()
    {
        return subjectiveQualityIdx;
    }

    /**
     * Returns a boolean that indicates whether or not this
     * {@link RTPEncodingImpl} depends on the subjective quality index that is
     * passed as an argument.
     *
     * @param idx
     * @return true if this {@link RTPEncodingImpl} depends on the subjective
     * quality index that is passed as an argument, false otherwise.
     */
    boolean requires(int idx)
    {
        if (idx < 0)
        {
            return false;
        }

        if (idx == this.subjectiveQualityIdx)
        {
            return true;
        }

        Boolean requires = dependencyCache.get(idx);

        if (requires == null)
        {
            requires = false;

            if (!ArrayUtils.isNullOrEmpty(dependencyEncodings))
            {
                for (RTPEncodingImpl enc : dependencyEncodings)
                {
                    if (enc.requires(idx))
                    {
                        requires = true;
                        break;
                    }
                }
            }

            dependencyCache.put(idx, requires);
        }

        return requires;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(byte[] buf, int off, int len)
    {
        long ssrc = RawPacket.getSSRCAsLong(buf, off, len);

        if (primarySSRC != ssrc && rtxSSRC != ssrc)
        {
            return false;
        }

        int tid = track.getMediaStreamTrackReceiver()
            .getStream().getTemporalID(buf, off, len);

        return (tid == -1 && ArrayUtils.isNullOrEmpty(dependencyEncodings))
                || tid == temporalId;
    }

    /**
     * Gets a boolean flag that indicates whether or not this instance is
     * streaming or if it's suspended.
     *
     * @param active true if this {@link RTPEncodingImpl} is active, otherwise
     * false.
     */
    void setActive(boolean active)
    {
        this.active = active;
    }

    /**
     *
     * @param pkt
     * @param nowMs
     *
     * @return the {@link SourceFrameDesc} that was updated, otherwise null.
     */
    SourceFrameDesc update(RawPacket pkt, long nowMs)
    {
        // Update rate stats.
        rateStatistics.update(pkt.getLength(), nowMs);

        long ts = pkt.getTimestamp();
        SourceFrameDesc frame = frames.get(ts);

        if (frame == null)
        {
            synchronized (frames)
            {
                frames.put(ts, frame = new SourceFrameDesc(this));
            }

            // We measure the stable bitrate on every new frame.
            lastStableBitrateBps = getBitrateBps(nowMs);
        }

        // Update the frame description.
        boolean frameChanged = frame.update(pkt);
        if (frameChanged)
        {
            // Frame boundaries heuristics.

            // Find the closest next frame.
            Map.Entry<Long, SourceFrameDesc> ceilingEntry
                = frames.ceilingEntry((ts + 1) & 0xFFFFFFFFL);

            if (ceilingEntry != null)
            {
                SourceFrameDesc ceilingFrame = ceilingEntry.getValue();
                if (ceilingFrame.getStart() != -1 || frame.getEnd() != -1)
                {
                    int diff = RTPUtils.sequenceNumberDiff(
                        ceilingFrame.getMinSeen(), frame.getMaxSeen());

                    // We can deal with distances that are less than 3.
                    if (Math.abs(diff) < 3)
                    {
                        frame.setEnd((frame.getMaxSeen() + 1) & 0xFFFF);
                        ceilingFrame.setStart(
                            (ceilingFrame.getMinSeen() - 1) & 0xFFFF);
                    }
                }
            }

            // Find the closest previous frame.
            Map.Entry<Long, SourceFrameDesc> floorEntry
                = frames.floorEntry((ts - 1) & 0xFFFFFFFFL);

            if (floorEntry != null)
            {
                SourceFrameDesc floorFrame = floorEntry.getValue();
                if (floorFrame.getEnd() != -1 || frame.getStart() != -1)
                {
                    int diff = RTPUtils.sequenceNumberDiff(
                        frame.getMinSeen(), floorFrame.getMaxSeen());

                    // We can deal with distances that are less than 3.
                    if (Math.abs(diff) < 3)
                    {
                        frame.setStart(
                            (frame.getMinSeen() - 1) & 0xFFFF);
                        floorFrame.setEnd(
                            (floorFrame.getMaxSeen() + 1) & 0xFFFF);
                    }
                }
            }
        }

        return frameChanged ? frame : null;
    }


    /**
     * Gets the cumulative bitrate (in bps) of this {@link RTPEncodingImpl} and
     * its dependencies.
     *
     * @param nowMs
     * @return the cumulative bitrate (in bps) of this {@link RTPEncodingImpl}
     * and its dependencies.
     */
    private long getBitrateBps(long nowMs)
    {
        long bitrateBps = rateStatistics.getRate(nowMs);

        if (!ArrayUtils.isNullOrEmpty(dependencyEncodings))
        {
            for (RTPEncodingImpl dependency : dependencyEncodings)
            {
                bitrateBps += dependency.getBitrateBps(nowMs);
            }
        }

        return bitrateBps;
    }

    /**
     * Finds the {@link SourceFrameDesc} that matches the RTP packet specified
     * in the buffer passed in as an argument.
     *
     * @param buf the <tt>byte</tt> array that contains the RTP packet data.
     * @param off the offset in <tt>buf</tt> at which the actual data starts.
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> which
     * constitute the actual data.
     *
     * @return the {@link SourceFrameDesc} that matches the RTP packet specified
     * in the buffer passed in as a parameter, or null if there is no matching
     * {@link SourceFrameDesc}.
     */
    public SourceFrameDesc resolveFrameDesc(byte[] buf, int off, int len)
    {
        long ts = RawPacket.getTimestamp(buf, off, len);
        synchronized (frames)
        {
            return frames.get(ts);
        }
    }
}
