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

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.packetlogging.*;

/**
 * RTPConnectorInputStream implementation for UDP protocol.
 *
 * @author Sebastien Vincent
 */
public class RTPConnectorUDPInputStream
    extends TransformInputStream<DatagramSocket>
{
    /**
     * Initializes a new <tt>RTPConnectorInputStream</tt> which is to receive
     * packet data from a specific UDP socket.
     *
     * @param socket the UDP socket the new instance is to receive data from
     */
    public RTPConnectorUDPInputStream(DatagramSocket socket)
    {
        super(socket);
    }

    /**
     * Log the packet.
     *
     * @param p packet to log
     */
    @Override
    protected void doLogPacket(DatagramPacket p)
    {
        if (socket.getLocalAddress() == null)
            return;

        PacketLoggingService pktLogging = getPacketLoggingService();

        if (pktLogging != null)
        {
            pktLogging.logPacket(
                    PacketLoggingService.ProtocolName.RTP,
                    p.getAddress().getAddress(),
                    p.getPort(),
                    socket.getLocalAddress().getAddress(),
                    socket.getLocalPort(),
                    PacketLoggingService.TransportName.UDP,
                    false,
                    p.getData(),
                    p.getOffset(),
                    p.getLength());
        }
    }

    /**
     * Receive packet.
     *
     * @param p packet for receiving
     * @throws IOException if something goes wrong during receiving
     */
    @Override
    protected void receive(DatagramPacket p)
        throws IOException
    {
        socket.receive(p);
    }

    @Override
    protected void setReceiveBufferSize(int receiveBufferSize)
        throws IOException
    {
        socket.setReceiveBufferSize(receiveBufferSize);
    }
}
