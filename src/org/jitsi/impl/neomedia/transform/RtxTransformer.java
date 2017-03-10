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
package org.jitsi.impl.neomedia.transform;

import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.stats.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;

/**
 * Intercepts RTX (RFC-4588) packets coming from an {@link MediaStream}, and
 * removes their RTX encapsulation.
 *
 * Intercepts NACKs and retransmits packets to a mediaStream (using the RTX
 * format if the destination supports it).
 *
 * @author Boris Grozev
 * @author George Politis
 */
public class RtxTransformer
    implements TransformEngine
{
    /**
     * The name of the property used to disable NACK termination.
     */
    public static final String DISABLE_NACK_TERMINATION_PNAME
        = "org.jitsi.impl.neomedia.rtcp.DISABLE_NACK_TERMINATION";

    /**
     * The <tt>MediaStream</tt> for the transformer.
     */
    private MediaStreamImpl mediaStream;

    /**
     * Maps an RTX SSRC to the last RTP sequence number sent with that SSRC.
     */
    private final Map<Long, Integer> rtxSequenceNumbers = new HashMap<>();

    /**
     * The {@link Logger} to be used by this instance to print debug
     * information.
     */
    private final Logger logger = Logger.getLogger(RtxTransformer.class);

    /**
     * The payload type number configured for RTX (RFC-4588), mapped by the
     * media payload type number.
     */
    private Map<Byte, Byte> apt2rtx = new HashMap<>();

    /**
     * The "associated payload type" number for RTX, mapped by the RTX payload
     * type number.
     */
    private Map<Byte, Byte> rtx2apt = new HashMap<>();

    /**
     * The transformer that decapsulates RTX.
     */
    private final RTPTransformer rtpTransformer = new RTPTransformer();

    /**
     * The transformer that handles NACKs.
     */
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();

    /**
     * Initializes a new <tt>RtxTransformer</tt> with a specific
     * <tt>MediaStreamImpl</tt>.
     *
     * @param mediaStream the <tt>MediaStreamImpl</tt> for the transformer.
     */
    public RtxTransformer(MediaStreamImpl mediaStream)
    {
        this.mediaStream = mediaStream;

        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg == null)
        {
            logger.warn("NOT initializing RTCP n' NACK termination because "
                + "the configuration service was not found.");
            return;
        }

        boolean enableNackTermination
            = !cfg.getBoolean(DISABLE_NACK_TERMINATION_PNAME, false);

        if (enableNackTermination)
        {
            CachingTransformer cache = mediaStream.getCachingTransformer();
            if (cache != null)
            {
                cache.setEnabled(true);
            }
            else
            {
                logger.warn("NACK termination is enabled, but we don't have" +
                    " a packet cache.");
            }
        }
    }

    /**
     * Implements {@link TransformEngine#getRTPTransformer()}.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return rtpTransformer;
    }

    /**
     * Implements {@link TransformEngine#getRTCPTransformer()}.
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    /**
     * Returns the sequence number to use for a specific RTX packet, which
     * is based on the packet's original sequence number.
     *
     * Because we terminate the RTX format, and with simulcast we might
     * translate RTX packets from multiple SSRCs into the same SSRC, we keep
     * count of the RTX packets (and their sequence numbers) which we sent for
     * each SSRC.
     *
     * @param ssrc the SSRC of the RTX stream for the packet.
     * @return the sequence number which should be used for the next RTX
     * packet sent using SSRC <tt>ssrc</tt>.
     */
    private int getNextRtxSequenceNumber(long ssrc)
    {
        Integer seq;
        synchronized (rtxSequenceNumbers)
        {
            seq = rtxSequenceNumbers.get(ssrc);
            if (seq == null)
                seq = new Random().nextInt(0xffff);
            else
                seq++;

            rtxSequenceNumbers.put(ssrc, seq);
        }

        return seq;
    }

    /**
     * Tries to find an SSRC paired with {@code ssrc} in an FID group in one
     * of the mediaStreams from {@link #mediaStream}'s {@code Content}. Returns -1 on
     * failure.
     * @param pkt the {@code RawPacket} that holds the RTP packet for
     * which to find a paired SSRC.
     * @return An SSRC paired with {@code ssrc} in an FID group, or -1.
     */
    private long getRtxSsrc(RawPacket pkt)
    {
        StreamRTPManager receiveRTPManager = mediaStream
            .getRTPTranslator()
            .findStreamRTPManagerByReceiveSSRC(pkt.getSSRC());

        MediaStreamTrackReceiver receiver = null;
        if (receiveRTPManager != null)
        {
            MediaStream receiveStream = receiveRTPManager.getMediaStream();
            if (receiveStream != null)
            {
                receiver = receiveStream.getMediaStreamTrackReceiver();
            }
        }

        if (receiver == null)
        {
            return -1;
        }

        RTPEncodingDesc encoding = receiver.findRTPEncodingDesc(pkt);
        if (encoding == null)
        {
            logger.warn("encoding_not_found"
                + ",stream_hash=" + mediaStream.hashCode()
                + " ssrc=" + pkt.getSSRCAsLong());
            return -1;
        }

        return encoding.getRTXSSRC();
    }
    /**
     * Retransmits a packet to {@link #mediaStream}. If the destination supports
     * the RTX format, the packet will be encapsulated in RTX, otherwise, the
     * packet will be retransmitted as-is.
     *
     * @param pkt the packet to retransmit.
     * @param after the {@code TransformEngine} in the chain of
     * {@code TransformEngine}s of the associated {@code MediaStream} after
     * which the injection of {@code pkt} is to begin
     * @return {@code true} if the packet was successfully retransmitted,
     * {@code false} otherwise.
     */
    private boolean retransmit(RawPacket pkt, TransformEngine after)
    {
        Byte rtxPt = apt2rtx.get(pkt.getPayloadType());
        boolean destinationSupportsRtx =  rtxPt != null;
        boolean retransmitPlain;

        if (destinationSupportsRtx)
        {
            long rtxSsrc = getRtxSsrc(pkt);

            if (rtxSsrc == -1)
            {
                logger.warn("Cannot find SSRC for RTX, retransmitting plain. "
                            + "SSRC=" + pkt.getSSRCAsLong());
                retransmitPlain = true;
            }
            else
            {
                retransmitPlain
                    = !encapsulateInRtxAndTransmit(pkt, rtxSsrc, rtxPt, after);
            }
        }
        else
        {
            retransmitPlain = true;
        }

        if (retransmitPlain)
        {
            if (mediaStream != null)
            {
                try
                {
                    mediaStream.injectPacket(pkt, /* data */ true, after);
                }
                catch (TransmissionFailedException tfe)
                {
                    logger.warn("Failed to retransmit a packet.");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Notifies this instance that the dynamic payload types of the associated
     * {@link MediaStream} have changed.
     */
    public void onDynamicPayloadTypesChanged()
    {
        Map<Byte, Byte> apt2rtx = new HashMap<>();
        Map<Byte, Byte> rtx2apt = new HashMap<>();

        Map<Byte, MediaFormat> mediaFormatMap
            = mediaStream.getDynamicRTPPayloadTypes();

        for (Iterator<Map.Entry<Byte, MediaFormat>>
             it = mediaFormatMap.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry<Byte, MediaFormat> entry = it.next();
            MediaFormat format = entry.getValue();
            if (!Constants.RTX.equalsIgnoreCase(format.getEncoding()))
            {
                continue;
            }

            Byte pt = entry.getKey(),
                apt = Byte.parseByte(format.getFormatParameters().get("apt"));

            apt2rtx.put(apt, pt);
            rtx2apt.put(pt, apt);
        }

        this.rtx2apt = rtx2apt;
        this.apt2rtx = apt2rtx;
    }

    /**
     * Encapsulates {@code pkt} in the RTX format, using {@code rtxSsrc} as its
     * SSRC, and transmits it to {@link #mediaStream} by injecting it in the
     * {@code MediaStream}.
     * @param pkt the packet to transmit.
     * @param rtxSsrc the SSRC for the RTX stream.
     * @param after the {@code TransformEngine} in the chain of
     * {@code TransformEngine}s of the associated {@code MediaStream} after
     * which the injection of {@code pkt} is to begin
     * @return {@code true} if the packet was successfully retransmitted,
     * {@code false} otherwise.
     */
    private boolean encapsulateInRtxAndTransmit(
        RawPacket pkt, long rtxSsrc, byte rtxPt, TransformEngine after)
    {
        byte[] buf = pkt.getBuffer();
        int len = pkt.getLength();
        int off = pkt.getOffset();

        byte[] newBuf = new byte[len + 2];
        RawPacket rtxPkt = new RawPacket(newBuf, 0, len + 2);

        int osn = pkt.getSequenceNumber();
        int headerLength = pkt.getHeaderLength();
        int payloadLength = pkt.getPayloadLength();

        // Copy the header.
        System.arraycopy(buf, off, newBuf, 0, headerLength);

        // Set the OSN field.
        newBuf[headerLength] = (byte) ((osn >> 8) & 0xff);
        newBuf[headerLength + 1] = (byte) (osn & 0xff);

        // Copy the payload.
        System.arraycopy(buf, off + headerLength,
                         newBuf, headerLength + 2,
                         payloadLength );

        if (mediaStream != null)
        {
            rtxPkt.setSSRC((int) rtxSsrc);
            rtxPkt.setPayloadType(rtxPt);
            // Only call getNextRtxSequenceNumber() when we're sure we're going
            // to transmit a packet, because it consumes a sequence number.
            rtxPkt.setSequenceNumber(getNextRtxSequenceNumber(rtxSsrc));
            try
            {
                mediaStream.injectPacket(
                        rtxPkt,
                        /* data */ true,
                        after);
            }
            catch (TransmissionFailedException tfe)
            {
                logger.warn("Failed to transmit an RTX packet.");
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the SSRC paired with <tt>ssrc</tt> in an FID source-group, if
     * any. If none is found, returns -1.
     *
     * @return the SSRC paired with <tt>ssrc</tt> in an FID source-group, if
     * any. If none is found, returns -1.
     */
    private long getPrimarySsrc(long rtxSSRC)
    {
        MediaStreamTrackReceiver receiver
            = mediaStream.getMediaStreamTrackReceiver();

        if (receiver == null)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(
                    "Dropping an incoming RTX packet from an unknown source.");
            }
            return -1;
        }

        RTPEncodingDesc encoding = receiver.findRTPEncodingDesc(rtxSSRC);
        if (encoding == null)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(
                    "Dropping an incoming RTX packet from an unknown source.");
            }
            return -1;
        }

        return encoding.getPrimarySSRC();
    }

    private RawPacketCache getCache()
    {
        MediaStreamImpl stream = this.mediaStream;
        return stream != null
            ? stream.getCachingTransformer().getOutgoingRawPacketCache() : null;
    }

    /**
     *
     * @param mediaSSRC
     * @param lostPackets
     */
    private void nackReceived(long mediaSSRC, Collection<Integer> lostPackets)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug(Logger.Category.STATISTICS,
                "nack_received,stream=" + mediaStream.hashCode()
                    + " ssrc=" + mediaSSRC
                    + ",lost_packets=" + lostPackets);
        }

        RawPacketCache cache = getCache();

        if (cache != null)
        {
            // Retransmitted packets need to be inserted:
            // * after SSRC-rewriting (the external transform engine)
            // * after statistics (we update them explicitly)
            // * before abs-send-time
            // * before SRTP
            // We use 'this', because the RtxTransformer happens to be in the
            // correct place in the chain. See
            // MediaStreamImpl#createTransformEngineChain.
            // The position of the packet cache does not matter, because we
            // update it explicitly.
            TransformEngine after = this;

            long rtt = mediaStream.getMediaStreamStats().getSendStats().getRtt();
            long now = System.currentTimeMillis();

            for (Iterator<Integer> i = lostPackets.iterator(); i.hasNext();)
            {
                int seq = i.next();
                RawPacketCache.Container container
                    = cache.getContainer(mediaSSRC, seq);


                MediaStreamStats2Impl stats
                    = mediaStream.getMediaStreamStats();
                if (container != null)
                {
                    // Cache hit.
                    long delay = now - container.timeAdded;
                    boolean send = (rtt == -1) ||
                        (delay >= Math.min(rtt * 0.9, rtt - 5));

                    if (logger.isDebugEnabled())
                    {
                        logger.debug(Logger.Category.STATISTICS,
                            "retransmitting,stream=" + mediaStream.hashCode()
                                + " ssrc=" + mediaSSRC
                                + ",seq=" + seq
                                + ",send=" + send);
                    }

                    if (send && retransmit(container.pkt, after))
                    {
                        stats.rtpPacketRetransmitted(
                            mediaSSRC, container.pkt.getLength());

                        // We just retransmitted the packet. Update its
                        // timestamp so that we use the new timestamp when we
                        // handle subsequent NACKs.
                        cache.updateTimestamp(mediaSSRC, seq, now);

                        i.remove();
                    }

                    if (!send)
                    {
                        stats.rtpPacketNotRetransmitted(
                            mediaSSRC, container.pkt.getLength());
                        i.remove();
                    }

                }
                else
                {
                    stats.rtpPacketCacheMiss(mediaSSRC);
                }
            }
        }

        if (!lostPackets.isEmpty())
        {
            // If retransmission requests are enabled, videobridge assumes
            // the responsibility of requesting missing packets.
            logger.debug("Packets missing from the cache.");
        }
    }

    /**
     * Sends padding packets with the RTX SSRC associated to the media SSRC that
     * is passed as a parameter. It implements packet triplication.
     *
     * @param ssrc the media SSRC to protect.
     * @param bytes the amount of padding to send in bytes.
     * @return the remaining padding bytes budget.
     */
    public int sendPadding(long ssrc, int bytes)
    {
        StreamRTPManager receiveRTPManager = mediaStream
            .getRTPTranslator()
            .findStreamRTPManagerByReceiveSSRC((int) ssrc);

        if (receiveRTPManager == null)
        {
            logger.warn("rtp_manager_not_found"
                + ",stream_hash=" + mediaStream.hashCode()
                + " ssrc=" + ssrc);
            return bytes;
        }

        MediaStream receiveStream = receiveRTPManager.getMediaStream();
        if (receiveStream == null)
        {
            logger.warn("stream_not_found"
                + ",stream_hash=" + mediaStream.hashCode()
                + " ssrc=" + ssrc);
            return bytes;
        }


        MediaStreamTrackReceiver receiver
            = receiveStream.getMediaStreamTrackReceiver();
        if (receiver == null)
        {
            logger.warn("receiver_not_found"
                + ",stream_hash=" + mediaStream.hashCode()
                + " ssrc=" + ssrc);
            return bytes;
        }

        RTPEncodingDesc encoding = receiver.findRTPEncodingDesc(ssrc);
        if (encoding == null)
        {
            logger.warn("encoding_not_found"
                + ",stream_hash=" + mediaStream.hashCode()
                + " ssrc=" + ssrc);
            return bytes;
        }

        RawPacketCache cache = getCache();
        if (cache == null)
        {
            return bytes;
        }

        Set<RawPacketCache.Container> lastNPackets
            = cache.getMany(ssrc, bytes);

        if (lastNPackets == null || lastNPackets.isEmpty())
        {
            return bytes;
        }

        // XXX this constant is not great, however the final place of the stream
        // protection strategy is not clear at this point so I expect the code
        // will change before taking its final form.
        for (int i = 0; i < 2; i++)
        {
            Iterator<RawPacketCache.Container> it = lastNPackets.iterator();

            while (it.hasNext())
            {
                RawPacketCache.Container container = it.next();
                RawPacket pkt = container.pkt;
                // Containers are recycled/reused, so we must check if the
                // packet is still there.
                if (pkt != null)
                {
                    int len = container.pkt.getLength();
                    Byte apt = rtx2apt.get(container.pkt.getPayloadType());

                    // If the client does not support RTX, then it's impossible
                    // to protect any media RTP stream because any duplicate
                    // packets that we send will be killed at the SRTP layer.
                    // The caller better probe using the JVB's SSRC.

                    if (bytes - len > 0 && apt != null)
                    {
                        retransmit(container.pkt, this);
                        bytes -= len;
                    }
                    else
                    {
                        // Don't break as we might be able to squeeze in the
                        // next packet.
                    }
                }
            }
        }

        return bytes;
    }

    /**
     * The transformer that decapsulates RTX.
     */
    private class RTPTransformer
        extends SinglePacketTransformerAdapter
    {
        /**
         * Ctor.
         */
        RTPTransformer()
        {
            super(RTPPacketPredicate.INSTANCE);
        }

        /**
         * Implements {@link PacketTransformer#transform(RawPacket[])}.
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            Byte apt = rtx2apt.get(pkt.getPayloadType());
            if (apt == null)
            {
                return pkt;
            }

            boolean success = false;

            if (pkt.getPayloadLength() - pkt.getPaddingSize() < 2)
            {
                // We need at least 2 bytes to read the OSN field.
                if (logger.isDebugEnabled())
                {
                    logger.debug(
                        "Dropping an incoming RTX packet with padding only: " + pkt);
                }

                return null;
            }

            long mediaSsrc = getPrimarySsrc(pkt.getSSRCAsLong());
            if (mediaSsrc != -1)
            {
                int osn = pkt.getOriginalSequenceNumber();
                // Remove the RTX header by moving the RTP header two bytes
                // right.
                byte[] buf = pkt.getBuffer();
                int off = pkt.getOffset();
                System.arraycopy(buf, off,
                    buf, off + 2,
                    pkt.getHeaderLength());

                pkt.setOffset(off + 2);
                pkt.setLength(pkt.getLength() - 2);

                pkt.setSSRC((int) mediaSsrc);
                pkt.setSequenceNumber(osn);
                pkt.setPayloadType(apt);
                success = true;
            }

            // If we failed to handle the RTX packet, drop it.
            return success ? pkt : null;
        }
    }

    /**
     * The transformer that handles NACKs.
     */
    private class RTCPTransformer
        extends SinglePacketTransformerAdapter
    {
        /**
         * Ctor.
         */
        RTCPTransformer()
        {
            super(RTCPPacketPredicate.INSTANCE);
        }

        /**
         * Implements {@link PacketTransformer#transform(RawPacket[])}.
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            RTCPIterator it = new RTCPIterator(pkt);
            while (it.hasNext())
            {
                ByteArrayBuffer next = it.next();
                if (NACKPacket.isNACKPacket(next))
                {
                    Collection<Integer> lostPackets
                        = NACKPacket.getLostPackets(next);
                    long mediaSSRC = NACKPacket.getSourceSSRC(next);
                    nackReceived(mediaSSRC, lostPackets);
                }
            }

            return pkt;
        }
    }
}
