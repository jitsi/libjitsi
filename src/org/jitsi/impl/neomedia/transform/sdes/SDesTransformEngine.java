/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
        SRTPContextFactory forwardCtx
            = getTransformEngine(outAttribute, true /* sender */);
        SRTPContextFactory reverseCtx
            = getTransformEngine(inAttribute, false /* receiver */);
        srtpTransformer = new SRTPTransformer(forwardCtx, reverseCtx);
        srtcpTransformer = new SRTCPTransformer(forwardCtx, reverseCtx);
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
