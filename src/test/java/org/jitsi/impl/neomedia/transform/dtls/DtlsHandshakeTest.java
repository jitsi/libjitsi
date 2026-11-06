/*
 * Copyright @ 2026 - present 8x8, Inc.
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
package org.jitsi.impl.neomedia.transform.dtls;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.bouncycastle.tls.*;
import org.jitsi.service.libjitsi.*;
import org.junit.jupiter.api.*;

/**
 * Runs a DTLS handshake between a {@link TlsClientImpl} (setup ACTIVE) and a
 * {@link TlsServerImpl} (setup PASSIVE) over an in-memory datagram pipe.
 */
public class DtlsHandshakeTest
{
    @BeforeAll
    public static void startLibJitsi()
    {
        LibJitsi.start();
    }

    @AfterAll
    public static void stopLibJitsi()
    {
        LibJitsi.stop();
    }

    @Test
    public void activeClientConnectsToPassiveServer()
        throws Exception
    {
        DtlsControlImpl clientControl = new DtlsControlImpl();
        DtlsControlImpl serverControl = new DtlsControlImpl();

        clientControl.setRemoteFingerprints(fingerprintsOf(serverControl));
        serverControl.setRemoteFingerprints(fingerprintsOf(clientControl));

        DtlsPacketTransformer clientTransformer = new DtlsPacketTransformer(
            new DtlsTransformEngine(clientControl), 1);
        DtlsPacketTransformer serverTransformer = new DtlsPacketTransformer(
            new DtlsTransformEngine(serverControl), 1);

        BlockingQueue<byte[]> toServer = new LinkedBlockingQueue<>();
        BlockingQueue<byte[]> toClient = new LinkedBlockingQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            Future<DTLSTransport> server = executor.submit(
                () -> new DTLSServerProtocol().accept(
                    new TlsServerImpl(serverTransformer),
                    new Pipe(toServer, toClient)));
            Future<DTLSTransport> client = executor.submit(
                () -> new DTLSClientProtocol().connect(
                    new TlsClientImpl(clientTransformer),
                    new Pipe(toClient, toServer)));

            assertNotNull(server.get(30, TimeUnit.SECONDS));
            assertNotNull(client.get(30, TimeUnit.SECONDS));
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private static Map<String, String> fingerprintsOf(DtlsControlImpl control)
    {
        return Collections.singletonMap(
            control.getLocalFingerprintHashFunction(),
            control.getLocalFingerprint());
    }

    private static class Pipe
        implements DatagramTransport
    {
        private final BlockingQueue<byte[]> in;

        private final BlockingQueue<byte[]> out;

        Pipe(BlockingQueue<byte[]> in, BlockingQueue<byte[]> out)
        {
            this.in = in;
            this.out = out;
        }

        @Override
        public int getReceiveLimit()
        {
            return 1500;
        }

        @Override
        public int getSendLimit()
        {
            return 1500;
        }

        @Override
        public int receive(byte[] buf, int off, int len, int waitMillis)
            throws IOException
        {
            try
            {
                byte[] packet = in.poll(waitMillis, TimeUnit.MILLISECONDS);
                if (packet == null)
                {
                    return -1;
                }
                int n = Math.min(len, packet.length);
                System.arraycopy(packet, 0, buf, off, n);
                return n;
            }
            catch (InterruptedException e)
            {
                throw new InterruptedIOException();
            }
        }

        @Override
        public void send(byte[] buf, int off, int len)
        {
            out.add(Arrays.copyOfRange(buf, off, off + len));
        }

        @Override
        public void close()
        {
        }
    }
}
