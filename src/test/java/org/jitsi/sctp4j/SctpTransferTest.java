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

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;

import static org.junit.jupiter.api.Assertions.*;

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

    private byte[] receivedData = null;

    /**
     * Set random generator seed for consistent tests.
     */
    private static final Random rand = new Random(12345);

    /**
     * How many tests to perform.
     */
    private final int ITERATIONS = 10;

    /**
     * How long to wait for data during lossy send.
     */
    private final long SECONDS_TO_WAIT = 10;

    /**
     * The loss rate to simulate.
     */
    private final double LOSS_RATE = 0.2;

    /**
     * The error rate to simulate.
     */
    private final double ERROR_RATE = 0.1;

    @BeforeEach
    public void setUp()
    {
        Sctp.init();

        peerA = Sctp.createSocket(portA);
        peerB = Sctp.createSocket(portB);
    }

    @AfterEach
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
        rand.nextBytes(dummy);
        return dummy;
    }

    /**
     * Tests the transfer with random link failures and packet loss.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testSocketBrokenLink()
        throws Exception
    {
        TestLink link = new TestLink(peerA, peerB, LOSS_RATE, ERROR_RATE);

        peerA.setLink(link);
        peerB.setLink(link);

        peerA.connect(portB);
        peerB.connect(portA);

        byte[] toSendA = createRandomData(2*1024);
        for (int i = 0; i < ITERATIONS; i++)
        {
            System.out.println("Testing broken link, iteration " + (i+1)
                    + " of " + ITERATIONS + ". NOTE: IOExceptions may be "
                    + "visible during this test, and are expected.");
            testTransferPart(peerA, peerB, toSendA, SECONDS_TO_WAIT);
        }
    }

    private void testTransferPart(
            SctpSocket sender,
            SctpSocket receiver,
            byte[] testData,
            long timeoutInSeconds)
        throws Exception
    {
        final CountDownLatch dataReceivedLatch = new CountDownLatch(1);
        receiver.setDataCallback(
            (data, sid, ssn, tsn, ppid, context, flags) -> {
                receivedData = data;
                dataReceivedLatch.countDown();
            });

        sender.send(testData, true, 0, 0);
        if (dataReceivedLatch.await(timeoutInSeconds, TimeUnit.SECONDS))
        {
            assertArrayEquals(testData, receivedData);
        }
        else
        {
            fail("Test data not received within "
                    + timeoutInSeconds + " seconds.");
        }
    }
}
