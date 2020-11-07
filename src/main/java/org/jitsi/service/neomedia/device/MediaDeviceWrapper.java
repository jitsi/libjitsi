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
package org.jitsi.service.neomedia.device;

/**
 * Represents a special-purpose <tt>MediaDevice</tt> which is effectively built
 * on top of and forwarding to another <tt>MediaDevice</tt>.
 *
 * @author Lyubomir Marinov
 */
public interface MediaDeviceWrapper
    extends MediaDevice
{
    /**
     * Gets the actual <tt>MediaDevice</tt> which this <tt>MediaDevice</tt> is
     * effectively built on top of and forwarding to.
     *
     * @return the actual <tt>MediaDevice</tt> which this <tt>MediaDevice</tt>
     * is effectively built on top of and forwarding to
     */
    public MediaDevice getWrappedDevice();
}
