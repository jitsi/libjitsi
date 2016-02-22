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
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import java.util.*;
import java.util.concurrent.*;
import net.sf.fmj.media.rtp.*;
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
     * A {@code Map} of the (received) {@code RemoteClock}s by synchronization
     * source identifier (SSRC).
     */
    private final Map<Integer, RemoteClock> remoteClocks
        = new ConcurrentHashMap<>();

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
    private void update(RTCPSRPacket sr)
    {
        int ssrc = sr.ssrc; // The media sender SSRC.
        long ntpTime
            = TimeUtils.constuctNtp(sr.ntptimestampmsw, sr.ntptimestamplsw);
        long systemTimeMs = TimeUtils.getTime(ntpTime);

        // Estimate the clock rate of the sender.
        RemoteClock oldClock = remoteClocks.get(ssrc);
        int frequencyHz = -1;

        if (oldClock != null)
        {
            // Calculate the clock rate.
            Timestamp oldTs = oldClock.getRemoteTimestamp();
            int rtpTimestampDiff
                = (int) sr.rtptimestamp - oldTs.getRtpTimestamp();
            long systemTimeMsDiff = systemTimeMs - oldTs.getSystemTimeMs();

            frequencyHz
                = Math.round(
                        (float) (rtpTimestampDiff & 0xffffffffL)
                            / systemTimeMsDiff);
        }

        // Replace whatever was in there before.
        remoteClocks.put(
                ssrc,
                new RemoteClock(
                        ssrc,
                        systemTimeMs,
                        (int) sr.rtptimestamp,
                        frequencyHz));
    }

    /**
     * Estimate the <tt>RemoteClock</tt> of a given RTP stream (identified by
     * its SSRC) at a given time.
     *
     * @param ssrc the SSRC of the RTP stream whose <tt>RemoteClock</tt> we want
     * to estimate.
     * @param time the local time that will be mapped to a remote time.
     * @return An estimation of the <tt>RemoteClock</tt> at time <tt>time</tt>.
     */
    public Timestamp estimate(int ssrc, long time)
    {
        RemoteClock remoteClock = remoteClocks.get(ssrc);
        int frequencyHz;

        if (remoteClock == null
                || (frequencyHz = remoteClock.getFrequencyHz()) == -1)
        {
            // We can't continue if we don't have NTP and RTP timestamps and/or
            // the original sender frequency, so move to the next one.
            return null;
        }

        long delayMs = time - remoteClock.getLocalReceiptTimeMs();

        // Estimate the remote wall clock.
        Timestamp remoteTs = remoteClock.getRemoteTimestamp();
        long remoteTime = remoteTs.getSystemTimeMs();
        long estimatedRemoteTime = remoteTime + delayMs;

        // Drift the RTP timestamp.
        int rtpTimestamp
            = remoteTs.getRtpTimestamp()
                + ((int) delayMs) * (frequencyHz / 1000);
        return new Timestamp(estimatedRemoteTime, rtpTimestamp);
    }
}
