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
package org.jitsi.service.neomedia.event;

/**
 * Represents a listener (to be) notified about changes in the volume
 * level/value maintained by a <tt>VolumeControl</tt>.
 *
 * @author Damian Minkov
 */
public interface VolumeChangeListener
{
    /**
     * Notifies this instance that the volume level/value maintained by a source
     * <tt>VolumeControl</tt> (to which this instance has previously been added)
     * has changed.
     *
     * @param volumeChangeEvent a <tt>VolumeChangeEvent</tt> which details the
     * source <tt>VolumeControl</tt> which has fired the notification and the
     * volume level/value
     */
    public void volumeChange(VolumeChangeEvent volumeChangeEvent);
}
