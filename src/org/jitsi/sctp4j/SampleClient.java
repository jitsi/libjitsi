/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import org.jitsi.util.*;

/**
 * Sample SCTP client that uses UDP socket for transfers.
 *
 * @author Pawel Domas
 */
public class SampleClient
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(SampleClient.class);

    public static void main(String[] args) throws Exception
    {
        String localAddr = "127.0.0.1";
        int localPort = 48002;
        int localSctpPort = 5002;

        String remoteAddr = "127.0.0.1";
        int remotePort = 48001;
        int remoteSctpPort = 5001;

        Sctp.init();

        final SctpSocket client = Sctp.createSocket(localSctpPort);

        UdpLink link
            = new UdpLink(
                client, localAddr, localPort, remoteAddr, remotePort);

        client.setLink(link);

        client.connect(remoteSctpPort);
                    
        try { Thread.sleep(1000); } catch(Exception e) { }
                    
        int sent = client.send(new byte[200], false, 0, 0);
        logger.info("Client sent: "+sent);
        
        Thread.sleep(4000);
        
        client.close();
        
        Sctp.finish();
    }
}
