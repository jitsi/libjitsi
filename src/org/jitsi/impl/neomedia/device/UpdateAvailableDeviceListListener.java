/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.util.*;

/**
 * Represents a listener which is to be notified before and after an associated
 * <tt>DeviceSystem</tt>'s function to update the list of available devices is
 * invoked.
 *
 * @author Lyubomir Marinov
 */
public interface UpdateAvailableDeviceListListener
    extends EventListener
{
    /**
     * Notifies this listener that the associated <tt>DeviceSystem</tt>'s
     * function to update the list of available devices was invoked.
     *
     * @throws Exception if this implementation encounters an error. Any
     * <tt>Throwable</tt> apart from <tt>ThreadDeath</tt> will be ignored
     * after it is logged for debugging purposes.
     */
    void didUpdateAvailableDeviceList()
        throws Exception;

    /**
     * Notifies this listener that the associated <tt>DeviceSystem</tt>'s
     * function to update the list of available devices will be invoked.
     *
     * @throws Exception if this implementation encounters an error. Any
     * <tt>Throwable</tt> apart from <tt>ThreadDeath</tt> will be ignored
     * after it is logged for debugging purposes.
     */
    void willUpdateAvailableDeviceList()
        throws Exception;
}
