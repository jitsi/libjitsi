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

import java.util.*;

import org.jitsi.service.neomedia.*;

/**
 * Represents the event fired when playback volume value has changed.
 *
 * @author Damian Minkov
 */
public class VolumeChangeEvent
    extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The volume level.
     */
    private final float level;

    /**
     * The indicator which determines whether the volume is muted.
     */
    private final boolean mute;

    /**
     * Initializes a new <tt>VolumeChangeEvent</tt> which is to notify about a
     * specific volume level and its mute state.
     *
     * @param source the <tt>VolumeControl</tt> which is the source of the
     * change
     * @param level the volume level
     * @param mute <tt>true</tt> if the volume is muted; otherwise,
     * <tt>false</tt>
     * @throws IllegalArgumentException if source is <tt>null</tt>
     */
    public VolumeChangeEvent(VolumeControl source, float level, boolean mute)
    {
        super(source);

        this.level = level;
        this.mute = mute;
    }

    /**
     * Gets the <tt>VolumeControl</tt> which is the source of the change
     * notified about by this <tt>VolumeChangeEvent</tt>.
     *
     * @return the <tt>VolumeControl</tt> which is the source of the change
     * notified about by this <tt>VolumeChangeEvent</tt>
     */
    public VolumeControl getSourceVolumeControl()
    {
        return (VolumeControl) getSource();
    }

    /**
     * Gets the volume level notified about by this <tt>VolumeChangeEvent</tt>.
     *
     * @return the volume level notified about by this
     * <tt>VolumeChangeEvent</tt>
     */
    public float getLevel()
    {
        return level;
    }

    /**
     * Gets the indicator which determines whether the volume is muted.
     *
     * @return <tt>true</tt> if the volume is muted; otherwise, <tt>false</tt>
     */
    public boolean getMute()
    {
        return mute;
    }
}
