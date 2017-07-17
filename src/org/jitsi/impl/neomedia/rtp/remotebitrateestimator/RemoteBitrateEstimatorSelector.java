package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.*;

import java.util.*;

/**
 * webrtc Commit: 3224906974b8205c7a29e494338428d321e02909
 * @author Julian Chukwu on 06/07/2017.
 */
public class RemoteBitrateEstimatorSelector
    extends SinglePacketTransformer
        implements RemoteBitrateEstimator,
                CallStatsObserver,
                RecurringRunnable,
                TransformEngine
{
    public static final long kTimeOffsetSwitchThreshold = 30;

    private static final Logger logger = Logger
            .getLogger(RemoteBitrateEstimatorSelector.class);
    private boolean usingAbsoluteSendTime;
    private AbsSendTimeEngine absSendTimeEngine;
    private long packetsSinceAbsoluteSendTime;
    private int minBitrateBps;
    private SinglePacketTransformer packetTransformer;
    private final Object critSect = new Object();
    private final RemoteBitrateObserver observer;

    public RemoteBitrateEstimatorSelector(RemoteBitrateObserver observe)
    {
        this.observer = observe;
        this.packetTransformer
                = new RemoteBitrateEstimatorSingleStream(observe);
        this.minBitrateBps = 0;
        this.absSendTimeEngine = new AbsSendTimeEngine();
        this.packetsSinceAbsoluteSendTime = 0;
    }
    private void PickEstimatorFromHeader(RawPacket packet) {
        synchronized (critSect) {
            if (absSendTimeEngine.hasAbsoluteSendTimeExtension(packet) != null)
            {
                // If we see AST in header, switch RBE strategy immediately.
                if (!usingAbsoluteSendTime) {
                    logger.warn("WrappingBitrateEstimator: Switching to" +
                            " absolute send time RBE.");
                    usingAbsoluteSendTime = true;
                    PickEstimator();
                }
                packetsSinceAbsoluteSendTime = 0;
            } else {
                // When we don't see AST, wait for a few packets before going
                // back to TOF.
                if (usingAbsoluteSendTime) {
                    ++packetsSinceAbsoluteSendTime;
                    if (packetsSinceAbsoluteSendTime
                            >= kTimeOffsetSwitchThreshold) {
                        logger.info("WrappingBitrateEstimator: Switching to " +
                                "transmission time offset RBE.");
                        usingAbsoluteSendTime = false;
                        PickEstimator();
                    }
                }
            }
        }
    }

    /**
     * Selects either to use RBE_abs_send_time or RBE_single_stream
     */
    private void PickEstimator()
    {
        synchronized (critSect)
        {
            if (usingAbsoluteSendTime)
            {
                logger.info("Now Using RemoteBitrateEstimatorAbsSendTime");
                packetTransformer
                        = new RemoteBitrateEstimatorAbsSendTime(this.observer);
            } else {
                logger.info("Now Using RemoteBitrateEstimatorSingleStream");
                packetTransformer
                       = new RemoteBitrateEstimatorSingleStream(this.observer);
            }
            ((RemoteBitrateEstimator)packetTransformer)
                    .setMinBitrate(minBitrateBps);
        }
    }


    @Override
    public Collection<Integer> getSsrcs()
    {
        return ((RemoteBitrateEstimator)packetTransformer).getSsrcs();
    }

    /**
     * Returns the estimated payload bitrate in bits per second if a valid
     * estimate exists; otherwise, <tt>-1</tt>.
     *
     * @return the estimated payload bitrate in bits per seconds if a valid
     * estimate exists; otherwise, <tt>-1</tt>
     */
    @Override
    public long getLatestEstimate()
    {

        return ((RemoteBitrateEstimator)packetTransformer).getLatestEstimate();
    }

    /**
     * Removes all data for <tt>ssrc</tt>.
     *
     * @param ssrc
     */
    @Override
    public void removeStream(int ssrc) {
        ((RemoteBitrateEstimator)packetTransformer).removeStream(ssrc);
    }

    @Override
    public void setMinBitrate(int minBitrateBps) {
        synchronized (critSect)
        {
            ((RemoteBitrateEstimator)packetTransformer)
                    .setMinBitrate(minBitrateBps);
            this.minBitrateBps = minBitrateBps;
        }
    }

    @Override
    public RawPacket reverseTransform(
            RawPacket packet) {
        /**
         * @Todo Find out why having synchronized here makes sense since it
         * exists in PickEstimatorFromHeader.
         */
        synchronized (critSect)
        {
            PickEstimatorFromHeader(packet);
            packetTransformer.reverseTransform(packet);
        }
        return packet;
    }

    /**
     * Transforms a specific packet.
     *
     * @param pkt the packet to be transformed.
     * @return the transformed packet.
     */
    @Override
    public RawPacket transform(RawPacket pkt) {
        return null;
    }

    /**
     * Gets the <tt>PacketTransformer</tt> for RTP packets.
     *
     * @return the <tt>PacketTransformer</tt> for RTP packets
     */
    @Override
    public PacketTransformer getRTPTransformer() {
        return this;
    }

    /**
     * Gets the <tt>PacketTransformer</tt> for RTCP packets.
     *
     * @return the <tt>PacketTransformer</tt> for RTCP packets
     */
    @Override
    public PacketTransformer getRTCPTransformer() {
        return null;
    }


    /**
     * Returns the number of milliseconds until this instance wants a worker
     * thread to call {@link #run()}. The method is called on the same
     * worker thread as Process will be called on.
     *
     * @return the number of milliseconds until this instance wants a worker
     * thread to call {@link #run()}
     */
    @Override
    public long getTimeUntilNextRun() {
        long timeUntilNextRun  =
                (packetTransformer instanceof RecurringRunnable) ?
             ((RecurringRunnable) packetTransformer)
                     .getTimeUntilNextRun() : -1;
        return  timeUntilNextRun;
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        if (packetTransformer instanceof Runnable)
        {
            ((Runnable)packetTransformer).run();
        }
    }

    @Override
    public void onRttUpdate(long avgRttMs, long maxRttMs) {
        if(packetTransformer instanceof CallStatsObserver){
            ((CallStatsObserver)packetTransformer)
                    .onRttUpdate(avgRttMs,maxRttMs);
        }
    }
}
