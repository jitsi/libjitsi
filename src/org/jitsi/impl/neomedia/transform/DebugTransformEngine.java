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

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.net.*;

/**
 * Logs all the packets that go in and out of a <tt>MediaStream</tt>. In order
 * to use it, you can setup logging like this:
 *
 * <pre>
 * net.java.sip.communicator.packetlogging.PACKET_LOGGING_ENABLED=true
 * net.java.sip.communicator.packetlogging.PACKET_LOGGING_ARBITRARY_ENABLED=true
 * net.java.sip.communicator.packetlogging.PACKET_LOGGING_FILE_COUNT=1
 * net.java.sip.communicator.packetlogging.PACKET_LOGGING_FILE_SIZE=-1
 * </pre>
 *
 * Next, you setup a named pipe like this:
 *
 * <pre>
 * mkfifo ~/.sip-communicator/log/jitsi0.pcap
 * </pre>
 *
 * Finally, you can launch Wireshark like this (assuming that you're using
 * Bash):
 *
 * <pre>
 * wireshark -k -i &lt;(cat ~/.sip-communicator/log/jitsi0.pcap)
 * </pre>
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class DebugTransformEngine implements TransformEngine
{
    /**
     * The <tt>Logger</tt> used by the <tt>DebugTransformEngine</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(DebugTransformEngine.class);

    /**
     * The <tt>MediaStream</tt> that owns this instance.
     */
    private final MediaStreamImpl mediaStream;

    /**
     * The {@code PacketLoggingService} (to be) used by this instance. Cached
     * for the sake of performance because fetching OSGi services is not
     * inexpensive.
     */
    private final PacketLoggingService _pktLogging;

    /**
     * The <tt>PacketTransformer</tt> that logs RTCP packets.
     */
    private final SinglePacketTransformer rtcpTransformer
        = new MyRTCPPacketTransformer();

    /**
     * The <tt>PacketTransformer</tt> that logs RTP packets.
     */
    private final SinglePacketTransformer rtpTransformer
        = new MyRTPPacketTransformer();

    /**
     * Ctor.
     *
     * @param mediaStream the <tt>MediaStream</tt> that owns this instance.
     * @param pktLogging the {@code PacketLoggingService} to be used by the new
     * instance
     */
    DebugTransformEngine(
            MediaStreamImpl mediaStream,
            PacketLoggingService pktLogging)
    {
        this.mediaStream = mediaStream;
        _pktLogging = pktLogging;
    }

    /**
     * Creates and returns a new <tt>DebugTransformEngine</tt> if logging is
     * enabled for <tt>PacketLoggingService.ProtocolName.ARBITRARY</tt>.
     *
     * @param mediaStream the <tt>MediaStream</tt> that will own the newly
     * created <tt>DebugTransformEngine</tt>.
     * @return a new <tt>DebugTransformEngine</tt> if logging is enabled for
     * <tt>PacketLoggingService.ProtocolName.ARBITRARY</tt>, otherwise null.
     */
    public static DebugTransformEngine createDebugTransformEngine(
            MediaStreamImpl mediaStream)
    {
        PacketLoggingService pktLogging = LibJitsi.getPacketLoggingService();

        if (pktLogging != null
                && pktLogging.isLoggingEnabled(
                        PacketLoggingService.ProtocolName.ARBITRARY))
        {
            return new DebugTransformEngine(mediaStream, pktLogging);
        }
        else
        {
            return null;
        }
    }

    @Override
    public SinglePacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    @Override
    public SinglePacketTransformer getRTPTransformer()
    {
        return rtpTransformer;
    }

    /**
     * Logs a specific {@code RawPacket} via the {@code PacketLoggingService}.
     *
     * @param pkt the {@code RawPacket} to log
     * @param data {@code true} if {@code pkt} represents a data/RTP packet or
     * {@code false} if {@code pkt} represents a control/RTCP packet
     * @param sender {@code true} if {@code pkt} has originated from the local
     * peer and is to be sent to the remote peer or {@code false} if {@code pkt}
     * has originated from the remote peer and has been received by the local
     * peer
     * @return {@code pkt}
     */
    private RawPacket transform(RawPacket pkt, boolean data, boolean sender)
    {
        if (pkt == null)
        {
            return null;
        }

        if (mediaStream == null)
        {
            logger.warn(
                    "Not logging a packet because the mediaStream is null");
            return pkt;
        }

        PacketLoggingService pktLogging = _pktLogging;

        if (pktLogging == null)
        {
            logger.warn(
                    "Not logging a packet because the PacketLoggingService is"
                        + " null.");
            return pkt;
        }

        InetSocketAddress src;
        InetSocketAddress dst;

        // When the caller is a sender:
        if (data)
        {
            InetSocketAddress localDataAddress
                = mediaStream.getLocalDataAddress();
            if (localDataAddress == null)
            {
                logger.warn("Not logging a packet because the local data " +
                        "address is null");
                return pkt;
            }

            MediaStreamTarget target = mediaStream.getTarget();
            if (target == null)
            {
                logger.warn("Not logging a packet because the media stream " +
                        "target is null.");
                return pkt;
            }

            InetSocketAddress targetDataAddress = target.getDataAddress();
            if (targetDataAddress == null)
            {
                logger.warn("Not logging a packet because the media stream " +
                        "target address is null.");
                return pkt;
            }

            src = localDataAddress;
            dst = targetDataAddress;
        }
        else
        {
            InetSocketAddress localControlAddress
                = mediaStream.getLocalControlAddress();
            if (localControlAddress == null)
            {
                logger.warn("Not logging a packet because the local data " +
                        "address is null");
                return pkt;
            }

            MediaStreamTarget target = mediaStream.getTarget();
            if (target == null)
            {
                logger.warn("Not logging a packet because the media stream " +
                        "target is null.");
                return pkt;
            }

            InetSocketAddress targetControlAddress = target.getControlAddress();
            if (targetControlAddress == null)
            {
                logger.warn("Not logging a packet because the media stream " +
                        "target address is null.");
                return pkt;
            }

            src = localControlAddress;
            dst = targetControlAddress;
        }

        // When the caller is a receiver, the situation is the exact
        // opposite of when the caller is a sender.
        if (!sender)
        {
            InetSocketAddress swap = src;

            src = dst;
            dst = swap;
        }

        pktLogging.logPacket(
                PacketLoggingService.ProtocolName.ARBITRARY,
                (src != null)
                ? src.getAddress().getAddress()
                : new byte[] { 0, 0, 0, 0 },
                (src != null) ? src.getPort() : 1,
                (dst != null)
                ? dst.getAddress().getAddress()
                : new byte[] { 0, 0, 0, 0 },
                (dst != null) ? dst.getPort() : 1,
                PacketLoggingService.TransportName.UDP,
                sender,
                pkt.getBuffer().clone(),
                pkt.getOffset(),
                pkt.getLength());

        return pkt;
    }

    /**
     * Logs RTP packets that go in and out of the <tt>MediaStream</tt> that owns
     * this instance.
     */
    class MyRTPPacketTransformer
        extends SinglePacketTransformer
    {
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            return
                DebugTransformEngine.this.transform(
                        pkt,
                        /* data */ true,
                        /* sender */ false);
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            return
                DebugTransformEngine.this.transform(
                        pkt,
                        /* data */ true,
                        /* sender */ true);
        }
    }

    /**
     * Logs RTCP packets that go in and out of the <tt>MediaStream</tt> that
     * owns this instance.
     */
    class MyRTCPPacketTransformer
        extends SinglePacketTransformer
    {
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            return
                DebugTransformEngine.this.transform(
                        pkt,
                        /* data */ false,
                        /* sender */ false);
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            return
                DebugTransformEngine.this.transform(
                        pkt,
                        /* data */ false,
                        /* sender */ true);
        }
    }
}
