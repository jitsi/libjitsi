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
import org.jitsi.util.*;

import java.util.concurrent.atomic.*;

/**
 * Media stream statistics implementation per received SSRC.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
public class ReceiveTrackStatsImpl
    extends AbstractTrackStats
    implements ReceiveTrackStats
{
    /**
     * The highest received sequence number.
     */
    private int highestSeq = -1;

    /**
     * The packet loss rate.
     */
    private RateStatistics packetLossRate;

    /**
     * The total number of lost packets.
     */
    private AtomicLong packetsLost = new AtomicLong();


    /**
     * Initializes a new instance.
     * @param interval the interval in milliseconds over which average bit- and
     * packet-rates will be computed.
     */
    ReceiveTrackStatsImpl(int interval, long ssrc)
    {
        super(interval, ssrc);
        packetLossRate = new RateStatistics(interval, 1000F);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getPacketsLost()
    {
        return packetsLost.get();
    }


    /**
     * Notifies this instance that an RTP packet with a given length and
     * sequence number was received.
     * @param seq the RTP sequence number of the packet.
     * @param length the length in bytes of the packet.
     */
    public void rtpPacketReceived(int seq, int length)
    {
        long now = System.currentTimeMillis();

        // update the bit- and packet-rate
        super.packetProcessed(length, now, true);

        if (highestSeq == -1)
        {
            highestSeq = seq;
            return;
        }

        // Now check for lost packets.
        int diff = RTPUtils.getSequenceNumberDelta(seq, highestSeq);
        if (diff <= 0)
        {
            // RFC3550 says that all packets should be counted as received.
            // However, we want to *not* count retransmitted packets as received,
            // otherwise the calculated loss rate will be close to zero as long
            // as all missing packets are requested and retransmitted.
            // Here we differentiate between packets received out of order and
            // those that were retransmitted.
            // Note that this can be avoided if retransmissions always use the
            // RTX format and "de-RTX-ed" packets are not fed to this instance.
            if (diff > -10)
            {
                packetsLost.addAndGet(-1);
                packetLossRate.update(-1, now);
            }
        }
        else
        {
            // A newer packet.
            highestSeq = seq;

            // diff = 1 is the "normal" case (i.e. we received the very next
            // packet).
            if (diff > 1)
            {
                packetsLost.addAndGet(diff - 1);
                packetLossRate.update(diff - 1, now);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCurrentPacketsLost()
    {
        return packetLossRate.getAccumulatedCount();
    }

    /**
     * Notifies this instance that an RTCP packet with a specific length was
     * received.
     * @param length the length in bytes.
     */
    public void rtcpPacketReceived(int length)
    {
        super.packetProcessed(length, System.currentTimeMillis(), false);
    }

    /**
     * {@inheritDoc}
     *
     * @return the loss rate in the last interval.
     */
    @Override
    public double getLossRate()
    {
        // This is not thread safe and the counters might change between the
        // two function calls below, but the result would be just a wrong
        // value for the packet loss rate, and likely just off by a little.
        long lost = getCurrentPacketsLost();
        long expected = lost + getCurrentPackets();

        return expected == 0 ? 0 : (lost / expected);
    }
}
