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
     * A map holding the received remote clocks.
     */
    private final Map<Integer, ReceivedRemoteClock> receivedClocks
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
        long remoteTime = TimeUtils.getTime(ntpTime);

        // Estimate the clock rate of the sender.
        int frequencyHz = -1;

        if (receivedClocks.containsKey(ssrc))
        {
            // Calculate the clock rate.
            RemoteClock oldRemoteClock
                = receivedClocks.get(ssrc).getRemoteClock();
            int rtpTimestampDiff
                = (int) sr.rtptimestamp - oldRemoteClock.getRtpTimestamp();
            long remoteTimeDiff = remoteTime - oldRemoteClock.getRemoteTime();

            frequencyHz
                = Math.round(
                        (float)
                            (rtpTimestampDiff & 0xffffffffL) / remoteTimeDiff);
        }

        // Replace whatever was in there before.
        receivedClocks.put(
                ssrc,
                new ReceivedRemoteClock(
                        ssrc,
                        remoteTime,
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
    public RemoteClock estimate(int ssrc, long time)
    {
        ReceivedRemoteClock receivedRemoteClock = receivedClocks.get(ssrc);
        int frequencyHz;

        if (receivedRemoteClock == null
                || (frequencyHz = receivedRemoteClock.getFrequencyHz()) == -1)
        {
            // We can't continue if we don't have NTP and RTP timestamps and/or
            // the original sender frequency, so move to the next one.
            return null;
        }

        long delayMillis = time - receivedRemoteClock.getReceivedTime();

        // Estimate the remote wall clock.
        long remoteTime = receivedRemoteClock.getRemoteClock().getRemoteTime();
        long estimatedRemoteTime = remoteTime + delayMillis;

        // Drift the RTP timestamp.
        int rtpTimestamp
            = receivedRemoteClock.getRemoteClock().getRtpTimestamp()
                + ((int) delayMillis) * (frequencyHz / 1000);
        return new RemoteClock(estimatedRemoteTime, rtpTimestamp);
    }
}
