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
 * Filters the packets of {@link MediaStreamTrackDesc} based on the currently
 * forwarded subjective quality index. It's also taking care of upscaling and
 * downscaling. As a {@link PacketTransformer}, it rewrites the forwarded
 * packets so that the gaps as a result of the drops are hidden.
 *
 * @author George Politis
 */
public class SimulcastController
{
    /**
     * The {@link Logger} to be used by this instance to print debug
     * information.
     */
    private static final Logger logger
        = Logger.getLogger(SimulcastController.class);

    /**
     * The transformation to use when a stream is suspended (or equivalently
     * when the target idx = -1.
     */
    private static final SimTransformation dropState
        = new SimTransformation(-1, -1, null);

    /**
     * The target SSRC is the primary SSRC of the first encoding of the source.
     */
    private final long targetSSRC;

    /**
     * A {@link WeakReference} to the {@link MediaStreamTrackDesc} that feeds
     * this instance with RTP/RTCP packets.
     */
    private final WeakReference<MediaStreamTrackDesc> weakSource;

    /**
     * The state for the filtering thread. R/W by the filtering thread.
     */
    private final FilterState filterState = new FilterState();

    /**
     * The target subjective quality index for this instance. This instance
     * switches between the available RTP streams and sub-encodings until it
     * reaches this target. -1 effectively means that the stream is suspended.
     */
    private int targetIdx = -1;

    /**
     * Read by the transform thread. Written by the filter thread.
     */
    private SimTransformation transformState = dropState;

