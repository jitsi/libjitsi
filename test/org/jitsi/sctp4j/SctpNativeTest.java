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
public class SctpNativeTest
{
    @Before
    public void setUp()
    {
        Sctp.init(0);
    }

    @After
    public void tearDown()
    {
        Sctp.finish();
    }

    /**
     * Tests against JVM crash when operations are executed on closed socket.
     */
    @Test
    public void throwIOonClosedSocket()
    {
        final SctpSocket sctpSocket = Sctp.createSocket(5001);

        sctpSocket.close();

        // SctpSocket.send
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                sctpSocket.send(new byte[]{1,2,3}, false, 1, 1);
            }
        });

        // SctpSocket.accept
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                sctpSocket.accept();
            }
        });

        // SctpSocket.listen
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                sctpSocket.listen();
            }
        });

        // SctpSocket.connect
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                sctpSocket.connect(5001);
            }
        });

        // SctpSocket.onConnIn
        testIOException(new IOExceptionRun()
        {
            @Override
            public void run()
                throws IOException
            {
                sctpSocket.onConnIn(new byte[]{1, 2, 3, 4, 5});
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

    @Test
    public void testNPEinConstructor()
    {
        try
        {
            new PublicSctpSocket(0, 0);

            fail("No NPE thrown");
        }
        catch (NullPointerException e)
        {
            // OK
        }
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
