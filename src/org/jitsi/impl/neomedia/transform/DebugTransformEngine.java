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

import org.jitsi.impl.libjitsi.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.service.packetlogging.*;

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
 */
public class DebugTransformEngine implements TransformEngine
{
    /**
     * The <tt>MediaStream</tt> that owns this instance.
     */
    private final MediaStreamImpl mediaStream;

    /**
     * The <tt>PacketTransformer</tt> that logs RTP packets.
     */
    private final PacketTransformer rtpTransformer
        = new MyRTPPacketTransformer();

    /**
     * The <tt>PacketTransformer</tt> that logs RTCP packets.
     */
    private final PacketTransformer rtcpTransformer
        = new MyRTCPPacketTransformer();

    /**
     * Ctor.
     *
     * @param mediaStream the <tt>MediaStream</tt> that owns this instance.
     */
    public DebugTransformEngine(MediaStreamImpl mediaStream)
    {
        this.mediaStream = mediaStream;
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
        PacketLoggingService packetLogging
            = LibJitsiImpl.getPacketLoggingService();

        if (packetLogging != null && packetLogging.isLoggingEnabled(
                    PacketLoggingService.ProtocolName.ARBITRARY))
        {
            return new DebugTransformEngine(mediaStream);
        }
        else
        {
            return null;
        }

    }

    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this.rtpTransformer;
    }

    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return this.rtcpTransformer;
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
            PacketLoggingService packetLogging
                = LibJitsiImpl.getPacketLoggingService();

            if (packetLogging != null)
            {
                InetSocketAddress sourceAddress
                    = mediaStream.getTarget().getDataAddress();

                InetSocketAddress destinationAddress
                    = mediaStream.getLocalDataAddress();

                packetLogging.logPacket(
                    PacketLoggingService.ProtocolName.ARBITRARY,
                    (sourceAddress != null)
                        ? sourceAddress.getAddress().getAddress()
                        : new byte[]{0, 0, 0, 0},
                    (sourceAddress != null)
                        ? sourceAddress.getPort()
                        : 1,
                    (destinationAddress != null)
                        ? destinationAddress.getAddress().getAddress()
                        : new byte[]{0, 0, 0, 0},
                    (destinationAddress != null)
                        ? destinationAddress.getPort()
                        : 1,
                    PacketLoggingService.TransportName.UDP,
                    false,
                    pkt.getBuffer().clone(),
                    pkt.getOffset(),
                    pkt.getLength());
            }

            return pkt;
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            PacketLoggingService packetLogging
                = LibJitsiImpl.getPacketLoggingService();

            if (packetLogging != null)
            {
                InetSocketAddress sourceAddress
                    = mediaStream.getLocalDataAddress();

                InetSocketAddress destinationAddress
                    = mediaStream.getTarget().getDataAddress();

                packetLogging.logPacket(
                    PacketLoggingService.ProtocolName.ARBITRARY,
                    (sourceAddress != null)
                        ? sourceAddress.getAddress().getAddress()
                        : new byte[]{0, 0, 0, 0},
                    (sourceAddress != null)
                        ? sourceAddress.getPort()
                        : 1,
                    (destinationAddress != null)
                        ? destinationAddress.getAddress().getAddress()
                        : new byte[]{0, 0, 0, 0},
                    (destinationAddress != null)
                        ? destinationAddress.getPort()
                        : 1,
                    PacketLoggingService.TransportName.UDP,
                    true,
                    pkt.getBuffer().clone(),
                    pkt.getOffset(),
                    pkt.getLength());
            }

            return pkt;
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
            PacketLoggingService packetLogging
                = LibJitsiImpl.getPacketLoggingService();

            if (packetLogging != null)
            {
                InetSocketAddress sourceAddress
                    = mediaStream.getTarget().getControlAddress();

                InetSocketAddress destinationAddress
                    = mediaStream.getLocalControlAddress();

                packetLogging.logPacket(
                    PacketLoggingService.ProtocolName.ARBITRARY,
                    (sourceAddress != null)
                        ? sourceAddress.getAddress().getAddress()
                        : new byte[]{0, 0, 0, 0},
                    (sourceAddress != null)
                        ? sourceAddress.getPort()
                        : 1,
                    (destinationAddress != null)
                        ? destinationAddress.getAddress().getAddress()
                        : new byte[]{0, 0, 0, 0},
                    (destinationAddress != null)
                        ? destinationAddress.getPort()
                        : 1,
                    PacketLoggingService.TransportName.UDP,
                    false,
                    pkt.getBuffer().clone(),
                    pkt.getOffset(),
                    pkt.getLength());
            }

            return pkt;
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            PacketLoggingService packetLogging
                = LibJitsiImpl.getPacketLoggingService();

            if (packetLogging != null)
            {
                InetSocketAddress sourceAddress
                    = mediaStream.getLocalControlAddress();

                InetSocketAddress destinationAddress
                    = mediaStream.getTarget().getControlAddress();

                packetLogging.logPacket(
                    PacketLoggingService.ProtocolName.ARBITRARY,
                    (sourceAddress != null)
                        ? sourceAddress.getAddress().getAddress()
                        : new byte[]{0, 0, 0, 0},
                    (sourceAddress != null)
                        ? sourceAddress.getPort()
                        : 1,
                    (destinationAddress != null)
                        ? destinationAddress.getAddress().getAddress()
                        : new byte[]{0, 0, 0, 0},
                    (destinationAddress != null)
                        ? destinationAddress.getPort()
                        : 1,
                    PacketLoggingService.TransportName.UDP,
                    true,
                    pkt.getBuffer().clone(),
                    pkt.getOffset(),
                    pkt.getLength());
            }

            return pkt;
        }
    }
}
