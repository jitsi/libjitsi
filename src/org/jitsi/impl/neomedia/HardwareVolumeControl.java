/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implementation of VolumeControl which uses system sound architecture (MacOsX
 * or Windows CoreAudio) to change input/output hardware volume.
 *
 * @author Vincent Lucas
 */
public class HardwareVolumeControl
    extends BasicVolumeControl
{
    /**
     * The <tt>Logger</tt> used by the <tt>HarwareVolumeControl</tt>
     * class and its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(HardwareVolumeControl.class);

    /**
     * The media service implementation.
     */
    MediaServiceImpl mediaServiceImpl = null;

    /**
     * The maximal power level used for hardware amplification. Over this value
     * software amplification is used.
     */
    private static final float MAX_HARDWARE_POWER = 1.0F;

    /**
     * Creates volume control instance and initializes initial level value
     * if stored in the configuration service.
     *
     * @param mediaServiceImpl The media service implementation.
     * @param volumeLevelConfigurationPropertyName the name of the configuration
     * property which specifies the value of the volume level of the new
     * instance
     */
    public HardwareVolumeControl(
        MediaServiceImpl mediaServiceImpl,
        String volumeLevelConfigurationPropertyName)
    {
        super(volumeLevelConfigurationPropertyName);
        this.mediaServiceImpl = mediaServiceImpl;

        // Gets the device volume (an error use the default volume).
        this.volumeLevel = getDefaultVolumeLevel();
        float volume = this.getVolume();
        if(volume != -1)
        {
            this.volumeLevel = volume;
        }
    }

    /**
     * Returns the default volume level.
     *
     * @return The default volume level.
     */
    protected static float getDefaultVolumeLevel()
    {
        // By default set the microphone at the middle of its hardware
        // sensibility range.
        return MAX_HARDWARE_POWER / 2;
    }

    /**
     * Returns the reference volume level for computing the gain.
     *
     * @return The reference volume level for computing the gain.
     */
    protected static float getGainReferenceLevel()
    {
        // Starts to activate the gain (software amplification), only once the
        // microphone sensibility is sets to its maximum (hardware
        // amplification).
        return MAX_HARDWARE_POWER;
    }

    /**
     * Modifies the hardware microphone sensibility (hardware amplification).
     */
    protected void updateHardwareVolume()
    {
        // Gets the selected input dvice UID.
        String deviceUID = getCaptureDeviceUID();

        // Computes the hardware volume.
        float jitsiHarwareVolumeFactor = MAX_VOLUME_LEVEL / MAX_HARDWARE_POWER;
        float hardwareVolumeLevel = this.volumeLevel * jitsiHarwareVolumeFactor;
        if(hardwareVolumeLevel > 1.0F)
        {
            hardwareVolumeLevel = 1.0F;
        }

        // Changes the input volume of the capture device.
        if(this.setInputDeviceVolume(
                    deviceUID,
                    hardwareVolumeLevel) != 0)
        {
            logger.debug("Could not change hardware input device level");
        }
    }

    /**
     * Returns the selected input device UID.
     *
     * @return The selected input device UID. Or null if not found.
     */
    protected String getCaptureDeviceUID()
    {
        AudioSystem audioSystem
            = mediaServiceImpl.getDeviceConfiguration().getAudioSystem();
        CaptureDeviceInfo2 captureDevice
            = (audioSystem == null)
                ? null
                : audioSystem.getSelectedDevice(AudioSystem.DataFlow.CAPTURE);

        return (captureDevice == null) ? null : captureDevice.getUID();
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
        if(deviceUID == null)
        {
            return -1;
        }

        if(CoreAudioDevice.initDevices() == -1)
        {
            CoreAudioDevice.freeDevices();
            logger.debug(
                    "Could not initialize CoreAudio input devices");
            return -1;
        }
        // Change the input volume of the capture device.
        if(CoreAudioDevice.setInputDeviceVolume(deviceUID, volume) != 0)
        {
            CoreAudioDevice.freeDevices();
            logger.debug(
                    "Could not change CoreAudio input device level");
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

        if(deviceUID == null)
        {
            return -1;
        }

        if(CoreAudioDevice.initDevices() == -1)
        {
            CoreAudioDevice.freeDevices();
            logger.debug(
                    "Could not initialize CoreAudio input devices");
            return -1;
        }
        // Get the input volume of the capture device.
        if((volume = CoreAudioDevice.getInputDeviceVolume(deviceUID))
                == -1)
        {
            CoreAudioDevice.freeDevices();
            logger.debug(
                    "Could not get CoreAudio input device level");
            return -1;
        }
        CoreAudioDevice.freeDevices();

        return volume;
    }

    /**
     * Current volume value.
     *
     * @return the current volume level.
     *
     * @see org.jitsi.service.neomedia.VolumeControl
     */
    public float getVolume()
    {
        String deviceUID = getCaptureDeviceUID();
        return this.getInputDeviceVolume(deviceUID);
    }
}
