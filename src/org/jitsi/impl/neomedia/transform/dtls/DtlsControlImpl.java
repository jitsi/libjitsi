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

import java.io.*;
import java.math.*;
import java.security.*;
import java.util.*;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.*;
import org.bouncycastle.asn1.x500.style.*;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.*;
import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.generators.*;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.tls.*;
import org.bouncycastle.crypto.util.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.operator.bc.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.version.*;
import org.jitsi.util.*;

/**
 * Implements {@link DtlsControl} i.e. {@link SrtpControl} for DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class DtlsControlImpl
    extends AbstractSrtpControl<DtlsTransformEngine>
    implements DtlsControl
{
    /**
     * The map which specifies which hash functions are to be considered
     * &quot;upgrades&quot; of which other hash functions. The keys are the hash
     * functions which have &quot;upgrades&quot; defined and are written in
     * lower case.
     */
    private static final Map<String,String[]> HASH_FUNCTION_UPGRADES
        = new HashMap<String,String[]>();

    /**
     * The table which maps half-<tt>byte</tt>s to their hex characters.
     */
    private static final char[] HEX_ENCODE_TABLE
        = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };

    /**
     * The <tt>Logger</tt> used by the <tt>DtlsControlImpl</tt> class and its
     * instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(DtlsControlImpl.class);

    /**
     * The number of milliseconds within a day i.e. 24 hours.
     */
    private static final long ONE_DAY = 1000L * 60L * 60L * 24L;

    /**
     * The name of the property which specifies the signature algorithm used 
     * during certificate creation. When a certificate is created and this 
     * property is not set, a default value of "SHA1withRSA" will be used.
     */
    public static final String PROP_SIGNATURE_ALGORITHM = 
        "org.jitsi.impl.neomedia.transform.dtls.SIGNATURE_ALGORITHM";

    /**
     * The <tt>SRTPProtectionProfile</tt>s supported by
     * <tt>DtlsControlImpl</tt>.
     */
    static final int[] SRTP_PROTECTION_PROFILES
        = {
            SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80,
            SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_32
        };

    /**
     * The indicator which specifies whether {@code DtlsControlImpl} is to tear
     * down the media session if the fingerprint does not match the hashed
     * certificate. The default value is {@code true} and may be overridden by
     * the {@code ConfigurationService} and/or {@code System} property
     * {@code VERIFY_AND_VALIDATE_CERTIFICATE_PNAME}.
     */
    private static final boolean VERIFY_AND_VALIDATE_CERTIFICATE;

    /**
     * The name of the {@code ConfigurationService} and/or {@code System}
     * property which specifies whether {@code DtlsControlImpl} is to tear down
     * the media session if the fingerprint does not match the hashed
     * certificate. The default value is {@code true}.
     */
    private static final String VERIFY_AND_VALIDATE_CERTIFICATE_PNAME
        = DtlsControlImpl.class.getName() + ".verifyAndValidateCertificate";

    /**
     * {@link #keyPair} cache
     */
    private static AsymmetricCipherKeyPair _cache_keyPair;

    /**
     * {@link #certificate} cache
     */
    private static org.bouncycastle.crypto.tls.Certificate _cache_certificate;

    /**
     * {@link #localFingerprintHashFunction} cache
     */
    private static String _cache_localFingerprintHashFunction;

    /**
     * {@link #localFingerprint} cache
     */
    private static String _cache_localFingerprint;

    /**
     *  cache generation timestamp (in milliseconds of system time).
     */
    private static long _cache_timestamp;

    static
    {
        // VERIFY_AND_VALIDATE_CERTIFICATE
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean verifyAndValidateCertificate = true;

        if (cfg == null)
        {
            String s
                = System.getProperty(VERIFY_AND_VALIDATE_CERTIFICATE_PNAME);

            if (s != null)
                verifyAndValidateCertificate = Boolean.parseBoolean(s);
        }
        else
        {
            verifyAndValidateCertificate
                = cfg.getBoolean(
                        VERIFY_AND_VALIDATE_CERTIFICATE_PNAME,
                        verifyAndValidateCertificate);
        }
        VERIFY_AND_VALIDATE_CERTIFICATE = verifyAndValidateCertificate;

        // HASH_FUNCTION_UPGRADES
        HASH_FUNCTION_UPGRADES.put(
                "sha-1",
                new String[] { "sha-224", "sha-256", "sha-384", "sha-512" });
    }

    /**
     * Chooses the first from a list of <tt>SRTPProtectionProfile</tt>s that is
     * supported by <tt>DtlsControlImpl</tt>.
     *
     * @param theirs the list of <tt>SRTPProtectionProfile</tt>s to choose from
     * @return the first from the specified <tt>theirs</tt> that is supported
     * by <tt>DtlsControlImpl</tt>
     */
    static int chooseSRTPProtectionProfile(int... theirs)
    {
        int[] ours = SRTP_PROTECTION_PROFILES;

        if (theirs != null)
        {
            for (int t = 0; t < theirs.length; t++)
            {
                int their = theirs[t];

                for (int o = 0; o < ours.length; o++)
                {
                    int our = ours[o];

                    if (their == our)
                        return their;
                }
            }
        }
        return 0;
    }

    /**
     * Computes the fingerprint of a specific certificate using a specific
     * hash function.
     *
     * @param certificate the certificate the fingerprint of which is to be
     * computed
     * @param hashFunction the hash function to be used in order to compute the
     * fingerprint of the specified <tt>certificate</tt> 
     * @return the fingerprint of the specified <tt>certificate</tt> computed
     * using the specified <tt>hashFunction</tt>
     */
    private static final String computeFingerprint(
            org.bouncycastle.asn1.x509.Certificate certificate,
            String hashFunction)
    {
        try
        {
            AlgorithmIdentifier digAlgId
                = new DefaultDigestAlgorithmIdentifierFinder().find(
                        hashFunction.toUpperCase());
            Digest digest = BcDefaultDigestProvider.INSTANCE.get(digAlgId);
            byte[] in = certificate.getEncoded(ASN1Encoding.DER);
            byte[] out = new byte[digest.getDigestSize()];

            digest.update(in, 0, in.length);
            digest.doFinal(out, 0);

            return toHex(out);
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
            {
                throw (ThreadDeath) t;
            }
            else
            {
                logger.error("Failed to generate certificate fingerprint!", t);
                if (t instanceof RuntimeException)
                    throw (RuntimeException) t;
                else
                    throw new RuntimeException(t);
            }
        }
    }

    /**
     * Determines the hash function i.e. the digest algorithm of the signature
     * algorithm of a specific certificate.
     *
     * @param certificate the certificate the hash function of which is to be
     * determined
     * @return the hash function of the specified <tt>certificate</tt> written
     * in lower case
     */
    private static String findHashFunction(
            org.bouncycastle.asn1.x509.Certificate certificate)
    {
        try
        {
            AlgorithmIdentifier sigAlgId = certificate.getSignatureAlgorithm();
            AlgorithmIdentifier digAlgId
                = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);

            return
                BcDefaultDigestProvider.INSTANCE
                    .get(digAlgId)
                        .getAlgorithmName()
                            .toLowerCase();
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
            {
                throw (ThreadDeath) t;
            }
            else
            {
                logger.warn(
                        "Failed to find the hash function of the signature"
                            + " algorithm of a certificate!",
                        t);
                if (t instanceof RuntimeException)
                    throw (RuntimeException) t;
                else
                    throw new RuntimeException(t);
            }
        }
    }

    /**
     * Finds a hash function which is an &quot;upgrade&quot; of a specific hash
     * function and has a fingerprint associated with it.
     *
     * @param hashFunction the hash function which is not associated with a
     * fingerprint and for which an &quot;upgrade&quot; associated with a
     * fingerprint is to be found
     * @param fingerprints the set of available hash function-fingerprint
     * associations
     * @return a hash function written in lower case which is an
     * &quot;upgrade&quot; of the specified {@code hashFunction} and has a
     * fingerprint associated with it in {@code fingerprints} if there is such a
     * hash function; otherwise, {@code null}
     */
    private static String findHashFunctionUpgrade(
            String hashFunction,
            Map<String,String> fingerprints)
    {
        String[] hashFunctionUpgrades
            = HASH_FUNCTION_UPGRADES.get(hashFunction);

        if (hashFunctionUpgrades != null)
        {
            for (String hashFunctionUpgrade : hashFunctionUpgrades)
            {
                String fingerprint = fingerprints.get(hashFunctionUpgrade);

                if (fingerprint != null)
                    return hashFunctionUpgrade.toLowerCase();
            }
        }
        return null;
    }

    /**
     * Generates a new subject for a self-signed certificate to be generated by
     * <tt>DtlsControlImpl</tt>.
     * 
     * @return an <tt>X500Name</tt> which is to be used as the subject of a
     * self-signed certificate to be generated by <tt>DtlsControlImpl</tt>
     */
    private static X500Name generateCN()
    {
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
        String applicationName
            = System.getProperty(Version.PNAME_APPLICATION_NAME);
        String applicationVersion
            = System.getProperty(Version.PNAME_APPLICATION_VERSION);
        StringBuilder cn = new StringBuilder();

        if (!StringUtils.isNullOrEmpty(applicationName, true))
            cn.append(applicationName);
        if (!StringUtils.isNullOrEmpty(applicationVersion, true))
        {
            if (cn.length() != 0)
                cn.append(' ');
            cn.append(applicationVersion);
        }
        if (cn.length() == 0)
            cn.append(DtlsControlImpl.class.getName());
        builder.addRDN(BCStyle.CN, cn.toString());

        return builder.build();
    }

    /**
     * Return a pair of RSA private and public keys.
     *
     * @return a pair of private and public keys
     */
    private static AsymmetricCipherKeyPair generateKeyPair()
    {
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        generator.init(
                new RSAKeyGenerationParameters(
                        new BigInteger("10001", 16),
                        new SecureRandom(),
                        1024,
                        80));
        return generator.generateKeyPair();
    }

    /**
     * Generates a new self-signed certificate with a specific subject and a
     * specific pair of private and public keys.
     *
     * @param subject the subject (and issuer) of the new certificate to be
     * generated
     * @param keyPair the pair of private and public keys of the certificate to
     * be generated
     * @return a new self-signed certificate with the specified 
     * <tt>subject</tt> and <tt>keyPair</tt>
     */
    private static org.bouncycastle.asn1.x509.Certificate
        generateX509Certificate(
                X500Name subject,
                AsymmetricCipherKeyPair keyPair)
    {
        // get property for certificate creation and default to sha1
        String signatureAlgorithm = "SHA1withRSA";
        // get property override from the config service if it exists
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            signatureAlgorithm
                = cfg.getString(PROP_SIGNATURE_ALGORITHM, "SHA1withRSA");
        }        
        if (logger.isDebugEnabled())
        {
            logger.debug("Signature algorithm: " + signatureAlgorithm);
        }
        try
        {
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now - ONE_DAY);
            Date notAfter = new Date(now + 6 * ONE_DAY);
            X509v3CertificateBuilder builder
                = new X509v3CertificateBuilder(
                        /* issuer */ subject,
                        /* serial */ BigInteger.valueOf(now),
                        notBefore,
                        notAfter,
                        subject,
                        /* publicKeyInfo */
                            SubjectPublicKeyInfoFactory
                                .createSubjectPublicKeyInfo(
                                    keyPair.getPublic()));
            AlgorithmIdentifier sigAlgId
                = new DefaultSignatureAlgorithmIdentifierFinder()
                    .find(signatureAlgorithm);
            AlgorithmIdentifier digAlgId
                = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
            ContentSigner signer
                = new BcRSAContentSignerBuilder(sigAlgId, digAlgId)
                    .build(keyPair.getPrivate());

            return builder.build(signer).toASN1Structure();
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
            {
                throw (ThreadDeath) t;
            }
            else
            {
                logger.error(
                        "Failed to generate self-signed X.509 certificate",
                        t);
                if (t instanceof RuntimeException)
                    throw (RuntimeException) t;
                else
                    throw new RuntimeException(t);
            }
        }
    }

    /**
     * generate a new KeyPair and Certificate
     */
    private static void refreshKeyCertCache()
    {
        _cache_keyPair = generateKeyPair();

        org.bouncycastle.asn1.x509.Certificate x509Certificate =
            generateX509Certificate(generateCN(), _cache_keyPair);

        _cache_certificate =
            new org.bouncycastle.crypto.tls.Certificate(
                new org.bouncycastle.asn1.x509.Certificate[]
                    {
                        x509Certificate
                    });
        _cache_localFingerprintHashFunction = findHashFunction(x509Certificate);
        _cache_localFingerprint =
            computeFingerprint(
                x509Certificate,
                _cache_localFingerprintHashFunction);

        _cache_timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the <tt>String</tt> representation of a fingerprint specified in the
     * form of an array of <tt>byte</tt>s in accord with RFC 4572.
     *
     * @param fingerprint an array of <tt>bytes</tt> which represents a
     * fingerprint the <tt>String</tt> representation in accord with RFC 4572
     * of which is to be returned 
     * @return the <tt>String</tt> representation in accord with RFC 4572 of the
     * specified <tt>fingerprint</tt>
     */
    private static String toHex(byte[] fingerprint)
    {
        if (fingerprint.length == 0)
            throw new IllegalArgumentException("fingerprint");

        char[] chars = new char[3 * fingerprint.length - 1];

        for (int f = 0, fLast = fingerprint.length - 1, c = 0;
                f <= fLast;
                f++)
        {
            int b = fingerprint[f] & 0xff;

            chars[c++] = HEX_ENCODE_TABLE[b >>> 4];
            chars[c++] = HEX_ENCODE_TABLE[b & 0x0f];
            if (f != fLast)
                chars[c++] = ':';
        }
        return new String(chars);
    }

    /**
     * The certificate with which the local endpoint represented by this
     * instance authenticates its ends of DTLS sessions. 
     */
    private final org.bouncycastle.crypto.tls.Certificate certificate;

    /**
     * The <tt>RTPConnector</tt> which uses the <tt>TransformEngine</tt> of this
     * <tt>SrtpControl</tt>.
     */
    private AbstractRTPConnector connector;

    /**
     * Indicates whether this <tt>DtlsControl</tt> will work in DTLS/SRTP or
     * DTLS mode.
     */
    private final boolean disableSRTP;

    /**
     * The indicator which determines whether this instance has been disposed
     * i.e. prepared for garbage collection by {@link #doCleanup()}.
     */
    private boolean disposed = false;

    /**
     * The private and public keys of {@link #certificate}.
     */
    private final AsymmetricCipherKeyPair keyPair;

    /**
     * The fingerprint of {@link #certificate}.
     */
    private final String localFingerprint;

    /**
     * The hash function of {@link #localFingerprint} (which is the same as the
     * digest algorithm of the signature algorithm of {@link #certificate} in
     * accord with RFC 4572).
     */
    private final String localFingerprintHashFunction;

    /**
     * The fingerprints presented by the remote endpoint via the signaling path. 
     */
    private Map<String,String> remoteFingerprints;

    /**
     * Whether rtcp-mux is in use.
     */
    private boolean rtcpmux = false;

    /**
     * The value of the <tt>setup</tt> SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance acts as a DTLS client
     * or a DTLS server.
     */
    private Setup setup;

    /**
     * The instances currently registered as users of this <tt>SrtpControl</tt>
     * (through {@link #registerUser(Object)}).
     */
    private final Set<Object> users = new HashSet<Object>();

    /**
     * Initializes a new <tt>DtlsControlImpl</tt> instance.
     */
    public DtlsControlImpl()
    {
        // By default we work in DTLS/SRTP mode.
        this(false);
    }

    /**
     * Initializes a new <tt>DtlsControlImpl</tt> instance.
     * @param disableSRTP <tt>true</tt> if pure DTLS mode without SRTP
     *                    extensions should be used.
     */
    public DtlsControlImpl(boolean disableSRTP)
    {
        super(SrtpControlType.DTLS_SRTP);

        this.disableSRTP = disableSRTP;

        synchronized (DtlsControlImpl.class)
        {
            if (_cache_timestamp + ONE_DAY < System.currentTimeMillis())
                refreshKeyCertCache();

            keyPair = _cache_keyPair;
            certificate = _cache_certificate;
            localFingerprintHashFunction = _cache_localFingerprintHashFunction;
            localFingerprint = _cache_localFingerprint;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup(Object user)
    {
        synchronized (users)
        {
            if (users.remove(user) && users.isEmpty())
                doCleanup();
        }
    }

    /**
     * Initializes a new <tt>DtlsTransformEngine</tt> instance to be associated
     * with and used by this <tt>DtlsControlImpl</tt> instance.
     *
     * @return a new <tt>DtlsTransformEngine</tt> instance to be associated with
     * and used by this <tt>DtlsControlImpl</tt> instance
     */
    @Override
    protected DtlsTransformEngine createTransformEngine()
    {
        DtlsTransformEngine transformEngine = new DtlsTransformEngine(this);

        transformEngine.setConnector(connector);
        transformEngine.setSetup(setup);
        transformEngine.setRtcpmux(rtcpmux);
        return transformEngine;
    }

    /**
     * Prepares this <tt>DtlsControlImpl</tt> for garbage collection.
     */
    private void doCleanup()
    {
        super.cleanup(null);

        setConnector(null);

        synchronized (this)
        {
            disposed = true;
            notifyAll();
        }
    }

    /**
     * Gets the certificate with which the local endpoint represented by this
     * instance authenticates its ends of DTLS sessions.
     *
     * @return the certificate with which the local endpoint represented by this
     * instance authenticates its ends of DTLS sessions.
     */
    org.bouncycastle.crypto.tls.Certificate getCertificate()
    {
        return certificate;
    }

    /**
     * The private and public keys of the <tt>certificate</tt> of this instance.
     *
     * @return the private and public keys of the <tt>certificate</tt> of this
     * instance
     */
    AsymmetricCipherKeyPair getKeyPair()
    {
        return keyPair;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprint()
    {
        return localFingerprint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprintHashFunction()
    {
        return localFingerprintHashFunction;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getSecureCommunicationStatus()
    {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Indicates if SRTP extensions are disabled which means we're working in
     * pure DTLS mode.
     * @return <tt>true</tt> if SRTP extensions must be disabled.
     */
    boolean isSrtpDisabled()
    {
        return disableSRTP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerUser(Object user)
    {
        synchronized (users)
        {
            users.add(user);
        }
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>DtlsControlImpl</tt> always returns
     * <tt>true</tt>.
     */
    @Override
    public boolean requiresSecureSignalingTransport()
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConnector(AbstractRTPConnector connector)
    {
        if (this.connector != connector)
        {
            this.connector = connector;

            DtlsTransformEngine transformEngine = this.transformEngine;

            if (transformEngine != null)
                transformEngine.setConnector(this.connector);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRemoteFingerprints(Map<String,String> remoteFingerprints)
    {
        if (remoteFingerprints == null)
            throw new NullPointerException("remoteFingerprints");

        synchronized (this)
        {
            // Make sure that the hash functions (which are keys of the field
            // remoteFingerprints) are written in lower case.
            Map<String,String> rfs
                = new HashMap<String,String>(remoteFingerprints.size());

            for (Map.Entry<String,String> e : remoteFingerprints.entrySet())
            {
                String k = e.getKey();

                // It makes no sense to provide a fingerprint without a hash
                // function.
                if (k != null)
                {
                    String v = e.getValue();

                    // It makes no sense to provide a hash function without a
                    // fingerprint.
                    if (v != null)
                        rfs.put(k.toLowerCase(), v);
                }
            }
            this.remoteFingerprints = rfs;

            notifyAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRtcpmux(boolean rtcpmux)
    {
        if (this.rtcpmux != rtcpmux)
        {
            this.rtcpmux = rtcpmux;

            DtlsTransformEngine transformEngine = this.transformEngine;

            if (transformEngine != null)
                transformEngine.setRtcpmux(rtcpmux);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSetup(Setup setup)
    {
        if (this.setup != setup)
        {
            this.setup = setup;

            DtlsTransformEngine transformEngine = this.transformEngine;

            if (transformEngine != null)
                transformEngine.setSetup(this.setup);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(MediaType mediaType)
    {
        DtlsTransformEngine transformEngine = getTransformEngine();

        if (transformEngine != null)
            transformEngine.start(mediaType);
    }

    /**
     * Verifies and validates a specific certificate against the fingerprints
     * presented by the remote endpoint via the signaling path.
     *
     * @param certificate the certificate to be verified and validated against
     * the fingerprints presented by the remote endpoint via the signaling path
     * @throws Exception if the specified <tt>certificate</tt> failed to verify
     * and validate against the fingerprints presented by the remote endpoint
     * via the signaling path
     */
    private void verifyAndValidateCertificate(
            org.bouncycastle.asn1.x509.Certificate certificate)
        throws Exception
    {
        // RFC 4572 "Connection-Oriented Media Transport over the Transport
        // Layer Security (TLS) Protocol in the Session Description Protocol
        // (SDP)" defines that "[a] certificate fingerprint MUST be computed
        // using the same one-way hash function as is used in the certificate's
        // signature algorithm."
        String hashFunction = findHashFunction(certificate);

        // As RFC 5763 "Framework for Establishing a Secure Real-time Transport
        // Protocol (SRTP) Security Context Using Datagram Transport Layer
        // Security (DTLS)" states, "the certificate presented during the DTLS
        // handshake MUST match the fingerprint exchanged via the signaling path
        // in the SDP."
        String remoteFingerprint;

        synchronized (this)
        {
            if (disposed)
            {
                throw new IllegalStateException("disposed");
            }
            Map<String,String> remoteFingerprints = this.remoteFingerprints;

            if (remoteFingerprints == null)
            {
                throw new IOException(
                        "No fingerprints declared over the signaling path!");
            }

            remoteFingerprint = remoteFingerprints.get(hashFunction);

            // Unfortunately, Firefox does not comply with RFC 5763 at the time
            // of this writing. Its certificate uses SHA-1 and it sends a
            // fingerprint computed with SHA-256. We could, of course, wait for
            // Mozilla to make Firefox compliant. However, we would like to
            // support Firefox in the meantime. That is why we will allow the
            // fingerprint to "upgrade" the hash function of the certificate
            // much like SHA-256 is an "upgrade" of SHA-1.
            if (remoteFingerprint == null)
            {
                String hashFunctionUpgrade
                    = findHashFunctionUpgrade(hashFunction, remoteFingerprints);

                if (hashFunctionUpgrade != null
                        && !hashFunctionUpgrade.equalsIgnoreCase(hashFunction))
                {
                    remoteFingerprint
                        = remoteFingerprints.get(hashFunctionUpgrade);
                    if (remoteFingerprint != null)
                        hashFunction = hashFunctionUpgrade;
                }
            }
        }
        if (remoteFingerprint == null)
        {
            throw new IOException(
                    "No fingerprint declared over the signaling path with hash"
                        + " function: " + hashFunction + "!");
        }

        String fingerprint = computeFingerprint(certificate, hashFunction);

        if (remoteFingerprint.equals(fingerprint))
        {
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "Fingerprint " + remoteFingerprint + " matches the "
                            + hashFunction + "-hashed certificate.");
            }
        }
        else
        {
            throw new IOException(
                    "Fingerprint " + remoteFingerprint + " does not match the "
                        + hashFunction + "-hashed certificate " + fingerprint
                        + "!");
        }
    }

    /**
     * Verifies and validates a specific certificate against the fingerprints
     * presented by the remote endpoint via the signaling path.
     *
     * @param certificate the certificate to be verified and validated against
     * the fingerprints presented by the remote endpoint via the signaling path
     * @return <tt>true</tt> if the specified <tt>certificate</tt> was
     * successfully verified and validated against the fingerprints presented by
     * the remote endpoint over the signaling path
     * @throws Exception if the specified <tt>certificate</tt> failed to verify
     * and validate against the fingerprints presented by the remote endpoint
     * over the signaling path
     */
    boolean verifyAndValidateCertificate(
            org.bouncycastle.crypto.tls.Certificate certificate)
        throws Exception
    {
        boolean b = false;

        try
        {
            org.bouncycastle.asn1.x509.Certificate[] certificateList
                = certificate.getCertificateList();

            if (certificateList.length == 0)
            {
                throw new IllegalArgumentException(
                        "certificate.certificateList");
            }
            else
            {
                for (org.bouncycastle.asn1.x509.Certificate x509Certificate
                        : certificateList)
                {
                    verifyAndValidateCertificate(x509Certificate);
                }
                b = true;
            }
        }
        catch (Exception e)
        {
            String message
                = "Failed to verify and/or validate a certificate offered over"
                    + " the media path against fingerprints declared over the"
                    + " signaling path!";
            String throwableMessage = e.getMessage();

            if (VERIFY_AND_VALIDATE_CERTIFICATE)
            {
                if (throwableMessage == null || throwableMessage.length() == 0)
                    logger.error(message, e);
                else
                    logger.error(message + " " + throwableMessage);

                throw e;
            }
            else
            {
                // XXX Contrary to RFC 5763 "Framework for Establishing a Secure
                // Real-time Transport Protocol (SRTP) Security Context Using
                // Datagram Transport Layer Security (DTLS)", we do NOT want to
                // teardown the media session if the fingerprint does not match
                // the hashed certificate. We want to notify the user via the
                // SrtpListener.
                if (throwableMessage == null || throwableMessage.length() == 0)
                    logger.warn(message, e);
                else
                    logger.warn(message + " " + throwableMessage);
            }
        }
        return b;
    }
}
