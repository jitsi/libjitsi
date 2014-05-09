/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import org.jitsi.util.*;

/**
 * Sample SCTP server that uses UDP socket for transfers.
 *
 * @author Pawel Domas
 */
public class SampleServer
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(SampleServer.class);

    public static void main(String[] args) throws Exception
    {
        String localAddr = "127.0.0.1";
        int localPort = 48001;
        int localSctpPort = 5001;

        String remoteAddr = "127.0.0.1";
        int remotePort = 48002;

        Sctp.init();

        final SctpSocket sock1 = Sctp.createSocket(localSctpPort);
        
        UdpLink link
            = new UdpLink(
                sock1, localAddr, localPort, remoteAddr, remotePort);

        sock1.setLink(link);

        sock1.listen();

        sock1.accept();

        sock1.setDataCallback(new SctpDataCallback()
        {
            @Override
            public void onSctpPacket(byte[] data, int sid, int ssn, int tsn,
                                     long ppid,
                                     int context, int flags)
            {
                logger.info("Server got some data: " + data.length
                                + " stream: " + sid
                                + " payload protocol id: " + ppid);
            }
        });

        Thread.sleep(40000);
        
        sock1.close();
        
        Sctp.finish();
    }
    
}
