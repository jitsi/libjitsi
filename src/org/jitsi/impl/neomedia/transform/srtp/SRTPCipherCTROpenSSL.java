package org.jitsi.impl.neomedia.transform.srtp;

import org.jitsi.util.*;

public class SRTPCipherCTROpenSSL implements SRTPCipherCTR
{
    /**
     * The <tt>Logger</tt> used by the <tt>SRTPCipherCTROpenSSL</tt> class to print out debug
     * information.
     */
    private static final Logger logger = Logger.getLogger(SRTPCipherCTROpenSSL.class);

    /**
     * The indicator which determines whether
     * OpenSSL (Crypto) library wrapper was loaded.
     */
    private static boolean libraryLoaded;

    private static final int IVLEN = 16;
    private static final int KEYLEN = 16;

    private static native long CIPHER_CTX_create();

    private static native void CIPHER_CTX_destroy(long ctx);

    private static native boolean AES128CTR_CTX_init(long ctx, byte[] key);

    private static native boolean AES128CTR_CTX_process(long ctx, byte[] iv, byte[] inOut, int offset, int len);

    private long ctx;

    public SRTPCipherCTROpenSSL()
    {
        if (!OpenSSLWrapperLoader.isLoaded())
            throw new RuntimeException("OpenSSL wrapper not loaded");

        ctx = CIPHER_CTX_create();
        if (ctx == 0)
            throw new RuntimeException("CIPHER_CTX_create");
    }

    public void init(byte[] key)
    {
        if (key.length != KEYLEN)
            throw new IllegalArgumentException("key.length != BLKLEN");

        if(!AES128CTR_CTX_init(ctx, key))
            throw new RuntimeException("AES128CTR_CTX_init");
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
                CIPHER_CTX_destroy(ctx);
                ctx = 0;
            }
        }
        finally
        {
            super.finalize();
        }
    }

    /*
     * Encrypt or decrypt data with AES 128 in counter mode
     * You must set the key with init(key) before using this function
     */
    public void process(
            byte[] data, int off, int len,
            byte[] iv)
    {
        if (off + len > data.length)
            throw new IllegalArgumentException("off + len > data.length");

        if (iv.length != IVLEN)
            throw new IllegalArgumentException("iv.length != BLKLEN");

        if(!AES128CTR_CTX_process(ctx, iv, data, off, len))
            throw new RuntimeException("AES128CTR_CTX_process");
    }
}
