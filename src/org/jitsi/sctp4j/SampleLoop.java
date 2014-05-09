/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import org.jitsi.util.*;

import java.io.*;

/**
 * Sample that uses two <tt>SctpSocket</tt>s with {@link DirectLink}.
 *
 * @author Pawel Domas
 */
public class SampleLoop
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(SampleLoop.class);

    public static void main(String[] args) throws Exception
    {
        Sctp.init();

        final SctpSocket server = Sctp.createSocket(5001);
        final SctpSocket client = Sctp.createSocket(5002);
        
        DirectLink link = new DirectLink(server, client);
        server.setLink(link);
        client.setLink(link);
        
        // Make server passive
        server.listen();

        // Client thread
        new Thread(
          new Runnable()
          {
            public void run()
            {
                try
                {
                    if(!client.connect(server.getPort()))
                    {
                        // FIXME: Unknown error returned on Windows,
                        //        but it works after that
                        //return;
                    }
                    logger.info("Client: connect");

                    try { Thread.sleep(1000); } catch(Exception e) { }

                    int sent = client.send(new byte[200], false, 0, 0);
                    logger.info("Client sent: " + sent);

                }
                catch (IOException e)
                {
                    logger.error(e, e);
                }
            }
          }
        ).start();

        server.setDataCallback(
            new SctpDataCallback()
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
            }
        );

        Thread.sleep(5*1000);
        
        server.close();
        client.close();
        
        Sctp.finish();
    }
}
