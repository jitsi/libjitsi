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
package org.jitsi.impl.neomedia.rtp.sendsidebandwidthestimation;

import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;

import java.util.*;

/**
 * Implements the send-side bandwidth estimation described in
 * https://tools.ietf.org/html/draft-ietf-rmcat-gcc-01
 * Heavily based on code from webrtc.org (send_side_bandwidth_estimation.cc,
 * commit ID 7ad9e661f8a035d49d049ccdb87c77ae8ecdfa35).
 *
 * @author Boris Grozev
 */
class SendSideBandwidthEstimation
    extends RTCPPacketListenerAdapter
    implements BandwidthEstimator
{
    /**
     * send_side_bandwidth_estimation.cc
     */
    private static final int kBweIncreaseIntervalMs = 1000;

    /**
     * send_side_bandwidth_estimation.cc
     */
    private static final long kBweDecreaseIntervalMs = 300;

    /**
     * send_side_bandwidth_estimation.cc
     */
    private static final int kDefaultMinBitrateBps = 10000;

    /**
     * send_side_bandwidth_estimation.cc
     */
    private static final int kDefaultMaxBitrateBps = 1000000000;

    /**
     * send_side_bandwidth_estimation.cc
     */
    private static final int kStartPhaseMs = 2000;

    /**
     * send_side_bandwidth_estimation.cc
     */
    private static final int kLimitNumPackets = 20;

    /**
     * The <tt>Logger</tt> used by the {@link SendSideBandwidthEstimation} class
     * and its instances for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(SendSideBandwidthEstimation.class);

    /**
     * The {@link TimeSeriesLogger} to be used by this instance to print time
     * series.
     */
    private static final TimeSeriesLogger timeSeriesLogger
            = TimeSeriesLogger.getTimeSeriesLogger(
                    SendSideBandwidthEstimation.class);

    /**
     * send_side_bandwidth_estimation.h
     */
    private long first_report_time_ms_ = -1;

    /**
     * send_side_bandwidth_estimation.h
     */
    private int lost_packets_since_last_loss_update_Q8_ = 0;

    /**
     * send_side_bandwidth_estimation.h
     */
    private int expected_packets_since_last_loss_update_ = 0;

    /**
     * send_side_bandwidth_estimation.h
     */
    private boolean has_decreased_since_last_fraction_loss_ = false;

    /**
     * send_side_bandwidth_estimation.h
     *
     * uint8_t last_fraction_loss_;
     */
    private int last_fraction_loss_ = 0;

    /**
     * send_side_bandwidth_estimation.h
     */
    private long time_last_receiver_block_ms_ = -1;

    /**
     * send_side_bandwidth_estimation.h
     */
    private int min_bitrate_configured_ = kDefaultMinBitrateBps;

    /**
     * send_side_bandwidth_estimation.h
     */
    private int max_bitrate_configured_ = kDefaultMaxBitrateBps;

    /**
     * send_side_bandwidth_estimation.h
     */
    private long time_last_decrease_ms_= 0;

    /**
     * send_side_bandwidth_estimation.h
     */
    private long bwe_incoming_ = 0;

    /**
     * send_side_bandwidth_estimation.h
     */
    private long bitrate_;

    /**
     * send_side_bandwidth_estimation.h
     */
    private Deque<Pair<Long>> min_bitrate_history_ = new LinkedList<>();

    /**
     * The {@link DiagnosticContext} of this instance.
     */
    private final DiagnosticContext diagnosticContext;

    private final List<BandwidthEstimator.Listener> listeners
        = new LinkedList<>();

    /**
     * The {@link MediaStream} for this {@link SendSideBandwidthEstimation}.
     */
    private final MediaStream mediaStream;

    SendSideBandwidthEstimation(MediaStreamImpl stream, long startBitrate)
    {
        mediaStream = stream;
        diagnosticContext = stream.getDiagnosticContext();
        setBitrate(startBitrate);
    }

    /**
     * bool SendSideBandwidthEstimation::IsInStartPhase(int64_t now_ms)
     */
    private synchronized boolean isInStartPhase(long now)
    {
        return first_report_time_ms_ == -1 ||
                now - first_report_time_ms_ < kStartPhaseMs;
    }

    /**
     * int SendSideBandwidthEstimation::CapBitrateToThresholds
     */
    private synchronized long capBitrateToThresholds(long bitrate)
    {
        if (bwe_incoming_ > 0 && bitrate > bwe_incoming_)
        {
            bitrate = bwe_incoming_;
        }
        if (bitrate > max_bitrate_configured_)
        {
            bitrate = max_bitrate_configured_;
        }
        if (bitrate < min_bitrate_configured_)
        {
            bitrate = min_bitrate_configured_;
        }
        return bitrate;
    }

    /**
     * void SendSideBandwidthEstimation::UpdateEstimate(int64_t now_ms)
     */
    protected synchronized void updateEstimate(long now)
    {
        long bitrate = bitrate_;

        // We trust the REMB during the first 2 seconds if we haven't had any
        // packet loss reported, to allow startup bitrate probing.
        if (last_fraction_loss_ == 0 && isInStartPhase(now) &&
                bwe_incoming_ > bitrate)
        {
            setBitrate(capBitrateToThresholds(bwe_incoming_));
            min_bitrate_history_.clear();
            min_bitrate_history_.addLast(new Pair<>(now, bitrate_));
            return;
        }
        updateMinHistory(now);
        // Only start updating bitrate when receiving receiver blocks.
        // TODO(pbos): Handle the case when no receiver report is received for a very
        // long time.
        if (time_last_receiver_block_ms_ != -1)
        {
            if (last_fraction_loss_ <= 5)
            {
                // Loss < 2%: Increase rate by 8% of the min bitrate in the last
                // kBweIncreaseIntervalMs.
                // Note that by remembering the bitrate over the last second one can
                // rampup up one second faster than if only allowed to start ramping
                // at 8% per second rate now. E.g.:
                //   If sending a constant 100kbps it can rampup immediatly to 108kbps
                //   whenever a receiver report is received with lower packet loss.
                //   If instead one would do: bitrate_ *= 1.08^(delta time), it would
                //   take over one second since the lower packet loss to achieve 108kbps.
                bitrate = (long) (min_bitrate_history_.getFirst().second * 1.08 + 0.5);

                // Add 1 kbps extra, just to make sure that we do not get stuck
                // (gives a little extra increase at low rates, negligible at higher
                // rates).
                bitrate += 1000;

                if (timeSeriesLogger.isTraceEnabled())
                {
                    timeSeriesLogger.trace(diagnosticContext
                            .makeTimeSeriesPoint("loss_estimate", now)
                            .addField("action", "increase")
                            .addField("last_fraction_loss", last_fraction_loss_)
                            .addField("bitrate_bps", bitrate));
                }

            }
            else if (last_fraction_loss_ <= 26)
            {
                // Loss between 2% - 10%: Do nothing.

                if (timeSeriesLogger.isTraceEnabled())
                {
                    timeSeriesLogger.trace(diagnosticContext
                            .makeTimeSeriesPoint("loss_estimate", now)
                            .addField("action", "keep")
                            .addField("last_fraction_loss", last_fraction_loss_)
                            .addField("bitrate_bps", bitrate));
                }
            }
            else
            {
                // Loss > 10%: Limit the rate decreases to once a kBweDecreaseIntervalMs +
                // rtt.
                if (!has_decreased_since_last_fraction_loss_ &&
                        (now - time_last_decrease_ms_) >=
                                (kBweDecreaseIntervalMs + getRtt()))
                {
                    time_last_decrease_ms_ = now;

                    // Reduce rate:
                    //   newRate = rate * (1 - 0.5*lossRate);
                    //   where packetLoss = 256*lossRate;
                    bitrate = (long) (
                        (bitrate * (512 - last_fraction_loss_)) / 512.0);
                    has_decreased_since_last_fraction_loss_ = true;

                    if (timeSeriesLogger.isTraceEnabled())
                    {
                        timeSeriesLogger.trace(diagnosticContext
                                .makeTimeSeriesPoint("loss_estimate", now)
                                .addField("action", "decrease")
                                .addField("last_fraction_loss", last_fraction_loss_)
                                .addField("bitrate_bps", bitrate));
                    }
                }
            }
        }
        setBitrate(capBitrateToThresholds(bitrate));
    }

    /**
     * void SendSideBandwidthEstimation::UpdateReceiverBlock
     */
    synchronized void updateReceiverBlock(
            long fraction_lost, long number_of_packets, long now)
    {
        if (first_report_time_ms_ == -1)
        {
            first_report_time_ms_ = now;
        }

        // Check sequence number diff and weight loss report
        if (number_of_packets > 0)
        {
            // Calculate number of lost packets.
            long num_lost_packets_Q8 = fraction_lost * number_of_packets;
            // Accumulate reports.
            lost_packets_since_last_loss_update_Q8_ += num_lost_packets_Q8;
            expected_packets_since_last_loss_update_ += number_of_packets;

            // Don't generate a loss rate until it can be based on enough packets.
            if (expected_packets_since_last_loss_update_ < kLimitNumPackets)
                return;

            has_decreased_since_last_fraction_loss_ = false;
            last_fraction_loss_ =
                    lost_packets_since_last_loss_update_Q8_ /
                    expected_packets_since_last_loss_update_;

            // Reset accumulators.
            lost_packets_since_last_loss_update_Q8_ = 0;
            expected_packets_since_last_loss_update_ = 0;
        }

        time_last_receiver_block_ms_ = now;
        updateEstimate(now);
    }

    /**
     * void SendSideBandwidthEstimation::UpdateMinHistory(int64_t now_ms)
     */
    private synchronized void updateMinHistory(long now_ms)
    {
        // Remove old data points from history.
        // Since history precision is in ms, add one so it is able to increase
        // bitrate if it is off by as little as 0.5ms.
        while (!min_bitrate_history_.isEmpty() &&
                now_ms - min_bitrate_history_.getFirst().first + 1 >
                        kBweIncreaseIntervalMs)
        {
            min_bitrate_history_.removeFirst();
        }

        // Typical minimum sliding-window algorithm: Pop values higher than current
        // bitrate before pushing it.
        while (!min_bitrate_history_.isEmpty() &&
                bitrate_ <= min_bitrate_history_.getLast().second)
        {
            min_bitrate_history_.removeLast();
        }

        min_bitrate_history_.addLast(new Pair<>(now_ms, bitrate_));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void updateReceiverEstimate(long bandwidth)
    {
        bwe_incoming_ = bandwidth;
        setBitrate(capBitrateToThresholds(bitrate_));
    }

    /**
     * void SendSideBandwidthEstimation::SetMinMaxBitrate
     */
    synchronized void setMinMaxBitrate(int min_bitrate, int max_bitrate)
    {
        min_bitrate_configured_ = Math.max(min_bitrate, kDefaultMinBitrateBps);
        if (max_bitrate > 0)
        {
            max_bitrate_configured_ =
                    Math.max(min_bitrate_configured_, max_bitrate);
        }
        else
        {
            max_bitrate_configured_ = kDefaultMaxBitrateBps;
        }
    }

    /**
     * Sets the value of {@link #bitrate_}.
     * @param newValue the value to set
     */
    private synchronized void setBitrate(long newValue)
    {
        long oldValue = bitrate_;
        bitrate_ = newValue;
        if (oldValue != bitrate_)
        {
            fireBandwidthEstimationChanged(oldValue, newValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLatestEstimate()
    {
        return bitrate_;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLatestREMB()
    {
        return bwe_incoming_;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLatestFractionLoss()
    {
        return last_fraction_loss_;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void removeListener(Listener listener)
    {
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rembReceived(RTCPREMBPacket remb)
    {
        updateReceiverEstimate(remb.getBitrate());
    }

    /**
     * Returns the last calculated RTT to the endpoint.
     * @return the last calculated RTT to the endpoint.
     */
    private synchronized long getRtt()
    {
        long rtt = mediaStream.getMediaStreamStats().getSendStats().getRtt();
        if (rtt < 0 || rtt > 1000)
        {
            logger.warn("RTT not calculated, or has a suspiciously high value ("
                + rtt + "). Using the default of 100ms.");
            rtt = 100;
        }

        return rtt;
    }

    /**
     * Notifies registered listeners that the estimation of the available
     * bandwidth has changed.
     * @param oldValue the old value (in bps).
     * @param newValue the new value (in bps).
     */
    private synchronized void fireBandwidthEstimationChanged(
            long oldValue, long newValue)
    {
        for (BandwidthEstimator.Listener listener : listeners)
        {
            listener.bandwidthEstimationChanged(newValue);
        }
    }

    private class Pair<T>
    {
        T first;
        T second;
        Pair(T a, T b)
        {
            first = a;
            second = b;
        }
    }
}
