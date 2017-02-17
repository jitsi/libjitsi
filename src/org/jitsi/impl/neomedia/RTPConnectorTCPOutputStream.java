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
package org.jitsi.impl.neomedia;

import java.io.*;
import java.net.*;

import org.ice4j.socket.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.packetlogging.*;

/**
 * RTPConnectorOutputStream implementation for TCP protocol.
 *
 * @author Sebastien Vincent
 */
public class RTPConnectorTCPOutputStream
    extends RTPConnectorOutputStream
{
    /**
     * TCP socket used to send packet data
     */
    private final Socket socket;

    /**
     * Initializes a new <tt>RTPConnectorTCPOutputStream</tt>.
     *
     * @param socket a <tt>Socket</tt>
     */
    public RTPConnectorTCPOutputStream(Socket socket)
    {
        this.socket = socket;
    }

    /**
     * Sends a specific <tt>RawPacket</tt> through this
     * <tt>OutputDataStream</tt> to a specific <tt>InetSocketAddress</tt>.
     *
     * @param packet the <tt>RawPacket</tt> to send through this
     * <tt>OutputDataStream</tt> to the specified <tt>target</tt>
     * @param target the <tt>InetSocketAddress</tt> to which the specified
     * <tt>packet</tt> is to be sent through this <tt>OutputDataStream</tt>
     * @throws IOException if anything goes wrong while sending the specified
     * <tt>packet</tt> through this <tt>OutputDataStream</tt> to the specified
     * <tt>target</tt>
     */
    @Override
    protected void sendToTarget(RawPacket packet, InetSocketAddress target)
        throws IOException
    {
        socket.getOutputStream().write(
                packet.getBuffer(),
                packet.getOffset(),
                packet.getLength());
    }

    /**
     * Log the packet.
     *
     * @param packet packet to log
     */
    @Override
    protected void doLogPacket(RawPacket packet, InetSocketAddress target)
    {
        // Do not log the packet if it has been processed (and already
        // logged) by the ice4j stack.
        if (socket instanceof MultiplexingSocket)
            return;

        PacketLoggingService pktLogging = getPacketLoggingService();

        if (pktLogging != null)
        {
            pktLogging.logPacket(
                    PacketLoggingService.ProtocolName.RTP,
                    socket.getLocalAddress().getAddress(),
                    socket.getLocalPort(),
                    target.getAddress().getAddress(),
                    target.getPort(),
                    PacketLoggingService.TransportName.TCP,
                    true,
                    packet.getBuffer(),
                    packet.getOffset(),
                    packet.getLength());
        }
    }

    /**
     * Returns whether or not this <tt>RTPConnectorOutputStream</tt> has a valid
     * socket.
     *
     * @return <tt>true</tt>if this <tt>RTPConnectorOutputStream</tt> has a valid
     * socket, and <tt>false</tt> otherwise.
     */
    @Override
    protected boolean isSocketValid()
    {
        return (socket != null);
    }
}
