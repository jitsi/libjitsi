/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;

/**
 * Manages the list of active (currently pluged-in) capture devices and manages
 * user preferences between all known devices (previously and actually
 * plugged-in).
 *
 * @author Vincent Lucas
 */
public class CaptureDevices
    extends Devices
{
    /**
     * The flag nuber if the capture device is null.
     */
    protected static final int FLAG_DEVICE_IS_NULL = 1;

    /**
     * The property of the capture devices.
     */
    public static final String PROP_DEVICE = "captureDevice";

    /**
     * Initializes the capture device list managment.
     *
     * @param audioSystem The audio system managing this capture device list.
     */
    public CaptureDevices(AudioSystem audioSystem)
    {
        super(audioSystem);
    }

    /**
     * Returns the list of the active devices.
     *
     * @param locator The string representation of the locator.
     *
     * @return The list of the active devices.
     */
    public List<CaptureDeviceInfo> getDevices(String locator)
    {
        MediaServiceImpl mediaServiceImpl
            = NeomediaServiceUtils.getMediaServiceImpl();
        DeviceConfiguration deviceConfiguration = (mediaServiceImpl == null)
                ? null
                : mediaServiceImpl.getDeviceConfiguration();
        List<CaptureDeviceInfo> deviceList;

        if (deviceConfiguration == null)
        {
            /*
             * XXX The initialization of MediaServiceImpl is very complex so it
             * is wise to not reference it at the early stage of its
             * initialization. The same goes for DeviceConfiguration. If for
             * some reason one of the two is not available at this time, just
             * fall back go something that makes sense.
             */
            @SuppressWarnings("unchecked")
            Vector<CaptureDeviceInfo> audioCaptureDeviceInfos
                = CaptureDeviceManager.getDeviceList(
                        new AudioFormat(AudioFormat.LINEAR, -1, 16, -1));

            deviceList = audioCaptureDeviceInfos;
        }
        else
        {
            deviceList = deviceConfiguration.getAvailableAudioCaptureDevices();
        }

        return DeviceSystem.filterDeviceListByLocatorProtocol(
                deviceList,
                locator);
    }

    /**
     * Sets the list of the active devices.
     *
     * @param activeDevices The list of the active devices.
     */
    public void setActiveDevices(List<CaptureDeviceInfo> activeDevices)
    {
        if(activeDevices != null)
        {
            boolean commit = false;

            for (CaptureDeviceInfo activeDevice : activeDevices)
            {
                CaptureDeviceManager.addDevice(activeDevice);
                commit = true;
            }
            if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad())
            {
                try
                {
                    CaptureDeviceManager.commit();
                }
                catch (IOException ioe)
                {
                    // Whatever.
                }
            }
        }
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
