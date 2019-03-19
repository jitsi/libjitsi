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

import org.jitsi.utils.*;
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
    protected final AtomicLong bytes = new AtomicLong();

    /**
     * The total number of RTP packets. This excludes RTCP packets, because
     * the value is used to calculate the number of lost RTP packets.
     */
    protected final AtomicLong packets = new AtomicLong();

    /**
     * Number of bytes retransmitted.
     */
    protected final AtomicLong bytesRetransmitted = new AtomicLong();

    /**
     * Number of bytes for packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     */
    protected final AtomicLong bytesNotRetransmitted = new AtomicLong();

    /**
     * Number of packets retransmitted.
     */
    protected final AtomicLong packetsRetransmitted = new AtomicLong();

    /**
     * Number of packets which were requested and found in the cache, but
     * were intentionally not retransmitted.
     */
    protected final AtomicLong packetsNotRetransmitted = new AtomicLong();

    /**
     * The number of packets for which retransmission was requested, but
     * they were missing from the cache.
     */
    protected final AtomicLong packetsMissingFromCache = new AtomicLong();


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

    /**
     * Gets the number of bytes retransmitted.
     *
     * @return the number of bytes retransmitted.
     */
    public long getBytesRetransmitted()
    {
        return bytesRetransmitted.get();
    }

    /**
     * Gets the number of bytes for packets which were requested and found
     * in the cache, but were intentionally not retransmitted.
     *
     * @return the number of bytes for packets which were requested and
     * found in the cache, but were intentionally not retransmitted.
     */
    public long getBytesNotRetransmitted()
    {
        return bytesNotRetransmitted.get();
    }

    /**
     * Gets the number of packets retransmitted.
     *
     * @return the number of packets retransmitted.
     */
    @Override
    public long getPacketsRetransmitted()
    {
        return packetsRetransmitted.get();
    }

    /**
     * Gets the number of packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     *
     * @return the number of packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     */
    public long getPacketsNotRetransmitted()
    {
        return packetsNotRetransmitted.get();
    }

    /**
     * Gets the number of packets for which retransmission was requested,
     * but they were missing from the cache.
     * @return the number of packets for which retransmission was requested,
     * but they were missing from the cache.
     */
    public long getPacketsMissingFromCache()
    {
        return packetsMissingFromCache.get();
    }

    /**
     * Notifies this instance that an RTP packet with a given length was not
     * retransmitted (that is, the remote endpoint requested it,
     * and it was found in the local cache, but it was not retransmitted).
     * @param length the length in bytes of the packet.
     */
    protected void rtpPacketRetransmitted(long length)
    {
        packetsRetransmitted.incrementAndGet();
        bytesRetransmitted.addAndGet(length);
    }

    /**
     * Notifies this instance that an RTP packet with a given length was
     * retransmitted.
     * @param length the length in bytes of the packet.
     */
    protected void rtpPacketNotRetransmitted(long length)
    {
        packetsNotRetransmitted.incrementAndGet();
        bytesNotRetransmitted.addAndGet(length);
    }

    /**
     * Notifies this instance that the remote endpoint requested retransmission
     * of a packet, and it was not found in the local cache.
     */
    void rtpPacketCacheMiss()
    {
        packetsMissingFromCache.incrementAndGet();
    }
}
