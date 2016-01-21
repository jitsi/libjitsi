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
 * A class that can be used to estimate the remote time at a given local
 * time.
 *
 * @author George Politis
 * @author Boris Grozev
 */
public class RemoteClockEstimator
{
    /**
     * A map holding the received remote clocks.
     */
    private final Map<Integer, ReceivedRemoteClock> receivedClocks
        = new ConcurrentHashMap<>();

    /**
     * Inspect an <tt>RTCPCompoundPacket</tt> and build-up the state for
     * future estimations.
     *
     * @param pkt
     */
    public void update(RTCPCompoundPacket pkt)
    {
        if (pkt == null || pkt.packets == null || pkt.packets.length == 0)
        {
            return;
        }

        for (RTCPPacket rtcpPacket : pkt.packets)
        {
            switch (rtcpPacket.type)
            {
            case RTCPPacket.SR:
                RTCPSRPacket sr = (RTCPSRPacket) rtcpPacket;

                // The media sender SSRC.
                int ssrc = sr.ssrc;

                long ntpTime
                    = TimeUtils.constuctNtp(
                        sr.ntptimestampmsw, sr.ntptimestamplsw);
                long remoteTime = TimeUtils.getTime(ntpTime);

                // Estimate the clock rate of the sender.
                int frequencyHz = -1;
                if (receivedClocks.containsKey(ssrc))
                {
                    // Calculate the clock rate.
                    ReceivedRemoteClock oldStats
                        = receivedClocks.get(ssrc);
                    RemoteClock oldRemoteClock
                        = oldStats.getRemoteClock();
                    frequencyHz = Math.round((float)
                        (((int) sr.rtptimestamp
                            - oldRemoteClock.getRtpTimestamp())
                                & 0xffffffffl)
                        / (remoteTime
                            - oldRemoteClock.getRemoteTime()));
                }

                // Replace whatever was in there before.
                receivedClocks.put(
                        ssrc,
                        new ReceivedRemoteClock(
                                ssrc, remoteTime,
                                (int) sr.rtptimestamp, frequencyHz));
                break;
            case RTCPPacket.SDES:
                break;
            }
        }
    }

    /**
     * Estimate the <tt>RemoteClock</tt> of a given RTP stream (identified
     * by its SSRC) at a given time.
     *
     * @param ssrc the SSRC of the RTP stream whose <tt>RemoteClock</tt> we
     * want to estimate.
     * @param time the local time that will be mapped to a remote time.
     * @return An estimation of the <tt>RemoteClock</tt> at time "time".
     */
    public RemoteClock estimate(int ssrc, long time)
    {
        ReceivedRemoteClock receivedRemoteClock = receivedClocks.get(ssrc);
        if (receivedRemoteClock == null
                || receivedRemoteClock.getFrequencyHz() == -1)
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
                + ((int) delayMillis) * (receivedRemoteClock.getFrequencyHz()
                    / 1000);
        return new RemoteClock(estimatedRemoteTime, rtpTimestamp);
    }
}
