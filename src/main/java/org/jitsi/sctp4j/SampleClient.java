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
package org.jitsi.sctp4j;

import org.jitsi.utils.logging.*;

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
