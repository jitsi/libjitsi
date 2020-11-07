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
package org.jitsi.impl.neomedia.device;


/**
 * Manages the list of active (currently plugged-in) playback devices and
 * manages user preferences between all known devices (previously and actually
 * plugged-in).
 *
 * @author Vincent Lucas
 */
public class PlaybackDevices
    extends Devices
{
    /**
     * The property of the playback devices.
     */
    public static final String PROP_DEVICE = "playbackDevice";

    /**
     * Initializes the playback device list management.
     *
     * @param audioSystem The audio system managing this playback device list.
     */
    public PlaybackDevices(AudioSystem audioSystem)
    {
        super(audioSystem);
    }

    /**
     * Returns the property of the capture devices.
     *
     * @return The property of the capture devices.
     */
    @Override
    protected String getPropDevice()
    {
        return PROP_DEVICE;
    }
}
