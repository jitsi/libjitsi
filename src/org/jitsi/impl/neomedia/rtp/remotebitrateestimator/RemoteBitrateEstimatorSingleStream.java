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
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import java.util.*;

import net.sf.fmj.media.rtp.util.*;

import org.ice4j.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.Logger;

/**
 * webrtc/modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.cc
 * webrtc/modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.h
 *
 * @author Lyubomir Marinov
 * @author George Politis
 */
public class RemoteBitrateEstimatorSingleStream
    implements RemoteBitrateEstimator
{
    /**
     * The <tt>Logger</tt> used by the
     * <tt>RemoteBitrateEstimatorSingleStream</tt> class and its instances for
     * logging output.
     */
    private static final Logger logger
        = Logger.getLogger(RemoteBitrateEstimatorSingleStream.class);

    static final double kTimestampToMs = 1.0 / 90.0;

    private final Object critSect = new Object();

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * {@code incomingPacket}.
     */
    private final long[] deltas = new long[3];

    private final RateStatistics incomingBitrate
        = new RateStatistics(kBitrateWindowMs, 8000F);

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * {@code updateEstimate} by promoting the {@code RateControlInput}
     * instance from a local variable to a field and reusing the same instance
     * across method invocations. (Consequently, the default values used to
     * initialize the field are of no importance because they will be
     * overwritten before they are actually used.)
     */
    private final RateControlInput input
        = new RateControlInput(BandwidthUsage.kBwNormal, 0L, 0D);

    private long lastProcessTime = -1L;

    private final RemoteBitrateObserver observer;

    private final Map<Integer,Detector> overuseDetectors = new HashMap<>();

    private long processIntervalMs = kProcessIntervalMs;

    private final AimdRateControl remoteRate = new AimdRateControl();

    /**
     * The set of synchronization source identifiers (SSRCs) currently being
     * received. Represents an unmodifiable copy/snapshot of the current keys of
     * {@link #overuseDetectors} suitable for public access and introduced for
     * the purposes of reducing the number of allocations and the effects of
     * garbage collection.
     */
    private Collection<Integer> ssrcs;

    public RemoteBitrateEstimatorSingleStream(RemoteBitrateObserver observer)
    {
        this.observer = observer;
    }

    private long getExtensionTransmissionTimeOffset(RTPPacket header)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLatestEstimate()
    {
        long bitrateBps;

        synchronized (critSect)
        {
            if (remoteRate.isValidEstimate())
            {
                if (getSsrcs().isEmpty())
                    bitrateBps = 0L;
                else
                    bitrateBps = remoteRate.getLatestEstimate();
            }
            else
            {
                bitrateBps = -1L;
            }
        }
        return bitrateBps;
    }

    @Override
    public Collection<Integer> getSsrcs()
    {
        synchronized (critSect)
        {
            if (ssrcs == null)
            {
                ssrcs
                    = Collections.unmodifiableCollection(
                            new ArrayList<>(overuseDetectors.keySet()));
            }
            return ssrcs;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incomingPacket(RawPacket pkt)
    {
        Integer ssrc_ = pkt.getSSRC();
        long nowMs = System.currentTimeMillis();

        synchronized (critSect)
        {
        // XXX The variable naming is chosen to keep the source code close to
        // the original.
        Detector it = overuseDetectors.get(ssrc_);

        if (it == null)
        {
            // This is a new SSRC. Adding to map.
            // TODO(holmer): If the channel changes SSRC the old SSRC will still
            // be around in this map until the channel is deleted. This is OK
            // since the callback will no longer be called for the old SSRC.
            // This will be automatically cleaned up when we have one
            // RemoteBitrateEstimator per REMB group.
            it = new Detector(nowMs, new OverUseDetectorOptions(), true);
            overuseDetectors.put(ssrc_, it);
            ssrcs = null;
        }

        // XXX The variable naming is chosen to keep the source code close to
        // the original.
        Detector estimator = it;

        estimator.lastPacketTimeMs = nowMs;
        this.incomingBitrate.update(pkt.getPayloadLength(), nowMs);

        BandwidthUsage priorState = estimator.detector.getState();
        long[] deltas = this.deltas;

        /* long timestampDelta */ deltas[0] = 0;
        /* long timeDelta */ deltas[1] = 0;
        /* int sizeDelta */ deltas[2] = 0;

        if (estimator.interArrival.computeDeltas(
                pkt.getTimestamp(),
                System.currentTimeMillis(),
                pkt.getPayloadLength(),
                deltas))
        {
            double timestampDeltaMs
                = /* timestampDelta */ deltas[0] * kTimestampToMs;

            estimator.estimator.update(
                    /* timeDelta */ deltas[1],
                    timestampDeltaMs,
                    /* sizeDelta */ (int) deltas[2],
                    estimator.detector.getState());
            estimator.detector.detect(
                    estimator.estimator.getOffset(),
                    timestampDeltaMs,
                    estimator.estimator.getNumOfDeltas(),
                    nowMs);

            if (logger.isTraceEnabled())
            {
                logger.trace("rbess_delay_estimated" +
                    "," + nowMs +
                    "," + (deltas[1] - timestampDeltaMs) +
                    "," + estimator.estimator.getOffset() +
                    "," + estimator.detector.getState() +
                    "," + observer.hashCode());
            }
        }

        boolean updateEstimate = false;
        if (lastProcessTime < 0L
            || lastProcessTime + processIntervalMs - nowMs <= 0L)
        {
            updateEstimate = true;
        }
        else if (estimator.detector.getState() == BandwidthUsage.kBwOverusing)
        {
            long incomingBitrateBps = this.incomingBitrate.getRate(nowMs);

            if (priorState != BandwidthUsage.kBwOverusing
                    || remoteRate.isTimeToReduceFurther(
                            nowMs,
                            incomingBitrateBps))
            {
                // The first overuse should immediately trigger a new estimate.
                // We also have to update the estimate immediately if we are
                // overusing and the target bitrate is too high compared to what
                // we are receiving.
                updateEstimate = true;
            }
        }

        if (updateEstimate)
        {
            updateEstimate(nowMs);
            lastProcessTime = nowMs;

            if (logger.isTraceEnabled())
            {
                logger.trace("rbess_bitrate_estimated" +
                    "," + nowMs +
                    "," + getLatestEstimate() +
                    "," + observer.hashCode());
            }
        }
        } // synchronized (critSect)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRttUpdate(long avgRttMs, long maxRttMs)
    {
        synchronized (critSect)
        {
            remoteRate.setRtt(avgRttMs);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeStream(int ssrc)
    {
        synchronized (critSect)
        {
            // Ignoring the return value which is the removed OveruseDetector.
            overuseDetectors.remove(ssrc);
            ssrcs = null;
        }
    }

    @Override
    public void setMinBitrate(int minBitrateBps)
    {
        synchronized (critSect)
        {
            remoteRate.setMinBitrate(minBitrateBps);
        }
    }

    /**
     * Triggers a new estimate calculation.
     *
     * @param nowMs
     */
    private void updateEstimate(long nowMs)
    {
        synchronized (critSect)
        {

        BandwidthUsage bwState = BandwidthUsage.kBwNormal;
        double sumVarNoise = 0D;

        for (Iterator<Detector> it = overuseDetectors.values().iterator();
                it.hasNext();)
        {
            Detector overuseDetector = it.next();
            long timeOfLastReceivedPacket = overuseDetector.lastPacketTimeMs;

            if (timeOfLastReceivedPacket >= 0L
                    && nowMs - timeOfLastReceivedPacket > kStreamTimeOutMs)
            {
                // This over-use detector hasn't received packets for
                // kStreamTimeOutMs milliseconds and is considered stale.
                it.remove();
                ssrcs = null;
            }
            else
            {
                sumVarNoise += overuseDetector.estimator.getVarNoise();

                // Make sure that we trigger an over-use if any of the over-use
                // detectors is detecting over-use.
                BandwidthUsage overuseDetectorBwState
                    = overuseDetector.detector.getState();

                if (overuseDetectorBwState.ordinal() > bwState.ordinal())
                    bwState = overuseDetectorBwState;
            }
        }
        // We can't update the estimate if we don't have any active streams.
        if (overuseDetectors.isEmpty())
        {
            remoteRate.reset();
            return;
        }

        double meanNoiseVar = sumVarNoise / (double) overuseDetectors.size();
        RateControlInput input = this.input;

        input.bwState = bwState;
        input.incomingBitRate = incomingBitrate.getRate(nowMs);
        input.noiseVar = meanNoiseVar;
        remoteRate.update(input, nowMs);

        long targetBitrate = remoteRate.updateBandwidthEstimate(nowMs);

        if (remoteRate.isValidEstimate())
        {
            processIntervalMs = remoteRate.getFeedBackInterval();

            RemoteBitrateObserver observer = this.observer;

            if (observer != null)
                observer.onReceiveBitrateChanged(getSsrcs(), targetBitrate);
        }

        } // synchronized (critSect)
    }

    private static class Detector
    {
        public OveruseDetector detector;

        public OveruseEstimator estimator;

        public InterArrival interArrival;

        public long lastPacketTimeMs;

        public Detector(
                long lastPacketTimeMs,
                OverUseDetectorOptions options,
                boolean enableBurstGrouping)
        {
            this.lastPacketTimeMs = lastPacketTimeMs;
            this.interArrival
                = new InterArrival(
                        90 * kTimestampGroupLengthMs,
                        kTimestampToMs,
                        enableBurstGrouping);
            this.estimator = new OveruseEstimator(options);
            this.detector = new OveruseDetector(options);
        }
    }
}
