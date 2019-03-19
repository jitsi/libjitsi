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
import org.jitsi.util.*;

/**
 * Media stream statistics implementation per send SSRC.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
public class SendTrackStatsImpl
    extends AbstractTrackStats
    implements SendTrackStats
{
    /**
     * The highest sent sequence number.
     */
    private int highestSeq = -1;

    /**
     * Rate of packet that we did not send (i.e. were lost on their way to us)
     */
    RateStatistics packetsNotSentRate = new RateStatistics(1000, 1000F);

    /**
     * The fraction lost reported in the most recently received RTCP Receiver
     * Report.
     */
    private double fractionLost = -1d;

    /**
     * The time at which {@link #fractionLost} was last updated.
     */
    private long fractionLostLastUpdate = -1;

    /**
     * Initializes a new instance.
     * @param interval the interval in milliseconds over which average bit- and
     * packet-rates will be computed.
     */
    SendTrackStatsImpl(int interval, long ssrc)
    {
        super(interval, ssrc);
    }

    /**
     * Notifies this instance that an RTP packet with a particular sequence
     * number was sent (or is about to be sent).
     * @param seq the RTP sequence number.
     * @param length the length in bytes.
     */
    void rtpPacketSent(int seq, int length)
    {
        long now = System.currentTimeMillis();

        // update the bit- and packet-rate
        super.packetProcessed(length, now, true);

        if (highestSeq == -1)
        {
            highestSeq = seq;
            return;
        }

        // We monitor the sequence numbers of sent packets in order to
        // calculate the actual number of lost packets.
        // If we are forwarding the stream (as opposed to generating it
        // locally), as is the case in jitsi-videobridge, packets may be lost
        // between the sender and us, and we need to take this into account
        // when calculating packet loss to the receiver.
        int diff = RTPUtils.getSequenceNumberDelta(seq, highestSeq);
        if (diff <= 0)
        {
            // An old packet, already counted as not send. Un-not-send it ;)
            packetsNotSentRate.update(-1, now);
        }
        else
        {
            // A newer packet.
            highestSeq = seq;

            // diff = 1 is the "normal" case (i.e. we received the very next
            // packet).
            if (diff > 1)
            {
                packetsNotSentRate.update(diff - 1, now);
            }
        }

        // update bytes, packets, loss...
    }

    /**
     * {@inheritDoc}
     *
     * Returns an estimation of the loss rate based on the most recent RTCP
     * Receiver Report that we received, and the rate of "non-sent" packets
     * (i.e. in the case of jitsi-videobridge the loss rate from the sender to
     * the bridge).
     */
    @Override
    public double getLossRate()
    {
        long now = System.currentTimeMillis();
        if (fractionLostLastUpdate == -1 || now - fractionLostLastUpdate > 8000)
        {
            // We haven't received a RR recently, so assume no loss.
            return 0;
        }

        // Take into account packets that we did not send
        long packetsNotSent = packetsNotSentRate.getAccumulatedCount(now);
        long packetsSent = packetRate.getAccumulatedCount(now);

        double fractionNotSent
            = (packetsSent+packetsNotSent > 0)
            ? (packetsNotSent / (packetsNotSent+packetsSent))
            : 0;

        return Math.max(0, fractionLost - fractionNotSent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getHighestSent()
    {
        return highestSeq;
    }

    /**
     * Notifies this instance that an RTCP packet with a given length in bytes
     * was sent (or is about to be sent).
     * @param length
     */
    void rtcpPacketSent(int length)
    {
        super.packetProcessed(length, System.currentTimeMillis(), false);
    }

    /**
     * Notifies this instance that an RTCP Receiver Report with a given value
     * for the "fraction lost" field was received.
     * @param fractionLost the value of the "fraction lost" field from an RTCP
     * Receiver Report as an unsigned integer.
     */
    void rtcpReceiverReportReceived(int fractionLost)
    {
        this.fractionLost = fractionLost / 256d;
        this.fractionLostLastUpdate = System.currentTimeMillis();
    }
}
