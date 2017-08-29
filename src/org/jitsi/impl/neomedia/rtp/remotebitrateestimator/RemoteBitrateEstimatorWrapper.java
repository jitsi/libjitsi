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

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;

import java.util.*;

/**
 * @author George Politis
 */
public class RemoteBitrateEstimatorWrapper
    extends SinglePacketTransformerAdapter
    implements RemoteBitrateEstimator, TransformEngine
{
    /**
     *
     */
    public final static String ENABLE_AST_RBE_PNAME
        = "org.jitsi.impl.neomedia.rtp.ENABLE_AST_RBE";

    /**
     *
     */
    private static final long kTimeOffsetSwitchThreshold = 30;

    /**
     * The {@link ConfigurationService} to get config values from.
     */
    private static final ConfigurationService
        cfg = LibJitsi.getConfigurationService();

    /**
     * Disable AST RBE by default.
     */
    private static final boolean ENABLE_AST_RBE_DEFAULT = false;

    /**
     * Determines whether or not to activate the abs-send-time remote bitrate
     * estimator.
     */
    private static final boolean ENABLE_AST_RBE =
        cfg != null ? cfg.getBoolean(ENABLE_AST_RBE_PNAME,
            ENABLE_AST_RBE_DEFAULT) : ENABLE_AST_RBE_DEFAULT;

    /**
     *
     */
    private final RemoteBitrateObserver observer;

    /**
     *
     */
    private int minBitrateBps = -1;

    /**
     * The ID of the abs-send-time RTP header extension.
     */
    private int extensionID = -1;

    /**
     *
     */
    private boolean usingAbsoluteSendTime = false;

    /**
     *
     */
    private int packetsSinceAbsoluteSendTime = 0;

    /**
     * The RBE that this class wraps.
     */
    private RemoteBitrateEstimator rbe;

    /**
     * Ctor.
     *
     * @param observer
     */
    public RemoteBitrateEstimatorWrapper(RemoteBitrateObserver observer)
    {
        this.observer = observer;

        // Initialize to the default RTP timestamp based RBE.
        rbe = new RemoteBitrateEstimatorSingleStream(observer);
    }

    @Override
    public long getLatestEstimate()
    {
        return rbe.getLatestEstimate();
    }

    @Override
    public Collection<Long> getSsrcs()
    {
        return rbe.getSsrcs();
    }

    @Override
    public void removeStream(long ssrc)
    {
        rbe.removeStream(ssrc);
    }

    @Override
    public void setMinBitrate(int minBitrateBps)
    {
        this.minBitrateBps = minBitrateBps;
        rbe.setMinBitrate(minBitrateBps);
    }

    @Override
    public void incomingPacket(RawPacket pkt)
    {
        RawPacket.HeaderExtension ext = null;
        if (ENABLE_AST_RBE && extensionID != -1)
        {
            ext = pkt.getHeaderExtension((byte) extensionID);
        }

        if (ext != null)
        {
            // If we see AST in header, switch RBE strategy immediately.
            if (!usingAbsoluteSendTime)
            {
                usingAbsoluteSendTime = true;

                RemoteBitrateEstimatorAbsSendTime ast
                    = new RemoteBitrateEstimatorAbsSendTime(observer);

                ast.setExtensionID(extensionID);

                this.rbe = ast;

                int minBitrateBps = this.minBitrateBps;
                if (minBitrateBps > 0)
                {
                    this.rbe.setMinBitrate(minBitrateBps);
                }
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
                if (packetsSinceAbsoluteSendTime >= kTimeOffsetSwitchThreshold)
                {
                    usingAbsoluteSendTime = false;
                    rbe = new RemoteBitrateEstimatorSingleStream(observer);

                    int minBitrateBps = this.minBitrateBps;
                    if (minBitrateBps > 0)
                    {
                        rbe.setMinBitrate(minBitrateBps);
                    }
                }
            }
        }

        rbe.incomingPacket(pkt);
    }

    @Override
    public void onRttUpdate(long avgRttMs, long maxRttMs)
    {
        rbe.onRttUpdate(avgRttMs, maxRttMs);
    }

    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        this.incomingPacket(pkt);

        return pkt;
    }

    public void setExtensionID(int extensionID)
    {
        this.extensionID = extensionID;
        if (rbe instanceof RemoteBitrateEstimatorAbsSendTime)
        {
            ((RemoteBitrateEstimatorAbsSendTime) rbe).setExtensionID(extensionID);
        }
    }
}
