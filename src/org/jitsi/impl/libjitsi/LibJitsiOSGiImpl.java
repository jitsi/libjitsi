/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.libjitsi;

import org.jitsi.service.libjitsi.*;
import org.osgi.framework.*;

/**
 * Represents an implementation of the <tt>libjitsi</tt> library which utilizes
 * OSGi.
 *
 * @author Lyubomir Marinov
 */
public class LibJitsiOSGiImpl
    extends LibJitsiImpl
{
    /**
     * The <tt>BundleContext</tt> discovered by this instance during its
     * initialization and used to look for registered services.
     */
    private final BundleContext bundleContext;

    /**
     * Initializes a new <tt>LibJitsiOSGiImpl</tt> instance with the
     * <tt>BundleContext</tt> of the <tt>Bundle</tt> which has loaded the
     * <tt>LibJitsi</tt> class.
     */
    public LibJitsiOSGiImpl()
    {
        Bundle bundle = FrameworkUtil.getBundle(LibJitsi.class);

        if (bundle == null)
            throw new IllegalStateException("FrameworkUtil.getBundle");
        else
        {
            BundleContext bundleContext = bundle.getBundleContext();

            if (bundleContext == null)
                throw new IllegalStateException("Bundle.getBundleContext");
            else
                this.bundleContext = bundleContext;
        }
    }

    /**
     * Initializes a new <tt>LibJitsiOSGiImpl</tt> instance with a specific
     * <tt>BundleContext</tt>.
     *
     * @param bundleContext the <tt>BundleContext</tt> to be used by the new
     * instance to look for registered services
     */
    public LibJitsiOSGiImpl(BundleContext bundleContext)
    {
        if (bundleContext == null)
            throw new NullPointerException("bundleContext");
        else
            this.bundleContext = bundleContext;
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
    protected <T> T getService(Class<T> serviceClass)
    {
        @SuppressWarnings("rawtypes")
        ServiceReference serviceReference
            = bundleContext.getServiceReference(serviceClass.getName());
        @SuppressWarnings("unchecked")
        T service
            = (serviceReference == null)
                ? null
                : (T) bundleContext.getService(serviceReference);

        if (service == null)
            service = super.getService(serviceClass);

        return service;
    }
}
