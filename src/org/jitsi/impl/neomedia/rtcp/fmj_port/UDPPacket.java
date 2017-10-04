package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Date;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.util.UDPPacket
 */
public class UDPPacket
    extends Packet
{
    public DatagramPacket datagrampacket;
    public int localPort;
    public int remotePort;
    public InetAddress remoteAddress;

    @Override
    public String toString()
    {
        String s = "UDP Packet of size " + super.length;
        if (super.received)
            s = s + " received at " + new Date(super.receiptTime) + " on port "
                    + localPort + " from " + remoteAddress + " port "
                    + remotePort;
        return s;
    }
}
