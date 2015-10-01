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

import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

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
    private static final Logger logger = Logger.getLogger(LibJitsiImpl.class);

    /**
     * The service instances associated with this implementation of the
     * <tt>libjitsi</tt> library mapped by their respective type/class names.
     */
    private final Map<String, Object> services
        = new HashMap<String, Object>();

    /**
     * Initializes a new <tt>LibJitsiImpl</tt> instance.
     */
    public LibJitsiImpl()
    {
        /*
         * The AudioNotifierService implementation uses a non-standard package
         * location so work around it.
         */
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
        String serviceClassName = serviceClass.getName();

        synchronized (services)
        {
            if (services.containsKey(serviceClassName))
            {
                @SuppressWarnings("unchecked")
                T service = (T) services.get(serviceClassName);

                return service;
            }
            else
            {
                /*
                 * Do not allow concurrent and/or repeating requests to create
                 * an instance of the specified serviceClass.
                 */
                services.put(serviceClassName, null);
            }
        }

        /*
         * Allow the service implementation class names to be specified as
         * System properties akin to standard Java class factory names.
         */
        String serviceImplClassName = System.getProperty(serviceClassName);
        boolean suppressClassNotFoundException = false;

        if ((serviceImplClassName == null)
                || (serviceImplClassName.length() == 0))
        {
            serviceImplClassName
                = serviceClassName
                    .replace(".service.", ".impl.")
                        .concat("Impl");
            /*
             * Nobody has explicitly mentioned serviceImplClassName, we have
             * just made it up. If it turns out that it cannot be found, do not
             * log the resulting ClassNotFountException in order to not stress
             * the developers and/or the users. 
             */
            suppressClassNotFoundException = true;
        }

        Class<?> serviceImplClass = null;
        Throwable exception = null;

        try
        {
            serviceImplClass = Class.forName(serviceImplClassName);
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

        if ((serviceImplClass != null)
                && serviceClass.isAssignableFrom(serviceImplClass))
        {
            try
            {
                @SuppressWarnings("unchecked")
                T t = (T) serviceImplClass.newInstance();

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

        if (exception == null)
        {
            if (service != null)
            {
                synchronized (services)
                {
                    services.put(serviceClassName, service);
                }
            }
        }
        else if (logger.isInfoEnabled())
        {
            logger.info(
                    "Failed to initialize service implementation "
                        + serviceImplClassName
                        + ". Will continue without it.", exception);
        }

        return service;
    }
}
