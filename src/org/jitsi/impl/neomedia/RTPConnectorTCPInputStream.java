/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.io.*;
import java.net.*;

import org.ice4j.socket.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.util.*;

/**
 * RTPConnectorInputStream implementation for TCP protocol.
 *
 * @author Sebastien Vincent
 */
public class RTPConnectorTCPInputStream
    extends TransformInputStream<Socket>
{
    /**
     * The <tt>Logger</tt> used by instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(RTPConnectorTCPInputStream.class);

    /**
     * Initializes a new <tt>RTPConnectorInputStream</tt> which is to receive
     * packet data from a specific TCP socket.
     *
     * @param socket the TCP socket the new instance is to receive data from
     */
    public RTPConnectorTCPInputStream(Socket socket)
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
        if(socket.getLocalAddress() == null)
            return;

        // Do not log the packet if this one has been processed (and already
        // logged) by the ice4j stack.
        if(socket instanceof MultiplexingSocket)
            return;

        PacketLoggingService packetLogging = LibJitsi.getPacketLoggingService();

        if (packetLogging != null)
            packetLogging.logPacket(
                    PacketLoggingService.ProtocolName.RTP,
                    (p.getAddress() != null)
                            ? p.getAddress().getAddress()
                            : new byte[] { 0,0,0,0 },
                    p.getPort(),
                    socket.getLocalAddress().getAddress(),
                    socket.getLocalPort(),
                    PacketLoggingService.TransportName.TCP,
                    false,
                    p.getData(),
                    p.getOffset(),
                    p.getLength());
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
        byte[] data;
        int len;

        try
        {
            data = p.getData();
            len = socket.getInputStream().read(data);
        }
        catch(Exception e)
        {
            data = null;
            len = -1;
            logger.info("problem read: " + e);
        }

        if(len > 0)
        {
            p.setData(data);
            p.setLength(len);
            p.setAddress(socket.getInetAddress());
            p.setPort(socket.getPort());
        }
        else
        {
            throw new IOException("Failed to read on TCP socket");
        }
    }

    @Override
    protected void setReceiveBufferSize(int receiveBufferSize)
        throws IOException
    {
        socket.setReceiveBufferSize(receiveBufferSize);
    }
}
