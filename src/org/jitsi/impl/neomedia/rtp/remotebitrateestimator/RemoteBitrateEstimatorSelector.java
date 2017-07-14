package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;

import java.util.*;

/**
 * webrtc Commit: 3224906974b8205c7a29e494338428d321e02909
 * @author Julian Chukwu on 06/07/2017.
 */
public class RemoteBitrateEstimatorSelector
    extends SinglePacketTransformer
        implements RemoteBitrateEstimator
{
    public static final long kTimeOffsetSwitchThreshold = 30;

    private static final Logger logger = Logger
            .getLogger(RemoteBitrateEstimatorSelector.class);
    private RemoteBitrateEstimator remoteBitrateEstimator;
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
        this.remoteBitrateEstimator = new RemoteBitrateEstimatorSingleStream(observe);
        this.minBitrateBps = 0;
        this.absSendTimeEngine = new AbsSendTimeEngine();
        this.packetsSinceAbsoluteSendTime = 0;
    }
    private void PickEstimatorFromHeader(RawPacket packet) {
        synchronized (critSect) {
            if (absSendTimeEngine.hasAbsoluteSendTimeExtension(packet) != null) {
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
            logger.info("NowPicking Estimator");
            if (usingAbsoluteSendTime)
            {
                logger.info("Now Using RemoteBitrateEstimatorAbsSendTime");
                packetTransformer = new RemoteBitrateEstimatorAbsSendTime(observer);
            } else {
                logger.info("Now Using RemoteBitrateEstimatorSingleStream");
                packetTransformer = new RemoteBitrateEstimatorSingleStream(observer);
            }
            remoteBitrateEstimator.setMinBitrate(minBitrateBps);
        }
    }


    @Override
    public Collection<Integer> getSsrcs() {
        return null;
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
        return remoteBitrateEstimator.getLatestEstimate();
    }

    /**
     * Removes all data for <tt>ssrc</tt>.
     *
     * @param ssrc
     */
    @Override
    public void removeStream(int ssrc) {

    }

    @Override
    public void setMinBitrate(int minBitrateBps) {
        synchronized (critSect)
        {
            remoteBitrateEstimator.setMinBitrate(minBitrateBps);
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
        logger.info("Now Choosing RemoteBitrateEstimator");
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


}
