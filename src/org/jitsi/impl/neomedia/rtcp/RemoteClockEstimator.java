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
import javax.media.rtp.rtcp.*;
import net.sf.fmj.media.rtp.*;
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
     * The {@link MediaType} of the SSRCs tracked by this instance. If
     * non-{@code null}, may provide hints to this instance such as video
     * defaulting to a clock frequency/rate of 90kHz.
     */
    private final MediaType _mediaType;

    /**
     * A {@code Map} of the (received) {@code RemoteClock}s by synchronization
     * source identifier (SSRC).
     */
    private final Map<Integer, RemoteClock> remoteClocks
        = new ConcurrentHashMap<>();

    /**
     * Initializes a new {@code RemoteClockEstimator} without a
     * {@code MediaType}.
     */
    public RemoteClockEstimator()
    {
        this(null);
    }

    /**
     * Initializes a new {@code RemoteClockEstimator} with a specific
     * {@code MediaType}.
     *
     * @param mediaType the {@code MediaType} to initialize the new instance
     * with. It may be used by the implementation as a hint to the default clock
     * frequency/rate.
     */
    public RemoteClockEstimator(MediaType mediaType)
    {
        _mediaType = mediaType;
    }

    /**
     * Inspect an <tt>RTCPCompoundPacket</tt> and build up the state for future
     * estimations.
     *
     * @param compound
     */
    public void update(RTCPCompoundPacket compound)
    {
        RTCPPacket[] rtcps;

        if (compound == null
                || (rtcps = compound.packets) == null
                || rtcps.length == 0)
        {
            return;
        }

        for (RTCPPacket rtcp : rtcps)
        {
            switch (rtcp.type)
            {
            case RTCPPacket.SR:
                update((RTCPSRPacket) rtcp);
                break;
            }
        }
    }

    /**
     * Inspects an {@code RTCPSRPacket} and builds up the state for future
     * estimations.
     *
     * @param sr
     */
    public void update(RTCPSRPacket sr)
    {
        update(
                sr.ssrc,
                sr.ntptimestampmsw, sr.ntptimestamplsw,
                sr.rtptimestamp);
    }

    /**
     * Inspects a {@code SenderReport} and builds up the state for future
     * estimations.
     *
     * @param sr
     */
    public void update(SenderReport sr)
    {
        update(
                (int) sr.getSSRC(),
                sr.getNTPTimeStampMSW(), sr.getNTPTimeStampLSW(),
                sr.getRTPTimeStamp());
    }

    /**
     * Adds a {@code RemoteClock} for an RTP stream identified by a specific
     * SSRC.
     *
     * @param ssrc the SSRC of the RTP stream whose {@code RemoteClock} is to be
     * added
     * @param ntptimestampmsw
     * @param ntptimestamplsw
     * @param rtptimestamp
     */
    private void update(
            int ssrc,
            long ntptimestampmsw, long ntptimestamplsw,
            long rtptimestamp)
    {
        long systemTimeMs
            = TimeUtils.getTime(
                    TimeUtils.constuctNtp(ntptimestampmsw, ntptimestamplsw));

        // Estimate the clock frequency/rate of the sender.
        int frequencyHz;

        if (MediaType.VIDEO.equals(_mediaType))
        {
            // XXX Don't calculate the clock frequency/rate for video because it
            // is easier and less error prone (e.g. there is no need to deal
            // with rounding).
            frequencyHz = 90 * 1000;
        }
        else
        {
            RemoteClock oldClock = remoteClocks.get(ssrc);

            if (oldClock != null)
            {
                // Calculate the clock frequency/rate.
                Timestamp oldTs = oldClock.getRemoteTimestamp();
                long rtpTimestampDiff
                    = rtptimestamp - oldTs.getRtpTimestampAsLong();
                long systemTimeMsDiff = systemTimeMs - oldTs.getSystemTimeMs();

                frequencyHz
                    = Math.round((float) rtpTimestampDiff / systemTimeMsDiff);
            }
            else
            {
                frequencyHz = -1;
            }
        }

        // Replace whatever was in there before.
        remoteClocks.put(
                ssrc,
                new RemoteClock(
                        ssrc,
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
    public RemoteClock getRemoteClock(int ssrc)
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
    public Timestamp estimate(int ssrc, long localTimeMs)
    {
        RemoteClock remoteClock = getRemoteClock(ssrc);

        // We can't continue if we don't have NTP and RTP timestamps.
        return (remoteClock == null) ? null : remoteClock.estimate(localTimeMs);
    }
}
