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

import java.security.*;

import org.bouncycastle.asn1.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.*;
import org.bouncycastle.tls.crypto.impl.bc.*;
import org.jitsi.utils.logging.*;

/**
 * Utilities and constants for use with the BouncyCastle API.
 */
public class DtlsUtils
{
    private static final Logger logger = Logger.getLogger(DtlsUtils.class);

    /**
     * A crypto instance initialized with the default {@link SecureRandom}.
     */
    public static final BcTlsCrypto BC_TLS_CRYPTO =
        new BcTlsCrypto(new SecureRandom());

    /**
     * Creates the credentials with which a DTLS client or server signs its
     * part of the handshake using a specific certificate.
     *
     * @param context the context of the DTLS client or server
     * @param certificateInfo the certificate and its keys
     * @return the credentials, or {@code null} if the signature algorithm of
     * the certificate is not supported
     */
    static TlsCredentialedSigner createCredentialedSigner(
            TlsContext context,
            CertificateInfo certificateInfo)
    {
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

        return new BcDefaultTlsCredentialedSigner(
                new TlsCryptoParameters(context),
                (BcTlsCrypto) context.getCrypto(),
                certificateInfo.getKeyPair().getPrivate(),
                certificateInfo.getCertificate(),
                sigAndHashAlg);
    }
}
