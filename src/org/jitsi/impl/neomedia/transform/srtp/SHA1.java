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

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.digests.*;
import org.jitsi.util.*;

/**
 * Implements a factory for a SHA-1 <tt>Digest</tt>.
 *
 * @author Lyubomir Marinov
 */
public class SHA1
{
    /**
     * The <tt>Logger</tt> used by the <tt>SHA1</tt> class to print out debug
     * information.
     */
    private static final Logger logger = Logger.getLogger(SHA1.class);

    /**
     * The indicator which determines whether the OpenSSL (Crypto) library is to
     * be used. If <tt>true</tt>, an attempt will be made to initialize an
     * <tt>OpenSSLDigest</tt> instance. If the attempt fails, <tt>false</tt>
     * will be assigned in order to not repeatedly attempt the initialization
     * which is known to have failed.
     */
    private static boolean useOpenSSL = true;

    /**
     * Initializes a new <tt>org.bouncycastle.crypto.Digest</tt> instance which
     * implements the SHA-1 cryptographic hash function/digest.
     *
     * @return a new <tt>org.bouncycastle.crypto.Digest</tt> instance which
     * implements the SHA-1 cryptographic hash function/digest
     */
    public static Digest createDigest()
    {
        // Optionally, use OpenSSL (Crypto).
        if (useOpenSSL)
        {
            try
            {
                return new OpenSSLDigest(OpenSSLDigest.SHA1);
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
                                + " SHA-1 implementation: "
                                + t.getLocalizedMessage());
                }
            }
        }

        // Fallback to BouncyCastle.
        return new SHA1Digest();
    }
}
