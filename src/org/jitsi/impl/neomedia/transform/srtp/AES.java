/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import java.lang.reflect.*;
import java.security.*;
import java.util.*;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.engines.*;
import org.bouncycastle.crypto.params.*;
import org.jitsi.util.*;

/**
 * Implements a factory for an AES <tt>BlockCipher</tt>.
 *
 * @author Lyubomir Marinov
 */
public class AES
{
    /**
     * The block size in bytes of the AES algorithm (implemented by the
     * <tt>BlockCipher</tt>s initialized by the <tt>AES</tt> class).
     */
    private static final int BLOCK_SIZE = 16;

    /**
     * The <tt>BlockCipherFactory</tt> implemented with BouncyCastle. It is the
     * well-known fallback.
     */
    private static final BlockCipherFactory BOUNCYCASTLE_FACTORY
        = new BouncyCastleBlockCipherFactory();

    /**
     * The <tt>BlockCipherFactory</tt> implementations known to the <tt>AES</tt>
     * class among which the fastest is to be elected as {@link #factory}.
     */
    private static BlockCipherFactory[] factories;

    /**
     * The <tt>BlockCipherFactory</tt> implementation which is (to be) used by
     * the class <tt>AES</tt> to initialize <tt>BlockCipher</tt>s.
     */
    private static BlockCipherFactory factory;

    /**
     * The time in milliseconds at which {@link #factories} were benchmarked and
     * {@link #factory} was elected.
     */
    private static long factoryTimestamp;

    /**
     * The <tt>Class</tt>es of the well-known <tt>BlockCipherFactory</tt>
     * implementations.
     */
    private static final Class<?>[] FACTORY_CLASSES
        = {
            BouncyCastleBlockCipherFactory.class,
            OpenSSLBlockCipherFactory.class,
            SunJCEBlockCipherFactory.class,
            SunPKCS11BlockCipherFactory.class,
        };

    /**
     * The number of milliseconds after which the benchmark which elected
     * {@link #factory} is to be considered expired.
     */
    public static final long FACTORY_TIMEOUT = 60 * 1000;

    /**
     * The input buffer to be used for the benchmarking of {@link #factories}.
     * It consists of blocks and its length specifies the number of blocks to
     * process for the purposes of the benchmark.
     */ 
    private static final byte[] in = new byte[BLOCK_SIZE * 1024];

    /**
     * The key buffer to be used for the benchmarking of {@link #factories}.
     */
    private static final byte[] key = new byte[BLOCK_SIZE];

    /**
     * The <tt>Logger</tt> used by the <tt>AES</tt> class to print out debug
     * information.
     */
    private static final Logger logger = Logger.getLogger(AES.class);

    /**
     * The output buffer to be used for the benchmarking of {@link #factories}.
     */
    private static final byte[] out = new byte[BLOCK_SIZE];

    /**
     * The random number generator which generates keys and inputs for the
     * benchmarking of the <tt>BlockCipherFactory</tt> implementations.
     */
    private static final Random random = new Random();

