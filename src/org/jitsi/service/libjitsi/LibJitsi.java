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
package org.jitsi.service.libjitsi;

import java.lang.reflect.*;

import org.jitsi.service.audionotifier.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.fileaccess.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.service.resources.*;
import org.jitsi.utils.logging.*;

/**
 * Represents the entry point of the <tt>libjitsi</tt> library.
 * <p>
 * The {@link #start()} method is to be called to initialize/start the use of
 * the library. Respectively, the {@link #stop()} method is to be called to
 * uninitialize/stop the use of the library (i.e. to release the resources
 * acquired by the library during its execution). The <tt>getXXXService()</tt>
 * methods may be called only after the <tt>start()</tt> method returns
 * successfully and before the <tt>stop()</tt> method is called.
 * </p>
 * <p>
 * The <tt>libjitsi</tt> library may be utilized both with and without OSGi. If
 * the library detects during the execution of the <tt>start()</tt> method that
 * (a) the <tt>LibJitsi</tt> class has been loaded as part of an OSGi
 * <tt>Bundle</tt> and (b) successfully retrieves the associated
 * <tt>BundleContext</tt>, it will look for the references to the
 * implementations of the supported service classes in the retrieved
 * <tt>BundleContext</tt>. Otherwise, the library will stand alone without
 * relying on OSGi functionality. In the case of successful detection of OSGi,
 * the library will not register the supported service class instances in the
 * retrieved <tt>BundleContext</tt>.
 * </p>
 *
 * @author Lyubomir Marinov
 */
public abstract class LibJitsi
{
    /**
     * The <tt>Logger</tt> used by the <tt>LibJitsi</tt> class for logging
     * output.
     */
    private static final Logger logger = Logger.getLogger(LibJitsi.class);

    /**
     * The <tt>LibJitsi</tt> instance which is provides the implementation of
     * the <tt>getXXXService</tt> methods.
     */
    private static LibJitsi impl;

    /**
     * Gets the <tt>AudioNotifierService</tt> instance. If no existing
     * <tt>AudioNotifierService</tt> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <tt>AudioNotifierService</tt> instance known to the library
     * or <tt>null</tt> if no <tt>AudioNotifierService</tt> instance is known to
     * the library
     */
    public static AudioNotifierService getAudioNotifierService()
    {
        return invokeGetServiceOnImpl(AudioNotifierService.class);
    }

    /**
     * Gets the <tt>ConfigurationService</tt> instance. If no existing
     * <tt>ConfigurationService</tt> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <tt>ConfigurationService</tt> instance known to the library
     * or <tt>null</tt> if no <tt>ConfigurationService</tt> instance is known to
     * the library
     */
    public static ConfigurationService getConfigurationService()
    {
        return invokeGetServiceOnImpl(ConfigurationService.class);
    }

    /**
     * Gets the <tt>FileAccessService</tt> instance. If no existing
     * <tt>FileAccessService</tt> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <tt>FileAccessService</tt> instance known to the library or
     * <tt>null</tt> if no <tt>FileAccessService</tt> instance is known to the
     * library
     */
    public static FileAccessService getFileAccessService()
    {
        return invokeGetServiceOnImpl(FileAccessService.class);
    }

    /**
     * Gets the <tt>MediaService</tt> instance. If no existing
     * <tt>MediaService</tt> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <tt>MediaService</tt> instance known to the library or
     * <tt>null</tt> if no <tt>MediaService</tt> instance is known to the
     * library
     */
    public static MediaService getMediaService()
    {
        return invokeGetServiceOnImpl(MediaService.class);
    }

    /**
     * Gets the <tt>PacketLoggingService</tt> instance. If no existing
     * <tt>PacketLoggingService</tt> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <tt>PacketLoggingService</tt> instance known to the library
     * or <tt>null</tt> if no <tt>PacketLoggingService</tt> instance is known to
     * the library
     */
    public static PacketLoggingService getPacketLoggingService()
    {
        return invokeGetServiceOnImpl(PacketLoggingService.class);
    }

    /**
     * Gets the <tt>ResourceManagementService</tt> instance. If no existing
     * <tt>ResourceManagementService</tt> instance is known to the library,
     * tries to initialize a new one. (Such a try to initialize a new instance
     * is performed just once while the library is initialized.)
     *
     * @return the <tt>ResourceManagementService</tt> instance known to the
     * library or <tt>null</tt> if no <tt>ResourceManagementService</tt>
     * instance is known to the library
     */
    public static ResourceManagementService getResourceManagementService()
    {
        return invokeGetServiceOnImpl(ResourceManagementService.class);
    }

