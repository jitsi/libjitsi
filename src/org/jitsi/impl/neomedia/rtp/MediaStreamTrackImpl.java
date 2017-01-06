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

/**
 * An implementation of a {@link MediaStreamTrack} that provides webrtc
 * simulcast stream suspension detection.
 *
 * @author George Politis
 */
public class MediaStreamTrackImpl
    implements MediaStreamTrack
{
    /**
     * The minimum time (in millis) that is required for the media engine to
     * generate a new key frame.
     */
    private static final int MIN_KEY_FRAME_WAIT_MS = 300;

    /**
     * The {@link RTPEncodingImpl}s that this {@link MediaStreamTrackImpl}
     * possesses, ordered by their subjective quality from low to high.
     */
    private final RTPEncodingImpl[] rtpEncodings;

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
     * @param rtpEncodings The {@link RTPEncoding}s that this instance
     * possesses.
     */
    public MediaStreamTrackImpl(
        MediaStreamTrackReceiver mediaStreamTrackReceiver,
        RTPEncodingImpl[] rtpEncodings)
    {
        this.rtpEncodings = rtpEncodings;
        this.mediaStreamTrackReceiver = mediaStreamTrackReceiver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTPEncodingImpl[] getRTPEncodings()
    {
        return rtpEncodings;
    }

    /**
     * Gets the {@link MediaStreamTrackReceiver} that receives this instance.
     *
     * @return The {@link MediaStreamTrackReceiver} that receives this instance.
     */
    MediaStreamTrackReceiver getMediaStreamTrackReceiver()
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
     * @param encoding the {@link RTPEncodingImpl} of the {@link RawPacket} that
     * is passed as an argument.
     */
    void update(RawPacket pkt, RTPEncodingImpl encoding)
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
            return;
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
    }
}
