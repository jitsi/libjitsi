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
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.function.*;

import java.lang.ref.*;
import java.util.*;

/**
 * @author George Politis
 */
public class BitstreamController
{
    /**
     * The available subjective quality indexes that this RTP stream offers.
     */
    private final int[] availableIdx;

    /**
     * The sequence number offset that this bitstream started.
     */
    private final int seqNumOff;

    /**
     * The timestamp offset that this bitstream started.
     */
    private final long tsOff;

    /**
     * The SSRC of the TL0 of the RTP stream that is currently being
     * forwarded.
     */
    private final long tl0SSRC;

    /**
     * A weak reference to the {@link MediaStreamTrackDesc} that this controller
     * is associated to.
     */
    private final WeakReference<MediaStreamTrackDesc> weakSource;

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
     * The number of transmitted bytes.
     */
    private long transmittedBytes;

    /**
     * The number of transmitted packets.
     */
    private long transmittedPackets;

    /**
     * The max (biggest timestamp) frame that we've sent out.
     */
    private FrameController maxSentFrame;

    /**
     * At 60fps, this holds 5 seconds worth of frames.
     * At 30fps, this holds 10 seconds worth of frames.
     */
    private Map<Long, FrameController> seenFrames
        = Collections.synchronizedMap(new LRUCache<Long, FrameController>(300));

    /**
     * Ctor.
     *
     * @param bc the previous {@link BitstreamController} (simulcast case).
     * @param sourceTL0Idx the TL0 of the current bitstream.
     */
    BitstreamController(BitstreamController bc, int sourceTL0Idx)
    {
        this(bc.weakSource, bc.getMaxSeqNum(), bc.getMaxTs(),
            bc.transmittedBytes, bc.transmittedPackets,
            sourceTL0Idx, bc.targetIdx, bc.optimalIdx);
    }

    /**
     * Ctor.
     */
    BitstreamController(
        MediaStreamTrackDesc source, int tl0Idx, int targetIdx, int optimalIdx)
    {
        this(new WeakReference<>(source),
            -1, -1, 0, 0, tl0Idx, targetIdx, optimalIdx);
    }

    /**
     * Ctor.
     */
    private BitstreamController(
        WeakReference<MediaStreamTrackDesc> weakSource,
        int seqNumOff, long tsOff,
        long transmittedBytes, long transmittedPackets,
        int initialIdx, int targetIdx, int optimalIdx)
    {
        this.seqNumOff = seqNumOff;
        this.tsOff = tsOff;
        this.transmittedBytes = transmittedBytes;
        this.transmittedPackets = transmittedPackets;
        this.optimalIdx = optimalIdx;
        this.weakSource = weakSource;

        MediaStreamTrackDesc source = weakSource.get();
        assert source != null;
        RTPEncodingDesc[] rtpEncodings = source.getRTPEncodings();
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            this.availableIdx = null;
            this.tl0SSRC = -1;
        }
        else
        {
            int initialTL0Idx = initialIdx;
            if (initialIdx > -1)
            {
                initialTL0Idx
                    = rtpEncodings[initialTL0Idx].getBaseLayer().getIndex();
            }

            // find the available qualities in this bitstream.

            // TODO optimize if we have a single quality
            List<Integer> availableQualities = new ArrayList<>();
            for (int i = 0; i < rtpEncodings.length; i++)
            {
                if (rtpEncodings[i].requires(initialTL0Idx))
                {
                    availableQualities.add(i);
                }
            }

            availableIdx = new int[availableQualities.size()];
            Iterator<Integer> iterator = availableQualities.iterator();
            for (int i = 0; i < availableIdx.length; i++)
            {
                availableIdx[i] = iterator.next();
            }

            if (initialTL0Idx > -1)
            {
                tl0SSRC = rtpEncodings[initialTL0Idx].getPrimarySSRC();
            }
            else
            {
                tl0SSRC = -1;
            }
        }

