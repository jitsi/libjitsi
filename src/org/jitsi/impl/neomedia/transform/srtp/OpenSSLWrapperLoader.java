package org.jitsi.impl.neomedia.transform.srtp;

import org.jitsi.util.*;

public class OpenSSLWrapperLoader
{
    /**
     * The <tt>Logger</tt> used by the <tt>OpenSSLWrapperLoader</tt> class to print out debug
     * information.
     */
    private static final Logger logger = Logger.getLogger(OpenSSLWrapperLoader.class);

    /**
     * The indicator which determines whether
     * OpenSSL (Crypto) library wrapper was loaded.
     */
    private static boolean libraryLoaded = false;

    private static native boolean OpenSSL_Init();

    static {
        try
        {
            JNIUtils.loadLibrary(
                "jnopenssl",
                OpenSSLWrapperLoader.class.getClassLoader());
            if (OpenSSL_Init()) {
                logger.info("jnopenssl successfully loaded aaaaa");
                libraryLoaded = true;
            } else {
                logger.warn("OpenSSL_Init failed");
            }
        }
        catch(Throwable t)
        {
            logger.warn("Unable to load jnopenssl: "+t.toString());
        }
    }

    private OpenSSLWrapperLoader() {}

    public static boolean isLoaded() {
        return libraryLoaded;
    }
}
