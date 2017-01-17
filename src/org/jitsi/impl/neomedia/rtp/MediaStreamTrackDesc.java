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

/**
 * Represents a collection of {@link RTPEncodingDesc}s that encode the same
 * media source. This specific implementation provides webrtc simulcast stream
 * suspension detection.
 *
 * @author George Politis
 */
public class MediaStreamTrackDesc
{
    /**
     * The minimum time (in millis) that is required for the media engine to
     * generate a new key frame.
     */
    private static final int MIN_KEY_FRAME_WAIT_MS = 300;

    /**
     * The {@link RTPEncodingDesc}s that this {@link MediaStreamTrackDesc}
     * possesses, ordered by their subjective quality from low to high.
     */
    private final RTPEncodingDesc[] rtpEncodings;

    /**
     * The {@link MediaStreamTrackReceiver} that receives this instance.
     */
    private final MediaStreamTrackReceiver mediaStreamTrackReceiver;

    /**
     * The time (in millis) that this instance last saw a keyframe.
     */
    private long lastKeyframeMs;

    /**
     * Ctor.
     *
     * @param mediaStreamTrackReceiver The {@link MediaStreamTrackReceiver} that
     * receives this instance.
     * @param rtpEncodings The {@link RTPEncodingDesc}s that this instance
     * possesses.
     */
    public MediaStreamTrackDesc(
        MediaStreamTrackReceiver mediaStreamTrackReceiver,
        RTPEncodingDesc[] rtpEncodings)
    {
        this.rtpEncodings = rtpEncodings;
        this.mediaStreamTrackReceiver = mediaStreamTrackReceiver;
    }

    /**
     * Returns an array of all the {@link RTPEncodingDesc}s for this instance,
     * in subjective quality ascending order.
     *
     * @return an array of all the {@link RTPEncodingDesc}s for this instance,
     * in subjective quality ascending order.
     */
    public RTPEncodingDesc[] getRTPEncodings()
    {
        return rtpEncodings;
    }

    /**
     * Gets the {@link MediaStreamTrackReceiver} that receives this instance.
     *
     * @return The {@link MediaStreamTrackReceiver} that receives this instance.
     */
    public MediaStreamTrackReceiver getMediaStreamTrackReceiver()
    {
        return mediaStreamTrackReceiver;
    }

    /**
     * Updates rate statistics for the encodings of the tracks that this
     * receiver is managing. Detects simulcast stream suspension/resuming.
     *
     * @param pkt the {@link RawPacket} that is causing the update of this
     * instance.
     *
     * @param encoding the {@link RTPEncodingDesc} of the {@link RawPacket} that
     * is passed as an argument.
     */
    RawPacket[] update(RawPacket pkt, RTPEncodingDesc encoding)
    {
        long nowMs = System.currentTimeMillis();

        // Update the encoding.
        FrameDesc frameDesc = encoding.update(pkt, nowMs);
        if (frameDesc == null /* no frame was changed */
            || !frameDesc.isIndependent() /* frame is dependent */

            // The webrtc engine is sending keyframes from high to low and less
            // often than 300 millis. The first keyframe that we observe after
            // we've waited for that long determines the streams that are
            // streaming (not suspended).
            || nowMs - lastKeyframeMs < MIN_KEY_FRAME_WAIT_MS)
        {
            return null;
        }

        // media engines may decide to suspend a stream for congestion control.
        // This is the case with the webrtc.org simulcast implementation. This
        // behavior induces a streaming dependency between the encodings of a
        // given track. The following piece of code assumes that the subjective
        // quality array is ordered in a way to represent the streaming
        // dependencies.

        lastKeyframeMs = nowMs;
        boolean isActive = false;

        for (int i = rtpEncodings.length - 1; i >= 0; i--)
        {
            if (!isActive && rtpEncodings[i].requires(encoding.getIndex()))
            {
                isActive = true;
            }

            rtpEncodings[i].setActive(isActive);
        }

        RawPacket[] extras = null;

        if (!frameDesc.isInOrder())
        {
            // Piggy back till max seen.
            RawPacketCache inCache = mediaStreamTrackReceiver.getStream()
                .getCachingTransformer().getIncomingRawPacketCache();

            int start = frameDesc.getStart();
            int len = (frameDesc.getMaxSeen() - start) & 0xFFFF;
            extras = new RawPacket[len];
            for (int i = 1; i <= len; i++)
            {
                extras[i] = inCache.get(encoding.getPrimarySSRC(),
                    (start + i) & 0xFFFF);
            }
        }
        return extras;
    }

    /**
     * Finds the {@link RTPEncodingDesc} that corresponds to the packet that is
     * specified in the buffer passed in as an argument.
     *
     * @param buf the byte array that holds the RTP packet.
     * @param off the offset in the byte array where the actual data starts
     * @param len the length of the actual data
     * @return the {@link RTPEncodingDesc} that corresponds to the packet that is
     * specified in the buffer passed in as an argument, or null.
     */
    public RTPEncodingDesc findRTPEncodingDesc(byte[] buf, int off, int len)
    {
        if (buf == null || buf.length < off + len)
        {
            return null;
        }

        for (RTPEncodingDesc encoding : rtpEncodings)
        {
            if (encoding.matches(buf, off, len))
            {
                return encoding;
            }
        }

        return null;
    }

    /**
     * Finds the {@link FrameDesc} that corresponds to the packet that is
     * specified in the buffer passed in as an argument.
     *
     * @param buf the byte array that holds the RTP packet.
     * @param off the offset in the byte array where the actual data starts
     * @param len the length of the actual data
     * @return the {@link FrameDesc} that corresponds to the packet that is
     * specified in the buffer passed in as an argument, or null.
     */
    public FrameDesc findFrameDesc(byte[] buf, int off, int len)
    {
        RTPEncodingDesc rtpEncoding = findRTPEncodingDesc(buf, off, len);
        if (rtpEncoding == null)
        {
            return null;
        }

        return rtpEncoding.findFrameDesc(buf, off, len);
    }
}