    /**
     * Invokes {@link #getService(Class)} on {@link #impl}.
     *
     * @param serviceClass the class of the service to be retrieved
     * @return a service of the specified type if such a service is associated
     * with the library
     * @throws IllegalStateException if the library is not currently initialized
     */
    private static <T> T invokeGetServiceOnImpl(Class<T> serviceClass)
    {
        LibJitsi impl = LibJitsi.impl;

        if (impl == null)
            throw new IllegalStateException("impl");
        else
            return impl.getService(serviceClass);
    }

    /**
     * Starts/initializes the use of the <tt>libjitsi</tt> library.
     */
    public static void start()
    {
        start(null);
    }

    /**
     * Starts/initializes the use of the <tt>libjitsi</tt> library.
     *
     * @param context an <tt>Object</tt>, if any, which represents a context in
     * which the <tt>libjitsi</tt> library is being started and is to be
     * executed. If non-<tt>null</tt>, a <tt>LibJitsi</tt> implementation which
     * accepts it will be used. For example, <tt>BundleContext</tt> may be
     * specified in which case an OSGi-aware <tt>LibJitsi</tt> implementation
     * will be used.
     */
    static void start(Object context)
    {
        if (null != LibJitsi.impl)
        {
            if (logger.isInfoEnabled())
            {
                logger.info("LibJitsi already started, using as " +
                        "implementation: " + impl.getClass().getCanonicalName());
            }
            
            return;
        }
        
        /*
         * LibJitsi implements multiple backends and tries to choose the most
         * appropriate at run time. For example, an OSGi-aware backend is used
         * if it is detected that an OSGi implementation is available.
         */
        String implBaseClassName
            = LibJitsi.class.getName().replace(".service.", ".impl.");
        String[] implClassNameExtensions
            = new String[] { "OSGi", "" };
        LibJitsi impl = null;

        for (int i = 0; i < implClassNameExtensions.length; i++)
        {
            Class<?> implClass = null;
            String implClassName
                = implBaseClassName + implClassNameExtensions[i] + "Impl";
            Throwable exception = null;

            try
            {
                implClass = Class.forName(implClassName);
            }
            catch (ClassNotFoundException cnfe)
            {
                exception = cnfe;
            }
            catch (ExceptionInInitializerError eiie)
            {
                exception = eiie;
            }
            catch (LinkageError le)
            {
                exception = le;
            }
            if ((implClass != null)
                    && LibJitsi.class.isAssignableFrom(implClass))
            {
                try
                {
                    if (context == null)
                    {
                        impl = (LibJitsi) implClass.newInstance();
                    }
                    else
                    {
                        /*
                         * Try to find a Constructor which will accept the
                         * specified context.
                         */
                        Constructor<?> constructor = null;

                        for (Constructor<?> aConstructor
                                : implClass.getConstructors())
                        {
                            Class<?>[] parameterTypes
                                = aConstructor.getParameterTypes();

                            if ((parameterTypes.length == 1)
                                    && parameterTypes[0].isInstance(context))
                            {
                                constructor = aConstructor;
                                break;
                            }
                        }

                        impl = (LibJitsi) constructor.newInstance(context);
                    }
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    else
                        exception = t;
                }
                if (impl != null)
                    break;
            }

            if ((exception != null) && logger.isInfoEnabled())
            {
                StringBuilder message = new StringBuilder();

                message.append("Failed to initialize LibJitsi backend ");
                message.append(implClassName);
                message.append(". (Exception stack trace follows.)");
                // If the current backend is not the last, we'll try the next.
                if (i < (implClassNameExtensions.length - 1))
                    message.append(" Will try an alternative.");
                logger.info(message, exception);
            }
        }

        if (impl == null)
            throw new IllegalStateException("impl");
        else
        {
            LibJitsi.impl = impl;

            if (logger.isInfoEnabled())
            {
                logger.info("Successfully started LibJitsi using as " +
                        "implementation: " + impl.getClass().getCanonicalName());
            }
        }
    }

    /**
     * Stops/uninitializes the use of the <tt>libjitsi</tt> library.
     */
    public static void stop()
    {
        impl = null;
    }

    /**
     * Initializes a new <tt>LibJitsi</tt> instance.
     */
    protected LibJitsi()
    {
    }

    /**
     * Gets a service of a specific type associated with this implementation of
     * the <tt>libjitsi</tt> library.
     *
     * @param serviceClass the type of the service to be retrieved
     * @return a service of the specified type if there is such an association
     * known to this implementation of the <tt>libjitsi</tt> library; otherwise,
     * <tt>null</tt>
     */
    protected abstract <T> T getService(Class<T> serviceClass);
}
