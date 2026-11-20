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
import java.math.*;
import java.security.*;
import java.util.*;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.*;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.*;
import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.generators.*;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.util.*;
import org.bouncycastle.jce.*;
import org.bouncycastle.jce.spec.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.tls.crypto.impl.bc.*;
import org.jitsi.service.libjitsi.*;
import org.junit.jupiter.api.*;

/**
 * Tests the RFC 8122 fingerprint verification of {@link DtlsControlImpl}:
 * the hash function comes from the signaled fingerprints, not from the
 * signature algorithm of the certificate.
 */
public class DtlsFingerprintTest
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
    public void localFingerprintUsesSha256()
        throws Exception
    {
        DtlsControlImpl control = new DtlsControlImpl();

        assertEquals("sha-256", control.getLocalFingerprintHashFunction());
        assertEquals(
            fingerprint(localX509(control), "SHA-256"),
            control.getLocalFingerprint());
    }

    @Test
    public void acceptsFingerprintWithHashOtherThanSignature()
        throws Exception
    {
        // The local certificate is signed with SHA256withECDSA by default; a
        // SHA-512 fingerprint must still be accepted.
        DtlsControlImpl control = new DtlsControlImpl();
        org.bouncycastle.asn1.x509.Certificate x509 = localX509(control);

        control.setRemoteFingerprints(
            Collections.singletonMap("sha-512", fingerprint(x509, "SHA-512")));

        assertTrue(control.verifyAndValidateCertificate(
            control.getCertificateInfo().getCertificate()));
    }

    @Test
    public void verifiesTheMostPreferredOfferedHash()
        throws Exception
    {
        // With a correct SHA-256 fingerprint and a wrong SHA-512 one, the
        // SHA-512 one is the one verified, so the certificate is rejected.
        DtlsControlImpl control = new DtlsControlImpl();
        org.bouncycastle.asn1.x509.Certificate x509 = localX509(control);
        Map<String, String> fingerprints = new HashMap<>();

        fingerprints.put("sha-256", fingerprint(x509, "SHA-256"));
        fingerprints.put("sha-512", corrupt(fingerprint(x509, "SHA-512")));
        control.setRemoteFingerprints(fingerprints);

        assertThrows(IOException.class, () ->
            control.verifyAndValidateCertificate(
                control.getCertificateInfo().getCertificate()));
    }

    @Test
    public void rejectsMismatchedFingerprint()
        throws Exception
    {
        DtlsControlImpl control = new DtlsControlImpl();
        org.bouncycastle.asn1.x509.Certificate x509 = localX509(control);

        control.setRemoteFingerprints(
            Collections.singletonMap(
                "sha-256", corrupt(fingerprint(x509, "SHA-256"))));

        assertThrows(IOException.class, () ->
            control.verifyAndValidateCertificate(
                control.getCertificateInfo().getCertificate()));
    }

    @Test
    public void rejectsFingerprintWithUnacceptedHash()
        throws Exception
    {
        // sha-224 is a valid fingerprint hash function but not in the default
        // accepted list.
        DtlsControlImpl control = new DtlsControlImpl();
        org.bouncycastle.asn1.x509.Certificate x509 = localX509(control);

        control.setRemoteFingerprints(
            Collections.singletonMap("sha-224", fingerprint(x509, "SHA-224")));

        assertThrows(IOException.class, () ->
            control.verifyAndValidateCertificate(
                control.getCertificateInfo().getCertificate()));
    }

    @Test
    public void acceptsSha1FingerprintWhenNothingStrongerIsOffered()
        throws Exception
    {
        DtlsControlImpl control = new DtlsControlImpl();
        org.bouncycastle.asn1.x509.Certificate x509 = localX509(control);

        control.setRemoteFingerprints(
            Collections.singletonMap("sha-1", fingerprint(x509, "SHA-1")));

        assertTrue(control.verifyAndValidateCertificate(
            control.getCertificateInfo().getCertificate()));
    }

    @Test
    public void acceptsLowercaseHexAndUppercaseHashName()
        throws Exception
    {
        DtlsControlImpl control = new DtlsControlImpl();
        org.bouncycastle.asn1.x509.Certificate x509 = localX509(control);

        control.setRemoteFingerprints(
            Collections.singletonMap(
                "SHA-256",
                fingerprint(x509, "SHA-256").toLowerCase(Locale.ROOT)));

        assertTrue(control.verifyAndValidateCertificate(
            control.getCertificateInfo().getCertificate()));
    }

    @Test
    public void parsesHashFunctionLists()
    {
        String defaults
            = DtlsControlImpl.DEFAULT_ACCEPTED_FINGERPRINT_HASH_FUNCTIONS;

        assertEquals(
            Arrays.asList("sha-512", "sha-384", "sha-256", "sha-1"),
            DtlsControlImpl.parseHashFunctions(defaults, defaults));
        // Order kept, names lowercased and trimmed, duplicates dropped.
        assertEquals(
            Arrays.asList("sha-256", "sha-512"),
            DtlsControlImpl.parseHashFunctions(
                " SHA-256 ,sha-512, sha-256", defaults));
        // Forbidden and unknown names are dropped; "sha256" is not the
        // RFC 8122 name and would never match a signaled fingerprint.
        assertEquals(
            Collections.singletonList("sha-384"),
            DtlsControlImpl.parseHashFunctions(
                "md5, MD2, sha256, sha3-256, 1.2.840.113549.2.5, sha-384",
                defaults));
        // Nothing usable falls back to the default list, in its order.
        assertEquals(
            Arrays.asList("sha-512", "sha-384", "sha-256", "sha-1"),
            DtlsControlImpl.parseHashFunctions("md5,,foo", defaults));
        assertEquals(
            Collections.singletonList("sha-256"),
            DtlsControlImpl.parseHashFunctions(
                "", DtlsControlImpl.DEFAULT_LOCAL_FINGERPRINT_HASH_FUNCTION));
    }

    @Test
    public void acceptsUnsignedCertificate()
        throws Exception
    {
        // RFC 9925: a certificate with the id-alg-unsigned algorithm and an
        // empty signature. Its signature algorithm has no digest, so only
        // the signaled fingerprint can select the hash function.
        org.bouncycastle.asn1.x509.Certificate x509 = unsignedX509();

        assertEquals(
            X509ObjectIdentifiers.id_alg_unsigned,
            x509.getSignatureAlgorithm().getAlgorithm());

        DtlsControlImpl control = new DtlsControlImpl();

        control.setRemoteFingerprints(
            Collections.singletonMap("sha-256", fingerprint(x509, "SHA-256")));

        assertTrue(control.verifyAndValidateCertificate(
            new org.bouncycastle.tls.Certificate(
                new BcTlsCertificate[] {
                    new BcTlsCertificate(BC_TLS_CRYPTO, x509) })));
    }

    private static org.bouncycastle.asn1.x509.Certificate localX509(
            DtlsControlImpl control)
        throws IOException
    {
        return org.bouncycastle.asn1.x509.Certificate.getInstance(
            control.getCertificateInfo().getCertificate()
                .getCertificateAt(0).getEncoded());
    }

    private static org.bouncycastle.asn1.x509.Certificate unsignedX509()
        throws Exception
    {
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        ECNamedCurveParameterSpec curve
            = ECNamedCurveTable.getParameterSpec("secp256r1");

        generator.init(
            new ECKeyGenerationParameters(
                new ECDomainParameters(
                    curve.getCurve(), curve.getG(), curve.getN(),
                    curve.getH(), curve.getSeed()),
                new SecureRandom()));

        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
        X500Name name = new X500Name("CN=unsigned");
        long now = System.currentTimeMillis();

        return new X509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                new Date(now - 86_400_000L),
                new Date(now + 86_400_000L),
                name,
                SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
                    keyPair.getPublic()))
            .build(new NoSignatureContentSigner())
            .toASN1Structure();
    }

    private static String fingerprint(
            org.bouncycastle.asn1.x509.Certificate x509,
            String algorithm)
        throws Exception
    {
        byte[] digest
            = MessageDigest.getInstance(algorithm)
                .digest(x509.getEncoded(ASN1Encoding.DER));
        StringBuilder sb = new StringBuilder();

        for (byte b : digest)
        {
            if (sb.length() > 0)
                sb.append(':');
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String corrupt(String fingerprint)
    {
        char first = fingerprint.charAt(0);

        return (first == '0' ? '1' : '0') + fingerprint.substring(1);
    }
}