        setTargetIndex(targetIdx);
    }

    /**
     *
     * @param sourceFrameDesc
     * @param buf
     * @param off
     * @param len
     * @return
     */
    public boolean accept(
        FrameDesc sourceFrameDesc, byte[] buf, int off, int len)
    {
        long srcTs = sourceFrameDesc.getTimestamp();
        FrameController destFrame = seenFrames.get(srcTs);

        if (destFrame == null)
        {
            // An unseen frame has arrived. We forward it iff all of the
            // following conditions hold:
            //
            // 1. it's a dependency of the currentIdx (obviously).
            // 2. it's newer than max frame (we can't modify the distances of
            // past frames).
            // 2. we know the boundaries of the max frame OR if the max frame is
            // *not* a TL0 (this allows for non-TL0 to be corrupt).
            //
            // Given the above conditions, we might decide to drop a TL0 frame.
            // This can happen when the max frame is a TL0 and we don't know its
            // boundaries. Then the stream will be broken an we should ask for a
            // key frame.

            int currentIdx = this.currentIdx;

            if (currentIdx >= 0 && (maxSentFrame == null
                || TimeUtils.rtpDiff(srcTs, maxSentFrame.srcTs) > 0))
            {
                // the stream is not suspended and we're not dealing with a late
                // frame.

                RTPEncodingDesc sourceEncodings[] = sourceFrameDesc
                    .getRTPEncoding().getMediaStreamTrack().getRTPEncodings();

                int sourceIdx = sourceFrameDesc.getRTPEncoding().getIndex();
                if (sourceEncodings[currentIdx].requires(sourceIdx))
                {
                    // the quality of the frame is a dependency of the
                    // forwarded quality.

                    // TODO ask for independent frame if we're corrupting a TL0.

                    int maxSeqNum = getMaxSeqNum();
                    SeqnumTranslation seqnumTranslation;
                    if (maxSeqNum > -1)
                    {
                        int seqNumDelta = (maxSeqNum
                            + 1 - sourceFrameDesc.getStart()) & 0xFFFF;

                        seqnumTranslation = new SeqnumTranslation(seqNumDelta);
                    }
                    else
                    {
                        seqnumTranslation = null;
                    }

                    long maxTs = getMaxTs();
                    TimestampTranslation tsTranslation;
                    if (maxTs > -1)
                    {
                        long tsDelta = (maxTs
                            + 3000 - sourceFrameDesc.getTimestamp()) & 0xFFFFFFFFL;

                        tsTranslation = new TimestampTranslation(tsDelta);
                    }
                    else
                    {
                        tsTranslation = null;
                    }

                    destFrame = new FrameController(
                        srcTs, seqnumTranslation, tsTranslation);
                    seenFrames.put(sourceFrameDesc.getTimestamp(), destFrame);
                    maxSentFrame = destFrame;
                }
                else
                {
                    destFrame = new FrameController(srcTs, null, null);
                    seenFrames.put(sourceFrameDesc.getTimestamp(), destFrame);
                }
            }
            else
            {
                destFrame = new FrameController(srcTs, null, null);
                seenFrames.put(sourceFrameDesc.getTimestamp(), destFrame);
            }
        }

        boolean accept = destFrame.accept(
            maxSentFrame == destFrame, sourceFrameDesc, buf, off, len);


        if (accept)
        {
            transmittedPackets++;
            transmittedBytes += len;
        }

        return accept;
    }

    /**
     * {@inheritDoc}
     */
    int getOptimalIndex()
    {
        return optimalIdx;
    }

    /**
     * {@inheritDoc}
     */
    int getCurrentIndex()
    {
        return currentIdx;
    }

    /**
     * {@inheritDoc}
     */
    int getTargetIndex()
    {
        return targetIdx;
    }

    /**
     * {@inheritDoc}
     */
    void setTargetIndex(int newTargetIdx)
    {
        this.targetIdx = newTargetIdx;
        if (newTargetIdx < 0)
        {
            // suspend the stream
            currentIdx = newTargetIdx;
        }
        else
        {
            int currentIdx = availableIdx[0];
            for (int i = 1; i < availableIdx.length; i++)
            {
                if (availableIdx[i] <= targetIdx)
                {
                    currentIdx = availableIdx[i];
                }
                else
                {
                    break;
                }
            }

            this.currentIdx = currentIdx;
        }
    }

    /**
     * {@inheritDoc}
     */
    void setOptimalIndex(int newOptimalIdx)
    {
        this.optimalIdx = newOptimalIdx;
    }

    /**
     *
     * @param pktIn
     * @return
     */
    RawPacket[] rtpTransform(RawPacket pktIn)
    {
        FrameController destFrame = seenFrames.get(pktIn.getTimestamp());
        if (destFrame == null)
        {
            return null;
        }

        return destFrame.rtpTransform(pktIn);
    }

    /**
     *
     * @param pktIn
     * @return
     */
    RawPacket rtcpTransform(RawPacket pktIn)
    {
        // Drop SRs from other streams.
        RTCPIterator it = new RTCPIterator(pktIn);
        while (it.hasNext())
        {
            ByteArrayBuffer baf = it.next();
            switch (RTCPHeaderUtils.getPacketType(baf))
            {
            case RTCPPacket.SR:
                if (RawPacket.getRTCPSSRC(baf) != tl0SSRC)
                {
                    continue;
                }
                // Rewrite timestamp.
                if (maxSentFrame != null
                    && maxSentFrame.getTimestampTranslation() != null)
                {
                    long srcTs = RTCPSenderInfoUtils.getTimestamp(pktIn);
                    // FIXME what if maxSentFrame == null?
                    long dstTs
                        = maxSentFrame.getTimestampTranslation().apply(srcTs);

                    if (srcTs != dstTs)
                    {
                        RTCPSenderInfoUtils.setTimestamp(pktIn, (int) dstTs);
                    }
                }

                // Rewrite packet/octet count.
                RTCPSenderInfoUtils.setOctetCount(pktIn, (int) transmittedBytes);
                RTCPSenderInfoUtils.setPacketCount(pktIn, (int) transmittedPackets);
                break;
            }
        }

        return pktIn;
    }

    /**
     * Gets the maximum sequence number that this instance has accepted.
     */
    private int getMaxSeqNum()
    {
        return maxSentFrame == null ? seqNumOff : maxSentFrame.getMaxSeqNum();
    }

    /**
     * Gets the maximum timestamp that this instance has accepted.
     */
    private long getMaxTs()
    {
        return maxSentFrame == null ? tsOff : maxSentFrame.getTs();
    }

    long getTL0SSRC()
    {
        return tl0SSRC;
    }

    class FrameController
    {
        /**
         * The sequence number translation to apply to accepted RTP packets.
         */
        private final SeqnumTranslation seqNumTranslation;

        /**
         * The RTP timestamp translation to apply to accepted RTP/RTCP packets.
         */
        private final TimestampTranslation tsTranslation;

        /**
         * A boolean that indicates whether or not the transform thread should
         * try to piggyback missed packets from the initial key frame.
         */
        private boolean maybeFixInitialIndependentFrame = true;

        /**
         * The source timestamp of this frame.
         */
        private final long srcTs;

        /**
         * The maximum source sequence number to accept. -1 means drop.
         */
        private int srcSeqNumLimit = -1;

        /**
         * The start source sequence number of this frame.
         */
        private int srcSeqNumStart = -1;

        /**
         *
         * @param ts
         * @param seqnumTranslation
         * @param tsTranslation
         */
        FrameController(long ts, SeqnumTranslation seqnumTranslation,
                        TimestampTranslation tsTranslation)
        {
            this.srcTs = ts;
            this.seqNumTranslation = seqnumTranslation;
            this.tsTranslation = tsTranslation;
        }

        boolean accept(boolean expand, FrameDesc source, byte[] buf, int off, int len)
        {
            if (expand)
            {
                if (srcSeqNumLimit == -1
                    || RTPUtils.sequenceNumberDiff(source.getMaxSeen(), srcSeqNumLimit) > 0)
                {
                    srcSeqNumLimit = source.getMaxSeen();
                }
            }

            if (srcSeqNumLimit == -1)
            {
                return false;
            }

            if (srcSeqNumStart == -1
                || RTPUtils.sequenceNumberDiff(srcSeqNumStart, source.getMinSeen()) > 0)
            {
                srcSeqNumStart = source.getMinSeen();
            }

            int seqNum = RawPacket.getSequenceNumber(buf, off, len);

            return RTPUtils.sequenceNumberDiff(seqNum, srcSeqNumLimit) <= 0;
        }

        /**
         * {@inheritDoc}
         */
        RawPacket[] rtpTransform(RawPacket pktIn)
        {
            RawPacket[] pktsOut;
            long srcSSRC = pktIn.getSSRCAsLong();

            if (maybeFixInitialIndependentFrame)
            {
                maybeFixInitialIndependentFrame = false;

                if (srcSeqNumStart != pktIn.getSequenceNumber())
                {
                    // Piggy back till max seen.
                    RawPacketCache inCache = weakSource.get()
                        .getMediaStreamTrackReceiver()
                        .getStream()
                        .getCachingTransformer()
                        .getIncomingRawPacketCache();

                    int len = RTPUtils.sequenceNumberDiff(
                        srcSeqNumLimit, srcSeqNumStart) + 1;
                    pktsOut = new RawPacket[len];
                    for (int i = 0; i < pktsOut.length; i++)
                    {
                        // Note that the ingress cache might not have the desired
                        // packet.
                        pktsOut[i] = inCache.get(srcSSRC, (srcSeqNumStart + i) & 0xFFFF);
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
            }
            return pktsOut;
        }

        TimestampTranslation getTimestampTranslation()
        {
            return tsTranslation;
        }

        int getMaxSeqNum()
        {
            return seqNumTranslation == null
                ? srcSeqNumLimit : seqNumTranslation.apply(srcSeqNumLimit);
        }

        long getTs()
        {
            return tsTranslation == null ? srcTs : tsTranslation.apply(srcTs);
        }
    }
}
