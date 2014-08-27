/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import java.util.*;

import net.sf.fmj.media.rtp.util.*;

/**
 * webrtc/webrtc/modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.cc
 *
 * @author Lyubomir Marinov
 */
public class RemoteBitrateEstimatorSingleStream
    implements RemoteBitrateEstimator
{
    private static final int kProcessIntervalMs = 1000;

    private static final int kStreamTimeOutMs = 2000;

    private final Object critSect = new Object();

    private final RateStatistics incomingBitrate
        = new RateStatistics(500, 8000);

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * <tt>updateEstimate</tt>.
     */
    private final RateControlInput input
        = new RateControlInput(BandwidthUsage.kBwNormal, 0L, 0.0D);

    private long lastProcessTime = -1L;

    private final RemoteBitrateObserver observer;

    private final Map<Integer,OveruseDetector> overuseDetectors
        = new HashMap<Integer,OveruseDetector>();

    private final RemoteRateControl remoteRate;

    public RemoteBitrateEstimatorSingleStream(
            RemoteBitrateObserver observer,
            long minBitrateBps)
    {
        if (observer == null)
            throw new NullPointerException("observer");

        this.observer = observer;
        remoteRate = new RemoteRateControl(minBitrateBps);
    }

    private long getExtensionTransmissionTimeOffset(RTPPacket header)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    private int[] getSsrcs()
    {
        Set<Integer> set = overuseDetectors.keySet();
        int[] array = new int[set.size()];
        int i = 0;

        for (Iterator<Integer> it = set.iterator(); it.hasNext();)
            array[i++] = it.next();
        return array;
    }

    long getTimeUntilNextProcess()
    {
        return
            (lastProcessTime < 0)
                ? 0
                : lastProcessTime
                    + kProcessIntervalMs
                    - System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incomingPacket(
            long arrivalTimeMs,
            int payloadSize,
            RTPPacket header)
    {
        Integer ssrc = Integer.valueOf(header.ssrc);
        long rtpTimestamp
            = header.timestamp + getExtensionTransmissionTimeOffset(header);
        long nowMs = System.currentTimeMillis();

        synchronized (critSect)
        {
        OveruseDetector overuseDetector = overuseDetectors.get(ssrc);

        if (overuseDetector == null)
        {
            // This is a new SSRC. Adding to map.
            // TODO(holmer): If the channel changes SSRC the old SSRC will still
            // be around in this map until the channel is deleted. This is OK
            // since the callback will no longer be called for the old SSRC.
            // This will be automatically cleaned up when we have one
            // RemoteBitrateEstimator per REMB group.
            overuseDetector = new OveruseDetector();
            overuseDetectors.put(ssrc, overuseDetector);
        }
        overuseDetector.setPacketTimeMs(nowMs);
        this.incomingBitrate.update(payloadSize, nowMs);

        BandwidthUsage prior_state = overuseDetector.getState();

        overuseDetector.update(payloadSize, -1, rtpTimestamp, arrivalTimeMs);
        if (overuseDetector.getState() == BandwidthUsage.kBwOverusing)
        {
            long incomingBitrate = this.incomingBitrate.getRate(nowMs);

            if (prior_state != BandwidthUsage.kBwOverusing
                    || remoteRate.isTimeToReduceFurther(nowMs, incomingBitrate))
            {
                // The first overuse should immediately trigger a new estimate.
                // We also have to update the estimate immediately if we are
                // overusing and the target bitrate is too high compared to what
                // we are receiving.
                updateEstimate(nowMs);
            }
        }
        } // synchronized (critSect)
    }

    /**
     * Triggers a new estimate calculation.
     *
     * @return
     */
    long process()
    {
        if (getTimeUntilNextProcess() <= 0)
        {
            long nowMs = System.currentTimeMillis();

            updateEstimate(nowMs);
            lastProcessTime = nowMs;
        }
        return 0;
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
            overuseDetectors.remove(Integer.valueOf(ssrc));
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
        double sumNoiseVar = 0.0D;

        for (Iterator<OveruseDetector> it
                    = overuseDetectors.values().iterator();
                it.hasNext();)
        {
            OveruseDetector overuseDetector = it.next();
            long packetTimeMs = overuseDetector.getPacketTimeMs();

            if (packetTimeMs >= 0 && nowMs - packetTimeMs > kStreamTimeOutMs)
            {
                // This over-use detector hasn't received packets for
                // kStreamTimeOutMs milliseconds and is considered stale.
                it.remove();
            }
            else
            {
                sumNoiseVar += overuseDetector.getNoiseVar();

                // Make sure that we trigger an over-use if any of the over-use
                // detectors is detecting over-use.
                BandwidthUsage overuseDetectorBwState
                    = overuseDetector.getState();

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

        double meanNoiseVar = sumNoiseVar / (double) overuseDetectors.size();
        RateControlInput input = this.input;

        input.bwState = bwState;
        input.incomingBitRate = incomingBitrate.getRate(nowMs);
        input.noiseVar = meanNoiseVar;

        RateControlRegion region = remoteRate.update(input, nowMs);
        long targetBitrate = remoteRate.updateBandwidthEstimate(nowMs);

        if (remoteRate.isValidEstimate())
            observer.onReceiveBitrateChanged(getSsrcs(), targetBitrate);
        for (OveruseDetector overuseDetector : overuseDetectors.values())
            overuseDetector.setRateControlRegion(region);
        } // synchronized (critSect)
    }
}
