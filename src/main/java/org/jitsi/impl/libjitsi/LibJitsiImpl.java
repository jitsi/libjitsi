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
package org.jitsi.impl.libjitsi;

import java.util.*;
import java.util.concurrent.locks.*;

import org.jitsi.service.libjitsi.*;
import org.jitsi.utils.logging.*;

/**
 * Represents an implementation of the <tt>libjitsi</tt> library which is
 * stand-alone and does not utilize OSGi.
 *
 * @author Lyubomir Marinov
 */
public class LibJitsiImpl
    extends LibJitsi
{
    /**
     * The <tt>Logger</tt> used by the <tt>LibJitsiImpl</tt> class and its
     * instances.
     */
    private static final Logger LOGGER = Logger.getLogger(LibJitsiImpl.class);

    /**
     * The service instances associated with this implementation of the
     * <tt>libjitsi</tt> library mapped by their respective type/class names.
     */
    private final Map<String, ServiceLock> services = new HashMap<>();

    /**
     * Initializes a new <tt>LibJitsiImpl</tt> instance.
     */
    public LibJitsiImpl()
    {
        // The AudioNotifierService implementation uses a non-standard package
        // location so work around it.
        String key
            = "org.jitsi.service.audionotifier.AudioNotifierService";
        String value = System.getProperty(key);

        if ((value == null) || (value.length() == 0))
        {
            System.setProperty(
                    key,
                    "org.jitsi.impl.neomedia.notify.AudioNotifierServiceImpl");
        }
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
    @Override
    protected <T> T getService(Class<T> serviceClass)
    {
        String className = serviceClass.getName();
        ServiceLock lock;

        synchronized (services)
        {
            lock = services.get(className);
            if (lock == null)
            {
                // Do not allow concurrent and/or repeating requests to create
                // an instance of the specified serviceClass.
                lock = new ServiceLock();
                services.put(className, lock);
            }
        }

        return lock.getService(className, serviceClass);
    }

    /**
     * Associates an OSGi service {@code Object} and its initialization with a
     * {@code Lock} in order to prevent concurrent, repeating, and/or recursive
     * initializations of one and the same OSGi service {@code Class}.
     */
    private static class ServiceLock
    {
        /**
         * The {@code Lock} associated with {@link #_service}.
         */
        private final ReentrantLock _lock = new ReentrantLock();

        /**
         * The OSGi service {@code Object} associated with {@link #_lock}.
         */
        private Object _service;

        /**
         * Gets the OSGi service {@code Object} associated with {@link #_lock}.
         *
         * @param clazz the runtime type of the returned value
         * @return the OSGi service {@code Object} associated with
         * {@link #_lock}
         */
        @SuppressWarnings("unchecked")
        public <T> T getService(String className, Class<T> clazz)
        {
            T t;
            // Do not allow repeating/recursive requests to create multiple
            // instances of the specified clazz.
            boolean initializeService = !_lock.isHeldByCurrentThread();

            _lock.lock();
            try
            {
                t = (T) _service;
                if (t == null && initializeService)
                    _service = t = initializeService(className, clazz);
            }
            finally
            {
                _lock.unlock();
            }
            return t;
        }

        /**
         * Initializes a new instance of a specific OSGi service {@code Class}.
         *
         * @param <T>
         * @param className the {@code name} of {@code clazz} which has already
         * been retrieved from {@code clazz}
         * @param clazz the {@code Class} of the OSGi service instance to be
         * initialized
         * @return a new instance of the specified OSGi service {@code clazz}
         */
        private static <T> T initializeService(String className, Class<T> clazz)
        {
            // Allow the service implementation class names to be specified as
            // System properties akin to standard Java class factory names.
            String implClassName = System.getProperty(className);
            boolean suppressClassNotFoundException = false;

            if (implClassName == null || implClassName.length() == 0)
            {
                implClassName
                    = className.replace(".service.", ".impl.").concat("Impl");
                // Nobody has explicitly mentioned implClassName, we have just
                // made it up. If it turns out that it cannot be found, do not
                // log the resulting ClassNotFountException in order to not
                // stress the developers and/or the users.
                suppressClassNotFoundException = true;
            }

            Class<?> implClass = null;
            Throwable exception = null;

            try
            {
                implClass = Class.forName(implClassName);
            }
            catch (ClassNotFoundException cnfe)
            {
                if (!suppressClassNotFoundException)
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

            T service = null;

            if (implClass != null && clazz.isAssignableFrom(implClass))
            {
                try
                {
                    @SuppressWarnings("unchecked")
                    T t = (T) implClass.newInstance();

                    service = t;
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                    {
                        throw (ThreadDeath) t;
                    }
                    else
                    {
                        exception = t;
                        if (t instanceof InterruptedException)
                            Thread.currentThread().interrupt();
                    }
                }
            }

            if (exception != null && LOGGER.isInfoEnabled())
            {
                LOGGER.info("Failed to initialize service implementation "
                            + implClassName + ".",
                        exception);
            }

            return service;
        }
    }
}
