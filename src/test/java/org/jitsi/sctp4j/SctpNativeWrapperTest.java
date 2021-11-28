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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

import java.io.*;


/**
 * Test for SCTP native wrapper.
 *
 * @author Pawel Domas
 */
@Disabled("Binaries not committed")
public class SctpNativeWrapperTest
{
    /**
     * Tested socket instance.
     */
    private SctpSocket testSocket;

    @BeforeEach
    public void setUp()
    {
        Sctp.init();

        testSocket = Sctp.createSocket(5000);
    }

    @AfterEach
    public void tearDown()
        throws IOException
    {
        testSocket.close();

        Sctp.finish();
    }

    /**
     * Tests against JVM crash when operations are executed on closed socket.
     */
    @Test
    public void throwIOonClosedSocket()
    {

        testSocket.close();

        // SctpSocket.send
        assertThrows(IOException.class, 
            () -> testSocket.send(new byte[]{1,2,3}, false, 1, 1));

        // SctpSocket.accept
        assertThrows(IOException.class, () -> testSocket.accept());

        // SctpSocket.listen
        assertThrows(IOException.class, () -> testSocket.listen());

        // SctpSocket.connect
        assertThrows(IOException.class, () -> testSocket.connect(5001));

        // SctpSocket.onConnIn
        assertThrows(IOException.class,
            () -> testSocket.onConnIn(new byte[]{1, 2, 3, 4, 5}, 0, 5));
    }

    @Test
    public void testNPEinConstructor()
    {
        assertThrows(NullPointerException.class, () -> new SctpSocket(0, 0));
    }

    /**
     * Tests {@link SctpSocket#onConnIn(byte[], int, int)} method for invalid
     * arguments.
     */
    @Test
    public void testOnConnIn()
        throws IOException
    {
        // Expect NPE
        try
        {
            testSocket.onConnIn(null, 0, 0);
            Assertions.fail("No NPE onConnIn called with null");
        }
        catch (NullPointerException npe)
        {
            // OK
        }

        try
        {
            testSocket.onConnIn(new byte[]{}, 0, 0);
            Assertions.fail("No illegal argument exception");
        }
        catch (IllegalArgumentException e)
        {
            // OK
        }

        try
        {
            testSocket.onConnIn(new byte[]{ 1, 2, 3}, -1, 3);
            Assertions.fail("No illegal argument exception");
        }
        catch (IllegalArgumentException e)
        {
            // OK
        }

        testSocket.onConnIn(new byte[]{ 1, 2, 3}, 0, 3);

        try
        {
            testSocket.onConnIn(new byte[]{ 1, 2, 3}, 2, 2);
            Assertions.fail("No illegal argument exception");
        }
        catch (IllegalArgumentException e)
        {
            // OK
        }
    }

    /**
     * Reproduced JVM crash when socket is closed while having native code
     * on the stack(that will be executed after the socket was closed).
     */
    @Test
    public void testCloseFromNotification()
        throws IOException, InterruptedException
    {
        testSocket.setNotificationListener(
            (socket, notification) -> {
                /*
                 * Notification is fired after usrsctp processed network packet
                 * which means we have native "on_network_in" on current stack.
                 * We own permission to SctpSocket monitor
                 * (because on_network_in requires that) and after we close the
                 * socket and return usrsctp stack will try to finish work after
                 * firing notification and it will operate on closed socket
                 * which will crash the JVM.
                 *
                 * To fix this problem after usrsctp calls "onSctpInboundPacket"
                 * it must be executed on different thread, so that the native
                 * one can continue and finish it's processing without affecting
                 * {@link SctpSocket#socketPtr}(protected by the lock when
                 * accessed from java code).
                 *
                 * FIXME:
                 * We can either detect this situation(and throw exception) or
                 * spawn new thread in "onSctpInboundPacket". For the time
                 * being the second option was applied.
                 */
                if(notification.sn_type == SctpNotification.SCTP_ASSOC_CHANGE)
                {
                    socket.close();
                }
            });

        SctpSocket s2 = Sctp.createSocket(5001);

        DirectLink link = new DirectLink(testSocket, s2);
        testSocket.setLink(link);
        s2.setLink(link);

        s2.listen();
        testSocket.connect(5001);

        Thread.sleep(500);

        s2.close();
    }
}
