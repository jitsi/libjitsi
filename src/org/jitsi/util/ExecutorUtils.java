/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util;

import java.util.concurrent.*;

/**
 * Implements utility functions to facilitate work with <tt>Executor</tt>s and
 * <tt>ExecutorService</tt>.
 *
 * @author Lyubomir Marinov
 */
public class ExecutorUtils
{
    /**
     * Creates a thread pool that creates new threads as needed, but will reuse
     * previously constructed threads when they are available. Optionally, the
     * new threads are created as daemon threads and their names are based on a
     * specific (prefix) string.
     *
     * @param daemon <tt>true</tt> to create the new threads as daemon threads
     * or <tt>false</tt> to create the new threads as user threads
     * @param baseName the base/prefix to use for the names of the new threads
     * or <tt>null</tt> to leave them with their default names
     * @return the newly created thread pool
     */
    public static ExecutorService newCachedThreadPool(
            final boolean daemon,
            final String baseName)
    {
        return
            Executors.newCachedThreadPool(
                    new ThreadFactory()
                    {
                        /**
                         * The default <tt>ThreadFactory</tt> implementation
                         * which is augmented by this instance to create daemon
                         * <tt>Thread</tt>s.
                         */
                        private final ThreadFactory defaultThreadFactory
                            = Executors.defaultThreadFactory();

                        @Override
                        public Thread newThread(Runnable r)
                        {
                            Thread t = defaultThreadFactory.newThread(r);

                            if (t != null)
                            {
                                t.setDaemon(daemon);

                                /*
                                 * Additionally, make it known through the name
                                 * of the Thread that it is associated with the
                                 * specified class for debugging/informational
                                 * purposes.
                                 */
                                if ((baseName != null)
                                        && (baseName.length() != 0))
                                {
                                    String name = t.getName();

                                    if (name == null)
                                        name = "";
                                    t.setName(baseName + "-" + name);
                                }
                            }
                            return t;
                        }
                    });
    }
}
