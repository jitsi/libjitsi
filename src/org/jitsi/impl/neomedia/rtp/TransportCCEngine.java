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
    RemoteBitrateObserver
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
     * Defines the micros to millis conversion rate. Units are millis per
     * 250 micros.
     */
    private static final double K_250_MICROS_TO_MILIS = 0.25;

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
    private byte extensionId = -1;

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
     * Incoming transport-wide sequence numbers mapped to the timestamp of their
     * reception (in milliseconds since the epoch).
     */
    private RTCPTCCPacket.PacketMap incomingPackets;

    /**
     * Used to synchronize access to {@link #incomingPackets}.
     */
    private final Object incomingPacketsSyncRoot = new Object();

    /**
     * The time (in milliseconds since the epoch) at which the first received
     * packet in {@link #incomingPackets} was received (or -1 if the map is empty).
     * Kept here for quicker access, because the map is ordered by sequence
     * number.
     */
    private long firstIncomingTs = -1;

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
                // TODO: use the correct SSRCs
                RTCPTCCPacket rtcpPacket
                    = new RTCPTCCPacket(
                    -1, -1,
                    packets,
                    (byte) (outgoingFbPacketCount.getAndIncrement() & 0xff));
                stream.injectPacket(rtcpPacket.toRawPacket(), false /* rtcp */,
                    null);
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
                logger.warn("Not sending transport-cc feedback, delta too big.");
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
    public void setExtensionID(byte id)
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
        VideoMediaStream videoStream = null;
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
        MediaStream videoStream = null;
        for (MediaStream stream : mediaStreams)
        {
            if (stream instanceof VideoMediaStream)
            {
                videoStream = stream;
                break;
            }
        }

        for (Map.Entry<Integer, Long> entries
            : tccPacket.getPackets().entrySet())
        {
            PacketDetail packetDetail
                = sentPacketDetails.remove(entries.getKey());

            if (packetDetail != null && videoStream != null)
            {
                long arrivalTimeMs
                    = (long) (entries.getValue() * K_250_MICROS_TO_MILIS);

                long sendTime24bits = RemoteBitrateEstimatorAbsSendTime
                    .convertMsTo24Bits(packetDetail.packetSendTimeMs);

                bitrateEstimatorAbsSendTime.incomingPacketInfo(
                    arrivalTimeMs,
                    sendTime24bits,
                    packetDetail.packetLength,
                    tccPacket.getSourceSSRC());
            }
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
                    = pkt.getHeaderExtension(extensionId);
                if (ext == null)
                {
                    ext = pkt.addExtension(extensionId, 2);
                }

                int seq = outgoingSeq.getAndIncrement() & 0xffff;
                RTPUtils.writeShort(
                    ext.getBuffer(),
                    ext.getOffset() + 1,
                    (short) seq);
                sentPacketDetails.put(seq, new PacketDetail(pkt.getLength(), System.currentTimeMillis()));
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
                    = pkt.getHeaderExtension(extensionId);
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
     * {@Link packetDetail} is an object that holds the
     * length(size) of the packet in {@Link packetLength}
     * and the time stamps of the outgoing packet
     * in {@Link packetSendTimeMs}
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
