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
