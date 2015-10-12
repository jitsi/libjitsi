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
 * Logs all the packets that go in and out of a <tt>MediaStream</tt>.
 *
 * @author George Politis
 */
public class DebugTransformEngine implements TransformEngine
{
    //region State

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
     * A boolean that indicates whether packet logging should be enabled or not.
     */
    private boolean enabled = false;

    /**
     * A boolean that indicates whether this instance has been initialized or
     * not.
     */
    private boolean initialized = false;

    //endregion

    //region Public methods

    /**
     * Ctor.
     *
     * @param mediaStream the <tt>MediaStream</tt> that owns this instance.
     */
    public DebugTransformEngine(MediaStreamImpl mediaStream)
    {
        this.mediaStream = mediaStream;
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

    //endregion

    //region Private methods

    private void assertInitialized()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        PacketLoggingService packetLogging
            = LibJitsiImpl.getPacketLoggingService();

        this.enabled = packetLogging != null
            && packetLogging.isLoggingEnabled(
                    PacketLoggingService.ProtocolName.ARBITRARY);
    }

    //endregion

    //region Nested types

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
            DebugTransformEngine.this.assertInitialized();
            if (!enabled)
            {
                return pkt;
            }

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
            DebugTransformEngine.this.assertInitialized();
            if (!enabled)
            {
                return pkt;
            }

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
            DebugTransformEngine.this.assertInitialized();
            if (!enabled)
            {
                return pkt;
            }

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
            DebugTransformEngine.this.assertInitialized();
            if (!enabled)
            {
                return pkt;
            }

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

    //endregion
}
