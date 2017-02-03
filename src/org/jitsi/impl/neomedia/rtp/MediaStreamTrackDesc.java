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
    private static final int SUSPENSION_THRESHOLD_MS = 300;

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
     * @param frameDesc
     * @param nowMs
     */
    void update(FrameDesc frameDesc, long nowMs)
    {
        if (nowMs - statistics.lastKeyframeMs < MIN_KEY_FRAME_WAIT_MS)
        {
            // The webrtc engine is sending keyframes from high to low and less
            // often than 300 millis. The first fresh keyframe that we observe
            // after we've waited for that long determines the streams that are
            // streaming (not suspended).
            //
            // On the other hand, if this packet is not a keyframe, the only
            // other action we can do is send an FIR and it's pointless to spam
            // the engine.
            return;
        }

        if (!frameDesc.isIndependent())
        {
            RTPEncodingDesc encoding = frameDesc.getRTPEncoding();

            // When we suspect that a stream is suspended, we send a FIR to
            // downscale receivers that are receiving the suspended stream.
            boolean maybeSuspended = false,

                // when a stream gets re-activated, it needs to start with an
                // independent frame so that receivers can switch to it.
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
                        maybeSuspended = true;
                        logger.info("maybe_suspended,stream="
                            + mediaStreamTrackReceiver.getStream().hashCode()
                            + " ssrc=" + enc.getPrimarySSRC());
                    }
                }
            }

            if (maybeSuspended || activated)
            {
                // FIXME only when suspended encodings are received.
                ((RTPTranslatorImpl) mediaStreamTrackReceiver.getStream()
                    .getRTPTranslator()).getRtcpFeedbackMessageSender()
                    .sendFIR((int) rtpEncodings[0].getPrimarySSRC());
            }
        }
        else
        {
            RTPEncodingDesc encoding = frameDesc.getRTPEncoding();

            FrameDesc lastReceivedFrame = encoding.getLastReceivedFrame();
            if (lastReceivedFrame != null && TimeUtils.rtpDiff(
                frameDesc.getTimestamp(), lastReceivedFrame.getTimestamp()) < 0)
            {
                // This is a late key frame header packet that we've missed.

                if (!encoding.isActive())
                {
                    // FIXME only when encodings is received.
                    ((RTPTranslatorImpl) mediaStreamTrackReceiver.getStream()
                        .getRTPTranslator()).getRtcpFeedbackMessageSender()
                        .sendFIR((int) rtpEncodings[0].getPrimarySSRC());
                }
            }
            else
            {
                // media engines may decide to suspend a stream for congestion
                // control. This is the case with the webrtc.org simulcast
                // implementation. This behavior induces a streaming dependency
                // between the encodings of a given track. The following piece
                // of code assumes that the subjective quality array is ordered
                // in a way to represent the streaming dependencies.

                statistics.lastKeyframeMs = nowMs;
                boolean isActive = false;

                for (int i = rtpEncodings.length - 1; i > -1; i--)
                {
                    if (!isActive && rtpEncodings[i].requires(encoding.getIndex()))
                    {
                        isActive = true;
                    }

                    rtpEncodings[i].setActive(isActive);
                }
            }
        }
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
