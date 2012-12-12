/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.wincoreaudio;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * Implementation of VolumeControl which uses Windows sound architecture
 * CoreAudio to change input/output hardware volume.
 *
 * @author Vincent Lucas
 */
public class CoreAudioVolumeControl
    extends AbstractHardwareVolumeControl
{
    /**
     * The <tt>Logger</tt> used by the <tt>CoreAudioVolumeControl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(CoreAudioVolumeControl.class);

    /**
     * Creates volume control instance and initializes initial level value
     * if stored in the configuration service.
     *
     * @param mediaServiceImpl The media service implementation.
     * @param volumeLevelConfigurationPropertyName the name of the configuration
     * property which specifies the value of the volume level of the new
     * instance
     */
    public CoreAudioVolumeControl(
        MediaServiceImpl mediaServiceImpl,
        String volumeLevelConfigurationPropertyName)
    {
        super(mediaServiceImpl, volumeLevelConfigurationPropertyName);
    }

    /**
     * Changes the device volume via the system API.
     *
     * @param deviceUID The device ID.
     * @param volume The volume requested.
     *
     * @return 0 if everything works fine.
     */
    protected int setInputDeviceVolume(String deviceUID, float volume)
    {
        if(CoreAudioDevice.initDevices() == -1)
        {
            CoreAudioDevice.freeDevices();
            logger.debug(
                    "Could not initialize Windows CoreAudio input devices");
            return -1;
        }
        // Change the input volume of the capture device.
        if(CoreAudioDevice.setInputDeviceVolume(deviceUID, volume) != 0)
        {
            CoreAudioDevice.freeDevices();
            logger.debug(
                    "Could not change Windows CoreAudio input device level");
            return -1;
        }
        CoreAudioDevice.freeDevices();

        return 0;
    }

    /**
     * Returns the device volume via the system API.
     *
     * @param deviceUID The device ID.
     *
     * @Return A scalar value between 0 and 1 if everything works fine. -1 if an
     * error occurred.
     */
    protected float getInputDeviceVolume(String deviceUID)
    {
        float volume;

        if(CoreAudioDevice.initDevices() == -1)
        {
            CoreAudioDevice.freeDevices();
            logger.debug(
                    "Could not initialize Windows CoreAudio input devices");
            return -1;
        }
        // Get the input volume of the capture device.
        if((volume = CoreAudioDevice.getInputDeviceVolume(deviceUID))
                == -1)
        {
            CoreAudioDevice.freeDevices();
            logger.debug(
                    "Could not get Windows CoreAudio input device level");
            return -1;
        }
        CoreAudioDevice.freeDevices();

        return volume;
    }
}
