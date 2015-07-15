/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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

    private static long EVP_aes_128_ctr;

    private static long EVP_aes_128_ecb;

    /**
     * The indicator which determines whether
     * <tt>System.loadLibrary(String)</tt> is to be invoked in order to load the
     * OpenSSL (Crypto) library.
     */
    private static boolean loadLibrary = true;

    private static native long EVP_aes_128_ecb();

    private static native int EVP_CIPHER_block_size(long e);

    private static native boolean EVP_CIPHER_CTX_cleanup(long a);

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
     * The value of the <tt>forEncryption</tt> argument in the last invocation
     * of {@link #init(boolean, CipherParameters)}. If <tt>null</tt>, then the
     * method <tt>init</tt> has not been invoked yet.
     */
    private Boolean forEncryption;

    /**
     * The key provided in the form of a {@link KeyParameter} in the last
     * invocation of {@link #init(boolean, CipherParameters)}.
     */
    private byte[] key;

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
        case AES_128_ECB:
            this.algorithmName = "AES";
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
                    EVP_aes_128_ctr = EVP_get_cipherbyname("AES-128-CTR");
                    EVP_aes_128_ecb = EVP_aes_128_ecb();
                }
                finally
                {
                    loadLibrary = false;
                }
            }
        }

        long type;

        switch (algorithm)
        {
        case AES_128_CTR:
            long EVP_aes_128_ctr = OpenSSLBlockCipher.EVP_aes_128_ctr;

            if (EVP_aes_128_ctr == 0)
                throw new IllegalStateException("EVP_aes_128_ctr");
            else
                type = EVP_aes_128_ctr;
            break;
        case AES_128_ECB:
            long EVP_aes_128_ecb = OpenSSLBlockCipher.EVP_aes_128_ecb;

            if (EVP_aes_128_ecb == 0)
                throw new IllegalStateException("EVP_aes_128_ecb");
            else
                type = EVP_aes_128_ecb;
            break;
        default:
            // It must have been checked prior to loading the OpenSSL (Crypto)
            // library but the compiler needs it to be convinced that we are not
            // attempting to use an uninitialized variable.
            throw new IllegalArgumentException("algorithm " + algorithm);
        }
        this.type = type;

        long ctx = EVP_CIPHER_CTX_create();

        if (ctx == 0)
        {
            throw new RuntimeException("EVP_CIPHER_CTX_create");
        }
        else
        {
            boolean ok = false;

            this.ctx = ctx;
            try
            {
                reset();

                int blockSize = EVP_CIPHER_block_size(type);

                // The AES we need has a block size of 16 and OpenSSL (Crypto)
                // claims it has a block size of 1.
                if (algorithm == AES_128_CTR && blockSize == 1)
                    blockSize = 16;
                this.blockSize = blockSize;

                ok = true;
            }
            finally
            {
                if (!ok)
                {
                    if (this.ctx == ctx)
                        this.ctx = 0;
                    EVP_CIPHER_CTX_destroy(ctx);
                }
            }
        }
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
            long ctx = this.ctx;

            if (ctx != 0)
            {
                this.ctx = 0;
                EVP_CIPHER_CTX_destroy(ctx);
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

        this.forEncryption = Boolean.valueOf(forEncryption);
        this.key = key;

        reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int processBlock(byte[] in, int inOff, byte[] out, int outOff)
        throws DataLengthException, IllegalStateException
    {
        long ctx = this.ctx;

        if (ctx == 0)
        {
            throw new IllegalStateException("ctx");
        }
        else
        {
            int blockSize = getBlockSize();
            int i
                = EVP_CipherUpdate(
                        ctx,
                        out, outOff, blockSize,
                        in, inOff, blockSize);

            if (i < 0)
                throw new RuntimeException("EVP_CipherUpdate");
            else
                return i;
        }
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
        long ctx = this.ctx;

        if (ctx == 0)
        {
            throw new IllegalStateException("ctx");
        }
        else
        {
            int blockSize = getBlockSize();
            int i
                = EVP_CipherUpdate(
                        ctx,
                        out, outOff, blockSize,
                        in, inOff, blockSize);

            if (i < 0)
                throw new RuntimeException("EVP_CipherUpdate");
            else
                return i;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset()
    {
        long ctx = this.ctx;

        if (ctx == 0)
        {
            throw new IllegalStateException("ctx");
        }
        else
        {
            EVP_CIPHER_CTX_cleanup(ctx);

            // As the javadoc on the interface declaration of the method reset
            // defines. resetting this cipher leaves it in the same state as it
            // was after the last init (if there was one).
            Boolean forEncryption = this.forEncryption;

            if (EVP_CipherInit_ex(
                    ctx,
                    type,
                    /* impl */ 0L,
                    key,
                    /* iv */ null,
                    (forEncryption == null)
                        ? -1
                        : (forEncryption.booleanValue() ? 1 : 0)))
            {
                // The manual page of EVP_CIPHER_CTX_set_padding documents it to
                // always return 1 i.e. true.
                EVP_CIPHER_CTX_set_padding(ctx, false);
            }
            else
            {
                throw new RuntimeException(
                        "EVP_CipherInit_ex(" + getAlgorithmName() + ")");
            }
        }
    }
}
