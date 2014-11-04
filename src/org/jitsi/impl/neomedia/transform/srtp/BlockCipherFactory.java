/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import org.bouncycastle.crypto.*;

/**
 * Defines the application programming interface (API) of a factory of
 * <tt>org.bouncycastle.crypto.BlockCipher</tt> instances.
 *
 * @author Lyubomir Marinov
 */
public interface BlockCipherFactory
{
    /**
     * Initializes a new <tt>BlockCipher</tt> instance.
     *
     * @return a new <tt>BlockCipher</tt> instance
     * @throws Exception if anything goes wrong while initializing a new
     * <tt>BlockCipher</tt> instance
     */
    public BlockCipher createBlockCipher()
        throws Exception;
}
