/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import java.lang.reflect.*;
import java.security.*;

import javax.crypto.*;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.engines.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

/**
 * Implements a factory for an AES <tt>BlockCipher</tt>.
 *
 * @author Lyubomir Marinov
 */
public class AES
{
    /**
     * The <tt>Logger</tt> used by the <tt>AES</tt> class to print out debug
     * information.
     */
    private static final Logger logger = Logger.getLogger(AES.class);

    /**
     * The <tt>java.security.Provider</tt> instance which is employed for an
     * optimized AES implementation.
     */
    private static Provider provider;

    /**
     * The name of the <tt>ConfigurationService</tt> and/or <tt>System</tt>
     * property which specifies the name of the <tt>java.security.Provider</tt>
     * to be employed for an optimized AES implementation.
     */
    private static final String PROVIDER_NAME_PNAME
        = AES.class.getName() + ".provider.name";

    /**
     * The indicator which determines whether {@link #provider} is to be used.
     * If <tt>true</tt>, an attempt will be made to initialize a well-known
     * <tt>java.security.Provider</tt> implementation. If the attempt fails,
     * <tt>false</tt> will be assigned in order to not repeatedly attempt the
     * initialization which is known to have failed.
     */
    private static boolean useProvider = true;

    /**
     * Initializes a new <tt>BlockCipher</tt> instance which implements Advanced
     * Encryption Standard (AES).
     *
     * @return a new <tt>BlockCipher</tt> instance which implements Advanced
     * Encryption Standard (AES)
     */
    public static BlockCipher createBlockCipher()
    {
        // The value of useProvider changes from true to false only and it does
        // not sound like a problem to have multiple threads access it
        // concurrently.
        if (useProvider)
        {
            try
            {
                java.security.Provider provider = getProvider();

                if (provider == null)
                {
                    useProvider = false;
                }
                else
                {
                    Cipher cipher
                        = Cipher.getInstance("AES/CTR/NoPadding", provider);

                    if (cipher == null)
                    {
                        // If AES is not found once, it is very likely to not be
                        // found multiple times.
                        useProvider = false;
                    }
                    else
                    {
                        return new BlockCipherAdapter(cipher);
                    }
                }
            }
            catch (Throwable t)
            {
                // If an exception is thrown once, it is very likely to be
                // thrown multiple times.
                useProvider = false;

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
                            "Failed to employ a java.security.Provider for an"
                                + " optimized AES implementation: "
                                + t.getLocalizedMessage());
                }
            }
        }

        return new AESFastEngine();
    }

    /**
     * Gets the <tt>java.security.Provider</tt> instance (to be) employed for an
     * optimized AES implementation.
     *
     * @return the <tt>java.security.Provider</tt> instance (to be) employed for
     * an optimized AES implementation
     */
    private static Provider getProvider()
        throws Exception
    {
        Provider provider;

        synchronized (AES.class)
        {
            provider = AES.provider;
            if ((provider == null) && useProvider)
            {
                ConfigurationService cfg = LibJitsi.getConfigurationService();
                String providerName
                    = (cfg == null)
                        ? System.getProperty(PROVIDER_NAME_PNAME)
                        : cfg.getString(PROVIDER_NAME_PNAME);

                try
                {
                    if ((providerName != null) && (providerName.length() != 0))
                    {
                        if ("SunPKCS11".equals(providerName))
                        {
                            Class<?> clazz
                                = Class.forName(
                                        "sun.security.pkcs11.SunPKCS11");

                            if (Provider.class.isAssignableFrom(clazz))
                            {
                                Constructor<?> contructor
                                    = clazz.getConstructor(String.class);

                                // The SunPKCS11 Config name should be unique in
                                // order to avoid repeated initialization
                                // exceptions.
                                String name = null;
                                Package pkg = AES.class.getPackage();

                                if (pkg != null)
                                    name = pkg.getName();
                                if (name == null || name.length() == 0)
                                {
                                    name
                                        = "org.jitsi.impl.neomedia.transform"
                                            + ".srtp";
                                }

                                provider
                                    = (Provider)
                                        contructor.newInstance(
                                                "--name=" + name + "\\n"
                                                    + "nssDbMode=noDb\\n"
                                                    + "attributes=compatibility");
                            }
                        }
                        else
                        {
                            provider = Security.getProvider(providerName);
                        }
                    }
                }
                finally
                {
                    if (provider == null)
                        useProvider = false;
                    else
                        AES.provider = provider;
                }
            }
        }
        return provider;
    }
}
