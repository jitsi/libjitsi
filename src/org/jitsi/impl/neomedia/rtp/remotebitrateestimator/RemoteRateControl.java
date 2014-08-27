/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

public class RemoteRateControl
{
    private static final int kDefaultRttMs = 200;

    private float avgChangePeriod;

    private float avgMaxBitRate;

    private float beta;

    @SuppressWarnings("unused")
    private RateControlState cameFromState;

    private long currentBitRate;

    private RateControlInput currentInput;

    private boolean initializedBitRate;

    private long lastBitRateChange;

    private long lastChangeMs;

    private long maxConfiguredBitRate;

    private long maxHoldRate;

    private long minConfiguredBitRate;

    private RateControlRegion rateControlRegion;

    private RateControlState rateControlState;

    private int rtt;

    private long timeFirstIncomingEstimate;

    private boolean updated;

    private float varMaxBitRate;

    public RemoteRateControl(long minBitrateBps)
    {
        reset(minBitrateBps, RateControlState.kRcDecrease);
    }

    private long changeBitRate(
            long currentBitRate,
            long incomingBitRate,
            double delayFactor,
            long nowMs)
    {
        if (!updated)
            return this.currentBitRate;

        updated = false;
        updateChangePeriod(nowMs);
        changeState(currentInput, nowMs);

        // calculated here because it's used in multiple places
        float incomingBitRateKbps = incomingBitRate / 1000.0f;
        // Calculate the max bit rate std dev given the normalized variance and
        // the current incoming bit rate.
        float stdMaxBitRate = (float) Math.sqrt(varMaxBitRate * avgMaxBitRate);
        boolean recovery = false;

        switch (rateControlState)
        {
        case kRcHold:
        {
            maxHoldRate = Math.max(maxHoldRate, incomingBitRate);
            break;
        }
        case kRcIncrease:
        {
            if (avgMaxBitRate >= 0)
            {
                if (incomingBitRateKbps > avgMaxBitRate + 3 * stdMaxBitRate)
                {
                    changeRegion(RateControlRegion.kRcMaxUnknown);
                    avgMaxBitRate = -1.0F;
                }
                else if (incomingBitRateKbps
                        > avgMaxBitRate + 2.5 * stdMaxBitRate)
                {
                    changeRegion(RateControlRegion.kRcAboveMax);
                }
            }

            long responseTime = ((long) (avgChangePeriod + 0.5F)) + rtt + 300;
            double alpha
                = getRateIncreaseFactor(
                        nowMs,
                        lastBitRateChange,
                        responseTime,
                        delayFactor);

            currentBitRate = ((long) (currentBitRate * alpha)) + 1000;
            if (maxHoldRate > 0 && beta * maxHoldRate > currentBitRate)
            {
                currentBitRate = (long) (beta * maxHoldRate);
                avgMaxBitRate = beta * maxHoldRate / 1000.0F;
                changeRegion(RateControlRegion.kRcNearMax);
                recovery = true;
            }
            maxHoldRate = 0;
            lastBitRateChange = nowMs;
            break;
        }
        case kRcDecrease:
        {
            if (incomingBitRate < minConfiguredBitRate)
            {
                currentBitRate = minConfiguredBitRate;
            }
            else
            {
                // Set bit rate to something slightly lower than max to get rid
                // of any self-induced delay.
                currentBitRate = (long) (beta * incomingBitRate + 0.5);
                if (currentBitRate > this.currentBitRate)
                {
                    // Avoid increasing the rate when over-using.
                    if (rateControlRegion != RateControlRegion.kRcMaxUnknown)
                    {
                        currentBitRate
                            = (long) (beta * avgMaxBitRate * 1000 + 0.5f);
                    }
                    currentBitRate
                        = Math.min(currentBitRate, this.currentBitRate);
                }
                changeRegion(RateControlRegion.kRcNearMax);

                if (incomingBitRateKbps < avgMaxBitRate - 3 * stdMaxBitRate)
                    avgMaxBitRate = -1.0F;

                updateMaxBitRateEstimate(incomingBitRateKbps);
            }
            // Stay on hold until the pipes are cleared.
            changeState(RateControlState.kRcHold);
            lastBitRateChange = nowMs;
            break;
        }
        default:
            throw new IllegalStateException("rateControlState");
        }
        if (!recovery
                && (incomingBitRate > 100000 || currentBitRate > 150000)
                && currentBitRate > 1.5 * incomingBitRate)
        {
            // Allow changing the bit rate if we are operating at very low rates
            // Don't change the bit rate if the send side is too far off
            currentBitRate = this.currentBitRate;
            lastBitRateChange = nowMs;
        }
        return currentBitRate;
    }