    /**
     * Ctor.
     *
     * @param source the {@link MediaStreamTrackDesc} that feeds this instance
     * with RTP/RTCP packets.
     */
    public SimulcastController(MediaStreamTrackDesc source)
    {
        this.weakSource = new WeakReference<>(source);
        this.targetSSRC = source.getRTPEncodings()[0].getPrimarySSRC();
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
        MediaStreamTrackDesc sourceTrack = weakSource.get();

        // If we're getting packets here => the MST is alive.
        assert sourceTrack != null;

        long sourceSSRC = RawPacket.getSSRCAsLong(buf, off, len);

        boolean currentRTPEncodingIsActive = false;
        if (transformState.currentIdx > -1)
        {
            RTPEncodingDesc currentRTPEncodingDesc
                = sourceTrack.getRTPEncodings()[transformState.currentIdx];

            currentRTPEncodingIsActive = currentRTPEncodingDesc.isActive();
        }

        if (transformState.currentIdx == targetIdx && currentRTPEncodingIsActive)
        {
            boolean accept = sourceSSRC == transformState.currentSSRC;

            if (accept)
            {
                onAccept(buf, off, len);
            }

            return accept;
        }

        // An intra-codec/simulcast switch pending.

        FrameDesc sourceFrameDesc
            = sourceTrack.findFrameDesc(buf, off, len);

        int sourceIdx = sourceFrameDesc.getRTPEncoding().getIndex();

        if (!sourceFrameDesc.isIndependent()
            || !sourceFrameDesc.getRTPEncoding().isActive()
            || sourceIdx == transformState.currentIdx)
        {
            // An intra-codec switch requires a key frame.
            boolean accept = sourceSSRC == transformState.currentSSRC;

            if (accept)
            {
                onAccept(buf, off, len);
            }

            return accept;
        }

        if ((targetIdx <= sourceIdx && sourceIdx < transformState.currentIdx)
            || (transformState.currentIdx < sourceIdx && sourceIdx <= targetIdx)
            || (!currentRTPEncodingIsActive && sourceIdx <= targetIdx))
        {
            // Pretend this is the next frame of whatever has already been
            // sent.

            long tsDelta; int seqNumDelta;
            if (filterState.maxSeqNum != -1)
            {
                seqNumDelta = (filterState.maxSeqNum
                    + 1 - sourceFrameDesc.getStart()) & 0xFFFF;
                tsDelta = (filterState.maxTs
                    + 3000 - sourceFrameDesc.getTimestamp()) & 0xFFFFFFFFL;
            }
            else
            {
                seqNumDelta = 0; tsDelta = 0;
            }

            if (logger.isInfoEnabled())
            {
                logger.info("new_transform src_ssrc=" + sourceSSRC
                    + ",src_idx=" + sourceIdx
                    + ",ts_delta=" + tsDelta
                    + ",seq_delta=" + seqNumDelta);
            }
            transformState = new SimTransformation(
                tsDelta, seqNumDelta, sourceFrameDesc);

            onAccept(buf, off, len);

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Update the target subjective quality index for this instance.
     *
     * @param targetIdx new target subjective quality index.
     */
    public void update(int targetIdx)
    {
        if (this.targetIdx == targetIdx)
        {
            return;
        }

        if (logger.isInfoEnabled())
        {
            MediaStreamTrackDesc sourceTrack = weakSource.get();
            if (sourceTrack != null)
            {
                RTPEncodingDesc[] sourceEncodings
                    = sourceTrack.getRTPEncodings();
                if (!ArrayUtils.isNullOrEmpty(sourceEncodings))
                {
                    long ssrc = sourceEncodings[0].getPrimarySSRC();
                    logger.info("target_update ssrc=" + ssrc
                        + ",new_target=" + targetIdx
                        + ",old_target=" + this.targetIdx);
                }
            }
        }

        this.targetIdx = targetIdx;
        if (targetIdx < 0)
        {
            // suspend the stream.
            transformState = dropState;
        }
        else
        {
            // send FIR.
            MediaStreamTrackDesc sourceTrack = weakSource.get();
            if (sourceTrack == null)
            {
                return;
            }

            ((RTPTranslatorImpl) sourceTrack.getMediaStreamTrackReceiver()
                .getStream().getRTPTranslator())
                .getRtcpFeedbackMessageSender().sendFIR((int) targetSSRC);
        }
    }

    /**
     * Increments the {@link #filterState} as a result of accepting the packet
     * specified in the arguments.
     *
     * @param buf the <tt>byte</tt> array that holds the RTP packet.
     * @param off the offset in <tt>buffer</tt> at which the actual RTP data
     * begins.
     * @param len the number of <tt>byte</tt>s in <tt>buffer</tt> which
     * constitute the actual RTP data.
     */
    private void onAccept(byte[] buf, int off, int len)
    {
        long ts = transformState
            .rewriteTimestamp(RawPacket.getTimestamp(buf, off, len));

        if (filterState.maxTs == -1
            || TimeUtils.rtpDiff(ts, filterState.maxTs) > 0)
        {
            filterState.maxTs = ts;
        }

        int seqNum = transformState
            .rewriteSeqNum(RawPacket.getSequenceNumber(buf, off, len));

        if (filterState.maxSeqNum == -1
            || RTPUtils.sequenceNumberDiff(seqNum, filterState.maxSeqNum) > 0)
        {
            filterState.maxSeqNum = seqNum;
        }

        filterState.transmittedBytes += len;
        filterState.transmittedPackets++;
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

        SimTransformation state = transformState;

        long srcSSRC = pktIn.getSSRCAsLong();
        if (srcSSRC != state.currentSSRC)
        {
            // We do _not_ forward packets from SSRCs other than the
            // current SSRC.
            return null;
        }

        RawPacket[] pktsOut;

        FrameDesc startFrame;
        if (transformState.maybeFixInitialIndependentFrame
            && (startFrame = state.weakStartFrame.get()) != null
            && startFrame.matches(pktIn))
        {
            transformState.maybeFixInitialIndependentFrame = false;

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

            int srcSeqNum = pktOut.getSequenceNumber();
            int dstSeqNum = state.rewriteSeqNum(srcSeqNum);

            long srcTs = pktOut.getTimestamp();
            long dstTs = state.rewriteTimestamp(srcTs);

            if (logger.isDebugEnabled())
            {
                logger.debug("sim_rewrite src_ssrc=" + pktIn.getSSRCAsLong()
                    + ",src_seq=" + srcSeqNum
                    + ",src_ts=" + srcTs
                    + ",dst_ssrc=" + targetSSRC
                    + ",dst_seq=" + dstSeqNum
                    + ",dst_ts=" + dstTs);
            }

            if (srcSeqNum != dstSeqNum)
            {
                pktOut.setSequenceNumber(dstSeqNum);
            }

            if (dstTs != srcTs)
            {
                pktOut.setTimestamp(dstTs);
            }

            if (srcSSRC != targetSSRC)
            {
                pktOut.setSSRC((int) targetSSRC);
            }
        }

        return pktsOut;
    }

    /**
     * Transform an RTCP {@link RawPacket} for the purposes of simulcast.
     *
     * @param pkt the {@link RawPacket} to be transformed.
     * @return the transformed RTCP {@link RawPacket}, or null if the packet
     * needs to be dropped.
     */
    public RawPacket rtcpTransform(RawPacket pkt)
    {
        if (!RTCPPacketPredicate.INSTANCE.test(pkt))
        {
            return pkt;
        }

        SimTransformation state = transformState;

        boolean removed = false;
        RTCPIterator it = new RTCPIterator(pkt);
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

                if (RawPacket.getRTCPSSRC(pkt) != state.currentSSRC)
                {
                    // SRs from other streams get axed.
                    removed = true;
                    it.remove();
                }
                else
                {
                    // Rewrite senderSSRC
                    RTCPHeaderUtils.setSenderSSRC(pkt, (int) targetSSRC);

                    // Rewrite timestamp.
                    long srcTs = RTCPSenderInfoUtils.getTimestamp(baf);
                    long dstTs = state.rewriteTimestamp(srcTs);

                    if (srcTs != dstTs)
                    {
                        RTCPSenderInfoUtils.setTimestamp(baf, (int) dstTs);
                    }

                    // Rewrite packet/octet count.
                    RTCPSenderInfoUtils.setOctetCount(
                        baf, (int) filterState.transmittedBytes);
                    RTCPSenderInfoUtils.setPacketCount(
                        baf, (int) filterState.transmittedPackets);
                }
            }
        }

        return pkt.getLength() > 0 ? pkt : null;
    }

