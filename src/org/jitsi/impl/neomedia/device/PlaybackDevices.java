/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
    protected String getPropDevice()
    {
        return PROP_DEVICE;
    }
}
