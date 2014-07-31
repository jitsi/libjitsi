/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.dtls;

import java.io.*;
import java.util.*;

import org.bouncycastle.crypto.tls.*;
import org.jitsi.util.*;

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
     *
     * @see DefaultTlsServer#getRSAEncryptionCredentials()
     */
    private TlsEncryptionCredentials rsaEncryptionCredentials;

    /**
     *
     * @see DefaultTlsServer#getRSASignerCredentials()
     */
    private TlsSignerCredentials rsaSignerCredentials;

    /**
     * Initializes a new <tt>TlsServerImpl</tt> instance.
     *
     * @param packetTransformer the <tt>PacketTransformer</tt> which is
     * initializing the new instance
     */
    public TlsServerImpl(DtlsPacketTransformer packetTransformer)
    {
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
     * Gets the <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP
     * server and its client.
     *
     * @return the <tt>SRTPProtectionProfile</tt> negotiated between this
     * DTLS-SRTP server and its client
     */
    int getChosenProtectionProfile()
    {
        return chosenProtectionProfile;
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to explicitly specify cipher suites
     * which we know to be supported by Bouncy Castle. At the time of this
     * writing, we know that Bouncy Castle implements Client Key Exchange only
     * with <tt>TLS_ECDHE_WITH_XXX</tt> and <tt>TLS_RSA_WITH_XXX</tt>.
     */
    @Override
    protected int[] getCipherSuites()
    {
        return
            new int[]
                    {
/* core/src/main/java/org/bouncycastle/crypto/tls/DefaultTlsServer.java */
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256,
                        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
                        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
/* core/src/test/java/org/bouncycastle/crypto/tls/test/MockDTLSServer.java */
                        CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_ESTREAM_SALSA20_SHA1,
                        CipherSuite.TLS_ECDHE_RSA_WITH_SALSA20_SHA1,
                        CipherSuite.TLS_RSA_WITH_ESTREAM_SALSA20_SHA1,
                        CipherSuite.TLS_RSA_WITH_SALSA20_SHA1
                    };
    }

    /**
     * Gets the <tt>TlsContext</tt> with which this <tt>TlsServer</tt> has been
     * initialized.
     *
     * @return the <tt>TlsContext</tt> with which this <tt>TlsServer</tt> has
     * been initialized
     */
    TlsContext getContext()
    {
        return context;
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

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>TlsServerImpl</tt> always returns
     * <tt>ProtocolVersion.DTLSv10</tt> because <tt>ProtocolVersion.DTLSv12</tt>
     * does not work with the Bouncy Castle Crypto APIs at the time of this
     * writing.
     */
    @Override
    protected ProtocolVersion getMaximumVersion()
    {
        return ProtocolVersion.DTLSv10;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ProtocolVersion getMinimumVersion()
    {
        return ProtocolVersion.DTLSv10;
    }

    /**
     * {@inheritDoc}
     *
     * Depending on the <tt>selectedCipherSuite</tt>, <tt>DefaultTlsServer</tt>
     * will require either <tt>rsaEncryptionCredentials</tt> or
     * <tt>rsaSignerCredentials</tt> neither of which is implemented by
     * <tt>DefaultTlsServer</tt>.
     */
    @Override
    protected TlsEncryptionCredentials getRSAEncryptionCredentials()
        throws IOException
    {
        if (rsaEncryptionCredentials == null)
        {
            DtlsControlImpl dtlsControl = getDtlsControl();

            rsaEncryptionCredentials
                = new DefaultTlsEncryptionCredentials(
                        context,
                        dtlsControl.getCertificate(),
                        dtlsControl.getKeyPair().getPrivate());
        }
        return rsaEncryptionCredentials;
    }

    /**
     * {@inheritDoc}
     *
     * Depending on the <tt>selectedCipherSuite</tt>, <tt>DefaultTlsServer</tt>
     * will require either <tt>rsaEncryptionCredentials</tt> or
     * <tt>rsaSignerCredentials</tt> neither of which is implemented by
     * <tt>DefaultTlsServer</tt>.
     */
    @Override
    protected TlsSignerCredentials getRSASignerCredentials()
        throws IOException
    {
        if (rsaSignerCredentials == null)
        {
            DtlsControlImpl dtlsControl = getDtlsControl();

            /*
             * FIXME The signature and hash algorithms should be retrieved from
             * the certificate.
             */
            rsaSignerCredentials
                = new DefaultTlsSignerCredentials(
                        context,
                        dtlsControl.getCertificate(),
                        dtlsControl.getKeyPair().getPrivate(),
                        new SignatureAndHashAlgorithm(
                                HashAlgorithm.sha1,
                                SignatureAlgorithm.rsa));
        }
        return rsaSignerCredentials;
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

        if (getDtlsControl().isSrtpDisabled())
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
                /*
                 * Upon receipt of a "use_srtp" extension containing a
                 * "srtp_mki" field, the server MUST include a matching
                 * "srtp_mki" value in its "use_srtp" extension to indicate that
                 * it will make use of the MKI.
                 */
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
     * {@inheritDoc}
     *
     * Overrides the super implementation as a simple means of detecting that
     * the security-related negotiations between the local and the remote
     * enpoints are starting. The detection carried out for the purposes of
     * <tt>SrtpListener</tt>.
     */
    @Override
    public void init(TlsServerContext context)
    {
        // TODO Auto-generated method stub
        super.init(context);
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
        if (getDtlsControl().isSrtpDisabled())
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

            /*
             * If there is no shared profile and that is not acceptable, the
             * server SHOULD return an appropriate DTLS alert.
             */
            if (chosenProtectionProfile == 0)
            {
                String msg = "No chosen SRTP protection profile!";
                TlsFatalAlert tfa
                    = new TlsFatalAlert(AlertDescription.illegal_parameter);

                logger.error(msg, tfa);
                throw tfa;
            }
            else
                super.processClientExtensions(clientExtensions);
        }
    }
}