    /**
     * The 2D translation to apply to outgoing RTP/RTCP packets for the purposes
     * of simulcast.
     *
     * NOTE(gp) Once we move to Java 8, I want this to be implemented as a
     * Function.
     */
    private static class SimTransformation
        extends Transformation
    {
        /**
         * Ctor.
         *
         * @param startFrame the frame that has caused this transformation.
         * @param tsDelta the RTP timestamp delta (mod 2^32) to apply to
         * outgoing RTP/RTCP packets.
         * @param seqNumDelta the RTP sequence number delta (mod 2^16) to apply
         * to outgoing RTP packets.
         */
        SimTransformation(long tsDelta, int seqNumDelta, FrameDesc startFrame)
        {
            super(tsDelta, seqNumDelta);
            if (startFrame != null)
            {
                this.currentSSRC = startFrame.getRTPEncoding().getPrimarySSRC();
                this.currentIdx = startFrame.getRTPEncoding().getIndex();
            }
            else
            {
                this.currentIdx = -1;
                this.currentSSRC = -1;
            }
            this.weakStartFrame = new WeakReference<>(startFrame);
        }

        private final WeakReference<FrameDesc> weakStartFrame;


        /**
         * The current SSRC that is currently being forwarded.
         */
        private final long currentSSRC;

        /**
         * The current subjective quality index for this instance. If this is
         * different than the target, then a switch is pending.
         */
        private final int currentIdx;

        /**
         * A boolean that indicates whether or not the transform thread should
         * try to piggyback missed packets from the initial key frame.
         */
        private boolean maybeFixInitialIndependentFrame = true;
    }

    /**
     * State that is kept by the filter thread. It includes the maximum RTP
     * sequence number and the maximum RTP timestamp that was accepted.
     */
    private static class FilterState
    {
        /**
         * The maximum sequence number (mod 2^16) that this instance has sent
         * out.
         */
        private int maxSeqNum = -1;

        /**
         * The maximum timestamp (mod 2^32) that this instance has sent out.
         */
        private long maxTs = -1;

        /**
         * The number of transmitted bytes.
         */
        private long transmittedBytes = 0;

        /**
         * The number of transmitted packets.
         */
        private long transmittedPackets = 0;
    }
}
