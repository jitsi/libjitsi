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
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import java.util.*;

import org.jitsi.utils.logging.*;

/**
 * Implements the Java counterpart of an <tt>IMMNotificationClient</tt> instance
 * statically allocated by the native counterpart of {@link WASAPI} and
 * automatically registered with all <tt>IMMDeviceEnumerator</tt> instances.
 * Invocations of methods on the <tt>IMMNotificationClient</tt> instance by
 * Windows Audio Session API (WASAPI) are forwarded by <tt>WASAPI</tt> to the
 * respective static methods of the <tt>MMNotificationClient</tt> class.
 *
 * @author Lyubomir Marinov
 */
public class MMNotificationClient
{
    /**
     * The <tt>Logger</tt> used by the <tt>MMNotificationClient</tt> class to
     * log debug information.
     */
    private static final Logger logger
        = Logger.getLogger(MMNotificationClient.class);

    /**
     * The set of <tt>IMMNotificationClient</tt>s to be notified when an audio
     * endpoint device is added or removed, when the state or properties of an
     * endpoint device change, or when there is a change in the default role
     * assigned to an endpoint device.
     */
    private static Collection<IMMNotificationClient> pNotifySet;

    public static void OnDefaultDeviceChanged(
            int flow,
            int role,
            String pwstrDefaultDevice)
    {
        // TODO Auto-generated method stub
    }

    public static void OnDeviceAdded(String pwstrDeviceId)
    {
        Iterable<IMMNotificationClient> pNotifySet;

        synchronized (MMNotificationClient.class)
        {
            pNotifySet = MMNotificationClient.pNotifySet;
        }

        if (pNotifySet != null)
        {
            for (IMMNotificationClient pNotify : pNotifySet)
            {
                try
                {
                    pNotify.OnDeviceAdded(pwstrDeviceId);
                }
                catch (Throwable t)
                {
                    /*
                     * XXX The native counterpart of MMNotificationClient which
                     * normally invokes the method will eventually call
                     * ExceptionClear anyway.
                     */
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    else
                    {
                        logger.error(
                                "An IMMNotificationClient failed to normally"
                                    + " complete the handling of an"
                                    + " OnDeviceAdded notification.",
                                t);
                    }
                }
            }
        }
    }

    public static void OnDeviceRemoved(String pwstrDeviceId)
    {
        Iterable<IMMNotificationClient> pNotifySet;

        synchronized (MMNotificationClient.class)
        {
            pNotifySet = MMNotificationClient.pNotifySet;
        }

        if (pNotifySet != null)
        {
            for (IMMNotificationClient pNotify : pNotifySet)
            {
                try
                {
                    pNotify.OnDeviceRemoved(pwstrDeviceId);
                }
                catch (Throwable t)
                {
                    /*
                     * XXX The native counterpart of MMNotificationClient which
                     * normally invokes the method will eventually call
                     * ExceptionClear anyway.
                     */
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    else
                    {
                        logger.error(
                                "An IMMNotificationClient failed to normally"
                                    + " complete the handling of an"
                                    + " OnDeviceRemoved notification.",
                                t);
                    }
                }
            }
        }
    }

    public static void OnDeviceStateChanged(
            String pwstrDeviceId,
            int dwNewState)
    {
        Iterable<IMMNotificationClient> pNotifySet;

        synchronized (MMNotificationClient.class)
        {
            pNotifySet = MMNotificationClient.pNotifySet;
        }

        if (pNotifySet != null)
        {
            for (IMMNotificationClient pNotify : pNotifySet)
            {
                try
                {
                    pNotify.OnDeviceStateChanged(pwstrDeviceId, dwNewState);
                }
                catch (Throwable t)
                {
                    /*
                     * XXX The native counterpart of MMNotificationClient which
                     * normally invokes the method will eventually call
                     * ExceptionClear anyway.
                     */
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    else
                    {
                        logger.error(
                                "An IMMNotificationClient failed to normally"
                                    + " complete the handling of an"
                                    + " OnDeviceStateChanged notification.",
                                t);
                    }
                }
            }
        }
    }

    public static void OnPropertyValueChanged(String pwstrDeviceId, long key)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Registers a specific <tt>IMMNotificationClient</tt> to be notified when
     * an audio endpoint device is added or removed, when the state or
     * properties of an endpoint device change, or when there is a change in the
     * default role assigned to an endpoint device.
     *
     * @param pNotify the <tt>IMMNotificationClient</tt> to register
     */
    public static void RegisterEndpointNotificationCallback(
            IMMNotificationClient pNotify)
    {
        if (pNotify == null)
            throw new NullPointerException("pNotify");

        synchronized (MMNotificationClient.class)
        {
            Collection<IMMNotificationClient> newPNotifySet;

            if (pNotifySet == null)
                newPNotifySet = new ArrayList<IMMNotificationClient>();
            else if (pNotifySet.contains(pNotify))
                return;
            else
            {
                newPNotifySet
                    = new ArrayList<IMMNotificationClient>(
                            pNotifySet.size() + 1);
                newPNotifySet.addAll(pNotifySet);
            }
            if (newPNotifySet.add(pNotify))
                pNotifySet = newPNotifySet;
        }
    }

    /**
     * Deletes the registration of a specific <tt>IMMNotificationClient</tt>
     * that the client registered in a previous call to
     * {@link #RegisterEndpointNotificationCallback(IMMNotificationClient)}.
     *
     * @param pNotify the <tt>IMMNotificationClient</tt> to delete the
     * registration of
     */
    public static void UnregisterEndpointNotificationCallback(
            IMMNotificationClient pNotify)
    {
        if (pNotify == null)
            throw new NullPointerException("pNotify");

        synchronized (MMNotificationClient.class)
        {
            /*
             * XXX The implementation bellow is hardly optimal because it
             * consecutively employs the contains and remove Collection methods
             * each of which performs a linear search for one and the same
             * element in effectively the same set of elements. Anyway, the
             * unregistering of IMMNotificationClients will very occur much less
             * often than notification deliveries.
             */
            if ((pNotifySet != null) && pNotifySet.contains(pNotify))
            {
                if (pNotifySet.size() == 1)
                    pNotifySet = null;
                else
                {
                    Collection<IMMNotificationClient> newPNotifySet
                        = new ArrayList<IMMNotificationClient>(pNotifySet);

                    if (newPNotifySet.remove(pNotify))
                        pNotifySet = newPNotifySet;
                }
            }
        }
    }

    /**
     * Prevents the initialization of <tt>MMNotificationClient</tt> instances.
     */
    private MMNotificationClient() {}
}
