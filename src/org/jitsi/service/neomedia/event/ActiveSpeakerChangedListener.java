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
 * Implementing classes can be notified about changes to the 'active' stream
 * (identified by its SSRC) using {@link #activeSpeakerChanged(long)}.
 *
 * @author Boris Grozev
 */
public interface ActiveSpeakerChangedListener
{
    /**
     * Notifies this listener that the active/dominant stream/speaker has been
     * changed to one identified by a specific synchronization source
     * identifier/SSRC.
     *
     * @param ssrc the SSRC of the latest/current active/dominant
     * stream/speaker
     */
    public void activeSpeakerChanged(long ssrc);
}
