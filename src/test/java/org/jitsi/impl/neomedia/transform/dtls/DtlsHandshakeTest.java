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

import static org.jitsi.impl.neomedia.transform.dtls.DtlsUtils.BC_TLS_CRYPTO;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.*;
import org.bouncycastle.tls.crypto.impl.bc.*;
import org.jitsi.service.libjitsi.*;
import org.junit.jupiter.api.*;

/**
 * Runs DTLS handshakes over an in-memory datagram pipe, between a
 * {@link TlsClientImpl} (setup ACTIVE) and a {@link TlsServerImpl} (setup
 * PASSIVE), or between one of them and a plain BouncyCastle peer whose
 * certificate libjitsi would not generate itself.
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

        handshake(libjitsiServer(serverControl), libjitsiClient(clientControl));
    }

    @Test
    public void mismatchedFingerprintFailsTheHandshake()
        throws Exception
    {
        DtlsControlImpl clientControl = new DtlsControlImpl();
        DtlsControlImpl serverControl = new DtlsControlImpl();

        clientControl.setRemoteFingerprints(
            corrupt(fingerprintsOf(serverControl)));
        serverControl.setRemoteFingerprints(fingerprintsOf(clientControl));

        assertThrows(
            ExecutionException.class,
            () -> handshake(
                libjitsiServer(serverControl),
                libjitsiClient(clientControl)));
    }

    @Test
    public void acceptsUnsignedServerCertificate()
        throws Exception
    {
        // RFC 9925: the server's certificate has the id-alg-unsigned signature
        // algorithm, as jitsi-videobridge can be configured to present. The
        // fingerprint check must not need its (absent) digest, and nothing
        // else in the handshake may reject it.
        CertificateInfo unsigned = TestCertificates.unsigned();
        DtlsControlImpl clientControl = new DtlsControlImpl();

        clientControl.setRemoteFingerprints(fingerprintsOf(unsigned));

        handshake(bcServer(unsigned), libjitsiClient(clientControl));
    }

    @Test
    public void acceptsUnsignedClientCertificate()
        throws Exception
    {
        CertificateInfo unsigned = TestCertificates.unsigned();
        DtlsControlImpl serverControl = new DtlsControlImpl();

        serverControl.setRemoteFingerprints(fingerprintsOf(unsigned));

        handshake(libjitsiServer(serverControl), bcClient(unsigned));
    }

    @Test
    public void rejectsUnsignedCertificateWithWrongFingerprint()
        throws Exception
    {
        // The unsigned certificate is accepted on the strength of its
        // fingerprint alone, so a wrong fingerprint must still reject it.
        CertificateInfo unsigned = TestCertificates.unsigned();
        DtlsControlImpl clientControl = new DtlsControlImpl();

        clientControl.setRemoteFingerprints(corrupt(fingerprintsOf(unsigned)));

        assertThrows(
            ExecutionException.class,
            () -> handshake(bcServer(unsigned), libjitsiClient(clientControl)));
    }

    /** One end of a DTLS handshake, run over a given transport. */
    private interface Side
    {
        DTLSTransport run(DatagramTransport transport)
            throws IOException;
    }

    private static Side libjitsiServer(DtlsControlImpl control)
    {
        return transport -> new DTLSServerProtocol().accept(
            new TlsServerImpl(transformer(control)), transport);
    }

    private static Side libjitsiClient(DtlsControlImpl control)
    {
        return transport -> new DTLSClientProtocol().connect(
            new TlsClientImpl(transformer(control)), transport);
    }

    private static DtlsPacketTransformer transformer(DtlsControlImpl control)
    {
        return new DtlsPacketTransformer(new DtlsTransformEngine(control), 1);
    }

    /**
     * A plain BouncyCastle DTLS 1.2 server which presents
     * {@code certificateInfo}, accepts any client certificate and echoes the
     * first SRTP protection profile the client offers.
     */
    private static Side bcServer(CertificateInfo certificateInfo)
    {
        return transport -> new DTLSServerProtocol().accept(
            new DefaultTlsServer(BC_TLS_CRYPTO)
            {
                private int chosenProfile;

                @Override
                protected ProtocolVersion[] getSupportedVersions()
                {
                    return ProtocolVersion.DTLSv12.only();
                }

                @Override
                protected int[] getSupportedCipherSuites()
                {
                    return TlsUtils.getSupportedCipherSuites(
                        getCrypto(),
                        new int[]
                        {
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
                        });
                }

                @Override
                protected TlsCredentialedSigner getECDSASignerCredentials()
                {
                    return signer(context, certificateInfo);
                }

                @Override
                @SuppressWarnings("rawtypes")
                public void processClientExtensions(Hashtable clientExtensions)
                    throws IOException
                {
                    super.processClientExtensions(clientExtensions);
                    chosenProfile
                        = TlsSRTPUtils.getUseSRTPExtension(clientExtensions)
                            .getProtectionProfiles()[0];
                }

                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public Hashtable getServerExtensions()
                    throws IOException
                {
                    Hashtable serverExtensions = super.getServerExtensions();

                    if (serverExtensions == null)
                        serverExtensions = new Hashtable();
                    TlsSRTPUtils.addUseSRTPExtension(
                        serverExtensions,
                        new UseSRTPData(
                            new int[] { chosenProfile },
                            TlsUtils.EMPTY_BYTES));
                    return serverExtensions;
                }
            },
            transport);
    }

    /**
     * A plain BouncyCastle DTLS 1.2 client which presents
     * {@code certificateInfo} when asked for a certificate, offers libjitsi's
     * SRTP protection profiles and accepts any server certificate.
     */
    private static Side bcClient(CertificateInfo certificateInfo)
    {
        return transport -> new DTLSClientProtocol().connect(
            new DefaultTlsClient(BC_TLS_CRYPTO)
            {
                @Override
                protected ProtocolVersion[] getSupportedVersions()
                {
                    return ProtocolVersion.DTLSv12.only();
                }

                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public Hashtable getClientExtensions()
                    throws IOException
                {
                    Hashtable clientExtensions = super.getClientExtensions();

                    if (clientExtensions == null)
                        clientExtensions = new Hashtable();
                    TlsSRTPUtils.addUseSRTPExtension(
                        clientExtensions,
                        new UseSRTPData(
                            DtlsControlImpl.SRTP_PROTECTION_PROFILES,
                            TlsUtils.EMPTY_BYTES));
                    return clientExtensions;
                }

                @Override
                public TlsAuthentication getAuthentication()
                {
                    return new TlsAuthentication()
                    {
                        @Override
                        public void notifyServerCertificate(
                                TlsServerCertificate serverCertificate)
                        {
                        }

                        @Override
                        public TlsCredentials getClientCredentials(
                                CertificateRequest certificateRequest)
                        {
                            return signer(context, certificateInfo);
                        }
                    };
                }
            },
            transport);
    }

    /**
     * Signs the handshake with the key of {@code certificateInfo}, as ECDSA
     * with SHA-256 whatever the signature algorithm of the certificate.
     */
    private static TlsCredentialedSigner signer(
            TlsContext context,
            CertificateInfo certificateInfo)
    {
        return new BcDefaultTlsCredentialedSigner(
            new TlsCryptoParameters(context),
            BC_TLS_CRYPTO,
            certificateInfo.getKeyPair().getPrivate(),
            certificateInfo.getCertificate(),
            SignatureAndHashAlgorithm.getInstance(
                HashAlgorithm.sha256, SignatureAlgorithm.ecdsa));
    }

    /**
     * Runs a DTLS handshake between the two sides and fails if either does
     * not complete it.
     */
    private static void handshake(Side serverSide, Side clientSide)
        throws Exception
    {
        BlockingQueue<byte[]> toServer = new LinkedBlockingQueue<>();
        BlockingQueue<byte[]> toClient = new LinkedBlockingQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            Future<DTLSTransport> server = executor.submit(
                () -> serverSide.run(new Pipe(toServer, toClient)));
            Future<DTLSTransport> client = executor.submit(
                () -> clientSide.run(new Pipe(toClient, toServer)));

            // Wait for both sides before failing: the side which raised an
            // alert knows why, the other only received e.g. internal_error.
            Throwable clientFailure = failureOf(client);
            Throwable serverFailure = failureOf(server);

            if (clientFailure != null || serverFailure != null)
            {
                throw new ExecutionException(
                    "client: " + clientFailure + ", server: " + serverFailure,
                    serverFailure != null ? serverFailure : clientFailure);
            }
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    /**
     * Waits for one side of the handshake and returns why it failed, or
     * {@code null} if it completed.
     */
    private static Throwable failureOf(Future<DTLSTransport> side)
        throws Exception
    {
        try
        {
            assertNotNull(side.get(30, TimeUnit.SECONDS));
            return null;
        }
        catch (ExecutionException e)
        {
            return e.getCause();
        }
    }

    /** Flips the first hex digit of each fingerprint. */
    private static Map<String, String> corrupt(Map<String, String> fingerprints)
    {
        Map<String, String> wrong = new HashMap<>();

        for (Map.Entry<String, String> e : fingerprints.entrySet())
        {
            String fp = e.getValue();
            wrong.put(
                e.getKey(),
                (fp.charAt(0) == '0' ? '1' : '0') + fp.substring(1));
        }
        return wrong;
    }

    private static Map<String, String> fingerprintsOf(DtlsControlImpl control)
    {
        return fingerprintsOf(control.getCertificateInfo());
    }

    private static Map<String, String> fingerprintsOf(CertificateInfo info)
    {
        return Collections.singletonMap(
            info.localFingerprintHashFunction, info.localFingerprint);
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
