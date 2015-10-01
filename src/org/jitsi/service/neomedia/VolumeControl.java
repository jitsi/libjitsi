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
 * Control for volume level in (neo)media service.
 *
 * @author Damian Minkov
 */
public interface VolumeControl
{
    /**
     * The name of the configuration property which specifies the volume level
     * of audio input.
     */
    public final static String CAPTURE_VOLUME_LEVEL_PROPERTY_NAME
        = "net.java.sip.communicator.service.media.CAPTURE_VOLUME_LEVEL";

    /**
     * The name of the configuration property which specifies the volume level
     * of audio output.
     */
    public final static String PLAYBACK_VOLUME_LEVEL_PROPERTY_NAME
        = "net.java.sip.communicator.service.media.PLAYBACK_VOLUME_LEVEL";

    /**
     * Adds a <tt>VolumeChangeListener</tt> to be informed about changes in the
     * volume level of this instance.
     *
     * @param listener the <tt>VolumeChangeListener</tt> to be informed about
     * changes in the volume level of this instance
     */
    public void addVolumeChangeListener(VolumeChangeListener listener);

    /**
     * Returns the maximum allowed volume value/level.
     *
     * @return the maximum allowed volume value/level
     */
    public float getMaxValue();

    /**
     * Returns the minimum allowed volume value/level.
     *
     * @return the minimum allowed volume value/level
     */
    public float getMinValue();

    /**
     * Get mute state of sound playback.
     *
     * @return mute state of sound playback.
     */
    public boolean getMute();

    /**
     * Gets the current volume value/level.
     *
     * @return the current volume value/level
     */
    public float getVolume();

    /**
     * Removes a <tt>VolumeChangeListener</tt> to no longer be notified about
     * changes in the volume level of this instance.
     *
     * @param listener the <tt>VolumeChangeListener</tt> to no longer be
     * notified about changes in the volume level of this instance
     */
    public void removeVolumeChangeListener(VolumeChangeListener listener);

    /**
     * Mutes current sound playback.
     *
     * @param mute mutes/unmutes playback.
     */
    public void setMute(boolean mute);

    /**
     * Sets the current volume value/level.
     *
     * @param value the volume value/level to set on this instance
     * @return the actual/current volume value/level set on this instance
     */
    public float setVolume(float value);
}
