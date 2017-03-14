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

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Describes a frame of an RTP stream.
 *
 * TODO rename to LayerFrame.
 *
 * @author George Politis
 */
public class FrameDesc
{
    /**
     * The {@link Logger} used by the {@link FrameDesc} class to print
     * debug information.
     */
    private static final Logger logger
        = Logger.getLogger(FrameDesc.class);

    /**
     * The time in (millis) when the first packet of this frame was received.
     */
    private final long receivedMs;

    /**
     * The {@link RTPEncodingDesc} that this {@link FrameDesc} belongs to.
     */
    private final RTPEncodingDesc rtpEncoding;

    /**
     * The RTP timestamp of this frame.
     */
    private final long ts;

    /**
     * A boolean indicating whether or not this frame is independent or not
     * (e.g. VP8 key frame).
     */
    private Boolean independent;

    /**
     * The minimum sequence number that we've seen for this source frame.
     */
    private int minSeen = -1;

    /**
     * The maximum sequence number that we've seen for this source frame.
     */
    private int maxSeen = -1;

    /**
     * The start sequence number that we've seen for this source frame.
     */
    private int start = -1;

    /**
     * The end sequence number that we've seen for this source frame.
     */
    private int end = -1;

    /**
     * Ctor.
     *
     * @param rtpEncoding the {@link RTPEncodingDesc} that this instance belongs
     * to.
     * @param ts the RTP timestamp for this frame.
     * @param receivedMs the time (in millis) when the first packet of this
     * frame was received.
     */
    FrameDesc(RTPEncodingDesc rtpEncoding, long ts, long receivedMs)
    {
        this.rtpEncoding = rtpEncoding;
        this.ts = ts;
        this.receivedMs = receivedMs;
    }

    /**
     * Gets the {@link RTPEncodingDesc} that this {@link FrameDesc} belongs to.
     *
     * @return the {@link RTPEncodingDesc} that this {@link FrameDesc} belongs
     * to.
     */
    public RTPEncodingDesc getRTPEncoding()
    {
        return rtpEncoding;
    }

    /**
     * Gets the RTP timestamp for this frame.
     *
     * @return the RTP timestamp for this frame.
     */
    public long getTimestamp()
    {
        return ts;
    }

    /**
     * Gets the time (in millis) when the first packet of this frame was
     * received.
     *
     * @return the time (in millis) when the first packet of this frame was
     * received.
     */
    public long getReceivedMs()
    {
        return receivedMs;
    }

    /**
     * Gets the end sequence number for this source frame.
     *
     * @return the end sequence number for this source frame.
     */
    int getEnd()
    {
        return end;
    }

    /**
     * Sets the end sequence number of this source frame.
     *
     * @param end the end sequence number of this source frame.
     */
    void setEnd(int end)
    {
        this.end = end;
    }

    /**
     * Gets the start sequence number for this source frame.
     *
     * @return the start sequence number for this source frame.
     */
    public int getStart()
    {
        return start;
    }

    /**
     * Sets the start sequence number of this source frame.
     *
     * @param start the start sequence number of this source frame.
     */
    void setStart(int start)
    {
        this.start = start;
    }

    /**
     * Gets a boolean indicating whether or not this frame is independent.
     *
     * @return true if this frame is independent, false otherwise.
     */
    public boolean isIndependent()
    {
        return independent == null ? false : independent;
    }

    /**
     * Gets the minimum sequence number that we've seen for this source frame.
     *
     * @return the minimum sequence number that we've seen for this source
     * frame.
     */
    public int getMinSeen()
    {
        return minSeen;
    }

    /**
     * Gets the maximum sequence number that we've seen for this source frame.
     *
     * @return the maximum sequence number that we've seen for this source
     * frame.
     */
    public int getMaxSeen()
    {
        return maxSeen;
    }

    /**
     * Determines whether a packet belongs to this frame or not.
     * @param pkt the {@link RawPacket} to determine whether or not it belongs
     * to this frame.
     * @return true if the {@link RawPacket} passed as an argument belongs to
     * this frame, otherwise false.
     */
    public boolean matches(RawPacket pkt)
    {
        if (!RTPPacketPredicate.INSTANCE.test(pkt)
            || ts != pkt.getTimestamp()
            || minSeen == -1 /* <=> maxSeen == -1 */)
        {
            return false;
        }

        int seqNum = pkt.getSequenceNumber();
        return RTPUtils.sequenceNumberDiff(seqNum, minSeen) >= 0
            && RTPUtils.sequenceNumberDiff(seqNum, maxSeen) <= 0;
    }


    /**
     * Updates the state of this {@link FrameDesc}.
     *
     * @param pkt the {@link RawPacket} that will be used to update the state of
     * this {@link FrameDesc}.
     * @return true if the state has changed, false otherwise.
     */
    boolean update(RawPacket pkt)
    {
        boolean changed = false;

        int seqNum = pkt.getSequenceNumber();
        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset(), len = pkt.getLength();

        MediaStreamImpl stream = rtpEncoding.getMediaStreamTrack()
            .getMediaStreamTrackReceiver().getStream();

        boolean isSOF = stream.isStartOfFrame(buf, off, len);

        if (minSeen == -1 || RTPUtils.sequenceNumberDiff(minSeen, seqNum) > 0)
        {
            changed = true;
            minSeen = seqNum;
        }

        if (maxSeen == -1 || RTPUtils.sequenceNumberDiff(maxSeen, seqNum) < 0)
        {
            changed = true;
            maxSeen = seqNum;
        }

        if (end == -1 && stream.isEndOfFrame(buf, off, len))
        {
            changed = true;
            end = seqNum;
        }

        if (start == -1 && isSOF)
        {
            changed = true;
            start = seqNum;
            independent = stream.isKeyFrame(pkt);

            if (independent)
            {
                if (logger.isInfoEnabled())
                {
                    logger.info("keyframe,stream=" + stream.hashCode()
                        + " ssrc=" + rtpEncoding.getPrimarySSRC()
                        + "," + toString());
                }
            }
        }

        return changed;
    }

    @Override
    public String toString()
    {
        return "ts=" + ts +
            ",independent=" + independent +
            ",min_seen=" + minSeen +
            ",max_seen=" + maxSeen +
            ",start=" + start +
            ",end=" + end;
    }
}
