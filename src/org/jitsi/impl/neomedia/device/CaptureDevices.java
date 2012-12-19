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
 * Manages the list of active (currently plugged-in) capture devices and manages
 * user preferences between all known devices (previously and actually
 * plugged-in).
 *
 * @author Vincent Lucas
 */
public class CaptureDevices
    extends Devices
{
    /**
     * The property of the capture devices.
     */
    public static final String PROP_DEVICE = "captureDevice";

    /**
     * The list of active (actually plugged-in) capture devices.
     */
    private List<ExtendedCaptureDeviceInfo> activeCaptureDevices;

    /**
     * Initializes the capture device list management.
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
     * @return The list of the active devices.
     */
    public List<ExtendedCaptureDeviceInfo> getDevices()
    {
        List<ExtendedCaptureDeviceInfo> devices;

        if(activeCaptureDevices == null)
            devices = Collections.emptyList();
        else
        {
            devices
                = new ArrayList<ExtendedCaptureDeviceInfo>(
                        activeCaptureDevices.size());

            Format format = new AudioFormat(AudioFormat.LINEAR, -1, 16, -1);

            for(ExtendedCaptureDeviceInfo device: activeCaptureDevices)
            {
                for(Format deviceFormat : device.getFormats())
                {
                    if(deviceFormat.matches(format))
                    {
                        devices.add(device);
                        break;
                    }
                }
            }
        }

        return devices;
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

    /**
     * Sets the list of the active devices.
     *
     * @param activeDevices The list of the active devices.
     */
    public void setActiveDevices(List<ExtendedCaptureDeviceInfo> activeDevices)
    {
        if(activeDevices == null)
            activeCaptureDevices = null;
        else
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

            activeCaptureDevices
                = new ArrayList<ExtendedCaptureDeviceInfo>(activeDevices);
        }
    }
}
