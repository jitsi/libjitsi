/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import org.jitsi.util.*;

import java.net.*;
import java.io.*;

/**
 * Class used in code samples to send SCTP packets through UDP sockets.
 *
 * FIXME: fix receiving loop
 *
 * @author Pawel Domas
 */
public class UdpLink
    implements NetworkLink
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(UdpLink.class);

    /**
     * <tt>SctpSocket</tt> instance that is used in this connection.
     */
    private final SctpSocket sctpSocket;

    /**
     * Udp socket used for transport.
     */
    private final DatagramSocket udpSocket;

    /**
     * Destination UDP port.
     */
    private final int remotePort;

    /**
     * Destination <tt>InetAddress</tt>.
     */
    private final InetAddress remoteIp;

    /**
     * Creates new instance of <tt>UdpConnection</tt>.
     *
     * @param sctpSocket SCTP socket instance used by this connection.
     * @param localIp local IP address.
     * @param localPort local UDP port.
     * @param remoteIp remote address.
     * @param remotePort destination UDP port.
     * @throws IOException when we fail to resolve any of addresses
     *                     or when opening UDP socket.
     */
    public UdpLink(SctpSocket sctpSocket,
                   String localIp, int localPort,
                   String remoteIp, int remotePort)
        throws IOException
    {
        this.sctpSocket = sctpSocket;
        
        this.udpSocket
            = new DatagramSocket(localPort, InetAddress.getByName(localIp));

        this.remotePort = remotePort;
        this.remoteIp = InetAddress.getByName(remoteIp);
        
        // Listening thread
        new Thread(
            new Runnable()
            {
                public void run()
                {
                    try
                    {
                        byte[] buff = new byte[2048];
                        DatagramPacket p = new DatagramPacket(buff, 2048);
                        while(true)
                        {
                            udpSocket.receive(p);
                            int len = p.getLength();
                            byte[] packet = new byte[len];
                            System.arraycopy(buff, 0, packet, 0, len);
                            UdpLink.this.sctpSocket.onConnIn(packet);
                        }
                    }
                    catch(IOException e)
                    {
                        logger.error(e, e);
                    }
                }    
            }
        ).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConnOut(final SctpSocket s, final byte[] packetData)
    {
        try
        {
            DatagramPacket packet 
                = new DatagramPacket( packetData,
                                      packetData.length,
                                      remoteIp,
                                      remotePort);
            udpSocket.send(packet);
        }
        catch(IOException e)
        {
            logger.error("Error while trying to send SCTP packet", e);
        }
    }
}
