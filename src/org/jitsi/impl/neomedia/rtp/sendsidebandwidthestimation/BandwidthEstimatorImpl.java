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

import net.sf.fmj.media.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.concurrent.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Implements part of the send-side bandwidth estimation described in
 * https://tools.ietf.org/html/draft-ietf-rmcat-gcc-01
 * Heavily based on code from webrtc.org (bitrate_controller_impl.cc, commit ID
 * 7ad9e661f8a035d49d049ccdb87c77ae8ecdfa35).
 *
 * @author Boris Grozev
 */
public class BandwidthEstimatorImpl
    extends RTCPReportAdapter
    implements BandwidthEstimator, RecurringProcessible
{
    /**
     * The interval at which {@link #process()} should be called, in
     * milliseconds.
     */
    private static final int PROCESS_INTERVAL_MS = 25;

    /**
     * The minimum value to be output by this estimator, in bits per second.
     */
    private final static int MIN_BITRATE_BPS = 30000;

    /**
     * The maximum value to be output by this estimator, in bits per second.
     */
    private final static int MAX_BITRATE_BPS = 20 * 1000 * 1000;

    /**
     * The initial value of the estimation, in bits per secod.
     */
    private static final int START_BITRATE_BPS = 300000;

    /**
     * bitrate_controller_impl.h
     */
    private Map<Long,Long> ssrc_to_last_received_extended_high_seq_num_
        = new ConcurrentHashMap<>();

    private long lastUpdateTime = -1;

    /**
     * bitrate_controller_impl.h
     */
    private final SendSideBandwidthEstimation sendSideBandwidthEstimation;

    /**
     * The {@link MediaStream} which owns this instance.
     */
    private final MediaStream mediaStream;

    /**
     * Initializes a new instance which is to belong to a particular
     * {@link MediaStream}.
     * @param stream the {@link MediaStream}.
     */
    public BandwidthEstimatorImpl(MediaStream stream)
    {
        mediaStream = stream;
        sendSideBandwidthEstimation
            = new SendSideBandwidthEstimation(stream, START_BITRATE_BPS);
        sendSideBandwidthEstimation.setMinMaxBitrate(
                MIN_BITRATE_BPS, MAX_BITRATE_BPS);

        // Hook us up to receive Report Blocks and REMBs.
        MediaStreamStats stats = stream.getMediaStreamStats();
        stats.addRembListener(sendSideBandwidthEstimation);
        stats.getRTCPReports().addRTCPReportListener(this);
    }

    /**
     * {@inheritDoc}
     *
     * bitrate_controller_impl.cc
     * BitrateControllerImpl::OnReceivedRtcpReceiverReport
     */
    @Override
    public void rtcpReportReceived(RTCPReport report)
    {
        if (report == null || report.getFeedbackReports() == null
                || report.getFeedbackReports().isEmpty())
        {
            return;
        }

        long total_number_of_packets = 0;
        long fraction_lost_aggregate = 0;

        // Compute the a weighted average of the fraction loss from all report
        // blocks.
        for (RTCPFeedback feedback : report.getFeedbackReports())
        {
            long ssrc = feedback.getSSRC();
            long extSeqNum = feedback.getXtndSeqNum();

            Long lastEHSN
                    = ssrc_to_last_received_extended_high_seq_num_.get(ssrc);
            if (lastEHSN == null)
            {
                lastEHSN = extSeqNum;
                continue;
            }

            ssrc_to_last_received_extended_high_seq_num_.put(ssrc, extSeqNum);

            if (lastEHSN >= extSeqNum)
            {
                //the first report for this SSRC
                continue;
            }

            long number_of_packets = extSeqNum - lastEHSN;

            fraction_lost_aggregate
                    += number_of_packets * feedback.getFractionLost();
            total_number_of_packets += number_of_packets;
        }

        if (total_number_of_packets == 0)
        {
            fraction_lost_aggregate = 0;
        }
        else
        {
            fraction_lost_aggregate = (fraction_lost_aggregate +
                    total_number_of_packets / 2) / total_number_of_packets;
        }
        if (fraction_lost_aggregate > 255)
        {
            return;
        }

        synchronized (sendSideBandwidthEstimation)
        {
            lastUpdateTime = System.currentTimeMillis();
            sendSideBandwidthEstimation.updateReceiverBlock(
                    fraction_lost_aggregate,
                    total_number_of_packets,
                    lastUpdateTime);
        }
    }

    @Override
    public void addListener(Listener listener)
    {
        sendSideBandwidthEstimation.addListener(listener);
    }

    @Override
    public void removeListener(Listener listener)
    {
        sendSideBandwidthEstimation.removeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimeUntilNextProcess()
    {
        return
                (lastUpdateTime < 0L)
                        ? 0L
                        : lastUpdateTime + PROCESS_INTERVAL_MS
                            - System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long process()
    {
        synchronized (sendSideBandwidthEstimation)
        {
            lastUpdateTime = System.currentTimeMillis();
            sendSideBandwidthEstimation.updateEstimate(lastUpdateTime);
        }
        return 0;
    }
}
