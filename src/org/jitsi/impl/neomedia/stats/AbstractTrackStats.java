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
package org.jitsi.impl.neomedia.stats;

import org.ice4j.util.*;
import org.jitsi.service.neomedia.stats.*;

import java.util.concurrent.atomic.*;

/**
 * Media stream statistics per send or receive SSRC.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
public abstract class AbstractTrackStats
    implements TrackStats
{
    /**
     * The last jitter (in milliseconds).
     */
    private double jitter = JITTER_UNSET;

    /**
     * The RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1,
     * subsection "delay since last SR (DLSR): 32 bits"). {@code -1} if the RTT
     * has not been computed yet. Otherwise, the RTT in milliseconds.
     */
    private long rtt = -1;

    /**
     * The total number of bytes.
     */
    protected AtomicLong bytes = new AtomicLong();

    /**
     * The total number of bytes.
     */
    protected AtomicLong packets = new AtomicLong();

    /**
     * The bitrate.
     */
    protected RateStatistics bitrate;

    /**
     * The packet rate.
     */
    protected RateStatistics packetRate;

    /**
     * The length of the interval over which the average bitrate, packet rate
     * and packet loss rate are computed.
     */
    private int interval;

    /**
     * The SSRC, if any, associated with this instance.
     */
    private final long ssrc;

    /**
     * Initializes a new {@code AbstractTrackStats} instance.
     */
    AbstractTrackStats(int interval, long ssrc)
    {
        this.interval = interval;
        this.ssrc = ssrc;
        bitrate = new RateStatistics(interval);
        packetRate = new RateStatistics(interval, 1000F);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSSRC()
    {
        return ssrc;
    }

    /**
     * Notifies this instance that a packet with a given length was processed
     * (i.e. sent or received).
     * @param length the length of the packet.
     * @param now the time at which the packet was processed (passed in order
     * to avoid calling {@link System#currentTimeMillis()}).
     * @param rtp whether the packet is an RTP or RTCP packet.
     */
    protected void packetProcessed(int length, long now, boolean rtp)
    {
        bytes.addAndGet(length);
        bitrate.update(length, now);

        // Don't count RTCP packets towards the packet rate since it is used to
        // calculate the number of lost packets.
        if (rtp)
        {
            packets.addAndGet(1);
            packetRate.update(1, now);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getJitter()
    {
        return jitter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRtt()
    {
        return rtt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBytes()
    {
        return bytes.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getPackets()
    {
        return packets.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBitrate()
    {
        return bitrate.getRate(System.currentTimeMillis());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getPacketRate()
    {
        return packetRate.getRate(System.currentTimeMillis());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCurrentBytes()
    {
        // Note that this.bitrate counts bytes and only converts to bits per
        // second in getRate().
        return bitrate.getAccumulatedCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCurrentPackets()
    {
        return packetRate.getAccumulatedCount();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public long getInterval()
    {
        return interval;
    }


    /**
     * Sets the last jitter that was sent/received.
     *
     * @param jitter the new value to set on this instance as the last
     * sent/received jitter (in milliseconds).
     */
    protected void setJitter(double jitter)
    {
        this.jitter = jitter;
    }

    /**
     * Sets {@link #rtt} to a specific value in milliseconds.
     */
    protected void setRtt(long rtt)
    {
        this.rtt = rtt;
    }
}
