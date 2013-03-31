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
     * Initializes the capture device list management.
     *
     * @param audioSystem The audio system managing this capture device list.
     */
    public CaptureDevices(AudioSystem audioSystem)
    {
        super(audioSystem);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CaptureDeviceInfo2> getDevices()
    {
        List<CaptureDeviceInfo2> devices = super.getDevices();

        if (!devices.isEmpty())
        {
            List<CaptureDeviceInfo2> thisDevices
                = new ArrayList<CaptureDeviceInfo2>(devices.size());
            Format format = new AudioFormat(AudioFormat.LINEAR, -1, 16, -1);

            for(CaptureDeviceInfo2 device: devices)
            {
                for(Format deviceFormat : device.getFormats())
                {
                    if(deviceFormat.matches(format))
                    {
                        thisDevices.add(device);
                        break;
                    }
                }
            }
            devices = thisDevices;
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
     * {@inheritDoc}
     */
    @Override
    public void setDevices(List<CaptureDeviceInfo2> devices)
    {
        super.setDevices(devices);

        if (devices != null)
        {
            boolean commit = false;

            for (CaptureDeviceInfo activeDevice : devices)
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
}
