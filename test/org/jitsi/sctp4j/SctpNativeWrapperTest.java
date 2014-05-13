/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import java.io.*;

import static org.junit.Assert.fail;

/**
 * Test for SCTP native wrapper.
 *
 * @author Pawel Domas
 */
@RunWith(JUnit4.class)
public class SctpNativeWrapperTest
{
    /**
     * Tested socket instance.
     */
    private SctpSocket testSocket;

    @Before
    public void setUp()
    {
        Sctp.init();

        testSocket = Sctp.createSocket(5000);
    }

    @After
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
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                testSocket.send(new byte[]{1,2,3}, false, 1, 1);
            }
        });

        // SctpSocket.accept
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                testSocket.accept();
            }
        });

        // SctpSocket.listen
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                testSocket.listen();
            }
        });

        // SctpSocket.connect
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                testSocket.connect(5001);
            }
        });

        // SctpSocket.onConnIn
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                testSocket.onConnIn(new byte[]{1, 2, 3, 4, 5});
            }
        });
    }

    private void testIOException(IOExceptionRun methodRunCode)
    {
        try
        {
            methodRunCode.run();

            // No IOException
            fail("expected IOException");
        }
        catch (IOException e)
        {
            // Success
        }
    }

    @Test(expected = NullPointerException.class)
    public void testNPEinConstructor()
    {
        new SctpSocket(0, 0);
    }

    /**
     * Tests {@link SctpSocket#onConnIn(byte[])} method for invalid arguments.
     */
    @Test
    public void testOnConnIn()
        throws IOException
    {
        // Expect NPE
        try
        {
            testSocket.onConnIn(null);
            fail("No NPE onConnIn called with null");
        }
        catch (NullPointerException npe)
        {
            // OK
        }

        // Test empty buffer, should not crash
        testSocket.onConnIn(new byte[]{});
    }

    /**
     * Interface used to test whether enclosed code
     * will throw an <tt>IOException</tt>.
     */
    private interface IOExceptionRun
    {
        /**
         * Runs the code that shall throw <tt>IOException</tt>.
         */
        public void run() throws IOException;
    }
}
