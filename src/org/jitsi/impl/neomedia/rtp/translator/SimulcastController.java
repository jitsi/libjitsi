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
 * Filters the packets of
 * {@link MediaStreamTrackDesc} based on the currently forwarded subjective
 * quality index. It's also taking care of upscaling and downscaling. As a
 * {@link PacketTransformer}, it rewrites the forwarded packets so that the
 * gaps as a result of the drops are hidden.
 *
 * @author George Politis
 */
public class SimulcastController
    implements TransformEngine
{
    /**
     * The {@link Logger} to be used by this instance to print debug
     * information.
     */
    private final Logger logger = Logger.getLogger(SimulcastController.class);

    private static final SimTransformation dropState
        = new SimTransformation(-1, -1, -1, -1);

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
     * The {@link PacketTransformer} that handles incoming/outgoing RTP
     * packets for this {@link SimulcastController} instance.
     */
    private final RTPTransformer rtpTransformer = new RTPTransformer();

    /**
     * The {@link PacketTransformer} that handles incoming/outgoing RTCP
     * packets for this {@link SimulcastController} instance.
     */
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();

    /**
     * The state for the filtering thread. R/W by the filtering thread.
     */
    private final FilterState filterState = new FilterState();

    /**
     * The target subjective quality index for this instance. This instance
     * switches between the available RTP streams and sub-encodings until it
     * reaches this target.
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
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return rtpTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    /**
     *
     * @param data
     * @param buf
     * @param off
     * @param len
     * @return
     */
    public boolean rtpTranslatorWillWrite(
        boolean data, byte[] buf, int off, int len)
    {
        MediaStreamTrackDesc sourceTrack = weakSource.get();

        // If we're getting packets here => the MST is alive.
        assert sourceTrack != null;

        long sourceSSRC;
        if (data)
        {
            sourceSSRC = RawPacket.getSSRCAsLong(buf, off, len);
        }
        else
        {
            // NOTE(gp) This will need to be adjusted when we enable
            // reduced-size RTCP.
            sourceSSRC = RTCPHeaderUtils.getSenderSSRC(buf, off, len);
            return sourceSSRC == transformState.currentSSRC;
        }

        boolean currentRTPEncodingIsActive = false;
        if (transformState.currentIdx > -1)
        {
            RTPEncodingDesc
                currentRTPEncodingDesc = sourceTrack
                .getRTPEncodings()[transformState.currentIdx];

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

        if (((targetIdx <= sourceIdx && sourceIdx < transformState.currentIdx)
            || (transformState.currentIdx < sourceIdx && sourceIdx <= targetIdx)
            || !currentRTPEncodingIsActive && sourceIdx <= targetIdx))
        {
            // Pretend this is the next frame of whatever has already been
            // sent.

            long tsDelta; int seqNumDelta;
            if (filterState.maxSeqNum != -1)
            {
                seqNumDelta
                    = (filterState.maxSeqNum + 1 - sourceFrameDesc.getStart()) & 0xFFFF;
                tsDelta
                    = (filterState.maxTs + 3000 - sourceFrameDesc.getTimestamp()) & 0xFFFFFFFFL;
            }
            else
            {
                seqNumDelta = 0; tsDelta = 0;
            }

            transformState = new SimTransformation(
                sourceSSRC, tsDelta, seqNumDelta, sourceIdx);

            onAccept(buf, off, len);

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void update(int targetIdx)
    {
        if (this.targetIdx == targetIdx)
        {
            return;
        }

        if (logger.isDebugEnabled())
        {
            long ssrc = weakSource.get().getRTPEncodings()[0].getPrimarySSRC();
            logger.debug("target_update ssrc=" + ssrc + ",target=" + targetIdx);
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
     * Transforms outgoing RTP packets.
     */
    private class RTPTransformer implements PacketTransformer
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public void close()
        {
            // TODO update the receive counters
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket[] reverseTransform(RawPacket[] pkts)
        {
            //  No need for that (yet?).
            return pkts;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket[] transform(RawPacket[] pkts)
        {
            if (ArrayUtils.isNullOrEmpty(pkts))
            {
                return pkts;
            }

            SimTransformation state = transformState;

            for (int i = 0; i < pkts.length; i++)
            {
                if (!RTPPacketPredicate.INSTANCE.test(pkts[i]))
                {
                    continue;
                }

                long srcSSRC = pkts[i].getSSRCAsLong();
                if (srcSSRC != state.currentSSRC)
                {
                    // We do _not_ forward packets from SSRCs other than the
                    // current SSRC.
                    pkts[i] = null;
                    continue;
                }

                int srcSeqNum = pkts[i].getSequenceNumber();
                int dstSeqNum = state.rewriteSeqNum(srcSeqNum);

                long srcTs = pkts[i].getTimestamp();
                long dstTs = state.rewriteTimestamp(srcTs);

                if (logger.isDebugEnabled())
                {
                    logger.debug("src_ssrc=" + pkts[i].getSSRCAsLong()
                        + ",src_seq=" + srcSeqNum
                        + ",src_ts=" + srcTs
                        + ",dst_ssrc=" + targetSSRC
                        + ",dst_seq=" + dstSeqNum
                        + ",dst_ts=" + dstTs);
                }

                if (srcSeqNum != dstSeqNum)
                {
                    pkts[i].setSequenceNumber(dstSeqNum);
                }

                if (dstTs != srcTs)
                {
                    pkts[i].setTimestamp(dstTs);
                }

                if (srcSSRC != targetSSRC)
                {
                    pkts[i].setSSRC((int) targetSSRC);
                }
            }

            return pkts;
        }
    }

    /**
     * Transforms outgoing RTCP packets.
     */
    private class RTCPTransformer implements PacketTransformer
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public void close()
        {
            // TODO update the receive counters
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket[] reverseTransform(RawPacket[] pkts)
        {
            //  No need for that (yet?).
            return pkts;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket[] transform(RawPacket[] pkts)
        {
            if (ArrayUtils.isNullOrEmpty(pkts))
            {
                return pkts;
            }

            SimTransformation state = transformState;

            for (int i = 0; i < pkts.length; i++)
            {
                if (!RTCPPacketPredicate.INSTANCE.test(pkts[i]))
                {
                    continue;

                }
                if (RTCPHeaderUtils.getSenderSSRC(pkts[i]) != state.currentSSRC)
                {
                    // Anything from anywhere else gets axed.
                    pkts[i] = null;
                    continue;
                }

                RTCPIterator it = new RTCPIterator(pkts[i]);
                while (it.hasNext())
                {
                    // Anything other than SR and SDES gets axed. SRs are
                    // rewritten.
                    ByteArrayBuffer baf = it.next();
                    switch (RTCPHeaderUtils.getPacketType(baf))
                    {
                    case RTCPPacket.SR:

                        // Rewrite timestamp.
                        long srcTs = RTCPSenderInfoUtils.getTimestamp(baf);
                        long dstTs = state.rewriteTimestamp(srcTs);

                        if (srcTs != dstTs)
                        {
                            RTCPSenderInfoUtils.setTimestamp(baf, dstTs);
                        }

                        // Rewrite packet/octet count.
                        RTCPSenderInfoUtils
                            .setOctetCount(baf, filterState.transmittedBytes);
                        RTCPSenderInfoUtils
                            .setPacketCount(baf, filterState.transmittedPackets);

                    case RTCPPacket.SDES:
                    case RTCPPacket.BYE:
                        break;
                    default:
                        it.remove();
                    }
                }

            }

            return pkts;
        }
    }

    /**
     *
     */
    private static class SimTransformation
        extends Transformation
    {
        /**
         * Ctor.
         *
         * @param currentSSRC
         * @param tsDelta
         * @param seqNumDelta
         */
        SimTransformation(
            long currentSSRC, long tsDelta, int seqNumDelta, int currentIdx)
        {
            super(tsDelta, seqNumDelta);
            this.currentSSRC = currentSSRC;
            this.currentIdx = currentIdx;
        }

        /**
         * The current SSRC that is currently being forwarded.
         */
        private final long currentSSRC;

        /**
         * The current subjective quality index for this instance. If this is
         * different than the target, then a switch is pending.
         */
        private final int currentIdx;
    }

    /**
     *
     */
    private static class FilterState
    {
        /**
         * The maximum sequence number (mod 16) that this instance has sent out.
         */
        private int maxSeqNum = -1;

        /**
         * The maximum timestamp (mod 32) that this instance has sent out.
         */
        private long maxTs = -1;

        /**
         *
         */
        private long transmittedBytes = 0;

        /*

         */
        private long transmittedPackets = 0;
    }
}
