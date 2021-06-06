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

        while (!sock1.accept())
        {
            Thread.sleep(100);
        }

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
