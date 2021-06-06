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
package org.jitsi.service.neomedia.recording;

/**
 * An interface that allows handling of <tt>RecorderEvent</tt> instances, such
 * as writing them to disk in some format.
 *
 * @author Boris Grozev
 */
public interface RecorderEventHandler
{
    /**
     * Handle a specific <tt>RecorderEvent</tt>
     * @param ev the event to handle.
     * @return
     */
    public boolean handleEvent(RecorderEvent ev);

    /**
     * Closes the <tt>RecorderEventHandler</tt>.
     */
    public void close();
}
