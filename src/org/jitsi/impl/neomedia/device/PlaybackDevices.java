/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.util.*;

import javax.media.*;

/**
 * Manages the list of active (currently pluged-in) playback devices and manages
 * user preferences between all known devices (previously and actually
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
     * The list of active (actually plugged-in) playback devices.
     */
    private List<ExtendedCaptureDeviceInfo> activePlaybackDevices = null;

    /**
     * Initializes the playback device list managment.
     *
     * @param audioSystem The audio system managing this playback device list.
     */
    public PlaybackDevices(AudioSystem audioSystem)
    {
        super(audioSystem);
    }

    /**
     * Returns the list of the active devices.
     *
     * @return The list of the active devices.
     */
    public List<ExtendedCaptureDeviceInfo> getDevices()
    {
        return (this.activePlaybackDevices == null)
                ? null
                : new ArrayList<ExtendedCaptureDeviceInfo>(
                        this.activePlaybackDevices);
    }

    /**
     * Sets the list of the active devices.
     *
     * @param activeDevices The list of the active devices.
     */
    public void setActiveDevices(List<ExtendedCaptureDeviceInfo> activeDevices)
    {
        this.activePlaybackDevices = (activeDevices == null)
                ? null
                : new ArrayList<ExtendedCaptureDeviceInfo>(activeDevices);
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
