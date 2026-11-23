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
import java.math.*;
import java.security.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.*;
import org.bouncycastle.asn1.x500.style.*;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.*;
import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.generators.*;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.jce.*;
import org.bouncycastle.jce.spec.*;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.*;
import org.bouncycastle.tls.crypto.impl.bc.*;
import org.bouncycastle.crypto.util.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.operator.bc.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.*;

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
     * property is not set, a default value of "SHA256withECDSA" will be
     * used.
     */
    public static final String PROP_SIGNATURE_ALGORITHM =
        "org.jitsi.impl.neomedia.transform.dtls.SIGNATURE_ALGORITHM";

    /**
     * The name of the property to specify RSA Key length.
     */
    public static final String RSA_KEY_SIZE_PNAME =
        "org.jitsi.impl.neomedia.transform.dtls.RSA_KEY_SIZE";

    /**
     * The default RSA key size when configuration properties are not found.
     */
    public static final int DEFAULT_RSA_KEY_SIZE = 2048;

    /**
     * The RSA key size to use.
     * The default value is {@code DEFAULT_RSA_KEY_SIZE} but may be overridden
     * by the {@code ConfigurationService} and/or {@code System} property
     * {@code RSA_KEY_SIZE_PNAME}.
     */
    public static final int RSA_KEY_SIZE;

    /**
     * The name of the property to specify RSA key size certainty.
     * https://docs.oracle.com/javase/7/docs/api/java/math/BigInteger.html
     */
    public static final String RSA_KEY_SIZE_CERTAINTY_PNAME =
        "org.jitsi.impl.neomedia.transform.dtls.RSA_KEY_SIZE_CERTAINTY";

    /**
     * The RSA key size certainty to use.
     * The default value is {@code DEFAULT_RSA_KEY_SIZE_CERTAINTY} but may be
     * overridden by the {@code ConfigurationService} and/or {@code System}
     * property {@code RSA_KEY_SIZE_CERTAINTY_PNAME}.
     * For more on certainty, look at the three parameter constructor here:
     * https://docs.oracle.com/javase/7/docs/api/java/math/BigInteger.html
     */
    public static final int RSA_KEY_SIZE_CERTAINTY;

    /**
     * The default RSA key size certainty when config properties are not found.
     */
    public static final int DEFAULT_RSA_KEY_SIZE_CERTAINTY = 80;

    /**
     * The name of the property to specify DTLS certificate cache expiration.
     */
    public static final String CERT_CACHE_EXPIRE_TIME_PNAME =
        "org.jitsi.impl.neomedia.transform.dtls.CERT_CACHE_EXPIRE_TIME";


    /**
     * The certificate cache expiration time to use, in milliseconds.
     * The default value is {@code DEFAULT_CERT_CACHE_EXPIRE_TIME} but may be
     * overridden by the {@code ConfigurationService} and/or {@code System}
     * property {@code CERT_CACHE_EXPIRE_TIME_PNAME}.
     */
    public static final long CERT_CACHE_EXPIRE_TIME;

    /**
     * The default certificate cache expiration time, when config properties
     * are not found.
     */
    public static final long DEFAULT_CERT_CACHE_EXPIRE_TIME = ONE_DAY;

    /**
     * The public exponent to always use for RSA key generation.
     */
    public static final BigInteger RSA_KEY_PUBLIC_EXPONENT
        = new BigInteger("10001", 16);

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
     * The name of the {@code ConfigurationService} and/or {@code System}
     * property which specifies the hash function used to compute the
     * fingerprint of the local certificate, as it is signaled to the remote
     * endpoint (RFC 8122). The default value is
     * {@link #DEFAULT_LOCAL_FINGERPRINT_HASH_FUNCTION}.
     */
    public static final String LOCAL_FINGERPRINT_HASH_FUNCTION_PNAME
        = DtlsControlImpl.class.getName() + ".localFingerprintHashFunction";

    /**
     * The default hash function used to compute the fingerprint of the local
     * certificate.
     */
    public static final String DEFAULT_LOCAL_FINGERPRINT_HASH_FUNCTION
        = "sha-256";

    /**
     * The hash function used to compute the fingerprint of the local
     * certificate, written in lower case.
     */
    private static final String LOCAL_FINGERPRINT_HASH_FUNCTION;

    /**
     * The name of the {@code ConfigurationService} and/or {@code System}
     * property which specifies the hash functions accepted for the
     * fingerprints of remote certificates, as a comma-separated list in
     * decreasing order of preference (RFC 8122). The default value is
     * {@link #DEFAULT_ACCEPTED_FINGERPRINT_HASH_FUNCTIONS}; it keeps sha-1,
     * at the lowest preference, for SIP peers which signal nothing stronger.
     */
    public static final String ACCEPTED_FINGERPRINT_HASH_FUNCTIONS_PNAME
        = DtlsControlImpl.class.getName()
            + ".acceptedFingerprintHashFunctions";

    /**
     * The default hash functions accepted for the fingerprints of remote
     * certificates, in decreasing order of preference.
     */
    public static final String DEFAULT_ACCEPTED_FINGERPRINT_HASH_FUNCTIONS
        = "sha-512,sha-384,sha-256,sha-1";

    /**
     * The hash function names of the "Hash Function Textual Names" IANA
     * registry which RFC 8122 allows for fingerprints. MD2 and MD5 are in the
     * registry but RFC 8122 section 5 forbids them.
     */
    private static final List<String> KNOWN_FINGERPRINT_HASH_FUNCTIONS
        = Collections.unmodifiableList(
                Arrays.asList(
                        "sha-1", "sha-224", "sha-256", "sha-384", "sha-512"));

    /**
     * The hash functions accepted for the fingerprints of remote
     * certificates, written in lower case, in decreasing order of preference.
     */
    private static final List<String> ACCEPTED_FINGERPRINT_HASH_FUNCTIONS;

    /**
     * The cache of {@link #certificateInfo} so that we do not invoke CPU
     * intensive methods for each new {@code DtlsControlImpl} instance.
     */
    private static CertificateInfo certificateInfoCache;

    static
    {
        // Set configurable options using ConfigurationService.

        VERIFY_AND_VALIDATE_CERTIFICATE
            = ConfigUtils.getBoolean(
                    LibJitsi.getConfigurationService(),
                    VERIFY_AND_VALIDATE_CERTIFICATE_PNAME,
                    true);

        RSA_KEY_SIZE
            = ConfigUtils.getInt(
                    LibJitsi.getConfigurationService(),
                    RSA_KEY_SIZE_PNAME,
                    DEFAULT_RSA_KEY_SIZE);

        RSA_KEY_SIZE_CERTAINTY
            = ConfigUtils.getInt(
                LibJitsi.getConfigurationService(),
                    RSA_KEY_SIZE_CERTAINTY_PNAME,
                    DEFAULT_RSA_KEY_SIZE_CERTAINTY);

        CERT_CACHE_EXPIRE_TIME
            = ConfigUtils.getLong(
                LibJitsi.getConfigurationService(),
                    CERT_CACHE_EXPIRE_TIME_PNAME,
                    DEFAULT_CERT_CACHE_EXPIRE_TIME);

        // A list without a usable name throws, so that a misconfiguration
        // (a typo in every name, say) refuses to start rather than silently
        // widening or narrowing what is accepted.
        LOCAL_FINGERPRINT_HASH_FUNCTION
            = parseHashFunctions(
                    ConfigUtils.getString(
                            LibJitsi.getConfigurationService(),
                            LOCAL_FINGERPRINT_HASH_FUNCTION_PNAME,
                            DEFAULT_LOCAL_FINGERPRINT_HASH_FUNCTION))
                .get(0);

        ACCEPTED_FINGERPRINT_HASH_FUNCTIONS
            = parseHashFunctions(
                    ConfigUtils.getString(
                            LibJitsi.getConfigurationService(),
                            ACCEPTED_FINGERPRINT_HASH_FUNCTIONS_PNAME,
                            DEFAULT_ACCEPTED_FINGERPRINT_HASH_FUNCTIONS));
    }

    /**
     * Parses a comma-separated list of hash function names, dropping the
     * names which are not in the "Hash Function Textual Names" IANA registry
     * (the names RFC 8122 allows in a fingerprint attribute) and the
     * registered names which RFC 8122 forbids (MD2 and MD5).
     *
     * @param list the comma-separated list of hash function names to parse
     * @return the usable hash function names of {@code list} written in lower
     * case and in the order in which they appear in {@code list}
     * @throws IllegalArgumentException if {@code list} contains no usable
     * hash function name
     */
    static List<String> parseHashFunctions(String list)
    {
        List<String> hashFunctions = new ArrayList<>();

        for (String hashFunction : list.split(","))
        {
            hashFunction = hashFunction.trim().toLowerCase(Locale.ROOT);
            if (hashFunction.isEmpty())
                continue;

            if (hashFunction.equals("md2") || hashFunction.equals("md5"))
            {
                logger.warn(
                        "Ignoring forbidden fingerprint hash function "
                            + hashFunction);
            }
            else if (!KNOWN_FINGERPRINT_HASH_FUNCTIONS.contains(hashFunction))
            {
                logger.warn(
                        "Ignoring unknown fingerprint hash function "
                            + hashFunction + ", not one of "
                            + KNOWN_FINGERPRINT_HASH_FUNCTIONS);
            }
            else if (!hashFunctions.contains(hashFunction))
            {
                hashFunctions.add(hashFunction);
            }
        }

        if (hashFunctions.isEmpty())
        {
            String message
                = "No usable fingerprint hash function in '" + list
                    + "', expected a list of "
                    + KNOWN_FINGERPRINT_HASH_FUNCTIONS;

            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        return Collections.unmodifiableList(hashFunctions);
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
        if (theirs != null)
        {
            int[] ours = SRTP_PROTECTION_PROFILES;

            for (int their : theirs)
            {
                for (int our : ours)
                {
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
    private static String computeFingerprint(
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
     * Generates a new certificate from a new key pair and computes its
     * fingerprint with the configured hash function.
     *
     * @return CertificateInfo a new certificate generated from a new key pair,
     * the hash function of its fingerprint, and the fingerprint
     */
    private static CertificateInfo generateCertificateInfo()
    {
        AsymmetricCipherKeyPair keyPair = generateKeyPair();

        org.bouncycastle.asn1.x509.Certificate x509Certificate
            = generateX509Certificate(generateCN(), keyPair);

        org.bouncycastle.tls.Certificate certificate
            = new org.bouncycastle.tls.Certificate(new BcTlsCertificate[]{
                new BcTlsCertificate(BC_TLS_CRYPTO, x509Certificate) } );
        String localFingerprintHashFunction
            = LOCAL_FINGERPRINT_HASH_FUNCTION;
        String localFingerprint
            = computeFingerprint(
                    x509Certificate,
                    localFingerprintHashFunction);

        long timestamp = System.currentTimeMillis();

        return
            new CertificateInfo(
                    keyPair,
                    certificate,
                    localFingerprintHashFunction,
                    localFingerprint,
                    timestamp);
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
        // TODO: Get the versions from a VersionService
        String applicationName = "libjitsi";
        StringBuilder cn = new StringBuilder();

        if (StringUtils.isNotEmpty(applicationName))
            cn.append(applicationName);
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
        String signatureAlgorithm
            = ConfigUtils.getString(
            LibJitsi.getConfigurationService(),
            PROP_SIGNATURE_ALGORITHM,
            "SHA256withECDSA");
        if (signatureAlgorithm.toUpperCase(Locale.ROOT).endsWith("RSA"))
        {
            RSAKeyPairGenerator generator = new RSAKeyPairGenerator();

            generator.init(
                new RSAKeyGenerationParameters(
                    RSA_KEY_PUBLIC_EXPONENT,
                    new SecureRandom(),
                    RSA_KEY_SIZE,
                    RSA_KEY_SIZE_CERTAINTY));
            return generator.generateKeyPair();
        }
        else if (signatureAlgorithm.toUpperCase(Locale.ROOT).endsWith("ECDSA"))
        {
            ECKeyPairGenerator generator = new ECKeyPairGenerator();
            ECNamedCurveParameterSpec curve =
                ECNamedCurveTable.getParameterSpec("secp256r1");
            ECDomainParameters domainParams =
                new ECDomainParameters(curve.getCurve(), curve.getG(),
                    curve.getN(), curve.getH(), curve.getSeed());
            generator.init(new ECKeyGenerationParameters(domainParams,
                new SecureRandom()));
            return generator.generateKeyPair();
        }
        
        throw new IllegalArgumentException("Unknown signature algorithm: "
            + signatureAlgorithm);
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
        // The signature algorithm of the generated certificate defaults to
        // SHA256withECDSA. However, allow the overriding of the default via
        // the ConfigurationService.
        String signatureAlgorithm
            = ConfigUtils.getString(
                    LibJitsi.getConfigurationService(),
                    PROP_SIGNATURE_ALGORITHM,
                    "SHA256withECDSA");

        if (logger.isDebugEnabled())
            logger.debug("Signature algorithm: " + signatureAlgorithm);

        try
        {
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now - ONE_DAY);
            Date notAfter = new Date(now + ONE_DAY * 6 + CERT_CACHE_EXPIRE_TIME);
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
            ContentSigner signer;
            if (keyPair.getPrivate() instanceof RSAKeyParameters)
            {
                signer = new BcRSAContentSignerBuilder(sigAlgId, digAlgId)
                    .build(keyPair.getPrivate());
            }
            else
            {
                signer = new BcECContentSignerBuilder(sigAlgId, digAlgId)
                    .build(keyPair.getPrivate());
            }

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
     * Gets the <tt>String</tt> representation of a fingerprint specified in the
     * form of an array of <tt>byte</tt>s in accord with RFC 8122.
     *
     * @param fingerprint an array of <tt>bytes</tt> which represents a
     * fingerprint the <tt>String</tt> representation in accord with RFC 8122
     * of which is to be returned
     * @return the <tt>String</tt> representation in accord with RFC 8122 of the
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
     * The certificate, hash function, fingerprint, etc. with which the local
     * endpoint represented by this instance authenticates its ends of DTLS
     * sessions.
     */
    private final CertificateInfo certificateInfo;

    /**
     * The indicator which determines whether this instance has been disposed
     * i.e. prepared for garbage collection by {@link #doCleanup()}.
     */
    private boolean disposed = false;

    /**
     * The fingerprints presented by the remote endpoint via the signaling path.
     */
    private Map<String,String> remoteFingerprints;

    /**
     * The properties of {@code DtlsControlImpl} and their values which this
     * instance shares with {@link DtlsTransformEngine} and
     * {@link DtlsPacketTransformer}.
     */
    private final Properties properties;

    /**
     * Initializes a new <tt>DtlsControlImpl</tt> instance.
     */
    public DtlsControlImpl()
    {
        // By default we work in DTLS/SRTP mode.
        this(/* srtpDisabled */ false);
    }

    /**
     * Initializes a new <tt>DtlsControlImpl</tt> instance.
     *
     * @param srtpDisabled <tt>true</tt> if pure DTLS mode without SRTP
     * extensions is to be used; otherwise, <tt>false</tt>
     */
    public DtlsControlImpl(boolean srtpDisabled)
    {
        super(SrtpControlType.DTLS_SRTP);

        CertificateInfo certificateInfo;

        // The methods generateKeyPair(), generateX509Certificate() and/or
        // computeFingerprint() may be too CPU intensive to invoke for each new
        // DtlsControlImpl instance. That's why we've decided to reuse their
        // return values within a certain time frame. Attempt to retrieve from
        // the cache.
        synchronized (DtlsControlImpl.class)
        {
            certificateInfo = certificateInfoCache;
            if (certificateInfo == null
                    || certificateInfo.timestamp + CERT_CACHE_EXPIRE_TIME
                        < System.currentTimeMillis())
            {
                // The cache doesn't exist yet or has outlived its lifetime.
                // Rebuild the cache.
                certificateInfoCache
                    = certificateInfo
                        = generateCertificateInfo();
            }
        }
        this.certificateInfo = certificateInfo;

        properties = new Properties(srtpDisabled);
    }

    /**
     * Initializes a new <tt>DtlsTransformEngine</tt> instance to be associated
     * with and used by this <tt>DtlsControlImpl</tt> instance. The method is
     * implemented as a factory.
     *
     * @return a new <tt>DtlsTransformEngine</tt> instance to be associated with
     * and used by this <tt>DtlsControlImpl</tt> instance
     */
    @Override
    protected DtlsTransformEngine createTransformEngine()
    {
        return new DtlsTransformEngine(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doCleanup()
    {
        super.doCleanup();

        setConnector(null);

        synchronized (this)
        {
            disposed = true;
            notifyAll();
        }
    }

    /**
     * Gets the certificate, hash function, fingerprint, etc. with which the
     * local endpoint represented by this instance authenticates its ends of
     * DTLS sessions.
     *
     * @return the certificate, hash function, fingerprint, etc. with which the
     * local endpoint represented by this instance authenticates its ends of
     * DTLS sessions
     */
    CertificateInfo getCertificateInfo()
    {
        return certificateInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprint()
    {
        return getCertificateInfo().localFingerprint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprintHashFunction()
    {
        return getCertificateInfo().localFingerprintHashFunction;
    }

    /**
     * Gets the properties of {@code DtlsControlImpl} and their values which
     * this instance shares with {@link DtlsTransformEngine} and
     * {@link DtlsPacketTransformer}.
     *
     * @return the properties of {@code DtlsControlImpl} and their values which
     * this instance shares with {@code DtlsTransformEngine} and
     * {@code DtlsPacketTransformer}
     */
    Properties getProperties()
    {
        return properties;
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
     * Gets the value of the {@code setup} SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance acts as a DTLS client
     * or a DTLS server.
     *
     * @return the value of the {@code setup} SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance acts as a DTLS client
     * or a DTLS server
     */
    public DtlsControl.Setup getSetup()
    {
        return getProperties().getSetup();
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
        properties.put(Properties.CONNECTOR_PNAME, connector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRemoteFingerprints(Map<String,String> remoteFingerprints)
    {
        if (remoteFingerprints == null)
            throw new NullPointerException("remoteFingerprints");

        // Don't pass an empty list to the stack in order to avoid wiping
        // certificates that were contained in a previous request.
        if (remoteFingerprints.isEmpty())
        {
            return;
        }

        // Make sure that the hash functions (which are keys of the field
        // remoteFingerprints) are written in lower case.
        Map<String,String> rfs = new HashMap<>(remoteFingerprints.size());

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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRtcpmux(boolean rtcpmux)
    {
        properties.put(Properties.RTCPMUX_PNAME, rtcpmux);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSetup(Setup setup)
    {
        properties.put(Properties.SETUP_PNAME, setup);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(MediaType mediaType)
    {
        properties.put(Properties.MEDIA_TYPE_PNAME, mediaType);
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
        // RFC 8122 "Connection-Oriented Media Transport over the Transport
        // Layer Security (TLS) Protocol in the Session Description Protocol
        // (SDP)" section 5.1: "An endpoint MUST select the set of
        // fingerprints that use its most preferred hash function (out of
        // those offered by the peer) and verify that each certificate used
        // matches one fingerprint out of that set." The hash function is
        // chosen from the signaled fingerprints only; unlike RFC 4572, which
        // RFC 8122 obsoletes, it is no longer tied to the signature algorithm
        // of the certificate. The certificate may even be unsigned (RFC 9925).
        // The DtlsControl interface carries one fingerprint per hash
        // function, so the "set" has at most one member here.
        Map<String,String> remoteFingerprints;

        synchronized (this)
        {
            if (disposed)
            {
                throw new IllegalStateException("disposed");
            }

            remoteFingerprints = this.remoteFingerprints;
        }
        if (remoteFingerprints == null)
        {
            throw new IOException(
                    "No fingerprints declared over the signaling path!");
        }

        for (String hashFunction : ACCEPTED_FINGERPRINT_HASH_FUNCTIONS)
        {
            String remoteFingerprint = remoteFingerprints.get(hashFunction);

            if (remoteFingerprint == null)
                continue;

            String fingerprint = computeFingerprint(certificate, hashFunction);

            if (remoteFingerprint.equalsIgnoreCase(fingerprint))
            {
                if (logger.isTraceEnabled())
                {
                    logger.trace(
                            "Fingerprint " + remoteFingerprint + " matches the "
                                + hashFunction + "-hashed certificate.");
                }
                return;
            }
            throw new IOException(
                    "Fingerprint " + remoteFingerprint + " does not match the "
                        + hashFunction + "-hashed certificate " + fingerprint
                        + "!");
        }
        throw new IOException(
                "No fingerprint declared over the signaling path with any of"
                    + " the accepted hash functions "
                    + ACCEPTED_FINGERPRINT_HASH_FUNCTIONS + ", only with "
                    + remoteFingerprints.keySet() + "!");
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
            org.bouncycastle.tls.Certificate certificate)
        throws Exception
    {
        boolean b = false;

        try
        {
            TlsCertificate[] certificateList
                = certificate.getCertificateList();

            if (certificateList.length == 0)
            {
                throw new IllegalArgumentException(
                        "certificate.certificateList");
            }
            else
            {
                for (org.bouncycastle.tls.crypto.TlsCertificate tlsCertificate
                        : certificateList)
                {
                    verifyAndValidateCertificate(org.bouncycastle.asn1.x509.Certificate
                        .getInstance(tlsCertificate.getEncoded()));
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
