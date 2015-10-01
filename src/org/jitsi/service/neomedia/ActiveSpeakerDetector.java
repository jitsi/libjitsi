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
package org.jitsi.service.neomedia;

import org.jitsi.service.neomedia.event.*;

/**
 * Represents an algorithm for the detection/identification of the
 * active/dominant speaker/participant/endpoint/stream in a multipoint
 * conference.
 * <p>
 * Implementations of <tt>ActiveSpeakerDetector</tt> get notified about the
 * (current) audio levels of multiple audio streams (identified by their
 * synchronization source identifiers/SSRCs) via calls to
 * {@link #levelChanged(long, int)} and determine/identify which stream is
 * dominant/active (in terms of speech). When the active stream changes,
 * listeners registered via
 * {@link #addActiveSpeakerChangedListener(ActiveSpeakerChangedListener)} are
 * notified.
 * </p>
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 */
public interface ActiveSpeakerDetector
{
    /**
     * Adds a listener to be notified by this active speaker detector when the
     * active stream changes.
     *
     * @param listener the listener to register with this instance for
     * notifications about changes of the active speaker
     */
    public void addActiveSpeakerChangedListener(
            ActiveSpeakerChangedListener listener);

    /**
     * Notifies this <tt>ActiveSpeakerDetector</tt> about the latest/current
     * audio level of a stream/speaker identified by a specific synchronization
     * source identifier/SSRC.
     *
     * @param ssrc the SSRC of the stream/speaker
     * @param level the latest/current audio level of the stream/speaker with
     * the specified <tt>ssrc</tt>
     */
    public void levelChanged(long ssrc, int level);

    /**
     * Removes a listener to no longer be notified by this active speaker
     * detector when the active stream changes.
     *
     * @param listener the listener to unregister with this instance for
     * notifications about changes of the active speaker
     */
    public void removeActiveSpeakerChangedListener(
            ActiveSpeakerChangedListener listener);
}
