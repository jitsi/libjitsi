/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import java.security.*;

import javax.crypto.*;

import org.bouncycastle.crypto.*;

/**
 * Implements a <tt>BlockCipherFactory</tt> which initializes
 * <tt>BlockCipher</tt>s that are implemented by a
 * <tt>java.security.Provider</tt>.
 *
 * @author Lyubomir Marinov
 */
public class SecurityProviderBlockCipherFactory
    implements BlockCipherFactory
{
    /**
     * The <tt>java.security.Provider</tt> which provides the implementations of
     * the <tt>BlockCipher</tt>s to be initialized by this instance.
     */
    private final Provider provider;

    /**
     * The name of the transformation.
     */
    private final String transformation;

    /**
     * Initializes a new <tt>SecurityProvider</tt> instance which is to
     * initialize <tt>BlockCipher</tt>s that are implemented by a specific
     * <tt>java.security.Provider</tt>.
     *
     * @param transformation the name of the transformation
     * @param provider the <tt>java.security.Provider</tt> which provides the
     * implementations of the <tt>BlockCipher</tt>s to be initialized by the new
     * instance
     */
    public SecurityProviderBlockCipherFactory(
            String transformation,
            Provider provider)
    {
        if (transformation == null)
            throw new NullPointerException("transformation");
        if (transformation.length() == 0)
            throw new IllegalArgumentException("transformation");
        if (provider == null)
            throw new NullPointerException("provider");

        this.transformation = transformation;
        this.provider = provider;
    }

    /**
     * Initializes a new <tt>SecurityProvider</tt> instance which is to
     * initialize <tt>BlockCipher</tt>s that are implemented by a specific
     * <tt>java.security.Provider</tt>.
     *
     * @param transformation the name of the transformation
     * @param provider the name of the <tt>java.security.Provider</tt> which
     * provides the implementations of the <tt>BlockCipher</tt>s to be
     * initialized by the new instance
     */
    public SecurityProviderBlockCipherFactory(
            String transformation,
            String providerName)
    {
        this(transformation, Security.getProvider(providerName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockCipher createBlockCipher()
        throws Exception
    {
        return
            new BlockCipherAdapter(
                    Cipher.getInstance(transformation, provider));
    }
}
