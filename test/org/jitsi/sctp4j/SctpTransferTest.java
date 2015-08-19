/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import org.junit.*;

import java.io.*;
import java.util.*;

import static org.junit.Assert.assertArrayEquals;

/**
 * Transfer tests.
 *
 * @author Pawel Domas
 */
public class SctpTransferTest
{
    private SctpSocket peerA;

    private final int portA = 5000;

    private SctpSocket peerB;

    private final int portB = 5001;

    private final Object transferLock = new Object();

    private byte[] receivedData = null;

    @Before
    public void setUp()
    {
        Sctp.init();

        peerA = Sctp.createSocket(portA);
        peerB = Sctp.createSocket(portB);
    }

    @After
    public void tearDown()
        throws IOException
    {
        peerA.close();
        peerB.close();

        Sctp.finish();
    }

    public static byte[] createRandomData(int size)
    {
        byte[] dummy = new byte[size];
        new Random().nextBytes(dummy);
        return dummy;
    }

    /**
     * Tests the transfer with random link failures and packet loss.
     *
     * @throws Exception
     */
    @Test
    public void testSocketBrokenLink()
        throws Exception
    {
        TestLink link = new TestLink(peerA, peerB,
                                     0.2, /* loss rate */
                                     0.1  /* error rate */);

        peerA.setLink(link);
        peerB.setLink(link);

        peerA.connect(portB);
        peerB.connect(portA);

        byte[] toSendA = createRandomData(2*1024);
        for(int i=0; i < 10; i++)
        {
            try
            {
                testTransferPart(peerA, peerB, toSendA, 5000);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private void testTransferPart(SctpSocket sender, SctpSocket receiver,
                                  byte[] testData, long timeout)
        throws Exception
    {
        receiver.setDataCallback(new SctpDataCallback()
        {
            @Override
            public void onSctpPacket(byte[] data, int sid, int ssn, int tsn,
                                     long ppid,
                                     int context, int flags)
            {
                synchronized (transferLock)
                {
                    receivedData = data;
                    transferLock.notifyAll();
                }
            }
        });

        sender.send(testData, true, 0, 0);

        synchronized (transferLock)
        {
            transferLock.wait(timeout);

            assertArrayEquals(testData, receivedData);
        }
    }
}
