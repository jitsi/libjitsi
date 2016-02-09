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
package org.jitsi.impl.neomedia.transform.srtp;

import java.nio.*;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.params.*;
import org.jitsi.util.*;

/**
 * Implements the interface <tt>org.bouncycastle.crypto.BlockCipher</tt> using
 * the OpenSSL Crypto library.
 *
 * @author Lyubomir Marinov
 */
public class OpenSSLBlockCipher
    implements NIOBlockCipher
{
    public static final int AES_128_CTR = 1;

    public static final int AES_128_ECB = 2;

    /**
     * The indicator which determines whether
     * <tt>System.loadLibrary(String)</tt> is to be invoked in order to load the
     * OpenSSL (Crypto) library.
     */
    private static boolean loadLibrary = true;

    private static native int EVP_CIPHER_block_size(long e);

    private static native long EVP_CIPHER_CTX_create();

    private static native void EVP_CIPHER_CTX_destroy(long ctx);

    private static native boolean EVP_CIPHER_CTX_set_padding(
            long x,
            boolean padding);

    private static native int EVP_CipherFinal_ex(
            long ctx,
            byte[] out, int outOff, int outl);

    private static native boolean EVP_CipherInit_ex(
            long ctx,
            long type,
            long impl,
            byte[] key,
            byte[] iv,
            int enc);

    private static native int EVP_CipherUpdate(
            long ctx,
            byte[] out, int outOff, int outl,
            byte[] in, int inOff, int inl);

    private static native int EVP_CipherUpdate(
            long ctx,
            ByteBuffer out, int outOff, int outl,
            ByteBuffer in, int inOff, int inl);

    private static native long EVP_get_cipherbyname(String name);

    /**
     * The name of the algorithm implemented by this instance.
     */
    private final String algorithmName;

    /**
     * The block size in bytes of the cipher implemented by this instance.
     */
    private final int blockSize;

    /**
     * The cipher context of the OpenSSL (Crypto) library through which the
     * actual algorithm implementation is invoked by this instance.
     */
    private long ctx;

    /**
     * Indicate if init() has been called
     */
    private boolean initDone = false;

    /**
     * The OpenSSL Crypto type of the cipher implemented by this instance.
     */
    private final long type;

    /**
     * Initializes a new <tt>OpenSSLBlockCipher</tt> instance with a specific
     * algorithm.
     *
     * @param algorithm the algorithm with which to initialize the new instance
     * @see #AES_128_CTR
     * @see #AES_128_ECB
     */
    public OpenSSLBlockCipher(int algorithm)
    {
        // Make sure the provided arguments are legal.
        switch (algorithm)
        {
        case AES_128_CTR:
            algorithmName = "AES-128-CTR";
            // CTR mode real block size is 1, but here blockSize specify
            // how much data we process each time
            blockSize = 16;
            break;
        case AES_128_ECB:
            algorithmName = "AES-128-ECB";
            blockSize = 16;
            break;
        default:
            throw new IllegalArgumentException("algorithm " + algorithm);
        }

        // Load the OpenSSL (Crypto) library if necessary.
        synchronized (OpenSSLBlockCipher.class)
        {
            if (loadLibrary)
            {
                try
                {
                    JNIUtils.loadLibrary(
                            "jnopenssl",
                            OpenSSLBlockCipher.class.getClassLoader());
                }
                finally
                {
                    loadLibrary = false;
                }
            }
        }

        type = EVP_get_cipherbyname(algorithmName);
        if (type == 0)
            throw new RuntimeException("EVP_get_cipherbyname("+algorithmName+")");

        ctx = EVP_CIPHER_CTX_create();
        if (ctx == 0)
            throw new RuntimeException("EVP_CIPHER_CTX_create");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalize()
        throws Throwable
    {
        try
        {
            // Well, the destroying in the finalizer should exist as a backup
            // anyway. There is no way to explicitly invoke the destroying at
            // the time of this writing but it is a start.
            if (ctx != 0)
            {
                EVP_CIPHER_CTX_destroy(ctx);
                ctx = 0;
            }
        }
        finally
        {
            super.finalize();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlgorithmName()
    {
        return algorithmName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getBlockSize()
    {
        return blockSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(boolean forEncryption, CipherParameters params)
        throws IllegalArgumentException
    {
        byte[] key
            = (params instanceof KeyParameter)
                ? ((KeyParameter) params).getKey()
                : null;

        if (key == null)
            throw new IllegalStateException("key == null");

        if (!EVP_CipherInit_ex(
                ctx,
                type,
                0L /* impl */,
                key,
                null /* iv */,
                (forEncryption ? 1 : 0))) {
            throw new RuntimeException("EVP_CipherInit_ex() init failed");
        }
        EVP_CIPHER_CTX_set_padding(ctx, false);

        initDone = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int processBlock(byte[] in, int inOff, byte[] out, int outOff)
        throws DataLengthException, IllegalStateException
    {
        if (!initDone)
            throw new IllegalStateException("init not done");

        int i = EVP_CipherUpdate(
                    ctx,
                    out, outOff, blockSize,
                    in, inOff, blockSize);

        if (i < 0)
            throw new RuntimeException("EVP_CipherUpdate");
        else
            return i;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int processBlock(
            ByteBuffer in, int inOff,
            ByteBuffer out, int outOff)
        throws DataLengthException, IllegalStateException
    {
        if (!initDone)
            throw new IllegalStateException("init not done");

        int i = EVP_CipherUpdate(
                    ctx,
                    out, outOff, blockSize,
                    in, inOff, blockSize);

        if (i < 0)
            throw new RuntimeException("EVP_CipherUpdate");
        else
            return i;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset()
    {
        if (!initDone)
            throw new IllegalStateException("init not done");

        if (!EVP_CipherInit_ex(ctx, 0L, 0L, null, null, -1))
            throw new RuntimeException("EVP_CipherInit_ex() reset failed");
    }
}
