/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
