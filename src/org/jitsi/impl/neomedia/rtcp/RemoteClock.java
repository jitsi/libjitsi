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

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;

/**
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class RemoteClock
{
    /**
     * The remote <tt>Timestamp</tt> which was received at
     * {@link #localReceiptTimeMs} for this RTP stream.
     */
    private final Timestamp remoteTimestamp;

    /**
     * The local time (in milliseconds since the epoch) at which we received the
     * RTCP report with the RTP/NTP timestamps. It's a signed long.
     */
    private final long localReceiptTimeMs;

    /**
     * The clock rate for the ssrc that this clock pertains to. We need to have
     * received at least two SRs in order to be able to calculate this. Unsigned
     * short.
     */
    private final int frequencyHz;

    /**
     * Ctor.
     *
     * @param remoteTime the remote (system/wallclock) time in milliseconds
     * since the epoch
     * @param rtpTimestamp the RTP timestamp corresponding to
     * <tt>remoteTime</tt>.
     * @param frequencyHz the RTP clock rate.
     */
    RemoteClock(long remoteTime, int rtpTimestamp, int frequencyHz)
    {
        this.remoteTimestamp = new Timestamp(remoteTime, rtpTimestamp);
        this.frequencyHz = frequencyHz;
        this.localReceiptTimeMs = System.currentTimeMillis();
    }

    public Timestamp estimate(long localTimeMs)
    {
        int frequencyHz = getFrequencyHz();

        if (frequencyHz == -1)
        {
            // We can't continue if we don't have the sender's clock
            // frequency/rate.
            return null;
        }

        long delayMs = localTimeMs - getLocalReceiptTimeMs();

        // Estimate the remote wall clock.
        Timestamp remoteTs = getRemoteTimestamp();
        long remoteTime = remoteTs.getSystemTimeMs();
        long estimatedRemoteTime = remoteTime + delayMs;

        // Drift the RTP timestamp.
        long rtpTimestamp
            = remoteTs.getRtpTimestampAsLong() + delayMs * (frequencyHz / 1000);

        return new Timestamp(estimatedRemoteTime, (int) rtpTimestamp);
    }

    /**
     * Gets the RTP timestamp that corresponds to a specific (remote) system
     * time in milliseconds.
     *
     * @param systemTimeMs the (remote) system time in milliseconds for which
     * the corresponding RTP timestamp is to be returned
     * @return the RTP timestamp that corresponds to the specified (remote)
     * {@code systemTimeMs} or {@code null}
     */
    public Timestamp remoteSystemTimeMs2rtpTimestamp(long systemTimeMs)
    {
        int frequencyHz = getFrequencyHz();

        if (false && frequencyHz < 1000)
        {
            // We can't continue if (1) we don't have the sender's clock
            // frequency/rate or (2) the sender's clock frequency/rate is bellow
            // 1kHz.

            // XXX unfortunately Chrome is sending RTCP with RTP timestamps that
            // are not proportional to the wallclock. This results in
            // frequencies that are lower than 1000. We have even observed
            // negative frequencies. This is clearly a bug in the webrtc code
            // that we need to take into account here. We keep this code here
            // as a reminder to the situation.
            return null;
        }

        Timestamp ts = getRemoteTimestamp();
        long rtpTimestamp
            = ts.getRtpTimestampAsLong()
                + (systemTimeMs - ts.getSystemTimeMs()) * (frequencyHz / 1000);

        return new Timestamp(systemTimeMs, (int) rtpTimestamp);
    }

    /**
     * Gets the (remote) system time in milliseconds that corresponds to a
     * specific RTP timestamp.
     *
     * @param rtpTimestamp the RTP timestamp for which the (remote) system time
     * in milliseconds is to be returned
     * @return the (remote) system time in milliseconds that corresponds to
     * {@code rtpTimestamp}
     */
    public Timestamp rtpTimestamp2remoteSystemTimeMs(long rtpTimestamp)
    {
        int frequencyHz = getFrequencyHz();

        if (false && frequencyHz < 1000)
        {
            // We can't continue if (1) we don't have the sender's clock
            // frequency/rate or (2) the sender's clock frequency/rate is bellow
            // 1kHz.

            // XXX unfortunately Chrome is sending RTCP with RTP timestamps that
            // are not proportional to the wallclock. This results in
            // frequencies that are lower than 1000. We have even observed
            // negative frequencies. This is clearly a bug in the webrtc code
            // that we need to take into account here. We keep this code here
            // as a reminder to the situation.
            return null;
        }

        Timestamp ts = getRemoteTimestamp();

        return
            new Timestamp(
                    ts.getSystemTimeMs()
                        + (rtpTimestamp - ts.getRtpTimestampAsLong())
                            / (frequencyHz / 1000),
                    (int) rtpTimestamp);
    }

    /**
     * Gets the remote {@code Timestamp} (i.e. RTP timestamp and system time in
     * milliseconds) which was received at {@link #localReceiptTimeMs} for this
     * RTP stream.
     *
     * @return the remote {@code Timestamp} which was received at
     * {@code localReceiptTimeMs} for this RTP stream
     */
    public Timestamp getRemoteTimestamp()
    {
        return remoteTimestamp;
    }

    /**
     * Gets the local time (in milliseconds since the epoch) at which we
     * received the RTCP report with the RTP/NTP timestamps.
     *
     * @return the local time (in milliseconds since the epoch) at which we
     * received the RTCP report with the RTP/NTP timestamps
     */
    public long getLocalReceiptTimeMs()
    {
        return localReceiptTimeMs;
    }

    /**
     *
     * @return
     */
    public int getFrequencyHz()
    {
        return frequencyHz;
    }
}