    /**
     * Initializes a new <tt>BlockCipher</tt> instance which implements Advanced
     * Encryption Standard (AES).
     *
     * @return a new <tt>BlockCipher</tt> instance which implements Advanced
     * Encryption Standard (AES)
     */
    public static BlockCipher createBlockCipher()
    {
        BlockCipherFactory factory;

        synchronized (AES.class)
        {
            long now = System.currentTimeMillis();

            factory = AES.factory;
            if ((factory != null) && (now > factoryTimestamp + FACTORY_TIMEOUT))
                factory = null;
            if (factory == null)
            {
                try
                {
                    factory = getBlockCipherFactory();
                }
                catch (Throwable t)
                {
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
                                "Failed to initialize an optimized AES"
                                    + " implementation: "
                                    + t.getLocalizedMessage());
                    }
                }
                finally
                {
                    if (factory == null)
                    {
                        factory = AES.factory;
                        if (factory == null)
                            factory = BOUNCYCASTLE_FACTORY;
                    }

                    AES.factory = factory;
                    AES.factoryTimestamp = now;
                }
            }
        }

        try
        {
            return factory.createBlockCipher();
        }
        catch (Exception ex)
        {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            else
                throw new RuntimeException(ex);
        }
    }

    /**
     * Gets a <tt>BlockCipherFactory</tt> instance to be used by the
     * <tt>AES</tt> class to initialize <tt>BlockCipher</tt>s.
     *
     * <p>
     * Benchmarks the well-known <tt>BlockCipherFactory</tt> implementations and
     * returns the fastest one. 
     * </p>
     *
     * @return a <tt>BlockCipherFactory</tt> instance to be used by the
     * <tt>AES</tt> class to initialize <tt>BlockCipher</tt>s
     */
    private static BlockCipherFactory getBlockCipherFactory()
    {
        BlockCipherFactory[] factories = AES.factories;

        if (factories == null)
        {
            // A single instance of each well-known BlockCipherFactory
            // implementation will be initialized i.e. the attempt to initialize
            // BlockCipherFactory instances will be made once only.
            AES.factories
                = factories
                    = new BlockCipherFactory[FACTORY_CLASSES.length];

            int i = 0;

            for (Class<?> clazz : FACTORY_CLASSES)
            {
                try
                {
                    if (BlockCipherFactory.class.isAssignableFrom(clazz))
                    {
                        BlockCipherFactory factory;

                        if (BouncyCastleBlockCipherFactory.class.equals(clazz))
                            factory = BOUNCYCASTLE_FACTORY;
                        else
                            factory = (BlockCipherFactory) clazz.newInstance();

                        factories[i++] = factory;
                    }
                }
                catch (Throwable t)
                {
                    if (t instanceof InterruptedException)
                        Thread.currentThread().interrupt();
                    else if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                }
            }
        }

        Random random = AES.random;
        byte[] key = AES.key;
        byte[] in = AES.in;

        random.nextBytes(key);
        random.nextBytes(in);

        CipherParameters params = new KeyParameter(key);
        int blockSize = BLOCK_SIZE;
        int inEnd = in.length - blockSize + 1;
        byte[] out = AES.out;
        long minTime = Long.MAX_VALUE;
        BlockCipherFactory minFactory = null;

        for (int f = 0; f < factories.length; ++f)
        {
            BlockCipherFactory factory = factories[f];

            try
            {
                BlockCipher cipher = factory.createBlockCipher();

                if (cipher == null)
                {
                    // The BlockCipherFactory failed to initialize a new
                    // BlockCipher instance. We will not use it again because
                    // the failure may persist.
                    factories[f] = null;
                }
                else
                {
                    cipher.init(true, params);

                    long startTime = System.nanoTime();

                    for (int inOff = 0;
                            inOff < inEnd;
                            inOff = inOff + blockSize)
                    {
                        cipher.processBlock(in, inOff, out, 0);
                    }
                    // We do not invoke the method BlockCipher.reset() so we do
                    // not need to take it into account in the benchmark.

                    long endTime = System.nanoTime();
                    long time = endTime - startTime;

                    if (time < minTime)
                    {
                        minTime = time;
                        minFactory = factory;
                    }
                }
            }
            catch (Throwable t)
            {
                if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                else if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
            }
        }
        return minFactory;
    }

    /**
     * Implements <tt>BlockCipherFactory</tt> using BouncyCastle.
     *
     * @author Lyubomir Marinov
     */
    public static class BouncyCastleBlockCipherFactory
        implements BlockCipherFactory
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public BlockCipher createBlockCipher()
            throws Exception
        {
            return new AESFastEngine();
        }
    }

    /**
     * Implements <tt>BlockCipherFactory</tt> using OpenSSL.
     *
     * @author Lyubomir Marinov
     */
    public static class OpenSSLBlockCipherFactory
        implements BlockCipherFactory
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public BlockCipher createBlockCipher()
            throws Exception
        {
            return new OpenSSLBlockCipher(OpenSSLBlockCipher.AES_128_ECB);
        }
    }

    /**
     * Implements <tt>BlockCipherFactory</tt> using Sun JCE.
     *
     * @author Lyubomir Marinov
     */
    public static class SunJCEBlockCipherFactory
        extends SecurityProviderBlockCipherFactory
    {
        /**
         * Initializes a new <tt>SunJCEBlockCipherFactory</tt> instance.
         */
        public SunJCEBlockCipherFactory()
        {
            super("AES_128/ECB/NoPadding", "SunJCE");
        }
    }

    /**
     * Implements <tt>BlockCipherFactory</tt> using Sun PKCS#11.
     *
     * @author Lyubomir Marinov
     */
    public static class SunPKCS11BlockCipherFactory
        extends SecurityProviderBlockCipherFactory
    {
        /**
         * The <tt>java.security.Provider</tt> instance (to be) employed for an
         * (optimized) AES implementation.
         */
        private static Provider provider;

        /**
         * The indicator which determines whether {@link #provider} is to be
         * used. If <tt>true</tt>, an attempt will be made to initialize a
         * <tt>java.security.Provider</tt> instance. If the attempt fails,
         * <tt>false</tt> will be assigned in order to not repeatedly attempt
         * the initialization which is known to have failed.
         */
        private static boolean useProvider = true;

        /**
         * Gets the <tt>java.security.Provider</tt> instance (to be) employed
         * for an (optimized) AES implementation.
         *
         * @return the <tt>java.security.Provider</tt> instance (to be) employed
         * for an (optimized) AES implementation
         */
        private static synchronized Provider getProvider()
            throws Exception
        {
            Provider provider = SunPKCS11BlockCipherFactory.provider;

            if ((provider == null) && useProvider)
            {
                try
                {
                    Class<?> clazz
                        = Class.forName("sun.security.pkcs11.SunPKCS11");

                    if (Provider.class.isAssignableFrom(clazz))
                    {
                        Constructor<?> contructor
                            = clazz.getConstructor(String.class);

                        // The SunPKCS11 Config name should be unique in order
                        // to avoid repeated initialization exceptions.
                        String name = null;
                        Package pkg = AES.class.getPackage();

                        if (pkg != null)
                            name = pkg.getName();
                        if (name == null || name.length() == 0)
                            name = "org.jitsi.impl.neomedia.transform.srtp";

                        provider
                            = (Provider)
                                contructor.newInstance(
                                        "--name=" + name + "\\n"
                                            + "nssDbMode=noDb\\n"
                                            + "attributes=compatibility");
                    }
                }
                finally
                {
                    if (provider == null)
                        useProvider = false;
                    else
                        SunPKCS11BlockCipherFactory.provider = provider;
                }
            }
            return provider;
        }

        /**
         * Initializes a new <tt>SunPKCS11BlockCipherFactory</tt> instance.
         *
         * @throws Exception if anything goes wrong while initializing a new
         * <tt>SunPKCS11BlockCipherFactory</tt> instance
         */
        public SunPKCS11BlockCipherFactory()
            throws Exception
        {
            super("AES_128/ECB/NoPadding", getProvider());
        }
    }
}