    private void changeRegion(RateControlRegion region)
    {
        rateControlRegion = region;
        switch (rateControlRegion)
        {
        case kRcAboveMax:
        case kRcMaxUnknown:
            beta = 0.9F;
            break;
        case kRcNearMax:
            beta = 0.95F;
            break;
        default:
            throw new IllegalStateException("rateControlRegion");
        }
    }

    private void changeState(RateControlInput input, long nowMs)
    {
        switch (currentInput.bwState)
        {
        case kBwNormal:
            if (rateControlState == RateControlState.kRcHold)
            {
                lastBitRateChange = nowMs;
                changeState(RateControlState.kRcIncrease);
            }
            break;
        case kBwOverusing:
            if (rateControlState != RateControlState.kRcDecrease)
            {
                changeState(RateControlState.kRcDecrease);
            }
            break;
        case kBwUnderusing:
            changeState(RateControlState.kRcHold);
            break;
        default:
            throw new IllegalStateException("currentInput.bwState");
        }
    }

    private void changeState(RateControlState newState)
    {
        cameFromState = rateControlState;
        rateControlState = newState;
    }

    public long getLatestEstimate()
    {
        return currentBitRate;
    }

    private double getRateIncreaseFactor(
            long nowMs,
            long lastMs,
            long reactionTimeMs,
            double noiseVar)
    {
        // alpha = 1.02 + B ./ (1 + exp(b*(tr - (c1*s2 + c2))))
        // Parameters
        double B = 0.0407;
        double b = 0.0025;
        double c1 = -6700.0 / (33 * 33);
        double c2 = 800.0;
        double d = 0.85;

        double alpha
            = 1.005 + B / (1 + Math.exp(b * (d * reactionTimeMs - (c1 * noiseVar + c2))));

        if (alpha < 1.005)
          alpha = 1.005;
        else if (alpha > 1.3)
          alpha = 1.3;

        if (lastMs > -1)
            alpha = Math.pow(alpha, (nowMs - lastMs) / 1000.0);

        if (rateControlRegion == RateControlRegion.kRcNearMax)
        {
            // We're close to our previous maximum. Try to stabilize the bit
            // rate in this region, by increasing in smaller steps.
            alpha = alpha - (alpha - 1.0) / 2.0;
        }
        else if (rateControlRegion == RateControlRegion.kRcMaxUnknown)
        {
            alpha = alpha + (alpha - 1.0) * 2.0;
        }

        return alpha;
    }

    /**
     * Returns <tt>true</tt> if the bitrate estimate hasn't been changed for
     * more than an RTT, or if the <tt>incomingBitrate</tt> is more than 5%
     * above the current estimate. Should be used to decide if we should reduce
     * the rate further when over-using.
     *
     * @param timeNow
     * @param incomingBitrate
     * @return
     */
    public boolean isTimeToReduceFurther(long timeNow, long incomingBitrate)
    {
        int bitrateReductionInterval = Math.max(Math.min(rtt, 200), 10);

        if (timeNow - lastBitRateChange >= bitrateReductionInterval)
            return true;
        if (isValidEstimate())
        {
          long threshold = (long) (1.05 * incomingBitrate);
          long bitrateDifference = getLatestEstimate() - incomingBitrate;

          return bitrateDifference > threshold;
        }
        return false;
    }

