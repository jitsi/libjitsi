/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.macs.*;
import org.jitsi.util.*;

/**
 * Implements a factory for an HMAC-SHA1 <tt>org.bouncycastle.crypto.Mac</tt>.
 *
 * @author Lyubomir Marinov
 */
public class HMACSHA1
{
    /**
     * The <tt>Logger</tt> used by the <tt>HMACSHA1</tt> class to print out
     * debug information.
     */
    private static final Logger logger = Logger.getLogger(HMACSHA1.class);

    /**
     * The indicator which determines whether the OpenSSL (Crypto) library is to
     * be used. If <tt>true</tt>, an attempt will be made to initialize an
     * <tt>OpenSSLHMAC</tt> instance. If the attempt fails, <tt>false</tt> will
     * be assigned in order to not repeatedly attempt the initialization which
     * is known to have failed.
     */
    private static boolean useOpenSSL = true;

    /**
     * Initializes a new <tt>org.bouncycastle.crypto.Mac</tt> instance which
     * implements a keyed-hash message authentication code (HMAC) with SHA-1.
     *
     * @return a new <tt>org.bouncycastle.crypto.Mac</tt> instance which
     * implements a keyed-hash message authentication code (HMAC) with SHA-1
     */
    public static Mac createMac()
    {
        // Optionally, use OpenSSL (Crypto).
        if (useOpenSSL)
        {
            try
            {
                return new OpenSSLHMAC(OpenSSLDigest.SHA1);
            }
            catch (Throwable t)
            {
                // If an exception is thrown once, it is very likely to be
                // thrown multiple times.
                useOpenSSL = false;

                if (t instanceof InterruptedException)
                {
                    Thread.currentThread().interrupt();
                }
                else if (t instanceof ThreadDeath)
                {
                    throw (ThreadDeath) t;
                }
                else
                {
                    logger.warn(
                            "Failed to employ OpenSSL (Crypto) for an optimized"
                                + " HMAC-SHA1 implementation: "
                                + t.getLocalizedMessage());
                }
            }
        }

        // Fallback to BouncyCastle.
        return new HMac(SHA1.createDigest());
    }
}
