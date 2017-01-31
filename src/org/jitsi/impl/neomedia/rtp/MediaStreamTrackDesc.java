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

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.util.*;

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
     * The {@link Logger} used by the {@link MediaStreamTrackDesc} class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaStreamTrackDesc.class);

    /**
     * The minimum time (in millis) that is required for the media engine to
     * generate a new key frame.
     */
    private static final int MIN_KEY_FRAME_WAIT_MS = 300;

    /**
     * The maximum time interval (in millis) an encoding can be considered
     * active without new frames.
     */
    private static final int SUSPENSION_THRESHOLD_MS = 100;

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
     * Stats for this {@link MediaStreamTrackDesc} instance.
     */
    private final Statistics statistics = new Statistics();

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
     * Notifies this instance that a {@link RTCPSRPacket} has been received.
     *
     * @param sr the received {@link RTCPSRPacket}.
     */
    public void srReceived(RTCPSRPacket sr)
    {
        long sendTimeMs = TimeUtils.getTime(TimeUtils.constuctNtp(
            sr.ntptimestampmsw, sr.ntptimestamplsw));

        long latencyMs = System.currentTimeMillis() - sendTimeMs;
        if (latencyMs < 0)
        {
            statistics.latencyMs = 0;
        }

        if (logger.isDebugEnabled())
        {
            logger.debug("latency=" + statistics.latencyMs);
        }
    }

    /**
     * Gets the stats for this {@link MediaStreamTrackDesc} instance.
     *
     * @return gets the stats for this {@link MediaStreamTrackDesc} instance.
     */
    public Statistics getStatistics()
    {
        return statistics;
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
     * @param pkt the received {@link RawPacket}.
     *
     * @param encoding the {@link RTPEncodingDesc} of the {@link RawPacket} that
     * is passed as an argument.
     *
     * @return the extra packets to piggy back to this packet.
     */
    RawPacket[] reverseTransform(RawPacket pkt, RTPEncodingDesc encoding)
    {
        long nowMs = System.currentTimeMillis();

        // Update the encoding.
        FrameDesc frameDesc = encoding.update(pkt, nowMs);
        if (frameDesc == null /* no frame was changed */)
        {
            return null;
        }

        // Stream suspension detection.
        boolean deactivated = false,
            activated = !encoding.isActive() && !frameDesc.isIndependent();

        for (int i = encoding.getIndex() + 1; i < rtpEncodings.length; i++)
        {
            RTPEncodingDesc enc = rtpEncodings[i];
            FrameDesc lastReceivedFrame = enc.getLastReceivedFrame();

            if (lastReceivedFrame != null)
            {
                long silentIntervalMs
                    = nowMs - lastReceivedFrame.getReceivedMs();

                if (enc.isActive()
                    && silentIntervalMs > SUSPENSION_THRESHOLD_MS)
                {
                    deactivated = true;
                    rtpEncodings[i].setActive(false);
                    logger.info("suspended,stream="
                        + mediaStreamTrackReceiver.getStream().hashCode()
                        + " ssrc=" + enc.getPrimarySSRC());
                }
            }
        }

        if (deactivated || activated)
        {
            // FIXME only when suspended encodings are received.
            ((RTPTranslatorImpl) mediaStreamTrackReceiver.getStream()
                .getRTPTranslator()).getRtcpFeedbackMessageSender()
                .sendFIR((int) rtpEncodings[0].getPrimarySSRC());
        }

        if (!frameDesc.isIndependent() /* frame is dependent */

            // The webrtc engine is sending keyframes from high to low and less
            // often than 300 millis. The first keyframe that we observe after
            // we've waited for that long determines the streams that are
            // streaming (not suspended).
            || nowMs - statistics.lastKeyframeMs < MIN_KEY_FRAME_WAIT_MS)
        {
            return null;
        }

        // media engines may decide to suspend a stream for congestion control.
        // This is the case with the webrtc.org simulcast implementation. This
        // behavior induces a streaming dependency between the encodings of a
        // given track. The following piece of code assumes that the subjective
        // quality array is ordered in a way to represent the streaming
        // dependencies.

        statistics.lastKeyframeMs = nowMs;
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

        if (frameDesc.getStart() != -1 && !frameDesc.isSofInOrder())
        {
            frameDesc.setSofInOrder(true);

            // Piggy back till max seen.
            RawPacketCache inCache = mediaStreamTrackReceiver.getStream()
                .getCachingTransformer().getIncomingRawPacketCache();

            int start = frameDesc.getStart();
            int len = RTPUtils
                    .sequenceNumberDiff(frameDesc.getMaxSeen(), start);
            extras = new RawPacket[len];
            for (int i = 0; i < extras.length; i++)
            {
                // skip the first packet of the key frame as this will be
                // included by the calling function.. here we only include the
                // extra packets. Furthermore, this only runs once, when we
                // receive the first packet of a keyframe.
                extras[i] = inCache.get(encoding.getPrimarySSRC(),
                    (start + i + 1) & 0xFFFF);
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

    /**
     *
     * @param ssrc
     * @return
     */
    public boolean matches(long ssrc)
    {
        return rtpEncodings[0].getPrimarySSRC() == ssrc;
    }

    /**
     * Stats for {@link MediaStreamTrackDesc} instances.
     */
    public static class Statistics
    {
        /**
         * The time (in millis) that this instance last saw a keyframe.
         */
        private long lastKeyframeMs = -1;

        /**
         * The time (in millis) from when the first bit leaves the transmitter
         * until the last is received.
         */
        long latencyMs = -1;

        /**
         * Gets the time (in millis) from when the first bit leaves the
         * transmitter until the last is received.
         *
         * @return the time (in millis) from when the first bit leaves the
         * transmitter until the last is received.
         */
        public long getLatencyMs()
        {
            return latencyMs;
        }
    }
}
