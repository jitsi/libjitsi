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

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.lang.ref.*;

/**
 * Filters the packets comming from a specific {@link MediaStreamTrackDesc}
 * based on the currently forwarded subjective quality index. It's also taking
 * care of upscaling and downscaling. As a {@link PacketTransformer}, it
 * rewrites the forwarded packets so that the gaps as a result of the drops are
 * hidden.
 *
 * @author George Politis
 */
public class SimulcastController
    implements PaddingParams
{
    /**
     * A {@link WeakReference} to the {@link MediaStreamTrackDesc} that feeds
     * this instance with RTP/RTCP packets.
     */
    private final WeakReference<MediaStreamTrackDesc> weakSource;

    /**
     * The SSRC to protect when probing for bandwidth ({@see PaddingParams}) and
     * for RTP/RTCP packet rewritting.
     */
    private final long targetSSRC;

    /**
     * The {@link BitstreamController} for the currently forwarded RTP stream.
     */
    private BitstreamController bitstreamController;

    /**
     * Ctor.
     *
     * @param source the source {@link MediaStreamTrackDesc}
     */
    public SimulcastController(MediaStreamTrackDesc source)
    {
        weakSource = new WeakReference<>(source);

        RTPEncodingDesc[] rtpEncodings = source.getRTPEncodings();
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            targetSSRC = -1;
        }
        else
        {
            targetSSRC = rtpEncodings[0].getPrimarySSRC();
        }

        bitstreamController = new BitstreamController(source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bitrates getBitrates()
    {
        long currentBps = 0;
        MediaStreamTrackDesc source = weakSource.get();
        if (source == null)
        {
            return Bitrates.EMPTY;
        }

        RTPEncodingDesc[] sourceEncodings = source.getRTPEncodings();
        if (ArrayUtils.isNullOrEmpty(sourceEncodings))
        {
            return Bitrates.EMPTY;
        }

        int currentIdx = bitstreamController.getCurrentIndex();
        if (currentIdx > -1 && sourceEncodings[currentIdx].isActive())
        {
            currentBps = sourceEncodings[currentIdx].getLastStableBitrateBps();
        }

        long optimalBps = 0;
        int optimalIdx = bitstreamController.getOptimalIndex();
        if (optimalIdx > -1)
        {
            for (int i = optimalIdx; i > -1; i--)
            {
                if (!sourceEncodings[i].isActive())
                {
                    continue;
                }

                long bps = sourceEncodings[i].getLastStableBitrateBps();
                if (bps > 0)
                {
                    optimalBps = bps;
                    break;
                }
            }
        }

        return new Bitrates(currentBps, optimalBps);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTargetSSRC()
    {
        return targetSSRC;
    }

    /**
     * Defines a packet filter that controls which packets to be written into
     * some arbitrary target/receiver that owns this {@link SimulcastController}.
     *
     * @param buf the <tt>byte</tt> array that holds the packet.
     * @param off the offset in <tt>buffer</tt> at which the actual data begins.
     * @param len the number of <tt>byte</tt>s in <tt>buffer</tt> which
     * constitute the actual data.
     * @return <tt>true</tt> to allow the specified packet/<tt>buffer</tt> to be
     * written into the arbitrary target/receiver that owns this
     * {@link SimulcastController} ; otherwise, <tt>false</tt>
     */
    public boolean accept(byte[] buf, int off, int len)
    {
        int targetIndex = bitstreamController.getTargetIndex()
            , currentIndex = bitstreamController.getCurrentIndex();

        if (targetIndex < 0 && currentIndex > -1)
        {
            synchronized (this)
            {
                bitstreamController
                    = new BitstreamController(bitstreamController, -1);

                return false;
            }
        }

        MediaStreamTrackDesc sourceTrack = weakSource.get();
        assert sourceTrack != null;
        FrameDesc sourceFrameDesc = sourceTrack.findFrameDesc(buf, off, len);

        if (sourceFrameDesc == null || buf == null || off < 0
            || len < RawPacket.FIXED_HEADER_SIZE || buf.length < off + len)
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

        // if the TL0 of the forwarded stream is suspended, we MUST downscale.
        boolean currentTL0IsActive = false;
        int currentTL0Idx = currentIndex;
        if (currentTL0Idx > -1)
        {
            currentTL0Idx
                = sourceEncodings[currentTL0Idx].getBaseLayer().getIndex();

            currentTL0IsActive = sourceEncodings[currentTL0Idx].isActive();
        }

        int targetTL0Idx = targetIndex;
        if (targetTL0Idx > -1)
        {
            targetTL0Idx
                = sourceEncodings[targetTL0Idx].getBaseLayer().getIndex();
        }

        if (currentTL0Idx == targetTL0Idx
            && (currentTL0IsActive || targetTL0Idx < 0 /* => currentIdx < 0 */))
        {
            // An intra-codec/simulcast switch is NOT pending.
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

        boolean sourceTL0IsActive = false;
        int sourceTL0Idx = sourceFrameDesc.getRTPEncoding().getIndex();
        if (sourceTL0Idx > -1)
        {
            sourceTL0Idx
                = sourceEncodings[sourceTL0Idx].getBaseLayer().getIndex();

            sourceTL0IsActive = sourceEncodings[sourceTL0Idx].isActive();
        }

        if (!sourceFrameDesc.isIndependent()
            || !sourceTL0IsActive // TODO this condition needs review
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
                bitstreamController = new BitstreamController(
                    bitstreamController, sourceTL0Idx);
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

        if (newTargetIdx < 0)
        {
            return;
        }

        // check whether it makes sense to send an FIR or not.
        MediaStreamTrackDesc sourceTrack = weakSource.get();
        if (sourceTrack == null)
        {
            return;
        }

        RTPEncodingDesc[] sourceEncodings = sourceTrack.getRTPEncodings();

        int currentTL0Idx = bitstreamController.getCurrentIndex();
        if (currentTL0Idx > -1)
        {
            currentTL0Idx
                = sourceEncodings[currentTL0Idx].getBaseLayer().getIndex();
        }

        int targetTL0Idx
            = sourceEncodings[newTargetIdx].getBaseLayer().getIndex();

        // Something lower than the current must be streaming, so we're able
        // to make a switch, so ask for a key frame.

        boolean sendFIR = targetTL0Idx < currentTL0Idx;
        if (!sendFIR && targetTL0Idx > currentTL0Idx)
        {
            // otherwise, check if anything higher is streaming.
            for (int i = currentTL0Idx + 1; i < targetTL0Idx + 1; i++)
            {
                if (sourceEncodings[i].isActive())
                {
                    sendFIR = true;
                    break;
                }
            }
        }

        if (sendFIR)
        {
            ((RTPTranslatorImpl) sourceTrack.getMediaStreamTrackReceiver()
                .getStream().getRTPTranslator())
                .getRtcpFeedbackMessageSender().sendFIR(
                (int) targetSSRC);
        }
    }

    /**
     * Update the optimal subjective quality index for this instance.
     *
     * @param optimalIndex new optimal subjective quality index.
     */
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
        if (!RTPPacketPredicate.INSTANCE.test(pktIn))
        {
            return new RawPacket[] { pktIn };
        }

        RawPacket[] pktsOut = bitstreamController.rtpTransform(pktIn);

        if (!ArrayUtils.isNullOrEmpty(pktsOut)
            && pktIn.getSSRCAsLong() != targetSSRC)
        {
            // Rewrite the SSRC of the output RTP stream.
            for (RawPacket pktOut : pktsOut)
            {
                if (pktOut != null)
                {
                    pktOut.setSSRC((int) targetSSRC);
                }
            }
        }

        return pktsOut;
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
        if (!RTCPPacketPredicate.INSTANCE.test(pktIn))
        {
            return pktIn;
        }

        BitstreamController bitstreamController = this.bitstreamController;

        // Drop SRs from other streams.
        boolean removed = false;
        RTCPIterator it = new RTCPIterator(pktIn);
        while (it.hasNext())
        {
            ByteArrayBuffer baf = it.next();
            switch (RTCPHeaderUtils.getPacketType(baf))
            {
            case RTCPPacket.SDES:
                if (removed)
                {
                    it.remove();
                }
                break;
            case RTCPPacket.SR:
                if (RawPacket.getRTCPSSRC(baf) != bitstreamController.getTL0SSRC())
                {
                    // SRs from other streams get axed.
                    removed = true;
                    it.remove();
                }
                else
                {
                    // Rewrite timestamp and transmission.
                    bitstreamController.rtcpTransform(baf);

                    // Rewrite senderSSRC
                    RTCPHeaderUtils.setSenderSSRC(baf, (int) targetSSRC);
                }
                break;
                case RTCPPacket.BYE:
                    // TODO rewrite SSRC.
                    break;
            }
        }

        return pktIn.getLength() > 0 ? pktIn : null;
    }
}
