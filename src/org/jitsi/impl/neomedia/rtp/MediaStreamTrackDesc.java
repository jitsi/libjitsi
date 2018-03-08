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

import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
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
     * The system property name that holds the maximum frequency (in millis) at
     * which the media engine generates key frame.
     *
     * We take that into account to identify batches of key frames which helps
     * us identify which simulcast flows are active/inactive.
     */
    public static final String MIN_KEY_FRAME_WAIT_MS_PNAME
        = "org.jitsi.impl.neomedia.rtp.MediaStreamTrackDesc" +
            ".MIN_KEY_FRAME_WAIT_MS";

    /**
     * The system property name that holds the maximum time interval (in millis)
     * an encoding can be considered active without new frames.
     */
    public static final String SUSPENSION_THRESHOLD_MS_PNAME
        = "org.jitsi.impl.neomedia.rtp.MediaStreamTrackDesc" +
            ".SUSPENSION_THRESHOLD_MS";

    /**
     * The default maximum frequency (in millis) at which the media engine
     * generates key frame.
     */
    private static final int MIN_KEY_FRAME_WAIT_MS_DEFAULT = 300;

    /**
     * The default maximum time interval (in millis) an encoding can be
     * considered active without new frames.
     */
    private static final int SUSPENSION_THRESHOLD_MS_DEFAULT = 600;

    /**
     * The {@link Logger} used by the {@link MediaStreamTrackDesc} class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaStreamTrackDesc.class);

    /**
     * The ConfigurationService to get config values from.
     */
    private static final ConfigurationService
        cfg = LibJitsi.getConfigurationService();

    /**
     * The minimum time (in millis) that is required for the media engine to
     * generate a new key frame (or, equivalently, the maximum frequency at
     * which the media engine generates key frame).
     */
    private static final int MIN_KEY_FRAME_WAIT_MS = cfg != null ? cfg.getInt(
        MIN_KEY_FRAME_WAIT_MS_PNAME, MIN_KEY_FRAME_WAIT_MS_DEFAULT)
        : MIN_KEY_FRAME_WAIT_MS_DEFAULT;

    /**
     * The maximum time interval (in millis) an encoding can be considered
     * active without new frames.
     */
    public static final int SUSPENSION_THRESHOLD_MS = cfg != null ? cfg.getInt(
        SUSPENSION_THRESHOLD_MS_PNAME, SUSPENSION_THRESHOLD_MS_DEFAULT)
        : SUSPENSION_THRESHOLD_MS_DEFAULT;

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
     * We have a bunch of code that is dealing with simulcast. Specifically,
     * we want to detect stream suspension so that we can switch to a lower
     * stream. If we're not using simulcast, we want to disable this code.
     */
    private final boolean simulcast;

    /**
     * Stats for this {@link MediaStreamTrackDesc} instance.
     */
    private final Statistics statistics = new Statistics();

    /**
     * A string which identifies the owner of this track (e.g. the endpoint
     * which is the sender of the track).
     */
    private final String owner;

    /**
     * Ctor.
     *
     * @param mediaStreamTrackReceiver The {@link MediaStreamTrackReceiver} that
     * receives this instance.
     * @param rtpEncodings The {@link RTPEncodingDesc}s that this instance
     * possesses.
     * @param simulcast true to enable simulcast logic, false otherwise
     */
    public MediaStreamTrackDesc(
        MediaStreamTrackReceiver mediaStreamTrackReceiver,
        RTPEncodingDesc[] rtpEncodings,
        boolean simulcast)
    {
        this(mediaStreamTrackReceiver, rtpEncodings, simulcast, null);
    }

    /**
     * Ctor.
     *
     * @param mediaStreamTrackReceiver The {@link MediaStreamTrackReceiver} that
     * receives this instance.
     * @param rtpEncodings The {@link RTPEncodingDesc}s that this instance
     * possesses.
     * @param simulcast true to enable simulcast logic, false otherwise
     */
    public MediaStreamTrackDesc(
        MediaStreamTrackReceiver mediaStreamTrackReceiver,
        RTPEncodingDesc[] rtpEncodings,
        boolean simulcast,
        String owner)
    {
        this.rtpEncodings = rtpEncodings;
        this.mediaStreamTrackReceiver = mediaStreamTrackReceiver;
        this.simulcast = simulcast;
        this.owner = owner;
    }

    /**
     * @return the identifier of the owner of this track.
     */
    public String getOwner()
    {
        return owner;
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
     * Gets the last "stable" bitrate (in bps) of the encoding of the specified
     * index. The "stable" bitrate is measured on every new frame and with a
     * 5000ms window.
     *
     * @param performTimeoutCheck when true, it requires the matching encoding
     * to have fresh data and not just its active property to be set to true.
     *
     * @return the last "stable" bitrate (bps) of the encoding at the specified
     * index.
     */
    public long getBps(int idx, boolean performTimeoutCheck)
    {
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            return 0;
        }

        if (idx > -1)
        {
            for (int i = idx; i > -1; i--)
            {
                if (!rtpEncodings[i].isActive(performTimeoutCheck))
                {
                    continue;
                }

                long bps = rtpEncodings[i].getLastStableBitrateBps();
                if (bps > 0)
                {
                    return bps;
                }
            }
        }

        return 0;
    }

    /**
     * Updates rate statistics for the encodings of the tracks that this
     * receiver is managing. Detects simulcast stream suspension/resuming.
     *
     * @param pkt the received RTP packet that caused the update method call.
     * @param frameDesc the {@link FrameDesc} to which the RTP packet belongs.
     * @param isPacketOfNewFrame true if the packet is from a frame that we
     * have not seen before, false otherwise.
     * @param nowMs now in millis.
     */
    void update(
        RawPacket pkt, FrameDesc frameDesc,
        boolean isPacketOfNewFrame, long nowMs)
    {
        if (!simulcast)
        {
            frameDesc.getRTPEncoding().setActive(true);
            return;
        }

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
            if (!isPacketOfNewFrame)
            {
                // We only check for stream suspension if this is the first
                // packet of a new frame
                return;
            }

            RTPEncodingDesc encoding = frameDesc.getRTPEncoding();

            // When we suspect that a stream is suspended, we send an FIR to the
            // sender so we can send a different stream to its receivers.
            boolean maybeSuspended = false,

            // when a stream gets re-activated, it needs to start with an
            // independent frame so that receivers can switch to it.
            activated = !encoding.isActive() && pkt.getPayloadLength(true) > 0;

            for (int i = encoding.getIndex() + 1; i < rtpEncodings.length; i++)
            {
                RTPEncodingDesc enc = rtpEncodings[i];
                if (!ArrayUtils.isNullOrEmpty(enc.getDependencyEncodings()))
                {
                    continue;
                }

                FrameDesc lastReceivedFrame = enc.getLastReceivedFrame();

                if (lastReceivedFrame != null)
                {
                    long timeSinceLastReceivedFrameMs
                        = nowMs - lastReceivedFrame.getReceivedMs();

                    if (enc.isActive()
                        && timeSinceLastReceivedFrameMs > SUSPENSION_THRESHOLD_MS
                        && enc.isReceived())
                    {
                        maybeSuspended = true;
                        logger.info("maybe_suspended,stream="
                            + mediaStreamTrackReceiver.getStream().hashCode()
                            + " ssrc=" + enc.getPrimarySSRC()
                            + ",idx=" + enc.getIndex()
                            + ",silent_ms=" + timeSinceLastReceivedFrameMs);
                    }
                }
            }

            if (maybeSuspended || activated)
            {
                if (logger.isTraceEnabled())
                {
                    String reason = maybeSuspended ? "maybe_suspended" : "activated";
                    logger.trace("send_fir,stream="
                        + mediaStreamTrackReceiver.getStream().hashCode()
                        + ",reason=" + reason);
                }
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
            if (lastReceivedFrame != null && RTPUtils.rtpTimestampDiff(
                frameDesc.getTimestamp(), lastReceivedFrame.getTimestamp()) < 0)
            {
                // This is a late key frame header packet that we've missed.

                if (!encoding.isActive())
                {
                    if (logger.isTraceEnabled())
                    {
                        logger.trace("send_fir,stream="
                            + mediaStreamTrackReceiver.getStream().hashCode()
                            + ",reason=late");
                    }

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
     * passed in as an argument. Assumes that the packet is valid.
     *
     * @param pkt the packet to match.
     * @return the {@link RTPEncodingDesc} that corresponds to the packet that is
     * specified in the buffer passed in as an argument, or null.
     */
    RTPEncodingDesc findRTPEncodingDesc(RawPacket pkt)
    {
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            return null;
        }

        for (RTPEncodingDesc encoding : rtpEncodings)
        {
            if (encoding.matches(pkt))
            {
                return encoding;
            }
        }

        return null;
    }

    /**
     * Finds the {@link RTPEncodingDesc} that corresponds to the specified
     * {@code ssrc}.
     *
     * @param ssrc the SSRC of the {@link RTPEncodingDesc} to find. If multiple
     * encodings share the same SSRC, the first match will be returned.
     * @return the {@link RTPEncodingDesc} that corresponds to the specified
     * {@code ssrc}.
     */
    RTPEncodingDesc findRTPEncodingDesc(long ssrc)
    {
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            return null;
        }

        for (RTPEncodingDesc encoding : rtpEncodings)
        {
            if (encoding.matches(ssrc))
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
     * @Deprecated use findFrameDesc(long, long)
     */
    @Deprecated
    public FrameDesc findFrameDesc(byte[] buf, int off, int len)
    {
        return findFrameDesc(
            RawPacket.getSSRCAsLong(buf, off, len),
            RawPacket.getTimestamp(buf, off, len));
    }

    /**
     * Finds the {@link FrameDesc} that corresponds to the given timestamp
     * for the given stream (identified by its ssrc)
     * @param ssrc the ssrc of the stream to which this frame belongs
     * @param timestamp the timestamp of the frame the caller is trying to find
     * @return the {@link FrameDesc} that corresponds to the ssrc and timestamp
     * given, or null
     */
    public FrameDesc findFrameDesc(long ssrc, long timestamp)
    {
        RTPEncodingDesc rtpEncoding = findRTPEncodingDesc(ssrc);
        if (rtpEncoding != null)
        {
            return rtpEncoding.findFrameDesc(timestamp);
        }
        return null;
    }

    /**
     * FIXME: this should probably check whether the specified SSRC is part
     * of this track (i.e. check all encodings and include secondary SSRCs).
     *
     * @param ssrc the SSRC to match.
     * @return {@code true} if the specified {@code ssrc} is the primary SSRC
     * for this track.
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
    }
}
