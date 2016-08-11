package org.jitsi.impl.neomedia.stats;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.stats.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author Boris Grozev
 */
public class MediaStreamStats2Impl
    extends MediaStreamStatsImpl
    implements MediaStreamStats2
{
    /**
     * Window over which rates will be computed.
     */
    private static int INTERVAL = 1000;

    /**
     * Hold per-SSRC statistics for received streams.
     */
    private final Map<Long,BasicReceiveStreamStatsImpl> receiveSsrcStats
        = new ConcurrentHashMap<>();

    /**
     * Hold per-SSRC statistics for sent streams.
     */
    private final Map<Long,BasicSendStreamStatsImpl> sendSsrcStats
        = new ConcurrentHashMap<>();

    /**
     * Global (aggregated) statistics for received streams.
     */
    private final AggregateBasicReceiveStats receiveStats
        = new AggregateBasicReceiveStats(INTERVAL, receiveSsrcStats);

    /**
     * Global (aggregated) statistics for sent streams.
     */
    private final AggregateBasicSendStats sendStats
        = new AggregateBasicSendStats(INTERVAL, sendSsrcStats);

    /**
     * Initializes a new {@link MediaStreamStats2Impl} instance.
     */
    public MediaStreamStats2Impl(MediaStreamImpl mediaStream)
    {
        super(mediaStream);
    }

    /**
     * Notifies this instance that an RTP packet with a particular SSRC,
     * sequence number and length was received.
     * @param ssrc the SSRC of the packet.
     * @param seq the RTP sequence number of the packet.
     * @param length the length in bytes of the packet.
     */
    public void rtpPacketReceived(long ssrc, int seq, int length)
    {
        synchronized (receiveStats)
        {
            getReceiveStats(ssrc).rtpPacketReceived(seq, length);
            receiveStats
                .packetProcessed(length, System.currentTimeMillis(), true);
        }
    }

    /**
     * Notifies this instance that an RTP packet with a particular SSRC,
     * sequence number and length was sent (or is about to be sent).
     * @param ssrc the SSRC of the packet.
     * @param seq the RTP sequence number of the packet.
     * @param length the length in bytes of the packet.
     */
    public void rtpPacketSent(long ssrc, int seq, int length)
    {
        synchronized (sendStats)
        {
            getSendStats(ssrc).rtpPacketSent(seq, length);
            sendStats.packetProcessed(length, System.currentTimeMillis(), true);
        }
    }

    /**
     * Notifies this instance that an RTCP Receiver Report packet with a
     * particular SSRC and the given values for total number of lost packets
     * and extended highest sequence number was received.
     * @param ssrc the SSRC of the packet.
     * @param fractionLost the value of the "fraction lost" field.
     */
    public void rtcpReceiverReportReceived(long ssrc, int fractionLost)
    {
        synchronized (sendStats)
        {
            getSendStats(ssrc).rtcpReceiverReportReceived(fractionLost);
        }
    }

    /**
     * Notifies this instance that an RTCP packet with a particular SSRC and
     * particular length was received.
     * @param ssrc the SSRC of the packet.
     * @param length the length in bytes of the packet.
     */
    public void rtcpPacketReceived(long ssrc, int length)
    {
        synchronized (receiveStats)
        {
            getReceiveStats(ssrc).rtcpPacketReceived(length);
            receiveStats
                .packetProcessed(length, System.currentTimeMillis(), false);
        }
    }

    /**
     * Notifies this instance that an RTCP packet with a particular SSRC and
     * particular length was sent (or is about to be sent).
     * @param ssrc the SSRC of the packet.
     * @param length the length in bytes of the packet.
     */
    public void rtcpPacketSent(long ssrc, int length)
    {
        synchronized (sendStats)
        {
            getSendStats(ssrc).rtcpPacketSent(length);
            sendStats
                .packetProcessed(length, System.currentTimeMillis(), false);
        }
    }

    /**
     * Notifies this instance of a new value for the RTP jitter of the stream
     * in a particular direction.
     * @param ssrc the SSRC of the stream for which the jitter changed.
     * @param direction whether the jitter is for a received or sent stream.
     * @param jitter the new jitter value in milliseconds.
     */
    public void updateJitter(long ssrc, StreamDirection direction, double jitter)
    {
        // TODO(boris) Currently we only maintain a jitter value for the entire
        // MediaStream, and not for any of the individual SSRCs. At the time of
        // this writing, it is unclear to me whether it should be kept this way,
        // or whether keeping a per-SSRC value can be useful for something. So
        // I am doing the easier thing.
        if (direction == StreamDirection.DOWNLOAD)
        {
            receiveStats.setJitter(jitter);
        }
        else if (direction == StreamDirection.UPLOAD)
        {
            sendStats.setJitter(jitter);
        }
    }

    /**
     * Notifies this instance of a new value for the round trip time measured
     * for the associated stream.
     * @param ssrc the SSRC of the stream for which the jitter changed.
     * @param rtt the new measured RTT in milliseconds.
     */
    public void updateRtt(long ssrc, long rtt)
    {
        // TODO(boris) Currently we only maintain an RTT value for the entire
        // MediaStream, and not for any of the individual SSRCs. At the time of
        // this writing, it is unclear to me whether it should be kept this way,
        // or whether keeping a per-SSRC value can be useful for something. So
        // I am doing the easier thing.
        receiveStats.setRtt(rtt);
        sendStats.setRtt(rtt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicReceiveStreamStats getReceiveStats()
    {
        return receiveStats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSendStreamStats getSendStats()
    {
        return sendStats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicReceiveStreamStatsImpl getReceiveStats(long ssrc)
    {
        BasicReceiveStreamStatsImpl stats = receiveSsrcStats.get(ssrc);
        if (stats == null)
        {
            synchronized (receiveSsrcStats)
            {
                stats = receiveSsrcStats.get(ssrc);
                if (stats == null)
                {
                    stats = new BasicReceiveStreamStatsImpl(INTERVAL, ssrc);
                    receiveSsrcStats.put(ssrc, stats);
                }
            }
        }

        return stats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSendStreamStatsImpl getSendStats(long ssrc)
    {
        BasicSendStreamStatsImpl stats = sendSsrcStats.get(ssrc);
        if (stats == null)
        {
            synchronized (sendSsrcStats)
            {
                stats = sendSsrcStats.get(ssrc);
                if (stats == null)
                {
                    stats = new BasicSendStreamStatsImpl(INTERVAL, ssrc);
                    sendSsrcStats.put(ssrc, stats);
                }
            }
        }

        return stats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends BasicSendStreamStats> getAllSendStats()
    {
        return sendSsrcStats.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends BasicReceiveStreamStats> getAllReceiveStats()
    {
        return receiveSsrcStats.values();
    }

    /**
     * An {@link BasicStreamStats} implementation which aggregates values for
     * a collection of {@link BasicStreamStats} instances.
     */
    private abstract class AggregateBasicStats<T>
        extends AbstractBasicStreamStats
    {
        /**
         * The collection of {@link BasicStreamStats} for which this instance
         * aggregates.
         */
        protected final Map<Long, ? extends T> children;

        /**
         * Initializes a new {@link AggregateBasicStats} instance.
         *
         * @param interval the interval in milliseconds over which average
         * values will be calculated.
         * @param children a reference to the map which holds the statistics to
         * aggregate.
         */
        AggregateBasicStats(int interval, Map<Long, ? extends T> children)
        {
            super(interval, -1);
            this.children = children;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void packetProcessed(int length, long now, boolean rtp)
        {
            // A hack to make RTCP packets count towards the aggregate packet
            // rate.
            super.packetProcessed(length, now, true);
        }
    }

    /**
     * An {@link BasicSendStreamStats} implementation which aggregates values for
     * a collection of {@link BasicSendStreamStats} instances.
     */
    private class AggregateBasicSendStats
        extends AggregateBasicStats<BasicSendStreamStats>
        implements BasicSendStreamStats
    {
        /**
         * Initializes a new {@link AggregateBasicStats} instance.
         *
         * @param interval the interval in milliseconds over which average
         * values will
         * be calculated.
         * @param children a reference to the map which holds the statistics to
         * aggregate.
         */
        AggregateBasicSendStats(
                int interval,
                Map<Long, ? extends BasicSendStreamStats> children)
        {
            super(interval, children);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public double getLossRate()
        {
            double sum = 0;
            int count = 0;
            for (BasicSendStreamStats child : children.values())
            {
                double fractionLoss = child.getLossRate();
                if (fractionLoss >= 0)
                {
                    sum += fractionLoss;
                    count++;
                }
            }
            return count != 0 ? sum/count : 0;
        }
    }

    /**
     * An {@link BasicReceiveStreamStats} implementation which aggregates values
     * for a collection of {@link BasicReceiveStreamStats} instances.
     */
    private class AggregateBasicReceiveStats
        extends AggregateBasicStats<BasicReceiveStreamStats>
        implements BasicReceiveStreamStats
    {
        /**
         * Initializes a new {@link AggregateBasicStats} instance.
         *
         * @param interval the interval in milliseconds over which average
         * values will
         * be calculated.
         * @param children a reference to the map which holds the statistics to
         */
        AggregateBasicReceiveStats(
                int interval,
                Map<Long, ? extends BasicReceiveStreamStats> children)
        {
            super(interval, children);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getPacketsLost()
        {
            long lost = 0;
            for (BasicReceiveStreamStats child : children.values())
            {
                lost += child.getPacketsLost();
            }
            return lost;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getCurrentPackets()
        {
            long packets = 0;
            for (BasicReceiveStreamStats child : children.values())
            {
                packets += child.getCurrentPackets();
            }
            return packets;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getCurrentPacketsLost()
        {
            long packetsLost = 0;
            for (BasicReceiveStreamStats child : children.values())
            {
                packetsLost += child.getCurrentPacketsLost();
            }
            return packetsLost;
        }
    }
}
