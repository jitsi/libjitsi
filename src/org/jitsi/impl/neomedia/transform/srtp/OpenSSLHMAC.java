/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.params.*;
import org.jitsi.util.*;

/**
 * Implements the interface <tt>org.bouncycastle.crypto.Mac</tt> using the
 * OpenSSL Crypto library.
 *
 * @author Lyubomir Marinov
 */
public class OpenSSLHMAC
    implements Mac
{
    private static long EVP_sha1;

    /**
     * The indicator which determines whether
     * <tt>System.loadLibrary(String)</tt> is to be invoked in order to load the
     * OpenSSL (Crypto) library.
     */
    private static boolean loadLibrary = true;

    private static native int EVP_MD_size(long md);

    private static native long EVP_sha1();

    private static native void HMAC_CTX_cleanup(long ctx);

    private static native long HMAC_CTX_create();

    private static native void HMAC_CTX_destroy(long ctx);

    private static native int HMAC_Final(
            long ctx,
            byte[] md, int mdOff, int mdLen);

    private static native boolean HMAC_Init_ex(
            long ctx,
            byte[] key, int keyLen,
            long md,
            long impl);

    private static native boolean HMAC_Update(
            long ctx,
            byte[] data, int off, int len);

    /**
     * The name of the algorithm implemented by this instance.
     */
    private final String algorithmName;

    /**
     * The context of the OpenSSL (Crypto) library through which the actual
     * algorithm implementation is invoked by this instance.
     */
    private long ctx;

    /**
     * The key provided in the form of a {@link KeyParameter} in the last
     * invocation of {@link #init(CipherParameters)}.
     */
    private byte[] key;

    /**
     * The block size in bytes for this MAC.
     */
    private final int macSize;

    /**
     * The OpenSSL Crypto type of the message digest implemented by this
     * instance.
     */
    private final long md;

    /**
     * Initializes a new <tt>OpenSSLHMAC</tt> instance with a specific digest
     * algorithm.
     *
     * @param digestAlgorithm the algorithm of the digest to initialize the new
     * instance with
     * @see OpenSSLDigest#SHA1
     */
    public OpenSSLHMAC(int digestAlgorithm)
    {
        if (digestAlgorithm == OpenSSLDigest.SHA1)
        {
            algorithmName = "SHA-1/HMAC";
        }
        else
        {
            throw new IllegalArgumentException(
                    "digestAlgorithm " + digestAlgorithm);
        }

        // Load the OpenSSL (Crypto) library if necessary.
        synchronized (OpenSSLDigest.class)
        {
            if (loadLibrary)
            {
                try
                {
                    JNIUtils.loadLibrary(
                            "jnopenssl",
                            OpenSSLHMAC.class.getClassLoader());
                    EVP_sha1 = EVP_sha1();
                }
                finally
                {
                    loadLibrary = false;
                }
            }
        }

        long md;

        if (digestAlgorithm == OpenSSLDigest.SHA1)
        {
            long EVP_sha1 = OpenSSLHMAC.EVP_sha1;

            if (EVP_sha1 == 0)
                throw new IllegalStateException("EVP_sha1");
            else
                md = EVP_sha1;
        }
        else
        {
            // It must have been checked prior to loading the OpenSSL (Crypto)
            // library but the compiler needs it to be convinced that we are not
            // attempting to use an uninitialized variable.
            throw new IllegalArgumentException(
                    "digestAlgorithm " + digestAlgorithm);
        }
        this.md = md;

        long ctx = HMAC_CTX_create();

        if (ctx == 0)
        {
            throw new RuntimeException("HMAC_CTX_create");
        }
        else
        {
            boolean ok = false;

            this.ctx = ctx;
            try
            {
                reset();

                macSize = EVP_MD_size(md);

                ok = true;
            }
            finally
            {
                if (!ok)
                {
                    if (this.ctx == ctx)
                        this.ctx = 0;
                    HMAC_CTX_destroy(ctx);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int doFinal(byte[] out, int outOff)
        throws DataLengthException, IllegalStateException
    {
        if (out == null)
            throw new NullPointerException("out");
        if ((outOff < 0) || (out.length <= outOff))
            throw new ArrayIndexOutOfBoundsException(outOff);

        int outLen = out.length - outOff;
        int macSize = getMacSize();

        if (outLen < macSize)
        {
            throw new DataLengthException(
                    "Space in out must be at least " + macSize + "bytes but is "
                        + outLen + " bytes!");
        }

        long ctx = this.ctx;

        if (ctx == 0)
        {
            throw new IllegalStateException("ctx");
        }
        else
        {
            outLen = HMAC_Final(ctx, out, outOff, outLen);
            if (outLen < 0)
            {
                throw new RuntimeException("HMAC_Final");
            }
            else
            {
                // As the javadoc on interface method specifies, the doFinal
                // call leaves this Digest reset.
                reset();
                return outLen;
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
                HMAC_CTX_destroy(ctx);
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
    public int getMacSize()
    {
        return macSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(CipherParameters params)
        throws IllegalArgumentException
    {
        byte[] key
            = (params instanceof KeyParameter)
                ? ((KeyParameter) params).getKey()
                : null;

        this.key = key;

        reset();
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
            HMAC_CTX_cleanup(ctx);

            // As the javadoc on the interface declaration of the method reset
            // defines. resetting this cipher leaves it in the same state as it
            // was after the last init (if there was one).
            if (!HMAC_Init_ex(
                    ctx,
                    key, (key == null) ? 0 : key.length,
                    md,
                    /* impl */ 0))
            {
                throw new RuntimeException(
                        "HMAC_Init_ex(" + getAlgorithmName() + ")");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(byte in)
        throws IllegalStateException
    {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(byte[] in, int off, int len)
        throws DataLengthException, IllegalStateException
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
            else if (!HMAC_Update(ctx, in, off, len))
                throw new RuntimeException("HMAC_Update");
        }
    }
}