    /**
     * Returns <tt>true</tt> if there is a valid estimate of the incoming
     * bitrate, <tt>false</tt> otherwise.
     *
     * @return
     */
    public boolean isValidEstimate()
    {
        return initializedBitRate;
    }

    public void reset()
    {
        reset(minConfiguredBitRate, RateControlState.kRcHold);
    }

    private void reset(long minBitrateBps, RateControlState cameFromState)
    {
        minConfiguredBitRate = minBitrateBps;
        maxConfiguredBitRate = 30000000L;
        currentBitRate = maxConfiguredBitRate;
        maxHoldRate = 0L;
        avgMaxBitRate = -1.0F;
        varMaxBitRate = 0.4F;
        rateControlState = RateControlState.kRcHold;
        this.cameFromState = cameFromState;
        rateControlRegion = RateControlRegion.kRcMaxUnknown;
        lastBitRateChange = -1L;
        currentInput = new RateControlInput(BandwidthUsage.kBwNormal, 0L, 1.0D);
        updated = false;
        timeFirstIncomingEstimate = -1L;
        initializedBitRate = false;
        avgChangePeriod = 1000.0F;
        lastChangeMs = -1L;
        beta = 0.9F;
        rtt = kDefaultRttMs;
    }

    public RateControlRegion update(RateControlInput input, long nowMs)
    {
        if (input == null)
            throw new NullPointerException("input");

        // Set the initial bit rate value to what we're receiving the first half
        // second.
        if (!initializedBitRate)
        {
            if (timeFirstIncomingEstimate < 0)
            {
                if (input.incomingBitRate > 0)
                    timeFirstIncomingEstimate = nowMs;
            }
            else if (nowMs - timeFirstIncomingEstimate > 500
                    && input.incomingBitRate > 0)
            {
                currentBitRate = input.incomingBitRate;
                initializedBitRate = true;
            }
        }

        if (updated && currentInput.bwState == BandwidthUsage.kBwOverusing)
        {
            // Only update delay factor and incoming bit rate. We always want to
            // react on an over-use.
            currentInput.noiseVar = input.noiseVar;
            currentInput.incomingBitRate = input.incomingBitRate;
            return rateControlRegion;
        }
        updated = true;
        currentInput.copy(input);
        return rateControlRegion;
    }

    public long updateBandwidthEstimate(long nowMs)
    {
        currentBitRate
            = changeBitRate(
                    currentBitRate,
                    currentInput.incomingBitRate,
                    currentInput.noiseVar,
                    nowMs);
        return currentBitRate;
    }

    private void updateChangePeriod(long nowMs)
    {
        long changePeriod = 0L;

        if (lastChangeMs > -1L)
        {
            changePeriod = nowMs - lastChangeMs;
        }
        lastChangeMs = nowMs;
        avgChangePeriod = 0.9F * avgChangePeriod + 0.1F * changePeriod;
    }

    private void updateMaxBitRateEstimate(float incomingBitRateKbps)
    {
        float alpha = 0.05F;

        if (avgMaxBitRate == -1.0F)
        {
            avgMaxBitRate = incomingBitRateKbps;
        }
        else
        {
            avgMaxBitRate
                = (1 - alpha) * avgMaxBitRate + alpha * incomingBitRateKbps;
        }

        // Estimate the max bit rate variance and normalize the variance with
        // the average max bit rate.
        float norm = Math.max(avgMaxBitRate, 1.0F);

        varMaxBitRate
            = (1 - alpha) * varMaxBitRate
                + alpha
                    * (avgMaxBitRate - incomingBitRateKbps)
                    * (avgMaxBitRate - incomingBitRateKbps)
                    / norm;
        // 0.4 ~= 14 kbit/s at 500 kbit/s
        if (varMaxBitRate < 0.4F)
            varMaxBitRate = 0.4F;
        // 2.5f ~= 35 kbit/s at 500 kbit/s
        if (varMaxBitRate > 2.5f)
            varMaxBitRate = 2.5f;
    }
}
