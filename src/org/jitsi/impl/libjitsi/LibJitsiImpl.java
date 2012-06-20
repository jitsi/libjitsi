/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.libjitsi;

import java.util.*;

import org.jitsi.service.libjitsi.*;

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
     * The service instances associated with this implementation of the
     * <tt>libjitsi</tt> library mapped by their respective type/class names.
     */
    private final Map<String, Object> services
        = new HashMap<String, Object>();

    /**
     * Gets a service of a specific type associated with this implementation of
     * the <tt>libjitsi</tt> library.
     *
     * @param serviceClass the type of the service to be retrieved
     * @return a service of the specified type if there is such an association
     * known to this implementation of the <tt>libjitsi</tt> library; otherwise,
     * <tt>null</tt>
     */
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

        Class<?> serviceImplClass = null;
        String serviceImplClassName
            = serviceClassName.replace(".service.", ".impl.").concat("Impl");
        Throwable exception = null;

        try
        {
            serviceImplClass = Class.forName(serviceImplClassName);
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
                    throw (ThreadDeath) t;
                else
                    exception = t;
            }
        }

        if (exception == null)
        {
            synchronized (services)
            {
                if (service != null)
                    services.put(serviceClassName, service);
            }
        }
        else
            exception.printStackTrace(System.err);

        return service;
    }
}
