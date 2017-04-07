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
import org.jitsi.util.*;
import org.jitsi.util.Logger;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Keeps track of how many channels receive it, its subjective quality index,
 * its last stable bitrate and other useful things for adaptivity/routing.
 *
 * TODO rename to Flow.
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
     * Gets the height of the bitstream that this instance represents.
     */
    private final int height;

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
     * A boolean flag that indicates whether or not this instance is streaming
     * or if it's suspended.
     */
    private boolean active = false;

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
     * @param track the {@link MediaStreamTrackDesc} that this instance belongs to.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     */
    public RTPEncodingDesc(MediaStreamTrackDesc track, long primarySSRC)
    {
        this(track, primarySSRC, -1 /* rtxSSRC */);
    }

    /**
     * Ctor.
     *
     * @param track the {@link MediaStreamTrackDesc} that this instance belongs to.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     * @param rtxSSRC The RTX SSRC for this layering/encoding.
     */
    public RTPEncodingDesc(
        MediaStreamTrackDesc track, long primarySSRC, long rtxSSRC)
    {
        this(track, 0, primarySSRC, rtxSSRC, NO_HEIGHT /* height */,
            -1 /* tid */, -1 /* sid */, null /* dependencies */);
    }

    /**
     * Ctor.
     *
     * @param track the {@link MediaStreamTrackDesc} that this instance belongs
     * to.
     * @param idx the subjective quality index for this
     * layering/encoding.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     * @param rtxSSRC The RTX SSRC for this layering/encoding.
     * @param tid temporal layer ID for this layering/encoding.
     * @param sid spatial layer ID for this layering/encoding.
     * @param height the height of this encoding
     * @param dependencyEncodings  The {@link RTPEncodingDesc} on which this
     * layer depends.
     */
    public RTPEncodingDesc(
        MediaStreamTrackDesc track, int idx,
        long primarySSRC, long rtxSSRC,
        int tid, int sid,
        int height,
        RTPEncodingDesc[] dependencyEncodings)
    {
        // XXX we should be able to snif the actual height from the RTP
        // packets.
        this.height = height;
        this.primarySSRC = primarySSRC;
        this.rtxSSRC = rtxSSRC;
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

    /**
     * Applies frame boundaries heuristics to frames a and b, assuming a
     * predates/is older than b.
     *
     * @param a the old {@link FrameDesc}.
     * @param b the new {@link FrameDesc}
     */
    private static void applyFrameBoundsHeuristics(FrameDesc a, FrameDesc b)
    {
        int aLastSeqNum = a.getEnd(), bFirstSeqNum = b.getStart();
        if (aLastSeqNum != -1 && bFirstSeqNum != -1)
        {
            // No need for heuristics.
            return;
        }

        long tsDiff = (b.getTimestamp() - a.getTimestamp()) & 0xFFFFFFFFL;
        if (tsDiff > (1L << 30) && tsDiff < (-(1L << 30) & 0xFFFFFFFFL))
        {
            // the distance (mod 2^32) between the two timestamps needs to be
            // less than half the timestamp space.
            return;
        }
        else if (tsDiff >= (-(1L << 30) & 0xFFFFFFFFL))
        {
            logger.warn("Frames that are out of order detected.");
        }
        else
        {
            int bMinSeqNum = b.getMinSeen(), aMaxSeqNum = a.getMaxSeen();
            int snDiff = (bMinSeqNum - aMaxSeqNum) & 0xFFFF;

            if (bFirstSeqNum != -1 || aLastSeqNum != -1)
            {
                if (snDiff == 2)
                {
                    if (aLastSeqNum == -1)
                    {
                        aLastSeqNum = (aMaxSeqNum + 1) & 0xFFFF;
                        if (logger.isDebugEnabled())
                        {
                            logger.debug("Guessed frame end=" + aLastSeqNum);
                        }
                        a.setEnd(aLastSeqNum);
                    }
                    else
                    {
                        bFirstSeqNum = (bMinSeqNum - 1) & 0xFFFF;
                        if (logger.isDebugEnabled())
                        {
                            logger.debug("Guessed frame start=" + bFirstSeqNum);
                        }
                        b.setStart(bFirstSeqNum);
                    }
                }
                else if (snDiff < 2 || snDiff > (-3 & 0xFFFF))
                {
                    logger.warn("Frame corruption or packets that are out of " +
                        "order detected.");
                }
            }
            else
            {
                if (snDiff == 3)
                {
                    bFirstSeqNum = (bMinSeqNum - 1) & 0xFFFF;
                    aLastSeqNum = (aMaxSeqNum + 1) & 0xFFFF;
                    if (logger.isDebugEnabled())
                    {
                        logger.debug(
                            "Guessed frame start=" + bFirstSeqNum
                                + ",end=" + aLastSeqNum);
                    }

                    a.setEnd(aLastSeqNum);
                    b.setStart(bFirstSeqNum);
                }
                else if (snDiff < 3 || snDiff > (-4 & 0xFFFF))
                {
                    logger.warn("Frame corruption or packets that are out of" +
                        " order detected.");
                }
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
     * Gets the RTX SSRC for this layering/encoding.
     *
     * @return the RTX SSRC for this layering/encoding.
     */
    public long getRTXSSRC()
    {
        return rtxSSRC;
    }

    /**
     * Gets a boolean value indicating whether or not this instance is
     * streaming.
     *
     * @return true if this instance is streaming, false otherwise.
     */
    public boolean isActive()
    {
        return active;
    }

    /**
     * Gets a boolean value indicating whether or not this instance is
     * streaming.
     *
     * @param performTimeoutCheck  when true, it requires fresh data and not
     * just the active property to be set.
     *
     * @return true if this instance is streaming, false otherwise.
     */
    public boolean isActive(boolean performTimeoutCheck)
    {
        if (active && performTimeoutCheck)
        {
            if (lastReceivedFrame == null)
            {
                return false;
            }
            else
            {
                long timeSinceLastReceivedFrameMs = System.currentTimeMillis()
                    - lastReceivedFrame.getReceivedMs();

                return timeSinceLastReceivedFrameMs
                    <= MediaStreamTrackDesc.SUSPENSION_THRESHOLD_MS;
            }
        }
        else
        {
            return active;
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
            ",rtx_ssrc=" + getRTXSSRC() +
            ",temporal_id=" + tid +
            ",spatial_id=" + sid +
            ",active=" + active +
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
     * Gets a boolean indicating whether or not the packet specified in the
     * arguments matches this encoding or not.
     *
     * @param buf the <tt>byte</tt> array that contains the RTP packet data.
     * @param off the offset in <tt>buf</tt> at which the actual data starts.
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> which
     * constitute the actual data.
     */
    public boolean matches(byte[] buf, int off, int len)
    {
        long ssrc = RawPacket.getSSRCAsLong(buf, off, len);

        if (primarySSRC != ssrc && rtxSSRC != ssrc)
        {
            return false;
        }

        if (tid == -1 && sid == -1)
        {
            return true;
        }

        int tid = this.tid != -1 ? track.getMediaStreamTrackReceiver()
            .getStream().getTemporalID(buf, off, len) : -1,
            sid = this.sid != -1 ? track.getMediaStreamTrackReceiver()
            .getStream().getSpatialID(buf, off, len) : -1;

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
        return primarySSRC == ssrc || rtxSSRC == ssrc;
    }

    /**
     * Gets a boolean flag that indicates whether or not this instance is
     * streaming or if it's suspended.
     *
     * @param active true if this {@link RTPEncodingDesc} is active, otherwise
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
     * @return the {@link FrameDesc} that was updated, otherwise null.
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
                base.streamFrames.put(ts, frame = new FrameDesc(this, ts, nowMs));
            }

            // We measure the stable bitrate on every new frame.
            lastStableBitrateBps = getBitrateBps(nowMs);

            if (lastReceivedFrame == null
                || TimeUtils.rtpDiff(ts, lastReceivedFrame.getTimestamp()) > 0)
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

        track.update(pkt, frame, isPacketOfNewFrame, nowMs);
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
    FrameDesc findFrameDesc(byte[] buf, int off, int len)
    {
        long ts = RawPacket.getTimestamp(buf, off, len);
        synchronized (base.streamFrames)
        {
            return base.streamFrames.get(ts);
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
     * Gets the height of the bitstream that this instance represents.
     *
     * @return the height of the bitstream that this instance represents.
     */
    public int getHeight()
    {
        return height;
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
