/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.coreaudio;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.util.*;

/**
 * Implementation of VolumeControl which uses MacOSX sound architecture
 * CoreAudio to change input/output hardware volume.
 *
 * @author Vincent Lucas
 */
public class CoreAudioVolumeControl
    extends AbstractVolumeControl
{
    /**
     * The <tt>Logger</tt> used by the <tt>CoreAudioVolumeControl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(CoreAudioVolumeControl.class);

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
    public CoreAudioVolumeControl(
        MediaServiceImpl mediaServiceImpl,
        String volumeLevelConfigurationPropertyName)
    {
        super(volumeLevelConfigurationPropertyName);
        this.mediaServiceImpl = mediaServiceImpl;
        updateHardwareVolume();
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
        AudioSystem audioSystem
            = mediaServiceImpl.getDeviceConfiguration().getAudioSystem();
        ExtendedCaptureDeviceInfo captureDevice = (audioSystem == null)
            ? null
            : audioSystem.getDevice(AudioSystem.CAPTURE_INDEX);
        String deviceUID = captureDevice.getUID();

        // Computes the hardware volume.
        float jitsiHarwareVolumeFactor = MAX_VOLUME_LEVEL / MAX_HARDWARE_POWER;
        float hardwareVolumeLevel = this.volumeLevel * jitsiHarwareVolumeFactor;
        if(hardwareVolumeLevel > 1.0F)
        {
            hardwareVolumeLevel = 1.0F;
        }

        // Changes the input volume of the capture device.
        if(CoreAudioDevice.setInputDeviceVolume(
                    deviceUID,
                    hardwareVolumeLevel) != 0)
        {
            logger.debug("Could not change CoreAudio input device level");
        }
    }
}
