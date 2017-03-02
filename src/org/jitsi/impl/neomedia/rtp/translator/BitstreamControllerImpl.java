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
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.function.*;

import java.lang.ref.*;

/**
 * @author George Politis
 */
public class BitstreamControllerImpl
    implements BitstreamController
{
    /**
     * A {@link WeakReference} to the {@link MediaStreamTrackDesc} that feeds
     * this instance with RTP/RTCP packets.
     */
    private final WeakReference<MediaStreamTrackDesc> weakSource;

    /**
     * The subjective quality index of the TL0 of this RTP stream.
     */
    private final int tl0Idx;

    /**
     * The SSRC to protect when probing for bandwidth ({@see PaddingParams})
     */
    private final long targetSSRC;

    /**
     * The current SSRC that is currently being forwarded.
     */
    private final long tl0SSRC;

    /**
     * The target subjective quality index for this instance. This instance
     * switches between the available RTP streams and sub-encodings until it
     * reaches this target. -1 effectively means that the stream is suspended.
     */
    private int targetIdx;

    /**
     * The optimal subjective quality index for this instance.
     */
    private int optimalIdx;

    /**
     * The current subjective quality index for this instance. If this is
     * different than the target, then a switch is pending.
     */
    private int currentIdx;

    /**
     * The maximum sequence number (mod 2^16) that this instance has sent out.
     */
    private int maxSeqNum;

    /**
     * The maximum timestamp (mod 2^32) that this instance has sent out.
     */
    private long maxTs;

    /**
     * The number of transmitted bytes.
     */
    private long transmittedBytes;

    /**
     * The number of transmitted packets.
     */
    private long transmittedPackets;

    /**
     * A boolean that indicates whether or not the transform thread should
     * try to piggyback missed packets from the initial key frame.
     */
    private boolean maybeFixInitialIndependentFrame = true;

    /**
     *
     */
    private WeakReference<FrameDesc> weakStartFrame;

    /**
     * The RTP timestamp translation to apply to accepted RTP/RTCP packets.
     */
    private TimestampTranslation tsTranslation;

    /**
     * The sequence number translation to apply to accepted RTP packets.
     */
    private SeqnumTranslation seqNumTranslation;

    /**
     * Ctor.
     */
    BitstreamControllerImpl(
        MediaStreamTrackDesc source, int tl0Idx, int targetIdx, int optimalIdx)
    {
        this(source, null, -1, -1, 0, 0, tl0Idx, targetIdx, optimalIdx);
    }

    /**
     * Ctor.
     */
    BitstreamControllerImpl(
        MediaStreamTrackDesc source, FrameDesc sourceFrameDesc,
        int seqNumOff, long tsOff,
        long transmittedBytes, long transmittedPackets,
        int tl0Idx, int targetIdx, int optimalIdx)
    {
        assert source != null;
        this.weakSource = new WeakReference<>(source);
        this.weakStartFrame = sourceFrameDesc != null
            ? new WeakReference<>(sourceFrameDesc) : null;

        this.maxSeqNum = seqNumOff;
        this.maxTs = tsOff;

        this.onStartFrameChanged();

        RTPEncodingDesc[] rtpEncodings = source.getRTPEncodings();
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            this.targetSSRC = -1;
            this.tl0SSRC = -1;
        }
        else
        {
            this.targetSSRC = rtpEncodings[0].getPrimarySSRC();

            if (tl0Idx > -1)
            {
                this.tl0SSRC = rtpEncodings[tl0Idx].getPrimarySSRC();
            }
            else
            {
                this.tl0SSRC = -1;
            }
        }

        this.transmittedBytes = transmittedBytes;
        this.transmittedPackets = transmittedPackets;

        this.tl0Idx = tl0Idx;
        this.optimalIdx = optimalIdx;
        this.targetIdx = targetIdx;
        this.currentIdx = -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean accept(
        FrameDesc sourceFrameDesc, byte[] buf, int off, int len)
    {
        RTPEncodingDesc sourceEncodings[] = sourceFrameDesc.getRTPEncoding()
            .getMediaStreamTrack().getRTPEncodings();

        int sourceTL0Idx
            = sourceEncodings[sourceFrameDesc.getRTPEncoding().getIndex()]
            .getBaseLayer().getIndex();

        if (targetIdx < 0 /* suspended */
            || sourceTL0Idx != tl0Idx /* not interested */)
        {
            return false;
        }

        if (currentIdx < 0 && sourceFrameDesc.isIndependent())
        {
            // Resume.
            // FIXME assumes 3 TLs.
            currentIdx = tl0Idx + 2;
            weakStartFrame = new WeakReference<>(sourceFrameDesc);
            onStartFrameChanged();
        }

        long ts = RawPacket.getTimestamp(buf, off, len);
        if (tsTranslation != null)
        {
            ts = tsTranslation.apply(ts);
        }

        if (maxTs == -1 || TimeUtils.rtpDiff(ts, maxTs) > 0)
        {
            maxTs = ts;
        }

        int seqNum = RawPacket.getSequenceNumber(buf, off, len);
        if (seqNumTranslation != null)
        {
            seqNum = seqNumTranslation.apply(seqNum);
        }

        if (maxSeqNum == -1
            || RTPUtils.sequenceNumberDiff(seqNum, maxSeqNum) > 0)
        {
            maxSeqNum = seqNum;
        }

        transmittedBytes += len;
        transmittedPackets++;

        return true;
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

        int currentIdx = this.currentIdx;
        if (currentIdx > -1 && sourceEncodings[currentIdx].isActive())
        {
            currentBps = sourceEncodings[currentIdx].getLastStableBitrateBps();
        }

        long optimalBps = 0;
        int optimalIdx = this.optimalIdx;
        if (optimalIdx >= 0)
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
     * {@inheritDoc}
     */
    @Override
    public RawPacket[] rtpTransform(RawPacket pktIn)
    {
        if (!RTPPacketPredicate.INSTANCE.test(pktIn))
        {
            return new RawPacket[] { pktIn };
        }

        long srcSSRC = pktIn.getSSRCAsLong();
        if (srcSSRC != tl0SSRC)
        {
            // We do _not_ forward packets from SSRCs other than the
            // current SSRC.
            return null;
        }

        RawPacket[] pktsOut;

        FrameDesc startFrame;
        if (maybeFixInitialIndependentFrame
            && (startFrame = weakStartFrame.get()) != null
            && startFrame.matches(pktIn))
        {
            maybeFixInitialIndependentFrame = false;

            if (startFrame.getStart() != pktIn.getSequenceNumber())
            {
                // Piggy back till max seen.
                RawPacketCache inCache = startFrame
                    .getRTPEncoding()
                    .getMediaStreamTrack()
                    .getMediaStreamTrackReceiver()
                    .getStream()
                    .getCachingTransformer()
                    .getIncomingRawPacketCache();

                int start = startFrame.getStart();
                int len = RTPUtils.sequenceNumberDiff(
                    startFrame.getMaxSeen(), start) + 1;
                pktsOut = new RawPacket[len];
                for (int i = 0; i < pktsOut.length; i++)
                {
                    // Note that the ingress cache might not have the desired
                    // packet.
                    pktsOut[i] = inCache.get(srcSSRC, (start + i) & 0xFFFF);
                }
            }
            else
            {
                pktsOut = new RawPacket[] { pktIn };
            }
        }
        else
        {
            pktsOut = new RawPacket[]{ pktIn };
        }

        for (RawPacket pktOut : pktsOut)
        {
            // Note that the ingress cache might not have the desired packet.
            if (pktOut == null)
            {
                continue;
            }

            if (seqNumTranslation != null)
            {
                int srcSeqNum = pktOut.getSequenceNumber();
                int dstSeqNum = seqNumTranslation.apply(srcSeqNum);

                if (srcSeqNum != dstSeqNum)
                {
                    pktOut.setSequenceNumber(dstSeqNum);
                }
            }

            if (tsTranslation != null)
            {
                long srcTs = pktOut.getTimestamp();
                long dstTs = tsTranslation.apply(srcTs);

                if (dstTs != srcTs)
                {
                    pktOut.setTimestamp(dstTs);
                }
            }

            if (srcSSRC != targetSSRC)
            {
                pktOut.setSSRC((int) targetSSRC);
            }
        }
        return pktsOut;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket rtcpTransform(RawPacket pktIn)
    {
        if (!RTCPPacketPredicate.INSTANCE.test(pktIn))
        {
            return pktIn;
        }

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

                if (RawPacket.getRTCPSSRC(baf) != getTL0SSRC())
                {
                    // SRs from other streams get axed.
                    removed = true;
                    it.remove();
                }
                else
                {
                    // Rewrite senderSSRC
                    RTCPHeaderUtils.setSenderSSRC(baf, (int) targetSSRC);

                    // Rewrite timestamp.
                    if (tsTranslation != null)
                    {
                        long srcTs = RTCPSenderInfoUtils.getTimestamp(baf);
                        long dstTs = tsTranslation.apply(srcTs);

                        if (srcTs != dstTs)
                        {
                            RTCPSenderInfoUtils.setTimestamp(baf, (int) dstTs);
                        }
                    }

                    // Rewrite packet/octet count.
                    RTCPSenderInfoUtils
                        .setOctetCount(baf, (int) transmittedBytes);
                    RTCPSenderInfoUtils
                        .setPacketCount(baf, (int) transmittedPackets);
                }
            }
        }

        return pktIn.getLength() > 0 ? pktIn : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTransmittedBytes()
    {
        return transmittedBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTransmittedPackets()
    {
        return transmittedPackets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOptimalIndex()
    {
        return optimalIdx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCurrentIndex()
    {
        return currentIdx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getTargetIndex()
    {
        return targetIdx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTargetIndex(int newTargetIdx)
    {
        this.targetIdx = newTargetIdx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOptimalIndex(int newOptimalIdx)
    {
        this.optimalIdx = newOptimalIdx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaStreamTrackDesc getSource()
    {
        return weakSource.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxSeqNum()
    {
        return maxSeqNum;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxTs()
    {
        return maxTs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTL0SSRC()
    {
        return tl0SSRC;
    }

    /**
     *
     */
    private void onStartFrameChanged()
    {
        maybeFixInitialIndependentFrame = true;
        FrameDesc sourceFrameDesc
            = weakStartFrame != null ? weakStartFrame.get() : null;
        if (sourceFrameDesc != null && maxSeqNum > -1)
        {
            int seqNumDelta = (maxSeqNum
                + 1 - sourceFrameDesc.getStart()) & 0xFFFF;


            long tsDelta = (maxTs
                + 3000 - sourceFrameDesc.getTimestamp()) & 0xFFFFFFFFL;

            tsTranslation = new TimestampTranslation(tsDelta);
            seqNumTranslation = new SeqnumTranslation(seqNumDelta);
        }
        else
        {
            tsTranslation = null;
            seqNumTranslation = null;
        }
    }
}
