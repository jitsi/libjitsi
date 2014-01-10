/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import org.jitsi.util.*;

/**
 * Extension for the JNI link to the WebrtcAec.
 *
 * @author Vincent Lucas
 */
public class WebrtcAec
{
    /**
     * The <tt>Logger</tt> used by the <tt>WebrtcAec</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(WebrtcAec.class);

    /**
     * Tells if the Webrtc library is correctly loaded.
     */
    public static boolean isLoaded;


    /**
     * Loads WebrtcAec if we are using MacOsX.
     */
    static
    {
        try
        {
            System.loadLibrary("jnwebrtc");
            System.loadLibrary("jnwebrtcaec");
        }
        catch (NullPointerException npe)
        {
            // Swallow whatever exceptions are known to be thrown by
            // System.loadLibrary() because the class has to be loaded in order
            // to not prevent the loading of its users and isLoaded will remain
            // false eventually.
            logger.info("Failed to load WebrtcAec library: ", npe);
        }
        catch (SecurityException se)
        {
            logger.info("Failed to load WebrtcAec library: ", se);
        }
        catch (UnsatisfiedLinkError ule)
        {
            logger.info("Failed to load WebrtcAec library: ", ule);
        }
    }

    public static void init()
    {
        // Nothing to do, but the first call to this function load the webrtc
        // and the webrtcaec libraries (cf. previous function).
    }

    public static void log(byte[] error)
    {
        String errorString = StringUtils.newString(error);
        logger.info(errorString);
    }
}
