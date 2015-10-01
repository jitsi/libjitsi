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

import java.util.*;

import org.jitsi.service.neomedia.event.*;

/**
 * Provides a base {@link ActiveSpeakerDetector} which aids the implementations
 * of actual algorithms for the detection/identification of the active/dominant
 * speaker in a multipoint conference.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 */
public abstract class AbstractActiveSpeakerDetector
    implements ActiveSpeakerDetector
{
    /**
     * An empty array with element type <tt>ActiveSpeakerChangedListener</tt>.
     * Explicitly defined for the purposes of reducing the total number of
     * unnecessary allocations and the undesired effects of the garbage
     * collector.
     */
    private static final ActiveSpeakerChangedListener[] NO_LISTENERS
        = new ActiveSpeakerChangedListener[0];

    /**
     * The list of listeners to be notified by this detector when the active
     * speaker changes.
     */
    private final List<ActiveSpeakerChangedListener> listeners
        = new LinkedList<ActiveSpeakerChangedListener>();

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified <tt>listener</tt> is
     * <tt>null</tt>
     */
    @Override
    public void addActiveSpeakerChangedListener(
            ActiveSpeakerChangedListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (listeners)
        {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Notifies the <tt>ActiveSpeakerChangedListener</tt>s registered with this
     * instance that the active speaker in multipoint conference associated with
     * this instance has changed and is identified by a specific synchronization
     * source identifier/SSRC.
     *
     * @param ssrc the synchronization source identifier/SSRC of the active
     * speaker in the multipoint conference
     */
    protected void fireActiveSpeakerChanged(long ssrc)
    {
        ActiveSpeakerChangedListener[] listeners
            = getActiveSpeakerChangedListeners();

        for (ActiveSpeakerChangedListener listener : listeners)
            listener.activeSpeakerChanged(ssrc);
    }

    /**
     * Gets the list of listeners to be notified by this detector when the
     * active speaker changes.
     *
     * @return an array of the listeners to be notified by this detector when
     * the active speaker changes. If no such listeners are registered with this
     * instance, an empty array is returned. 
     */
    protected ActiveSpeakerChangedListener[] getActiveSpeakerChangedListeners()
    {
        synchronized (listeners)
        {
            return
                (listeners.size() == 0)
                    ? NO_LISTENERS
                    : listeners.toArray(NO_LISTENERS);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeActiveSpeakerChangedListener(
            ActiveSpeakerChangedListener listener)
    {
        if (listener != null)
        {
            synchronized (listeners)
            {
                listeners.remove(listener);
            }
        }
    }
}
