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
import org.jitsi.service.libjitsi.*;
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

        // The transmission of data over a TCP socket is generally slower in
        // comparison to UDP. Moreover, we have observed that writes will block
        // for prolonged periods of time. In order to reduce the effects of
        // these on the threads which invoke the writes, perform the very
        // writing on a background thread.
        setMaxPacketsPerMillis(Integer.MAX_VALUE, 1);
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
        // Do not log the packet if this one has been processed (and already
        // logged) by the ice4j stack.
        if(socket instanceof MultiplexingSocket)
            return;

        PacketLoggingService packetLogging = LibJitsi.getPacketLoggingService();

        if (packetLogging != null)
            packetLogging.logPacket(
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
