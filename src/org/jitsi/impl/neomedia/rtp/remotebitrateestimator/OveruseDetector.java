/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import java.util.*;

/**
 * webrtc/webrtc/modules/remote_bitrate_estimator/overuse_detector.cc
 * webrtc/webrtc/modules/remote_bitrate_estimator/overuse_detector.h
 *
 * @author Lyubomir Marinov
 */
public class OveruseDetector
{
    private static class FrameSample
    {
        public long completeTimeMs = -1L;

        public long size = 0L;

        public long timestamp = -1L;

        public long timestampMs = -1L;

        /**
         * Assigns the values of the fields of <tt>source</tt> to the respective
         * fields of this <tt>FrameSample</tt>.
         *
         * @param source the <tt>FrameSample</tt> the values of the fields of
         * which are to be assigned to the respective fields of this
         * <tt>FrameSample</tt>
         */
        public void copy(FrameSample source)
        {
            completeTimeMs = source.completeTimeMs;
            size = source.size;
            timestamp = source.timestamp;
            timestampMs = source.timestampMs;
        }
    }

    private static final int kMinFramePeriodHistoryLength = 60;

    private static final int kOverUsingTimeThreshold = 100;

    /**
     * Creates and returns a deep copy of a <tt>double</tt> two-dimensional
     * matrix.
     *
     * @param matrix the <tt>double</tt> two-dimensional matrix to create and
     * return a deep copy of
     * @return a deep copy of <tt>matrix</tt>
     */
    private static double[][] clone(double[][] matrix)
    {
        int length = matrix.length;
        double[][] clone;

        clone = new double[length][];
        for (int i = 0; i < length; i++)
            clone[i] = matrix[i].clone();
        return clone;
    }

    /**
     * Returns <tt>true</tt> if <tt>timestamp</tt> represent a time which is
     * later than <tt>prevTimestamp</tt>.
     *
     * @param timestamp
     * @param prevTimestamp
     * @return
     */
    private static boolean isInOrderTimestamp(
            long timestamp,
            long prevTimestamp)
    {
        long timestampDiff = timestamp - prevTimestamp;

        // Assume that a diff this big must be due to reordering. Don't update
        // with reordered samples.
        return (timestampDiff < 0x80000000L);
    }

    private double avgNoise;

    private final FrameSample currentFrame = new FrameSample();

    private final double[][] E;

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * <tt>updateKalman</tt>.
     */
    private final double[] Eh = new double[2];

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * <tt>updateKalman</tt>.
     */
    private final double[] h = new double[2];
    
    private BandwidthUsage hypothesis = BandwidthUsage.kBwNormal;

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * <tt>updateKalman</tt>.
     */
    private final double[][] IKh = new double[][] { { 0, 0 }, { 0, 0 } };

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * <tt>updateKalman</tt>.
     */
    private final double[] K = new double[2];

    private int numOfDeltas;

    private double offset;

    private final OverUseDetectorOptions options;

    private int overUseCounter;

    private long packetTimeMs;

    private final FrameSample prevFrame = new FrameSample();

    private double prevOffset;

    private final double[] processNoise;

    private double slope;

    private double threshold;

    /**
     * The <tt>long</tt> <tt>tDelta</tt> and <tt>double</tt> <tt>tsDelta</tt>
     * output parameters of the method <tt>getTimeDeltas</tt>. Cached for the
     * purposes of reducing the effects of allocation and garbage collection.
     */
    private final double[] timeDeltas = new double[2];

    private double timeOverUsing = -1D;

    private final List<Double> tsDeltaHist = new LinkedList<Double>();

    private double varNoise;

    public OveruseDetector()
    {
        this(new OverUseDetectorOptions());
    }

    public OveruseDetector(OverUseDetectorOptions options)
    {
        if (options == null)
            throw new NullPointerException("options");

        this.options = options;

        avgNoise = this.options.initialAvgNoise;
        offset = this.options.initialOffset;
        processNoise = this.options.initialProcessNoise.clone();
        slope = this.options.initialSlope;
        threshold = this.options.initialThreshold;
        varNoise = this.options.initialVarNoise;
        E = clone(this.options.initialE);
    }

