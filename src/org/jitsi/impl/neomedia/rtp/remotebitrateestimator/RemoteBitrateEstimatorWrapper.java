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
     * The name of the property that determines whether or not to activate the
     * abs-send-time remote bitrate estimator.
     */
    public final static String ENABLE_AST_RBE_PNAME
        = "org.jitsi.impl.neomedia.rtp.ENABLE_AST_RBE";

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
     * After this many packets without the AST header, switch to the TOF RBE.
     */
    private static final long TOF_THRESHOLD = 30;

    /**
     * The observer to notify on bitrate estimation changes.
     */
    private final RemoteBitrateObserver observer;

    /**
     * Determines the minimum bitrate (in bps) for the estimates of this remote
     * bitrate estimator.
     */
    private int minBitrateBps = -1;

    /**
     * The ID of the abs-send-time RTP header extension.
     */
    private int astExtensionID = -1;

    /**
     * The ID of the TCC RTP header extension.
     */
    private int tccExtensionID = -1;

    /**
     * A boolean that determines whether the AST RBE is in use.
     */
    private boolean usingAbsoluteSendTime = false;

    /**
     * Counts packets without the AST header extension. After
     * {@link #TOF_THRESHOLD} many packets we switch back to the
     * single stream RBE.
     */
    private int packetsSinceAbsoluteSendTime = 0;

    /**
     * The RBE that this class wraps.
     */
    private RemoteBitrateEstimator rbe;

    /**
     * Ctor.
     *
     * @param observer the observer to notify on bitrate estimation changes.
     */
    public RemoteBitrateEstimatorWrapper(RemoteBitrateObserver observer)
    {
        this.observer = observer;

        // Initialize to the default RTP timestamp based RBE.
        rbe = new RemoteBitrateEstimatorSingleStream(observer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLatestEstimate()
    {
        return rbe.getLatestEstimate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Long> getSsrcs()
    {
        return rbe.getSsrcs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeStream(long ssrc)
    {
        rbe.removeStream(ssrc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinBitrate(int minBitrateBps)
    {
        this.minBitrateBps = minBitrateBps;
        rbe.setMinBitrate(minBitrateBps);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incomingPacket(RawPacket pkt)
    {
        if (!isEnabled())
        {
            return;
        }

        RawPacket.HeaderExtension ext = null;
        if (ENABLE_AST_RBE && astExtensionID != -1)
        {
            ext = pkt.getHeaderExtension((byte) astExtensionID);
        }

        if (ext != null)
        {
            // If we see AST in header, switch RBE strategy immediately.
            if (!usingAbsoluteSendTime)
            {
                usingAbsoluteSendTime = true;

                RemoteBitrateEstimatorAbsSendTime ast
                    = new RemoteBitrateEstimatorAbsSendTime(observer);

                ast.setExtensionID(astExtensionID);

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
                if (packetsSinceAbsoluteSendTime >= TOF_THRESHOLD)
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRttUpdate(long avgRttMs, long maxRttMs)
    {
        rbe.onRttUpdate(avgRttMs, maxRttMs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        this.incomingPacket(pkt);

        return pkt;
    }

    /**
     * Sets the ID of the abs-send-time RTP extension. Set to -1 to effectively
     * disable the AST remote bitrate estimator.
     *
     * @param astExtensionID the ID to set.
     */
    public void setAstExtensionID(int astExtensionID)
    {
        this.astExtensionID = astExtensionID;
        if (rbe instanceof RemoteBitrateEstimatorAbsSendTime)
        {
            ((RemoteBitrateEstimatorAbsSendTime) rbe).setExtensionID(astExtensionID);
        }
    }

    /**
     * Gets a boolean that indicates whether or not to perform receive-side
     * bandwidth estimations.
     *
     * @return true if receive-side bandwidth estimations are enabled, false
     * otherwise.
     */
    public boolean isEnabled()
    {
        return tccExtensionID == -1 /* && supportsRemb */;
    }

    /**
     * Sets the ID of the transport-cc RTP extension. Anything other than -1
     * disables receive-side bandwidth estimations.
     *
     * @param tccExtensionID the ID to set.
     */
    public void setTccExtensionID(int tccExtensionID)
    {
        this.tccExtensionID = tccExtensionID;
    }
}
