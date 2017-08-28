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
     * The transformer which handles RTCP packets for this instance.
     */
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();
    /**
     * The list of {@link MediaStream} that are using this
     * {@link TransportCCEngine}.
     */
    private final List<MediaStream> mediaStreams = new LinkedList<>();
    /**
     * Used to synchronize access to {@link #incomingPackets}.
     */
    private final Object incomingPacketsSyncRoot = new Object();
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
     * Incoming transport-wide sequence numbers mapped to the timestamp of their
     * reception (in milliseconds since the epoch).
     */
    private RTCPTCCPacket.PacketMap incomingPackets;

    /**
     * The time (in milliseconds since the epoch) at which the first received
     * packet in {@link #incomingPackets} was received (or -1 if the map is empty).
     * Kept here for quicker access, because the map is ordered by sequence
     * number.
     */
    private long firstIncomingTs = -1;

    /**
     * {@Link #sentPacketFields} holds a key value pair of the packet sequence
     * number and an object made up of the packet send time and the packet
     * size.
     */
    private Map<Integer, PacketDetail> sentPacketFields = new HashMap<Integer, PacketDetail>();


    /**
     *{@Link bitrateEstimatorAbsSendTime} used for estimating the bitrate from
     * RTCPTCC feedback packets
     */
    private RemoteBitrateEstimatorAbsSendTime  bitrateEstimatorAbsSendTime = new RemoteBitrateEstimatorAbsSendTime( this , new AbsSendTimeEngine());


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
        return rtcpTransformer;
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
    public void onReceiveBitrateChanged(Collection<Integer> ssrcs, long bitrate)
    {

    }

    /**
     * Adds a {@link MediaStream} to the list of {@link MediaStream}s which
     * use this {@link TransportCCEngine}.
     *
     * @param mediaStream the stream to add.
     */
    public void addMediaStream(MediaStream mediaStream)
    {
        synchronized (mediaStreams)
        {
            mediaStream.getMediaStreamStats().addRTCPPacketListener(this);
            mediaStreams.add(mediaStream);
        }
    }

    /**
     * Removes a {@link MediaStream} from the list of {@link MediaStream}s which
     * use this {@link TransportCCEngine}.
     *
     * @param mediaStream the stream to remove.
     */
    public void removeMediaStream(MediaStream mediaStream)
    {
        synchronized (mediaStreams)
        {
            while (mediaStreams.remove(mediaStream))
            {
                mediaStream.getMediaStreamStats().removeRTCPPacketListener(this);
                // we loop in order to remove all instances.
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
     * Calls the bitrate estimator with receiver and sender parameters.
     *
     * @param rtcpTccPacket
     * @note the bridge is the sender.
     */
    @Override
    public void tccReceived(RTCPTCCPacket rtcpTccPacket)
    {

        for (MediaStream stream : mediaStreams)
        {
            if (stream instanceof VideoMediaStream)
            {
                //packetMapEntry is a map of sequence no to timestamp.
                for (Map.Entry<Integer, Long> receivedRtcpTccEntry : rtcpTccPacket.getPackets().entrySet())
                {

                    if (sentPacketFields.containsKey(receivedRtcpTccEntry.getKey()))
                    {
                        PacketDetail retrievedRtpPacketDetail = sentPacketFields
                            .get(receivedRtcpTccEntry.getKey());
                        if (retrievedRtpPacketDetail != null)
                        {
                            //Calculate the sending bitrate

                            //Convert from 250us format to Milli-seconds
                            //timeStampToMs is the arrival time sent by the receiver.
                            long timeStampToMs = (long) (receivedRtcpTccEntry.getValue() * 0.25);

                            bitrateEstimatorAbsSendTime.processIncomingPacketInfo(
                                timeStampToMs,  //arrival timestamp in 250us format
                                RTPUtils.convertMsTo24Bits(retrievedRtpPacketDetail.packetSendTimeMs,
                                    bitrateEstimatorAbsSendTime.kAbsSendTimeFraction),
                                (long) retrievedRtpPacketDetail.packetLength,
                                rtcpTccPacket.getSourceSSRC());

                            /**
                             * Intuition: First Version in "Congestion Control
                             * for RTCWEB" internet draft where
                             * the Delay based controller and the loss based
                             * controller live on the send side.
                             *
                             * The loss based controller is fed with RTCPReport
                             * packet and called through the statistics engine.
                             * The delay based bitrate estimate is calculate
                             * here using the RBEAbsSendTime, afterwards, we
                             * make an REMBPacket with calculated bitrate
                             * estimate and feed it in to the sendside bandwidth
                             * estimator. The send side bandwidth estimator
                             * takes the estimated bitrate from the REMBPacket
                             * and the loss based estimate from the RTCPReport
                             * to estimate the target sending "bitrate" also
                             * referred to as "bandwidth".
                             * Note, The loss based controller estimate is
                             * in the sendSideBandwidthEstimation class.
                             * See new comments in
                             * SendSideBandwidthEstimation.java
                             */

                            //@Todo Delete comments below. Choice of REMBPAckets
                            //Note: The updateReceiverEstimate() method is
                            //private and accessed through RTCPPacketListener#rembReceived.
                            //This is a long way, perhaps tccPacketListener
                            //should implement a direct call to UpdateReceiverEstimate()
                            //The stream object has no direct reference to the
                            //updateReceiverEstimate method of the SendSideBandwidthEstimation class.
                            //1st option is make it public and pass the bitrate Estimate directly.
                            //the first option is a quick hack. I ll prefer to
                            //to stay consistent with existing code,
                            //thus Create new RTCPREMB packet to updateReceiverEstimate.

                            ((MediaStreamStatsImpl)
                                (stream.getMediaStreamStats()))
                                .rembReceived(new RTCPREMBPacket(
                                    rtcpTccPacket.senderSSRC,
                                    rtcpTccPacket.getSourceSSRC(),
                                    bitrateEstimatorAbsSendTime
                                        .getLatestEstimate(), null));

                            /**
                             * We shouldn't keep the sent <tt>PacketDetails</tt>
                             * once we are done with
                             * them. Hence, we should remove them
                             */
                            sentPacketFields.remove(receivedRtcpTccEntry.getKey());
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles RTCP packets for this {@link TransportCCEngine}.
     */
    private static class RTCPTransformer
        extends SinglePacketTransformerAdapter
    {
        /**
         * Initializes a new {@link RTPTransformer} instance.
         */
        private RTCPTransformer()
        {
            super(RTCPPacketPredicate.INSTANCE);
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
                sentPacketFields.put(seq, new PacketDetail(pkt.getLength(), System.currentTimeMillis()));
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