    private BandwidthUsage detect(double tsDelta)
    {
        if (numOfDeltas < 2)
            return BandwidthUsage.kBwNormal;

        double T = Math.min(numOfDeltas, 60) * offset;

        if (Math.abs(T) > threshold)
        {
            if (offset > 0)
            {
                if (timeOverUsing == -1D)
                {
                    // Initialize the timer. Assume that we've been over-using
                    // half of the time since the previous sample.
                    timeOverUsing = tsDelta / 2;
                }
                else
                {
                    // Increment timer
                    timeOverUsing += tsDelta;
                }
                overUseCounter++;
                if (timeOverUsing > kOverUsingTimeThreshold
                        && overUseCounter > 1)
                {
                    if (offset >= prevOffset)
                    {
                        timeOverUsing = 0D;
                        overUseCounter = 0;
                        hypothesis = BandwidthUsage.kBwOverusing;
                    }
                }
            }
            else
            {
                timeOverUsing = -1D;
                overUseCounter = 0;
                hypothesis = BandwidthUsage.kBwUnderusing;
            }
        }
        else
        {
            timeOverUsing = -1D;
            overUseCounter = 0;
            hypothesis = BandwidthUsage.kBwNormal;
        }
        return hypothesis;
    }

    private double getCurrentDrift()
    {
        return 1D;
    }

    public double getNoiseVar()
    {
        return varNoise;
    }

    public long getPacketTimeMs()
    {
        return packetTimeMs;
    }

    public BandwidthUsage getState()
    {
        return hypothesis;
    }

    private void getTimeDeltas(
            FrameSample currentFrame,
            FrameSample prevFrame,
            double[] timeDeltas)
    {
        long tDelta;
        double tsDelta;

        numOfDeltas++;
        if (numOfDeltas > 1000)
            numOfDeltas = 1000;
        if (currentFrame.timestampMs == -1L)
        {
            long timestampDiff = currentFrame.timestamp - prevFrame.timestamp;

            tsDelta = timestampDiff / 90D;
        }
        else
        {
            tsDelta = currentFrame.timestampMs - prevFrame.timestampMs;
        }
        tDelta = currentFrame.completeTimeMs - prevFrame.completeTimeMs;

        timeDeltas[0] = tDelta;
        timeDeltas[1] = tsDelta;
    }

    private boolean isPacketInOrder(long timestamp, long timestampMs)
    {
        if (currentFrame.timestampMs == -1L && currentFrame.timestamp > -1L)
        {
            return isInOrderTimestamp(timestamp, currentFrame.timestamp);
        }
        else if (currentFrame.timestampMs > 0L)
        {
            // Using timestamps converted to NTP time.
            return timestampMs > currentFrame.timestampMs;
        }
        // This is the first packet.
        return true;
    }

    public void setPacketTimeMs(long packetTimeMs)
    {
        this.packetTimeMs = packetTimeMs;
    }

    public void setRateControlRegion(RateControlRegion region)
    {
        switch (region)
        {
        case kRcMaxUnknown:
        {
            threshold = options.initialThreshold;
            break;
        }
        case kRcAboveMax:
        case kRcNearMax:
        {
            threshold = options.initialThreshold / 2D;
            break;
        }
        }
    }

    /**
     * Prepares the overuse detector to start using timestamps in milliseconds
     * instead of 90 kHz timestamps.
     */
    private void switchTimeBase()
    {
        currentFrame.size = 0L;
        currentFrame.completeTimeMs = -1L;
        currentFrame.timestamp = -1L;
        prevFrame.copy(currentFrame);
    }

    public void update(
            int packetSize,
            long timestampMs,
            long rtpTimestamp,
            long arrivalTimeMs)
    {
        boolean newTimestamp = (rtpTimestamp != currentFrame.timestamp);

        if (timestampMs >= 0)
        {
            if (prevFrame.timestampMs == -1L && currentFrame.timestampMs == -1L)
            {
                switchTimeBase();
            }
            newTimestamp = (timestampMs != currentFrame.timestampMs);
        }
        if (currentFrame.timestamp == -1L)
        {
            // This is the first incoming packet. We don't have enough data to
            // update the filter, so we store it until we have two frames of
            // data to process.
            currentFrame.timestamp = rtpTimestamp;
            currentFrame.timestampMs = timestampMs;
        }
        else if (!isPacketInOrder(rtpTimestamp, timestampMs))
        {
            return;
        }
        else if (newTimestamp)
        {
            // First packet of a later frame, the previous frame sample is ready.
            if (prevFrame.completeTimeMs >= 0L) // This is our second frame.
            {
                getTimeDeltas(currentFrame, prevFrame, timeDeltas);
                updateKalman(
                        /* tDelta */ (long) timeDeltas[0],
                        /* tsDelta */ timeDeltas[1],
                        currentFrame.size,
                        prevFrame.size);
            }
            prevFrame.copy(currentFrame);
            // The new timestamp is now the current frame.
            currentFrame.timestamp = rtpTimestamp;
            currentFrame.timestampMs = timestampMs;
            currentFrame.size = 0L;
        }
        // Accumulate the frame size
        currentFrame.size += packetSize;
        currentFrame.completeTimeMs = arrivalTimeMs;
    }

