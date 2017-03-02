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
package org.jitsi.impl.neomedia.rtp.translator;

import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Filters the packets of {@link MediaStreamTrackDesc} based on the currently
 * forwarded subjective quality index. It's also taking care of upscaling and
 * downscaling. As a {@link PacketTransformer}, it rewrites the forwarded
 * packets so that the gaps as a result of the drops are hidden.
 *
 * @author George Politis
 */
public class SimulcastController
    implements PaddingParams, BufferFilter
{
    /**
     * The {@link BitstreamControllerImpl} for the currently forwarded RTP stream.
     */
    private BitstreamControllerImpl bitstreamController;

    /**
     * Ctor.
     */
    public SimulcastController(
        MediaStreamTrackDesc source, int startTl0Idx, int targetIdx, int optimalIdx)
    {
        bitstreamController = new BitstreamControllerImpl(
            source, startTl0Idx, targetIdx, optimalIdx);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bitrates getBitrates()
    {
        return bitstreamController.getBitrates();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTargetSSRC()
    {
        return bitstreamController.getTargetSSRC();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean accept(FrameDesc sourceFrameDesc, byte[] buf, int off, int len)
    {
        if (sourceFrameDesc == null || buf == null || off < 0
            || len < RawPacket.FIXED_HEADER_SIZE || buf.length < off + len
            )
        {
            return false;
        }

        RTPEncodingDesc sourceEncodings[] = sourceFrameDesc.getRTPEncoding()
            .getMediaStreamTrack().getRTPEncodings();

        // If we're getting packets here => there must be at least 1 encoding.
        if (ArrayUtils.isNullOrEmpty(sourceEncodings))
        {
            return false;
        }

        // if the base (TL0) is suspended, we MUST downscale.
        boolean currentTL0IsActive = false;
        int currentTL0Idx = bitstreamController.getCurrentIndex();
        if (currentTL0Idx > -1)
        {
            currentTL0Idx
                = sourceEncodings[currentTL0Idx].getBaseLayer().getIndex();

            currentTL0IsActive = sourceEncodings[currentTL0Idx].isActive();
        }

        int targetTL0Idx = bitstreamController.getTargetIndex();
        if (targetTL0Idx > -1)
        {
            targetTL0Idx
                = sourceEncodings[targetTL0Idx].getBaseLayer().getIndex();
        }

        if (currentTL0Idx == targetTL0Idx && currentTL0IsActive)
        {
            long sourceSSRC = sourceFrameDesc.getRTPEncoding().getPrimarySSRC();
            boolean accept = sourceSSRC == bitstreamController.getTL0SSRC();

            if (!accept)
            {
                return false;
            }

            // Give the bitstream filter a chance to drop the packet.
            return bitstreamController.accept(sourceFrameDesc, buf, off, len);
        }

        // An intra-codec/simulcast switch pending.

        int sourceTL0Idx = sourceFrameDesc.getRTPEncoding().getIndex();
        if (sourceTL0Idx > -1)
        {
            sourceTL0Idx
                = sourceEncodings[sourceTL0Idx].getBaseLayer().getIndex();
        }

        boolean sourceTL0IsActive = sourceEncodings[sourceTL0Idx].isActive();
        if (!sourceFrameDesc.isIndependent()
            || !sourceTL0IsActive
            || sourceTL0Idx == currentTL0Idx)
        {
            // An intra-codec switch requires a key frame.

            long sourceSSRC = sourceFrameDesc.getRTPEncoding().getPrimarySSRC();
            boolean accept = sourceSSRC == bitstreamController.getTL0SSRC();

            if (!accept)
            {
                return false;
            }

            // Give the bitstream filter a chance to drop the packet.
            return bitstreamController.accept(sourceFrameDesc, buf, off, len);
        }

        if ((targetTL0Idx <= sourceTL0Idx && sourceTL0Idx < currentTL0Idx)
            || (currentTL0Idx < sourceTL0Idx && sourceTL0Idx <= targetTL0Idx)
            || (!currentTL0IsActive && sourceTL0Idx <= targetTL0Idx))
        {
            synchronized (this)
            {
                int maxSeqNum = bitstreamController.getMaxSeqNum(),
                    targetIdx = bitstreamController.getTargetIndex(),
                    optimalIdx = bitstreamController.getOptimalIndex();

                long maxTs = bitstreamController.getMaxTs(),
                    transmittedBytes = bitstreamController.getTransmittedBytes(),
                    transmittedPackets = bitstreamController.getTransmittedPackets();

                bitstreamController = new BitstreamControllerImpl(
                    sourceFrameDesc.getRTPEncoding().getMediaStreamTrack(),
                    sourceFrameDesc,
                    maxSeqNum, maxTs,
                    transmittedBytes, transmittedPackets,
                    sourceTL0Idx, targetIdx, optimalIdx);
            }

            // Give the bitstream filter a chance to drop the packet.
            return bitstreamController.accept(sourceFrameDesc, buf, off, len);
        }
        else
        {
            return false;
        }
    }

    /**
     * Update the target subjective quality index for this instance.
     *
     * @param newTargetIdx new target subjective quality index.
     */
    public void setTargetIndex(int newTargetIdx)
    {
        synchronized (this)
        {
            bitstreamController.setTargetIndex(newTargetIdx);
        }

        if (newTargetIdx > -1)
        {
            // maybe send FIR.
            MediaStreamTrackDesc sourceTrack = bitstreamController.getSource();
            if (sourceTrack == null)
            {
                return;
            }

            int currentTL0Idx = bitstreamController.getCurrentIndex();
            if (currentTL0Idx > -1)
            {
                currentTL0Idx = sourceTrack.getRTPEncodings()
                    [currentTL0Idx].getBaseLayer().getIndex();
            }

            int targetTL0Idx
                = sourceTrack.getRTPEncodings()[newTargetIdx].getBaseLayer().getIndex();

            if (currentTL0Idx != targetTL0Idx)
            {
                ((RTPTranslatorImpl) sourceTrack.getMediaStreamTrackReceiver()
                    .getStream().getRTPTranslator())
                    .getRtcpFeedbackMessageSender().sendFIR(
                    (int) bitstreamController.getTargetSSRC());
            }
        }
    }

    public void setOptimalIndex(int optimalIndex)
    {
        synchronized (this)
        {
            bitstreamController.setOptimalIndex(optimalIndex);
        }
    }

    /**
     * Transforms the RTP packet specified in the {@link RawPacket} that is
     * passed as an argument for the purposes of simulcast.
     *
     * @param pktIn the {@link RawPacket} to be transformed.
     * @return the transformed {@link RawPacket} or null if the packet needs
     * to be dropped.
     */
    public RawPacket[] rtpTransform(RawPacket pktIn)
    {
        return bitstreamController.rtpTransform(pktIn);
    }

    /**
     * Transform an RTCP {@link RawPacket} for the purposes of simulcast.
     *
     * @param pktIn the {@link RawPacket} to be transformed.
     * @return the transformed RTCP {@link RawPacket}, or null if the packet
     * needs to be dropped.
     */
    public RawPacket rtcpTransform(RawPacket pktIn)
    {
        return bitstreamController.rtcpTransform(pktIn);
    }
}
