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
package org.jitsi.impl.neomedia;

import java.net.*;

/**
 * Represents a listener of a source of a type of events involving/caused by
 * <tt>DatagramPacket</tt>s.
 *
 * @author Lyubomir Marinov
 */
public interface DatagramPacketListener
{
    /**
     * Notifies this listener about an event fired by a specific <tt>source</tt>
     * and involving/caused by a specific <tt>DatagramPacket</tt>.
     *
     * @param source the source of/which fired the event
     * @param p the <tt>DatagramPacket</tt> which caused/is involved in the
     * event fired by <tt>source</tt>
     */
    void update(Object source, DatagramPacket p);
}