    private void updateKalman(
            long tDelta,
            double tsDelta,
            long frameSize,
            long prevFrameSize)
    {
        double minFramePeriod = updateMinFramePeriod(tsDelta);
        double drift = getCurrentDrift();
        // Compensate for drift
        double tTsDelta = tDelta - tsDelta / drift;
        double fsDelta = ((double) frameSize) - prevFrameSize;

        // Update the Kalman filter
        double scaleFactor =  minFramePeriod / (1000D / 30D);

        E[0][0] += processNoise[0] * scaleFactor;
        E[1][1] += processNoise[1] * scaleFactor;

        if ((hypothesis == BandwidthUsage.kBwOverusing && offset < prevOffset)
                || (hypothesis == BandwidthUsage.kBwUnderusing
                        && offset > prevOffset))
        {
            E[1][1] += 10D * processNoise[1] * scaleFactor;
        }

        double[] h = this.h;
        double[] Eh = this.Eh;

        h[0] = fsDelta;
        h[1] = 1D;
        Eh[0] = E[0][0]*h[0] + E[0][1]*h[1];
        Eh[1] = E[1][0]*h[0] + E[1][1]*h[1];

        double residual = tTsDelta - slope*h[0] - offset;
        boolean stableState
            = (Math.min(numOfDeltas, 60) * Math.abs(offset) < threshold);

        // We try to filter out very late frames. For instance periodic key
        // frames doesn't fit the Gaussian model well.
        double threeTimesSqrtVarNoise = 3D * Math.sqrt(varNoise);
        double residualForUpdateNoiseEstimate
            = (Math.abs(residual) < threeTimesSqrtVarNoise)
                ? residual
                : threeTimesSqrtVarNoise;

        updateNoiseEstimate(
                residualForUpdateNoiseEstimate,
                minFramePeriod,
                stableState);

        double denom = varNoise + h[0]*Eh[0] + h[1]*Eh[1];
        double[] K = this.K;
        double[][] IKh = this.IKh;

        K[0] = Eh[0] / denom;
        K[1] = Eh[1] / denom;
        IKh[0][0] = 1D - K[0]*h[0];
        IKh[0][1] = -K[0]*h[1];
        IKh[1][0] = -K[1]*h[0];
        IKh[1][1] = 1D - K[1]*h[1];

        double e00 = E[0][0];
        double e01 = E[0][1];

        // Update state
        E[0][0] = e00 * IKh[0][0] + E[1][0] * IKh[0][1];
        E[0][1] = e01 * IKh[0][0] + E[1][1] * IKh[0][1];
        E[1][0] = e00 * IKh[1][0] + E[1][0] * IKh[1][1];
        E[1][1] = e01 * IKh[1][0] + E[1][1] * IKh[1][1];

        // Covariance matrix, must be positive semi-definite
//        assert(
//                E[0][0] + E[1][1] >= 0
//                    && E[0][0] * E[1][1] - E[0][1] * E[1][0] >= 0
//                    && E[0][0] >= 0);

        slope = slope + K[0] * residual;
        prevOffset = offset;
        offset = offset + K[1] * residual;

        detect(tsDelta);
    }

    private double updateMinFramePeriod(double tsDelta)
    {
        double minFramePeriod = tsDelta;

        if (tsDeltaHist.size() >= kMinFramePeriodHistoryLength)
            tsDeltaHist.remove(0);
        for (Double d : tsDeltaHist)
            minFramePeriod = Math.min(d, minFramePeriod);
        tsDeltaHist.add(Double.valueOf(tsDelta));
        return minFramePeriod;
    }

    private void updateNoiseEstimate(
            double residual,
            double tsDelta,
            boolean stableState)
    {
        if (!stableState)
            return;

        // Faster filter during startup to faster adapt to the jitter level of
        // the network alpha is tuned for 30 frames per second, but
        double alpha = 0.01D;

        if (numOfDeltas > 10 * 30)
            alpha = 0.002D;

        // Only update the noise estimate if we're not over-using beta is a
        // function of alpha and the time delta since the previous update.
        double beta = Math.pow(1 - alpha, tsDelta * 30D / 1000D);

        avgNoise = beta * avgNoise + (1 - beta) * residual;
        varNoise
            = beta * varNoise
                + (1 - beta) * (avgNoise - residual) * (avgNoise - residual);
        if (varNoise < 1e-7)
            varNoise = 1e-7;
    }
}
