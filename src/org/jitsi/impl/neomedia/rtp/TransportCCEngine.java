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
package org.jitsi.impl.neomedia.rtp;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.remotebitrateestimator.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Implements transport-cc functionality as a {@link TransformEngine}. The
 * intention is to have the same instance shared between all media streams of
 * a transport channel, so we expect it will be accessed by multiple threads.
 * See https://tools.ietf.org/html/draft-holmer-rmcat-transport-wide-cc-extensions-01
 *
 * @author Boris Grozev
 * @author Julian Chukwu
 * @author George Politis
 */
public class TransportCCEngine
    extends RTCPPacketListenerAdapter
    implements TransformEngine,
               RemoteBitrateObserver,
               CallStatsObserver
{
    /**
     * The maximum number of received packets and their timestamps to save.
     */
    private static final int MAX_INCOMING_PACKETS_HISTORY = 200;

    /**
     * The maximum number of received packets and their timestamps to save.
     *
     * XXX this is an uninformed value.
     */
    private static final int MAX_OUTGOING_PACKETS_HISTORY = 1000;

    /**
     * The {@link Logger} used by the {@link TransportCCEngine} class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(TransportCCEngine.class);

    /**
     * The transformer which handles RTP packets for this instance.
     */
    private final RTPTransformer rtpTransformer = new RTPTransformer();

    /**
     * The ID of the transport-cc RTP header extension, or -1 if one is not
     * configured.
     */
    private int extensionId = -1;

    /**
     * The next sequence number to use for outgoing data packets.
     */
    private AtomicInteger outgoingSeq = new AtomicInteger(1);

    /**
     * The running index of sent RTCP transport-cc feedback packets.
     */
    private AtomicInteger outgoingFbPacketCount = new AtomicInteger();

    /**
     * The list of {@link MediaStream} that are using this
     * {@link TransportCCEngine}.
     */
    private final List<MediaStream> mediaStreams = new LinkedList<>();

    /**
     * Some {@link VideoMediaStream} that utilizes this instance. We use it to
     * get the sender/media SSRC of the outgoing RTCP TCC packets.
     */
    private VideoMediaStream anyVideoMediaStream;

    /**
     * Incoming transport-wide sequence numbers mapped to the timestamp of their
     * reception (in milliseconds since the epoch).
     */
    private RTCPTCCPacket.PacketMap incomingPackets;

    /**
     * Used to synchronize access to {@link #incomingPackets}.
     */
    private final Object incomingPacketsSyncRoot = new Object();

    /**
     * Used to synchronize access to {@link #sentPacketDetails}.
     */
    private final Object sentPacketsSyncRoot = new Object();

    /**
     * The time (in milliseconds since the epoch) at which the first received
     * packet in {@link #incomingPackets} was received (or -1 if the map is
     * empty).
     * Kept here for quicker access, because the map is ordered by sequence
     * number.
     */
    private long firstIncomingTs = -1;

    /**
     * The reference time of the remote clock. This is used to rebase the
     * arrival times in the TCC packets to a meaningful time base (that of the
     * sender). This is technically not necessary and it's done for convenience.
     */
    private long remoteReferenceTimeMs = -1;

    /**
     * Local time to map to the reference time of the remote clock. This is used
     * to rebase the arrival times in the TCC packets to a meaningful time base
     * (that of the sender). This is technically not necessary and it's done for
     * convinience.
     */
    private long localReferenceTimeMs = -1;

    /**
     * Holds a key value pair of the packet sequence number and an object made
     * up of the packet send time and the packet size.
     */
    private Map<Integer, PacketDetail> sentPacketDetails
        = new LRUCache<>(MAX_OUTGOING_PACKETS_HISTORY);

    /**
     * Used for estimating the bitrate from RTCP TCC feedback packets
     */
    private RemoteBitrateEstimatorAbsSendTime bitrateEstimatorAbsSendTime
        = new RemoteBitrateEstimatorAbsSendTime(this);

    /**
     * Notifies this instance that a data packet with a specific transport-wide
     * sequence number was received on this transport channel.
     *
     * @param seq the transport-wide sequence number of the packet.
     * @param marked whether the RTP packet had the "marked" bit set.
     */
    private void packetReceived(int seq, boolean marked)
    {
        long now = System.currentTimeMillis();
        synchronized (incomingPacketsSyncRoot)
        {
            if (incomingPackets == null)
            {
                incomingPackets = new RTCPTCCPacket.PacketMap();
            }
            if (incomingPackets.size() >= MAX_INCOMING_PACKETS_HISTORY)
            {
                Iterator<Map.Entry<Integer, Long>> iter
                    = incomingPackets.entrySet().iterator();
                if (iter.hasNext())
                {
                    iter.next();
                    iter.remove();
                }

                // This shouldn't happen, because we will send feedback often.
                logger.info("Reached max size, removing an entry.");
            }

            if (incomingPackets.isEmpty())
            {
                firstIncomingTs = now;
            }
            incomingPackets.put(seq, now);
        }

        maybeSendRtcp(marked, now);
    }

    /**
     * Gets the source SSRC to use for the outgoing RTCP TCC packets.
     *
     * @return the source SSRC to use for the outgoing RTCP TCC packets.
     */
    private long getSourceSSRC()
    {
        MediaStream stream = anyVideoMediaStream;
        if (stream == null)
        {
            return -1;
        }

        MediaStreamTrackReceiver receiver
            = stream.getMediaStreamTrackReceiver();
        if (receiver == null)
        {
            return -1;
        }

        MediaStreamTrackDesc[] tracks = receiver.getMediaStreamTracks();
        if (tracks == null || tracks.length == 0)
        {
            return -1;
        }

        RTPEncodingDesc[] encodings = tracks[0].getRTPEncodings();
        if (encodings == null || encodings.length == 0)
        {
            return -1;
        }

        return encodings[0].getPrimarySSRC();
    }

    /**
     * Examines the list of received packets for which we have not yet sent
     * feedback and determines whether we should send feedback at this point.
     * If so, sends the feedback.
     * @param marked whether the last received RTP packet had the "marked" bit
     * set.
     * @param now the current time.
     */
    private void maybeSendRtcp(boolean marked, long now)
    {
        RTCPTCCPacket.PacketMap packets = null;
        long delta;

        synchronized (incomingPacketsSyncRoot)
        {
            if (incomingPackets == null || incomingPackets.isEmpty())
            {
                // No packets with unsent feedback.
                return;
            }

            delta = firstIncomingTs == -1 ? 0 : (now - firstIncomingTs);
            // This condition controls when we send feedback:
            // 1. If 100ms have passed,
            // 2. If we see the end of a frame, and 20ms have passed, or
            // 3. If we have at least 100 packets.
            // The exact values and logic here are to be improved.
            if (delta > 100
                || (delta > 20 && marked)
                || incomingPackets.size() > 100)
            {
                packets = incomingPackets;
                incomingPackets = null;
                firstIncomingTs = -1;
            }
        }

        if (packets != null)
        {
            MediaStream stream = getMediaStream();
            if (stream == null)
            {
                logger.warn("No media stream, can't send RTCP.");
                return;
            }

            try
            {
                long senderSSRC
                    = anyVideoMediaStream.getStreamRTPManager().getLocalSSRC();
                if (senderSSRC == -1)
                {
                    logger.warn("No sender SSRC, can't send RTCP.");
                    return;
                }


                long sourceSSRC = getSourceSSRC();
                if (sourceSSRC == -1)
                {
                    logger.warn("No source SSRC, can't send RTCP.");
                    return;
                }
                RTCPTCCPacket rtcpPacket = new RTCPTCCPacket(
                    senderSSRC, sourceSSRC,
                    packets,
                    (byte) (outgoingFbPacketCount.getAndIncrement() & 0xff));

                // Inject the TCC packet *after* this engine. We don't want
                // RTCP termination -which runs before this engine in the 
                // egress- to drop the packet we just sent.
                stream.injectPacket(
                        rtcpPacket.toRawPacket(), false /* rtcp */, this);
            }
            catch (IllegalArgumentException iae)
            {
                // This comes from the RTCPTCCPacket constructor when the
                // list of packets contains a delta which cannot be expressed
                // in a single packet (more than 8192 milliseconds). In this
                // case we would have to split the feedback in two RTCP packets.
                // We currently don't do this, because it only happens if the
                // receiver stops sending packets for over 8s. In this case
                // we will fail to send one feedback message.
                logger.warn(
                        "Not sending transport-cc feedback, delta too big.");
            }
            catch (IOException | TransmissionFailedException e)
            {
                logger.error("Failed to send transport feedback RTCP: ", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRttUpdate(long avgRttMs, long maxRttMs)
    {
        bitrateEstimatorAbsSendTime.onRttUpdate(avgRttMs, maxRttMs);
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
        return null;
    }

    /**
     * Sets the ID of the transport-cc RTP extension. Set to -1 to effectively
     * disable.
     * @param id the ID to set.
     */
    public void setExtensionID(int id)
    {
        extensionId = id;
    }

    /**
     * Called when a receive channel group has a new bitrate estimate for the
     * incoming streams.
     *
     * @param ssrcs
     * @param bitrate
     */
    @Override
    public void onReceiveBitrateChanged(Collection<Long> ssrcs, long bitrate)
    {
        VideoMediaStream videoStream;
        for (MediaStream stream : mediaStreams)
        {
            if (stream instanceof VideoMediaStream)
            {
                videoStream = (VideoMediaStream) stream;
                videoStream.getOrCreateBandwidthEstimator()
                    .updateReceiverEstimate(bitrate);
                break;
            }
        }
    }

    /**
     * Calls the bitrate estimator with receiver and sender parameters.
     *
     * @param tccPacket the received TCC packet.
     */
    @Override
    public void tccReceived(RTCPTCCPacket tccPacket)
    {
        RTCPTCCPacket.PacketMap packetMap = tccPacket.getPackets();
        long previousArrivalTimeMs = -1;
        for (Map.Entry<Integer, Long> entry : packetMap.entrySet())
        {
            long arrivalTime250Us = entry.getValue();
            if (arrivalTime250Us == -1)
            {
                continue;
            }

            if (remoteReferenceTimeMs == -1)
            {
                remoteReferenceTimeMs = RTCPTCCPacket.getReferenceTime(
                        new ByteArrayBufferImpl(
                            tccPacket.fci, 0, tccPacket.fci.length)) / 4;

                localReferenceTimeMs = System.currentTimeMillis();
            }

            PacketDetail packetDetail;
            synchronized (sentPacketsSyncRoot)
            {
                packetDetail = sentPacketDetails.remove(entry.getKey());
            }

            if (packetDetail == null)
            {
                continue;
            }

            long arrivalTimeMs = arrivalTime250Us / 4
                - remoteReferenceTimeMs + localReferenceTimeMs;

            if (logger.isDebugEnabled())
            {
                if (previousArrivalTimeMs != -1)
                {
                    long diff_ms = arrivalTimeMs - previousArrivalTimeMs;
                    logger.debug("seq=" + entry.getKey()
                            + ", arrival_time_ms=" + arrivalTimeMs
                            + ", diff_ms=" + diff_ms);
                }
                else
                {
                    logger.debug("seq=" + entry.getKey()
                            + ", arrival_time_ms=" + arrivalTimeMs);
                }
            }

            previousArrivalTimeMs = arrivalTimeMs;
            long sendTime24bits = RemoteBitrateEstimatorAbsSendTime
                .convertMsTo24Bits(packetDetail.packetSendTimeMs);

            bitrateEstimatorAbsSendTime.incomingPacketInfo(
                arrivalTimeMs,
                sendTime24bits,
                packetDetail.packetLength,
                tccPacket.getSourceSSRC());
        }
    }

    /**
     * Handles RTP packets for this {@link TransportCCEngine}.
     */
    private class RTPTransformer
        extends SinglePacketTransformerAdapter
    {
        /**
         * Initializes a new {@link RTPTransformer} instance.
         */
        private RTPTransformer()
        {
            super(RTPPacketPredicate.INSTANCE);
        }

        /**
         * {@inheritDoc}
         * <p></p>
         * If the transport-cc extension is configured, update the
         * transport-wide sequence number (adding a new extension if one doesn't
         * exist already).
         */
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            if (extensionId != -1)
            {
                RawPacket.HeaderExtension ext
                    = pkt.getHeaderExtension((byte) extensionId);
                if (ext == null)
                {
                    ext = pkt.addExtension((byte) extensionId, 2);
                }

                int seq = outgoingSeq.getAndIncrement() & 0xffff;
                RTPUtils.writeShort(
                    ext.getBuffer(),
                    ext.getOffset() + 1,
                    (short) seq);

                if (logger.isDebugEnabled())
                {
                    logger.debug("rtp_seq=" + pkt.getSequenceNumber()
                            + ",pt=" + pkt.getPayloadType()
                            + ",tcc_seq=" + seq);
                }

                synchronized (sentPacketsSyncRoot)
                {
                    sentPacketDetails.put(seq, new PacketDetail(
                                pkt.getLength(),
                                System.currentTimeMillis()));
                }
            }
            return pkt;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            if (extensionId != -1)
            {
                RawPacket.HeaderExtension he
                    = pkt.getHeaderExtension((byte) extensionId);
                if (he != null && he.getExtLength() == 2)
                {
                    int seq
                        = RTPUtils.readUint16AsInt(
                        he.getBuffer(), he.getOffset() + 1);
                    packetReceived(seq, pkt.isPacketMarked());
                }
            }
            return pkt;
        }
    }

    /**
     * Adds a {@link MediaStream} to the list of {@link MediaStream}s which
     * use this {@link TransportCCEngine}.
     * @param mediaStream the stream to add.
     */
    public void addMediaStream(MediaStream mediaStream)
    {
        synchronized (mediaStreams)
        {
            mediaStreams.add(mediaStream);

            // Hook us up to receive TCCs.
            MediaStreamStats stats = mediaStream.getMediaStreamStats();
            stats.addRTCPPacketListener(this);

            if (mediaStream instanceof VideoMediaStream)
            {
                anyVideoMediaStream = (VideoMediaStream) mediaStream;
            }
        }
    }

    /**
     * Removes a {@link MediaStream} from the list of {@link MediaStream}s which
     * use this {@link TransportCCEngine}.
     * @param mediaStream the stream to remove.
     */
    public void removeMediaStream(MediaStream mediaStream)
    {
        synchronized (mediaStreams)
        {
            while(mediaStreams.remove(mediaStream))
            {
                // we loop in order to remove all instances.
            }

            // Hook us up to receive TCCs.
            MediaStreamStats stats = mediaStream.getMediaStreamStats();
            stats.removeRTCPPacketListener(this);

            if (mediaStream == anyVideoMediaStream)
            {
                anyVideoMediaStream = null;
            }
        }
    }

    /**
     * @return one of the {@link MediaStream} instances which use this
     * {@link TransportCCEngine}, or null.
     */
    private MediaStream getMediaStream()
    {
        synchronized (mediaStreams)
        {
            return mediaStreams.isEmpty() ? null : mediaStreams.get(0);
        }
    }

    /**
     * {@link PacketDetail} is an object that holds the
     * length(size) of the packet in {@link #packetLength}
     * and the time stamps of the outgoing packet
     * in {@link #packetSendTimeMs}
     */
    private class PacketDetail
    {
        int packetLength;
        long packetSendTimeMs;

        PacketDetail(int length, long time)
        {
            packetLength = length;
            packetSendTimeMs = time;
        }
    }
}
