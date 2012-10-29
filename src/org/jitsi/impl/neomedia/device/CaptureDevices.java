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
     * The property of the capture devices.
     */
    public static final String PROP_DEVICE = "captureDevice";

    /**
     * The list of active (actually plugged-in) capture devices.
     */
    private List<ExtendedCaptureDeviceInfo> activeCaptureDevices = null;

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
     * @return The list of the active devices.
     */
    public List<ExtendedCaptureDeviceInfo> getDevices()
    {
        Format[] formats;
        Format format = new AudioFormat(AudioFormat.LINEAR, -1, 16, -1);
        List<ExtendedCaptureDeviceInfo> devices = null;

        if(this.activeCaptureDevices != null)
        {
            devices = new ArrayList<ExtendedCaptureDeviceInfo>(
                    this.activeCaptureDevices.size());

            for(ExtendedCaptureDeviceInfo device: this.activeCaptureDevices)
            {
                formats = device.getFormats();
                for(int i = 0; i < formats.length; ++i)
                {
                    if(formats[i].matches(format))
                    {
                        devices.add(device);
                        i = formats.length;
                    }
                }
            }
        }

        return devices;
    }

    /**
     * Sets the list of the active devices.
     *
     * @param activeDevices The list of the active devices.
     */
    public void setActiveDevices(List<ExtendedCaptureDeviceInfo> activeDevices)
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

        this.activeCaptureDevices = (activeDevices == null)
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
