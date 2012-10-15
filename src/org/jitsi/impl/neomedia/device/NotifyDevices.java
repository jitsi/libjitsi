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
 * Manages the list of active (currently pluged-in) notify devices and manages
 * user preferences between all known devices (previously and actually
 * plugged-in).
 *
 * @author Vincent Lucas
 */
public class NotifyDevices
    extends PlaybackDevices
{
    /**
     * The flag nuber if the notify device is null.
     */
    protected static final int FLAG_DEVICE_IS_NULL = 2;

    /**
     * The property of the notify devices.
     */
    public static final String PROP_DEVICE = "notifyDevice";

    /**
     * Initializes the notify device list managment.
     *
     * @param audioSystem The audio system managing this notify device list.
     */
    public NotifyDevices(AudioSystem audioSystem)
    {
        super(audioSystem);
    }

    /**
     * Returns the flag nuber if the capture device is null.
     *
     * @return The flag nuber if the capture device is null.
     */
    protected int getFlagDeviceIsNull()
    {
        return FLAG_DEVICE_IS_NULL;
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
