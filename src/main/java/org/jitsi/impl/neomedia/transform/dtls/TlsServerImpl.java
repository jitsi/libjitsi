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
package org.jitsi.impl.neomedia.transform.dtls;

import static org.jitsi.impl.neomedia.transform.dtls.DtlsUtils.BC_TLS_CRYPTO;

import java.io.*;
import java.util.*;

import org.bouncycastle.tls.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.utils.logging.*;

/**
 * Implements {@link TlsServer} for the purposes of supporting DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class TlsServerImpl
    extends DefaultTlsServer
{
    /**
     * The <tt>Logger</tt> used by the <tt>TlsServerImpl</tt> class and its
     * instances to print debug information.
     */
    private static final Logger logger = Logger.getLogger(TlsServerImpl.class);

    /**
     *
     * @see TlsServer#getCertificateRequest()
     */
    private final CertificateRequest certificateRequest
        = new CertificateRequest(
                new short[] { ClientCertificateType.rsa_sign },
                /* supportedSignatureAlgorithms */ null,
                /* certificateAuthorities */ null);

    /**
     * The <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP
     * server and its client.
     */
    private int chosenProtectionProfile;

    /**
     * The <tt>PacketTransformer</tt> which has initialized this instance.
     */
    private final DtlsPacketTransformer packetTransformer;

    /**
     * Initializes a new <tt>TlsServerImpl</tt> instance.
     *
     * @param packetTransformer the <tt>PacketTransformer</tt> which is
     * initializing the new instance
     */
    public TlsServerImpl(DtlsPacketTransformer packetTransformer)
    {
        super(BC_TLS_CRYPTO);
        this.packetTransformer = packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateRequest getCertificateRequest()
    {
        return certificateRequest;
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to explicitly specify cipher suites
     * which we know to be supported by Bouncy Castle and provide Perfect
     * Forward Secrecy.
     */
    @Override
    public int[] getCipherSuites()
    {
        int[] suites = new int[]
        {
/* core/src/main/java/org/bouncycastle/crypto/tls/DefaultTlsServer.java */
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA
        };
        return TlsUtils.getSupportedCipherSuites(getCrypto(), suites);
    }

    /**
     * Gets the <tt>DtlsControl</tt> implementation associated with this
     * instance.
     *
     * @return the <tt>DtlsControl</tt> implementation associated with this
     * instance
     */
    private DtlsControlImpl getDtlsControl()
    {
        return packetTransformer.getDtlsControl();
    }

    private Properties getProperties()
    {
        return packetTransformer.getProperties();
    }

    /**
     * {@inheritDoc}
     *
     * Includes the <tt>use_srtp</tt> extension in the DTLS extended server
     * hello.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public Hashtable getServerExtensions()
        throws IOException
    {
        Hashtable serverExtensions = super.getServerExtensions();

        if (isSrtpDisabled())
        {
            return serverExtensions;
        }

        if (TlsSRTPUtils.getUseSRTPExtension(serverExtensions) == null)
        {
            if (serverExtensions == null)
                serverExtensions = new Hashtable();

            UseSRTPData useSRTPData
                = TlsSRTPUtils.getUseSRTPExtension(clientExtensions);
            int chosenProtectionProfile
                = DtlsControlImpl.chooseSRTPProtectionProfile(
                        useSRTPData.getProtectionProfiles());

            /*
             * If there is no shared profile and that is not acceptable, the
             * server SHOULD return an appropriate DTLS alert.
             */
            if (chosenProtectionProfile == 0)
            {
                String msg = "No chosen SRTP protection profile!";
                TlsFatalAlert tfa
                    = new TlsFatalAlert(AlertDescription.internal_error);

                logger.error(msg, tfa);
                throw tfa;
            }
            else
            {
                // Upon receipt of a "use_srtp" extension containing a
                // "srtp_mki" field, the server MUST include a matching
                // "srtp_mki" value in its "use_srtp" extension to indicate that
                // it will make use of the MKI.
                TlsSRTPUtils.addUseSRTPExtension(
                        serverExtensions,
                        new UseSRTPData(
                                new int[] { chosenProtectionProfile },
                                useSRTPData.getMki()));
                this.chosenProtectionProfile = chosenProtectionProfile;
            }
        }
        return serverExtensions;
    }

    /**
     * Determines whether this {@code TlsServerImpl} is to operate in pure DTLS
     * mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return {@code true} for pure DTLS without SRTP extensions or
     * {@code false} for DTLS/SRTP
     */
    private boolean isSrtpDisabled()
    {
        return getProperties().isSrtpDisabled();
    }

    /**
     * {@inheritDoc}
     *
     * Forwards to {@link #packetTransformer}.
     */
    @Override
    public void notifyAlertRaised(
            short alertLevel,
            short alertDescription,
            String message,
            Throwable cause)
    {
        packetTransformer.notifyAlertRaised(
                this,
                alertLevel, alertDescription, message, cause);
    }

    @Override
    public void notifyHandshakeComplete()
    {
        if (packetTransformer.getProperties().isSrtpDisabled())
        {
            // SRTP is disabled, nothing to do. Why did we get here in
            // the first place?
            return;
        }

        SinglePacketTransformer srtpTransformer
            = packetTransformer.initializeSRTPTransformer(
            chosenProtectionProfile, context);

        synchronized (packetTransformer)
        {
            packetTransformer.setSrtpTransformer(srtpTransformer);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyClientCertificate(Certificate clientCertificate)
        throws IOException
    {
        try
        {
            getDtlsControl().verifyAndValidateCertificate(clientCertificate);
        }
        catch (Exception e)
        {
            logger.error(
                    "Failed to verify and/or validate client certificate!",
                    e);
            if (e instanceof IOException)
                throw (IOException) e;
            else
                throw new IOException(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that the DTLS extended client hello contains the
     * <tt>use_srtp</tt> extension.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void processClientExtensions(Hashtable clientExtensions)
        throws IOException
    {
        if (isSrtpDisabled())
        {
            super.processClientExtensions(clientExtensions);
            return;
        }

        UseSRTPData useSRTPData
            = TlsSRTPUtils.getUseSRTPExtension(clientExtensions);

        if (useSRTPData == null)
        {
            String msg
                = "DTLS extended client hello does not include the use_srtp"
                    + " extension!";
            IOException ioe = new IOException(msg);

            logger.error(msg, ioe);
            throw ioe;
        }
        else
        {
            int chosenProtectionProfile
                = DtlsControlImpl.chooseSRTPProtectionProfile(
                        useSRTPData.getProtectionProfiles());

            // If there is no shared profile and that is not acceptable, the
            // server SHOULD return an appropriate DTLS alert.
            if (chosenProtectionProfile == 0)
            {
                String msg = "No chosen SRTP protection profile!";
                TlsFatalAlert tfa
                    = new TlsFatalAlert(AlertDescription.illegal_parameter);

                logger.error(msg, tfa);
                throw tfa;
            }
            else
            {
                super.processClientExtensions(clientExtensions);
            }
        }
    }
}
