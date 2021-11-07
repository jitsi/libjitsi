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

import org.bouncycastle.asn1.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.tls.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.utils.logging.*;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;

/**
 * Implements {@link TlsClient} for the purposes of supporting DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class TlsClientImpl
    extends DefaultTlsClient
{
    /**
     * The <tt>Logger</tt> used by the <tt>TlsClientImpl</tt> class and its
     * instances to print debug information.
     */
    private static final Logger logger = Logger.getLogger(TlsClientImpl.class);

    private final TlsAuthentication authentication
        = new TlsAuthenticationImpl();

    /**
     * The <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP
     * client and its server.
     */
    private int chosenProtectionProfile;

    /**
     * The SRTP Master Key Identifier (MKI) used by the
     * <tt>SRTPCryptoContext</tt> associated with this instance. Since the
     * <tt>SRTPCryptoContext</tt> class does not utilize it, the value is
     * {@link TlsUtils#EMPTY_BYTES}.
     */
    private final byte[] mki = TlsUtils.EMPTY_BYTES;

    /**
     * The <tt>PacketTransformer</tt> which has initialized this instance.
     */
    private final DtlsPacketTransformer packetTransformer;

    /**
     * Initializes a new <tt>TlsClientImpl</tt> instance.
     *
     * @param packetTransformer the <tt>PacketTransformer</tt> which is
     * initializing the new instance
     */
    public TlsClientImpl(DtlsPacketTransformer packetTransformer)
    {
        super(BC_TLS_CRYPTO);
        this.packetTransformer = packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized TlsAuthentication getAuthentication()
    {
        return authentication;
    }

    /**
     * Always returns {@link ProtocolVersion#DTLSv12}.
     */
    @Override
    protected ProtocolVersion[] getSupportedVersions()
    {
        return ProtocolVersion.DTLSv12.only();
    }

    /**
     * {@inheritDoc}
     *
     * Includes the <tt>use_srtp</tt> extension in the DTLS extended client
     * hello.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public Hashtable getClientExtensions()
        throws IOException
    {
        Hashtable clientExtensions = super.getClientExtensions();

        if (!isSrtpDisabled()
                && TlsSRTPUtils.getUseSRTPExtension(clientExtensions) == null)
        {
            if (clientExtensions == null)
                clientExtensions = new Hashtable();
            TlsSRTPUtils.addUseSRTPExtension(
                    clientExtensions,
                    new UseSRTPData(
                            DtlsControlImpl.SRTP_PROTECTION_PROFILES,
                            mki));
        }
        return clientExtensions;
    }

    /**
     * Determines whether this {@code TlsClientImpl} is to operate in pure DTLS
     * mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return {@code true} for pure DTLS without SRTP extensions or
     * {@code false} for DTLS/SRTP
     */
    private boolean isSrtpDisabled()
    {
        return packetTransformer.getProperties().isSrtpDisabled();
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
     *
     * Makes sure that the DTLS extended server hello contains the
     * <tt>use_srtp</tt> extension.
     */
    @Override
    public void processServerExtensions(Hashtable serverExtensions)
        throws IOException
    {
        if (isSrtpDisabled())
        {
            super.processServerExtensions(serverExtensions);
            return;
        }

        UseSRTPData useSRTPData
            = TlsSRTPUtils.getUseSRTPExtension(serverExtensions);

        if (useSRTPData == null)
        {
            String msg
                = "DTLS extended server hello does not include the use_srtp"
                    + " extension!";
            IOException ioe = new IOException(msg);

            logger.error(msg, ioe);
            throw ioe;
        }
        else
        {
            int[] protectionProfiles = useSRTPData.getProtectionProfiles();
            int chosenProtectionProfile
                = (protectionProfiles.length == 1)
                    ? DtlsControlImpl.chooseSRTPProtectionProfile(
                            protectionProfiles[0])
                    : 0;

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
                /*
                 * If the client detects a nonzero-length MKI in the server's
                 * response that is different than the one the client offered,
                 * then the client MUST abort the handshake and SHOULD send an
                 * invalid_parameter alert.
                 */
                byte[] mki = useSRTPData.getMki();

                if (Arrays.equals(mki, this.mki))
                {
                    super.processServerExtensions(serverExtensions);

                    this.chosenProtectionProfile = chosenProtectionProfile;
                }
                else
                {
                    String msg
                        = "Server's MKI does not match the one offered by this"
                            + " client!";
                    TlsFatalAlert tfa
                        = new TlsFatalAlert(AlertDescription.illegal_parameter);

                    logger.error(msg, tfa);
                    throw tfa;
                }
            }
        }
    }

    /**
     * Implements {@link TlsAuthentication} for the purposes of supporting
     * DTLS-SRTP.
     *
     * @author Lyubomir Marinov
     */
    private class TlsAuthenticationImpl
        implements TlsAuthentication
    {
        private TlsCredentials clientCredentials;

        /**
         * {@inheritDoc}
         */
        @Override
        public TlsCredentials getClientCredentials(
                CertificateRequest certificateRequest)
        {
            if (clientCredentials == null)
            {
                CertificateInfo certificateInfo
                    = packetTransformer.getDtlsControl().getCertificateInfo();

                // FIXME ed448/ed25519? multiple certificates?
                String algName = new DefaultAlgorithmNameFinder()
                    .getAlgorithmName(new ASN1ObjectIdentifier(certificateInfo
                        .getCertificate()
                        .getCertificateAt(0)
                        .getSigAlgOID()));
                SignatureAndHashAlgorithm sigAndHashAlg = null;
                switch (algName)
                {
                case "SHA1WITHRSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha1, SignatureAlgorithm.rsa);
                    break;
                case "SHA224WITHRSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha224, SignatureAlgorithm.rsa);
                    break;
                case "SHA256WITHRSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha256, SignatureAlgorithm.rsa);
                    break;
                case "SHA384WITHRSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha384, SignatureAlgorithm.rsa);
                    break;
                case "SHA512WITHRSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha512, SignatureAlgorithm.rsa);
                    break;
                case "SHA1WITHECDSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha1, SignatureAlgorithm.ecdsa);
                    break;
                case "SHA224WITHECDSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha224, SignatureAlgorithm.ecdsa);
                    break;
                case "SHA256WITHECDSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa);
                    break;
                case "SHA384WITHECDSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha384, SignatureAlgorithm.ecdsa);
                    break;
                case "SHA512WITHECDSA":
                    sigAndHashAlg = SignatureAndHashAlgorithm
                        .getInstance(HashAlgorithm.sha512, SignatureAlgorithm.ecdsa);
                    break;
                default:
                    logger.warn("Unknown algOid in certificate: " + algName);
                    return null;
                }

                clientCredentials
                    = new BcDefaultTlsCredentialedSigner(
                            new TlsCryptoParameters(context),
                            (BcTlsCrypto) context.getCrypto(),
                            certificateInfo.getKeyPair().getPrivate(),
                            certificateInfo.getCertificate(),
                            sigAndHashAlg);
            }
            return clientCredentials;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void notifyServerCertificate(TlsServerCertificate serverCertificate)
            throws IOException
        {
            try
            {
                packetTransformer
                    .getDtlsControl()
                    .verifyAndValidateCertificate(
                        serverCertificate.getCertificate());
            }
            catch (Exception e)
            {
                logger.error(
                        "Failed to verify and/or validate server certificate!",
                        e);
                if (e instanceof IOException)
                    throw (IOException) e;
                else
                    throw new IOException(e);
            }
        }
    }
}
