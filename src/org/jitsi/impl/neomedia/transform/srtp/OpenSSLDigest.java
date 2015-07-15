/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import org.bouncycastle.crypto.*;
import org.jitsi.util.*;

/**
 * Implements the interface <tt>org.bouncycastle.crypto.Digest</tt> using the
 * OpenSSL Crypto library.
 *
 * @author Lyubomir Marinov
 */
public class OpenSSLDigest
    implements ExtendedDigest
{
    private static long EVP_sha1;

    /**
     * The indicator which determines whether
     * <tt>System.loadLibrary(String)</tt> is to be invoked in order to load the
     * OpenSSL (Crypto) library.
     */
    private static boolean loadLibrary = true;

    /**
     * The algorithm of the SHA-1 cryptographic hash function/digest.
     */
    public static final int SHA1 = 1;

    private static native int EVP_DigestFinal_ex(
            long ctx,
            byte[] md, int off);

    private static native boolean EVP_DigestInit_ex(
            long ctx,
            long type,
            long impl);

    private static native boolean EVP_DigestUpdate(
            long ctx,
            byte[] d, int off, int cnt);

    private static native int EVP_MD_CTX_block_size(long ctx);

    private static native long EVP_MD_CTX_create();

    private static native void EVP_MD_CTX_destroy(long ctx);

    private static native int EVP_MD_CTX_size(long ctx);

    private static native long EVP_sha1();

    /**
     * The name of the algorithm implemented by this instance.
     */
    private final String algorithmName;

    /**
     * The size in bytes of the internal buffer the digest applies its
     * compression function to.
     */
    private final int byteLength;

    /**
     * The digest context of the OpenSSL (Crypto) library through which the
     * actual algorithm implementation is invoked by this instance.
     */
    private long ctx;

    /**
     * The size in bytes of the digest produced by this message digest.
     */
    private final int digestSize;

    /**
     * The OpenSSL Crypto type of the message digest implemented by this
     * instance.
     */
    private final long type;

    /**
     * Initializes a new <tt>OpenSSLDigest</tt> instance with a specific
     * algorithm.
     *
     * @param algorithm the algorithm with which to initialize the new instance
     * @see #SHA1
     */
    public OpenSSLDigest(int algorithm)
    {
        // Make sure the provided arguments are legal.
        if (algorithm == SHA1)
            algorithmName = "SHA-1";
        else
            throw new IllegalArgumentException("algorithm " + algorithm);

        // Load the OpenSSL (Crypto) library if necessary.
        synchronized (OpenSSLDigest.class)
        {
            if (loadLibrary)
            {
                try
                {
                    JNIUtils.loadLibrary(
                            "jnopenssl",
                            OpenSSLDigest.class.getClassLoader());
                    EVP_sha1 = EVP_sha1();
                }
                finally
                {
                    loadLibrary = false;
                }
            }
        }

        long type;

        if (algorithm == SHA1)
        {
            long EVP_sha1 = OpenSSLDigest.EVP_sha1;

            if (EVP_sha1 == 0)
                throw new IllegalStateException("EVP_sha1");
            else
                type = EVP_sha1;
        }
        else
        {
            // It must have been checked prior to loading the OpenSSL (Crypto)
            // library but the compiler needs it to be convinced that we are not
            // attempting to use an uninitialized variable.
            throw new IllegalArgumentException("algorithm " + algorithm);
        }
        this.type = type;

        long ctx = EVP_MD_CTX_create();

        if (ctx == 0)
        {
            throw new RuntimeException("EVP_MD_CTX_create");
        }
        else
        {
            boolean ok = false;

            this.ctx = ctx;
            try
            {
                reset();

                // The byteLength and digestSize are actually properties of the
                // (OpenSSL Crypto) type so it is really safe to query them
                // once in light of the fact that the type is final.
                byteLength = EVP_MD_CTX_block_size(ctx);
                digestSize = EVP_MD_CTX_size(ctx);

                ok = true;
            }
            finally
            {
                if (!ok)
                {
                    if (this.ctx == ctx)
                        this.ctx = 0;
                    EVP_MD_CTX_destroy(ctx);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int doFinal(byte[] out, int off)
    {
        if (out == null)
            throw new NullPointerException("out");
        if ((off < 0) || (out.length <= off))
            throw new ArrayIndexOutOfBoundsException(off);

        long ctx = this.ctx;

        if (ctx == 0)
        {
            throw new IllegalStateException("ctx");
        }
        else
        {
            int s = EVP_DigestFinal_ex(ctx, out, off);

            if (s < 0)
            {
                throw new RuntimeException("EVP_DigestFinal_ex");
            }
            else
            {
                // As the javadoc on interface method specifies, the doFinal
                // call leaves this Digest reset.
                reset();
                return s;
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
                EVP_MD_CTX_destroy(ctx);
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
    public int getByteLength()
    {
        return byteLength;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDigestSize()
    {
        return digestSize;
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
        else if (!EVP_DigestInit_ex(ctx, type, /* impl */ 0L))
        {
            throw new RuntimeException(
                    "EVP_DigestInit_ex(" + getAlgorithmName() + ")");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(byte in)
    {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(byte[] in, int off, int len)
    {
        if (len != 0)
        {
            if (in == null)
                throw new NullPointerException("in");
            if ((off < 0) || (in.length <= off))
                throw new ArrayIndexOutOfBoundsException(off);
            if ((len < 0) || (in.length < off + len))
                throw new IllegalArgumentException("len " + len);

            long ctx = this.ctx;

            if (ctx == 0)
                throw new IllegalStateException("ctx");
            else if (!EVP_DigestUpdate(ctx, in, off, len))
                throw new RuntimeException("EVP_DigestUpdate");
        }
    }
}
