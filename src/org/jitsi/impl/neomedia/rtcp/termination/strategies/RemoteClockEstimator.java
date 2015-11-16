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

/**
 * A class that can be used to estimate the remote time at a given local
 * time.
 *
 * @author George Politis
 */
class RemoteClockEstimator
{
    /**
     * base: 7-Feb-2036 @ 06:28:16 UTC
     */
    private static final long MSB0_BASE_TIME = 2085978496000L;

    /**
     * base: 1-Jan-1900 @ 01:00:00 UTC
     */
    private static final long MSB1_BASE_TIME = -2208988800000L;

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
                RTCPSRPacket srPacket = (RTCPSRPacket) rtcpPacket;

                // The media sender SSRC.
                int ssrc = srPacket.ssrc;

                // Convert 64-bit NTP timestamp to Java standard time. Note that
                // java time (milliseconds) by definition has less precision
                // than NTP time (picoseconds) so converting NTP timestamp to
                // java time and back to NTP timestamp loses precision. For
                // example, Tue, Dec 17 2002 09:07:24.810 EST is represented by
                // a single Java-based time value of f22cd1fc8a, but its NTP
                // equivalent are all values ranging from c1a9ae1c.cf5c28f5 to
                // c1a9ae1c.cf9db22c.

                // Use round-off on fractional part to preserve going to lower
                // precision
                long fraction = Math.round(
                    1000D * srPacket.ntptimestamplsw / 0x100000000L);
                /*
                 * If the most significant bit (MSB) on the seconds field is set
                 * we use a different time base. The following text is a quote
                 * from RFC-2030 (SNTP v4):
                 *
                 * If bit 0 is set, the UTC time is in the range 1968-2036 and
                 * UTC time is reckoned from 0h 0m 0s UTC on 1 January 1900. If
                 * bit 0 is not set, the time is in the range 2036-2104 and UTC
                 * time is reckoned from 6h 28m 16s UTC on 7 February 2036.
                 */
                long msb = srPacket.ntptimestampmsw & 0x80000000L;
                long remoteTime = (msb == 0)
                    // use base: 7-Feb-2036 @ 06:28:16 UTC
                    ? MSB0_BASE_TIME
                        + (srPacket.ntptimestampmsw * 1000) + fraction
                    // use base: 1-Jan-1900 @ 01:00:00 UTC
                    : MSB1_BASE_TIME
                        + (srPacket.ntptimestampmsw * 1000) + fraction;

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
                        (((int) srPacket.rtptimestamp
                            - oldRemoteClock.getRtpTimestamp())
                                & 0xffffffffl)
                        / (remoteTime
                            - oldRemoteClock.getRemoteTime()));
                }

                // Replace whatever was in there before.
                receivedClocks.put(ssrc, new ReceivedRemoteClock(ssrc,
                    remoteTime, (int) srPacket.rtptimestamp,
                    frequencyHz));
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
