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

import java.math.*;
import java.security.*;
import java.util.*;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.*;
import org.bouncycastle.cert.*;
import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.generators.*;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.util.*;
import org.bouncycastle.jce.*;
import org.bouncycastle.jce.spec.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.tls.crypto.impl.bc.*;

/**
 * Builds certificates for the DTLS tests.
 */
final class TestCertificates
{
    private TestCertificates()
    {
    }

    /**
     * Generates an RFC 9925 "unsigned" certificate: the id-alg-unsigned
     * signature algorithm and an empty signature, over a fresh P-256 key.
     * Its signature algorithm has no digest, so only the signaled fingerprint
     * can select the hash function to verify it with.
     *
     * @return the certificate, its key pair and its sha-256 fingerprint, in
     * the form {@link DtlsControlImpl} keeps its own certificate
     */
    static CertificateInfo unsigned()
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

        org.bouncycastle.asn1.x509.Certificate x509
            = new X509v3CertificateBuilder(
                    name,
                    BigInteger.valueOf(now),
                    new Date(now - 86_400_000L),
                    new Date(now + 86_400_000L),
                    name,
                    SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
                        keyPair.getPublic()))
                .build(new NoSignatureContentSigner())
                .toASN1Structure();

        return new CertificateInfo(
            keyPair,
            new org.bouncycastle.tls.Certificate(
                new BcTlsCertificate[] {
                    new BcTlsCertificate(BC_TLS_CRYPTO, x509) }),
            "sha-256",
            fingerprint(x509, "SHA-256"),
            now);
    }

    /**
     * Computes the RFC 8122 fingerprint of a certificate.
     *
     * @param x509 the certificate
     * @param algorithm the {@link MessageDigest} algorithm name
     * @return the fingerprint, as upper-case colon-separated hex
     */
    static String fingerprint(
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
}
