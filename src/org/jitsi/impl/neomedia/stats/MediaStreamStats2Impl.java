package org.jitsi.impl.neomedia.stats;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.stats.*;
import org.jitsi.util.*;

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
     * The {@link Logger} used by the {@link MediaStreamStats2Impl} class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaStreamStatsImpl.class);

    /**
     * Hold per-SSRC statistics for received streams.
     */
    private final Map<Long,ReceiveTrackStatsImpl> receiveSsrcStats
        = new ConcurrentHashMap<>();

    /**
     * Hold per-SSRC statistics for sent streams.
     */
    private final Map<Long,SendTrackStatsImpl> sendSsrcStats
        = new ConcurrentHashMap<>();

    /**
     * Global (aggregated) statistics for received streams.
     */
    private final AggregateReceiveTrackStats receiveStats
        = new AggregateReceiveTrackStats(INTERVAL, receiveSsrcStats);

    /**
     * Global (aggregated) statistics for sent streams.
     */
    private final AggregateSendTrackStats sendStats
        = new AggregateSendTrackStats(INTERVAL, sendSsrcStats);

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
     * Notifies this instance that an RTP packet with a given SSRC and a given
     * length was retransmitted.
     * @param ssrc the SSRC of the packet.
     * @param length the length in bytes of the packet.
     */
    public void rtpPacketRetransmitted(long ssrc, long length)
    {
        getSendStats(ssrc).rtpPacketRetransmitted(length);
        sendStats.rtpPacketRetransmitted(length);
    }

    /**
     * Notifies this instance that an RTP packet with a given SSRC and a given
     * length was not retransmitted (that is, the remote endpoint requested it,
     * and it was found in the local cache, but it was not retransmitted).
     * @param ssrc the SSRC of the packet.
     * @param length the length in bytes of the packet.
     */
    public void rtpPacketNotRetransmitted(long ssrc, long length)
    {
        getSendStats(ssrc).rtpPacketNotRetransmitted(length);
        sendStats.rtpPacketNotRetransmitted(length);
    }

    /**
     * Notifies this instance that the remote endpoint requested retransmission
     * of a packet with a given SSRC, and it was not found in the local cache.
     * @param ssrc the SSRC of the requested packet.
     */
    public void rtpPacketCacheMiss(long ssrc)
    {
        getSendStats(ssrc).rtpPacketCacheMiss();
        sendStats.rtpPacketCacheMiss();
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
    public ReceiveTrackStats getReceiveStats()
    {
        return receiveStats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SendTrackStats getSendStats()
    {
        return sendStats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReceiveTrackStatsImpl getReceiveStats(long ssrc)
    {
        if (ssrc < 0)
        {
            logger.error("No received stats for an invalid SSRC: " + ssrc);
            // We don't want to lose the data (and trigger an NPE), but at
            // least we collect all invalid SSRC under the value of -1;
            ssrc = -1;
        }

        ReceiveTrackStatsImpl stats = receiveSsrcStats.get(ssrc);
        if (stats == null)
        {
            synchronized (receiveSsrcStats)
            {
                stats = receiveSsrcStats.get(ssrc);
                if (stats == null)
                {
                    stats = new ReceiveTrackStatsImpl(INTERVAL, ssrc);
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
    public SendTrackStatsImpl getSendStats(long ssrc)
    {
        if (ssrc < 0)
        {
            logger.error("No send stats for an invalid SSRC: " + ssrc);
            // We don't want to lose the data (and trigger an NPE), but at
            // least we collect all invalid SSRC under the value of -1;
            ssrc = -1;
        }

        SendTrackStatsImpl stats = sendSsrcStats.get(ssrc);
        if (stats == null)
        {
            synchronized (sendSsrcStats)
            {
                stats = sendSsrcStats.get(ssrc);
                if (stats == null)
                {
                    stats = new SendTrackStatsImpl(INTERVAL, ssrc);
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
    public Collection<? extends SendTrackStats> getAllSendStats()
    {
        return sendSsrcStats.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends ReceiveTrackStats> getAllReceiveStats()
    {
        return receiveSsrcStats.values();
    }

    /**
     * An {@link TrackStats} implementation which aggregates values for
     * a collection of {@link TrackStats} instances.
     */
    private abstract class AggregateTrackStats<T>
        extends AbstractTrackStats
    {
        /**
         * The collection of {@link TrackStats} for which this instance
         * aggregates.
         */
        protected final Map<Long, ? extends T> children;

        /**
         * Initializes a new {@link AggregateTrackStats} instance.
         *
         * @param interval the interval in milliseconds over which average
         * values will be calculated.
         * @param children a reference to the map which holds the statistics to
         * aggregate.
         */
        AggregateTrackStats(int interval, Map<Long, ? extends T> children)
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
     * An {@link SendTrackStats} implementation which aggregates values for
     * a collection of {@link SendTrackStats} instances.
     */
    private class AggregateSendTrackStats
        extends AggregateTrackStats<SendTrackStats>
        implements SendTrackStats
    {
        /**
         * Initializes a new {@link AggregateTrackStats} instance.
         *
         * @param interval the interval in milliseconds over which average
         * values will
         * be calculated.
         * @param children a reference to the map which holds the statistics to
         * aggregate.
         */
        AggregateSendTrackStats(
            int interval,
            Map<Long, ? extends SendTrackStats> children)
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
            for (SendTrackStats child : children.values())
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

        /**
         * {@inheritDoc}
         */
        @Override
        public int getHighestSent()
        {
            return -1;
        }
    }

    /**
     * An {@link ReceiveTrackStats} implementation which aggregates values
     * for a collection of {@link ReceiveTrackStats} instances.
     */
    private class AggregateReceiveTrackStats
        extends AggregateTrackStats<ReceiveTrackStats>
        implements ReceiveTrackStats
    {
        /**
         * Initializes a new {@link AggregateTrackStats} instance.
         *
         * @param interval the interval in milliseconds over which average
         * values will
         * be calculated.
         * @param children a reference to the map which holds the statistics to
         */
        AggregateReceiveTrackStats(
            int interval,
            Map<Long, ? extends ReceiveTrackStats> children)
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
            for (ReceiveTrackStats child : children.values())
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
            for (ReceiveTrackStats child : children.values())
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
            for (ReceiveTrackStats child : children.values())
            {
                packetsLost += child.getCurrentPacketsLost();
            }
            return packetsLost;
        }

        /**
         * {@inheritDoc}
         *
         * @return the loss rate in the last interval.
         */
        @Override
        public double getLossRate()
        {
            long lost = 0;
            long expected = 0;

            for (ReceiveTrackStats child : children.values())
            {
                // This is not thread safe and the counters might change
                // between the two function calls below, but the result would
                // be just a wrong value for the packet loss rate, and likely
                // just off by a little bit.
                long childLost = child.getCurrentPacketsLost();
                expected += childLost + child.getCurrentPackets();
                lost += childLost;
            }

            return expected == 0 ? 0 : (lost / expected);
        }
    }
}
