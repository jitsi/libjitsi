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

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.*;

import java.util.*;

/**
 * webrtc Commit: 3224906974b8205c7a29e494338428d321e02909
 *
 * @author Julian Chukwu on 06/07/2017.
 */
public class RemoteBitrateEstimatorSelector
    extends SinglePacketTransformerAdapter
    implements RemoteBitrateEstimator,
    CallStatsObserver,
    RecurringRunnable

{

    public final static String ENABLE_USE_OF_ABS_SEND_TIME =
        "org.jitsi.impl.neomedia.rtp.ENABLE_ABS_SEND_TIME";
    ;
    public static final long kTimeOffsetSwitchThreshold = 30;
    private static final Logger logger = Logger
        .getLogger(RemoteBitrateEstimatorSelector.class);
    private static final long defaultTimeUntilNextRunMs = 500;
    private final Object critSect = new Object();
    private boolean usingAbsoluteSendTime;
    private AbsSendTimeEngine absSendTimeEngine;
    private long packetsSinceAbsoluteSendTime;
    private int minBitrateBps;
    private VideoMediaStreamImpl stream;
    private SinglePacketTransformer packetTransformer;
    private RemoteBitrateObserver observer;
    /**
     * {@code enableUseofAbsSendTime} enables selection of
     * RemoteBitrateEstimatorAbsSendTime if set to "true"
     * Default is false
     */
    private boolean enableUseOfAbsSendTime;

    public RemoteBitrateEstimatorSelector(RemoteBitrateObserver observe,
                                          VideoMediaStreamImpl stream)
    {
        this.observer = observe;
        this.stream = stream;
        /**
         * RemoteBitrateEstimatorSingleStream is the Default BitrateEstimator
         */
        this.packetTransformer
            = new RemoteBitrateEstimatorSingleStream(observe);
        this.minBitrateBps = 0;
        this.packetsSinceAbsoluteSendTime = 0;
        setAbsSendTimeUsageConfiguration();
    }

    /**
     * Examines header to check if to use AbsSendTime or RTPTimeStamps and
     * calls appropriate bitrate estimator.
     *
     * @param packet is a RawPacket.
     */
    private void pickEstimatorFromHeader(RawPacket packet)
    {
        synchronized (critSect)
        {
            if (absSendTimeEngine.getAbsoluteSendTimeExtension(packet)
                != null)
            {
                // If we see AST in header, switch RBE strategy immediately.
                if (!usingAbsoluteSendTime)
                {
                    logger.warn("WrappingBitrateEstimator: Switching to" +
                        " absolute send time RBE.");
                    usingAbsoluteSendTime = true;
                    pickEstimator();
                }
                packetsSinceAbsoluteSendTime = 0;
            }
            else
            {
                // When we don't see AST, wait for a few packets before going
                // back to TOF.
                if (usingAbsoluteSendTime)
                {
                    ++packetsSinceAbsoluteSendTime;
                    if (packetsSinceAbsoluteSendTime
                        >= kTimeOffsetSwitchThreshold)
                    {
                        logger.info("WrappingBitrateEstimator: " +
                            "Switching to transmission time offset RBE.");
                        usingAbsoluteSendTime = false;
                        pickEstimator();
                    }
                }
            }
        }
    }

    public void setAbsSendTimeUsageConfiguration()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg == null)
        {
            logger.warn("FORGETTING about checking AbsSendTime because "
                + "the configuration service was not found.");
            return;
        }
        enableUseOfAbsSendTime
            = cfg.getBoolean(ENABLE_USE_OF_ABS_SEND_TIME, false);
    }

    /**
     * Selects either to use RBE_abs_send_time or RBE_single_stream
     */
    private void pickEstimator()
    {
        if (usingAbsoluteSendTime)
        {
            synchronized (critSect)
            {
                logger.info("Now Using RemoteBitrateEstimatorAbsSendTime");
                packetTransformer
                    = new RemoteBitrateEstimatorAbsSendTime(this.observer,
                    this.absSendTimeEngine);
            }
        }
        //Else we are already using RemoteBitrateEstimatorSingleStream
        this.setMinBitrate(minBitrateBps);
    }


    @Override
    public Collection<Integer> getSsrcs()
    {
        return ((RemoteBitrateEstimator) packetTransformer).getSsrcs();
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

        return ((RemoteBitrateEstimator) packetTransformer).getLatestEstimate();
    }

    /**
     * Removes all data for <tt>ssrc</tt>.
     *
     * @param ssrc
     */
    @Override
    public void removeStream(int ssrc)
    {
        ((RemoteBitrateEstimator) packetTransformer).removeStream(ssrc);
    }

    @Override
    public void setMinBitrate(int minBitrateBps)
    {
        synchronized (critSect)
        {
            ((RemoteBitrateEstimator) packetTransformer)
                .setMinBitrate(minBitrateBps);
            this.minBitrateBps = minBitrateBps;
        }
    }

    @Override
    public RawPacket reverseTransform(
        RawPacket packet)
    {
        /**
         * @Todo Find out why having synchronized here makes sense since it
         * exists in pickEstimatorFromHeader.
         */
        if (this.absSendTimeEngine == null)
        {
            this.absSendTimeEngine = stream.getAbsSendTimeEngine();
        }

        synchronized (critSect)
        {
            if (enableUseOfAbsSendTime)
            {
                pickEstimatorFromHeader(packet);
            }
            packetTransformer.reverseTransform(packet);
        }
        return packet;
    }

    /**
     * Gets the <tt>PacketTransformer</tt> for RTP packets.
     *
     * @return the <tt>PacketTransformer</tt> for RTP packets
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Gets the <tt>PacketTransformer</tt> for RTCP packets.
     *
     * @return the <tt>PacketTransformer</tt> for RTCP packets
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
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
    public long getTimeUntilNextRun()
    {
        long timeUntilNextRun =
            (packetTransformer instanceof RecurringRunnable) ?
                ((RecurringRunnable) packetTransformer)
                    .getTimeUntilNextRun() : defaultTimeUntilNextRunMs;
        return timeUntilNextRun;
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
    public void run()
    {
        if (packetTransformer instanceof Runnable)
        {
            ((Runnable) packetTransformer).run();
        }
    }

    @Override
    public void onRttUpdate(long avgRttMs, long maxRttMs)
    {
        if (packetTransformer instanceof CallStatsObserver)
        {
            ((CallStatsObserver) packetTransformer)
                .onRttUpdate(avgRttMs, maxRttMs);
        }
    }
}
