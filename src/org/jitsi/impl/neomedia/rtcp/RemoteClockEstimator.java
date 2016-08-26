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
package org.jitsi.impl.neomedia.rtcp;

import java.util.*;
import java.util.concurrent.*;
import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * A class that can be used to estimate the remote time at a given local time.
 *
 * @author George Politis
 * @author Boris Grozev
 * @author Lyubomir Marinov
 */
public class RemoteClockEstimator
{
    /**
     * The <tt>Logger</tt> used by the <tt>RemoteClockEstimator</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(RemoteClockEstimator.class);

    /**
     * The {@link MediaType} of the SSRCs tracked by this instance. If
     * non-{@code null}, may provide hints to this instance such as video
     * defaulting to a clock frequency/rate of 90kHz.
     */
    private final MediaType _mediaType;

    private final StreamRTPManager streamRTPManager;

    /**
     * A {@code Map} of the (received) {@code RemoteClock}s by synchronization
     * source identifier (SSRC).
     */
    private final Map<Long, RemoteClock> remoteClocks
        = new ConcurrentHashMap<>();

    /**
     * Initializes a new {@code RemoteClockEstimator} with a specific
     * {@code MediaType}.
     *
     * @param streamRTPManager the {@link MediaStream} that owns this instance.
     */
    public RemoteClockEstimator(StreamRTPManager streamRTPManager)
    {
        this.streamRTPManager = streamRTPManager;
        if (streamRTPManager.getMediaStream() instanceof VideoMediaStream)
        {
            _mediaType = MediaType.VIDEO;
        }
        else
        {
            _mediaType = MediaType.AUDIO;
        }
    }

    /**
     * Adds a {@code RemoteClock} for an RTP stream identified by a specific
     * SSRC.
     */
    public void update(byte[] buf, int off, int len)
    {
        long ssrc
            = RTCPHeaderUtils.getSenderSSRC(buf, off, len);

        if (ssrc == -1)
        {
            logger.warn("Failed to update the remote clock. Failed to read " +
                "the SSRC. streamHashCode="
                + streamRTPManager.getMediaStream().hashCode());
            return;
        }

        int pktLen = RTCPHeaderUtils.getLength(buf, off, len);
        if (pktLen == -1)
        {
            logger.warn("Failed to update the remote clock. The RTCP SR length"
                + " is invalid. streamHashCode="
                + streamRTPManager.getMediaStream().hashCode());
            return;
        }

        if (!RTCPSenderInfoUtils.isValid(
            buf, off + RTCPHeader.SIZE, len - RTCPHeader.SIZE))
        {
            logger.warn("Failed to update the remote clock. The RTCP sender" +
                " info section is invalid. streamHashCode="
                + streamRTPManager.getMediaStream().hashCode());
            return;
        }

        long rtptimestamp = RTCPSenderInfoUtils.getTimestamp(
            buf, off + RTCPHeader.SIZE, pktLen - RTCPHeader.SIZE);
        long ntptimestampmsw = RTCPSenderInfoUtils.getNtpTimestampMSW(
            buf, off + RTCPHeader.SIZE, pktLen - RTCPHeader.SIZE);
        long ntptimestamplsw = RTCPSenderInfoUtils.getNtpTimestampLSW(
            buf, off + RTCPHeader.SIZE, pktLen - RTCPHeader.SIZE);

        long systemTimeMs = TimeUtils.getTime(
            TimeUtils.constuctNtp(ntptimestampmsw, ntptimestamplsw));

        // Estimate the clock frequency/rate of the sender.
        int frequencyHz;

        RemoteClock oldClock = remoteClocks.get(ssrc);
        if (oldClock != null)
        {
            // Calculate the clock frequency/rate.
            Timestamp oldTs = oldClock.getRemoteTimestamp();
            long rtpTimestampDiff
                = rtptimestamp - oldTs.getRtpTimestampAsLong();
            long systemTimeMsDiff = systemTimeMs - oldTs.getSystemTimeMs();

            frequencyHz = Math.round(
                (float) (rtpTimestampDiff * 1000)/ systemTimeMsDiff);

            if (frequencyHz < 1)
            {
                /**
                 * It has been observed that sometimes Chrome is sending RTCP
                 * SRs with RTP timestamps that are not monotonically
                 * increasing, while the NTP timestamps do increase
                 * monotonically. This messes up our frequency calculation. Here
                 * we detect this situation and not take into account these
                 * data points.
                 */

                logger.warn("Not updating remote clock because the timestamp "
                    + "point is invalid. ssrc=" + ssrc
                    + ", systemTime=" + new Date(systemTimeMs)
                    + ", systemTimeMs=" + systemTimeMs
                    + ", rtpTimestamp=" + rtptimestamp
                    + ", frequencyHz=" + frequencyHz
                    + ", streamHashCode="
                    + streamRTPManager.getMediaStream().hashCode());
                return;
            }
        }
        else
        {
            if (MediaType.VIDEO.equals(_mediaType))
            {
                frequencyHz = 90 * 1000;
            }
            else
            {
                frequencyHz = 48 * 1000;
            }
        }

        if (logger.isDebugEnabled())
        {
            logger.debug("Updating the remote clock ssrc=" + ssrc
                + ", systemTime=" + new Date(systemTimeMs)
                + ", systemTimeMs=" + systemTimeMs
                + ", rtpTimestamp=" + rtptimestamp
                + ", frequencyHz=" + frequencyHz
                + ", streamHashCode="
                + streamRTPManager.getMediaStream().hashCode());
        }

        // Replace whatever was in there before.
        remoteClocks.put(
                ssrc,
                new RemoteClock(
                        systemTimeMs,
                        (int) rtptimestamp,
                        frequencyHz));
    }

    /**
     * Gets the {@link RemoteClock} of the RTP stream identifier by a specific
     * SSRC.
     *
     * @param ssrc the SSRC of the RTP stream whose {@code RemoteClock} is to be
     * returned
     * @return the {@code RemoteClock} of the RTP stream identified by
     * {@code ssrc} or {@code null}
     */
    public RemoteClock getRemoteClock(long ssrc)
    {
        return remoteClocks.get(ssrc);
    }

    /**
     * Estimate the remote {@code Timestamp} of a given RTP stream (identified
     * by its SSRC) at a given local time (in milliseconds).
     *
     * @param ssrc the SSRC of the RTP stream whose remote {@code Timestamp} we
     * want to estimate.
     * @param localTimeMs the local time (in milliseconds) that will be mapped
     * to a remote time.
     * @return an estimation of the remote {@code Timestamp} of {@code ssrc} at
     * time {@code localTimeMs}.
     */
    public Timestamp estimate(long ssrc, long localTimeMs)
    {
        RemoteClock remoteClock = getRemoteClock(ssrc);

        // We can't continue if we don't have NTP and RTP timestamps.
        return (remoteClock == null) ? null : remoteClock.estimate(localTimeMs);
    }
}
