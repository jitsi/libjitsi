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
package org.jitsi.impl.neomedia.transform.sdes;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.srtp.*;
import org.jitsi.service.neomedia.*;

import ch.imvs.sdes4j.srtp.*;

/**
 * TransformEngine for SDES based SRTP encryption.
 *
 * @author Ingo Bauersachs
 */
public class SDesTransformEngine
    implements SrtpControl.TransformEngine
{
    private SRTPTransformer srtpTransformer;
    private SRTCPTransformer srtcpTransformer;
    private SrtpCryptoAttribute inAttribute;
    private SrtpCryptoAttribute outAttribute;
    private SRTPContextFactory reverseCtx;
    private SRTPContextFactory forwardCtx;

    /**
     * Creates a new instance of this class.
     * @param inAttribute Key material for the incoming stream.
     * @param outAttribute Key material for the outgoing stream.
     */
    public SDesTransformEngine(SrtpCryptoAttribute inAttribute,
            SrtpCryptoAttribute outAttribute)
    {
        update(inAttribute, outAttribute);
    }

    /**
     * Updates this instance with new key materials.
     * @param inAttribute Key material for the incoming stream.
     * @param outAttribute Key material for the outgoing stream.
     */
    public void update(SrtpCryptoAttribute inAttribute,
            SrtpCryptoAttribute outAttribute)
    {
        // Only reset the context if the new keys are really different,
        // otherwise the ROC of an active but paused (on hold) stream will be
        // reset. A new stream (with a fresh context) should be advertised by
        // a new SSRC and thus receive a ROC of zero.
        boolean changed = false;
        if (!inAttribute.equals(this.inAttribute))
        {
            this.inAttribute = inAttribute;
            reverseCtx = getTransformEngine(inAttribute, false /* receiver */);
            changed = true;
        }

        if (!outAttribute.equals(this.outAttribute))
        {
            this.outAttribute = outAttribute;
            forwardCtx = getTransformEngine(outAttribute, true /* sender */);
            changed = true;
        }

        if (changed)
        {
            if (srtpTransformer == null)
            {
                srtpTransformer = new SRTPTransformer(forwardCtx, reverseCtx);
            }
            else
            {
                srtpTransformer.setContextFactory(forwardCtx, true);
                srtpTransformer.setContextFactory(reverseCtx, false);
            }

            if (srtcpTransformer == null)
            {
                srtcpTransformer = new SRTCPTransformer(forwardCtx, reverseCtx);
            }
            else
            {
                srtcpTransformer.updateFactory(forwardCtx, true);
                srtcpTransformer.updateFactory(reverseCtx, false);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void cleanup()
    {
        if (srtpTransformer != null)
            srtpTransformer.close();
        if (srtcpTransformer != null)
            srtcpTransformer.close();

        srtpTransformer = null;
        srtcpTransformer = null;
    }

    private static SRTPContextFactory getTransformEngine(
            SrtpCryptoAttribute attribute,
            boolean sender)
    {
        SrtpSessionParam[] sessionParams = attribute.getSessionParams();

        if ((sessionParams != null) && (sessionParams.length > 0))
        {
            throw new IllegalArgumentException(
                    "session parameters are not supported");
        }

        SrtpCryptoSuite cryptoSuite = attribute.getCryptoSuite();

        return
            new SRTPContextFactory(
                    sender,
                    getKey(attribute),
                    getSalt(attribute),
                    new SRTPPolicy(
                            getEncryptionCipher(cryptoSuite),
                            cryptoSuite.getEncKeyLength() / 8,
                            getHashAlgorithm(cryptoSuite),
                            cryptoSuite.getSrtpAuthKeyLength() / 8,
                            cryptoSuite.getSrtpAuthTagLength() / 8,
                            cryptoSuite.getSaltKeyLength() / 8),
                    new SRTPPolicy(
                            getEncryptionCipher(cryptoSuite),
                            cryptoSuite.getEncKeyLength() / 8,
                            getHashAlgorithm(cryptoSuite),
                            cryptoSuite.getSrtcpAuthKeyLength() / 8,
                            cryptoSuite.getSrtcpAuthTagLength() / 8,
                            cryptoSuite.getSaltKeyLength() / 8));
    }

    private static byte[] getKey(SrtpCryptoAttribute attribute)
    {
        int length = attribute.getCryptoSuite().getEncKeyLength() / 8;
        byte[] key = new byte[length];
        System.arraycopy(attribute.getKeyParams()[0].getKey(), 0, key, 0,
            length);
        return key;
    }

    private static byte[] getSalt(SrtpCryptoAttribute attribute)
    {
        int keyLength = attribute.getCryptoSuite().getEncKeyLength() / 8;
        int saltLength = attribute.getCryptoSuite().getSaltKeyLength() / 8;
        byte[] salt = new byte[keyLength];
        System.arraycopy(attribute.getKeyParams()[0].getKey(), keyLength, salt,
            0, saltLength);
        return salt;
    }

    private static int getEncryptionCipher(SrtpCryptoSuite cs)
    {
        switch (cs.getEncryptionAlgorithm())
        {
            case SrtpCryptoSuite.ENCRYPTION_AES128_CM:
            case SrtpCryptoSuite.ENCRYPTION_AES192_CM:
            case SrtpCryptoSuite.ENCRYPTION_AES256_CM:
                return SRTPPolicy.AESCM_ENCRYPTION;
            case SrtpCryptoSuite.ENCRYPTION_AES128_F8:
                return SRTPPolicy.AESF8_ENCRYPTION;
            default:
                throw new IllegalArgumentException("Unsupported cipher");
        }
    }

    private static int getHashAlgorithm(SrtpCryptoSuite cs)
    {
        switch (cs.getHashAlgorithm())
        {
            case SrtpCryptoSuite.HASH_HMAC_SHA1:
                return SRTPPolicy.HMACSHA1_AUTHENTICATION;
            default:
                throw new IllegalArgumentException("Unsupported hash");
        }
    }

    public PacketTransformer getRTPTransformer()
    {
        return srtpTransformer;
    }

    public PacketTransformer getRTCPTransformer()
    {
        return srtcpTransformer;
    }
}
