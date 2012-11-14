/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import javax.media.*;

/**
 * Adds some important information (i.e. device type, UID.) to FMJ
 * CaptureDeviceInfo.
 *
 * @author Vincent Lucas
 */
public class ExtendedCaptureDeviceInfo
    extends CaptureDeviceInfo
{
    /**
     * The device UID (unique identifier).
     */
    private final String UID;

    /**
     * The device transport type.
     */
    private final String transportType;

    /**
     * Constructs a CaptureDeviceInfo object with the specified name, media
     * locator, and array of Format objects.
     *
     * @param captureDeiceInfo the device info.
     * @param uid The device UID (unique identifier).
     * @param transportType The device transport type.
     */
    public ExtendedCaptureDeviceInfo(
            CaptureDeviceInfo captureDeviceInfo,
            String UID,
            String transportType)
    {
        this(
                captureDeviceInfo.getName(),
                captureDeviceInfo.getLocator(),
                captureDeviceInfo.getFormats(),
                UID,
                transportType);
    }

    /**
     * Constructs a CaptureDeviceInfo object with the specified name, media
     * locator, and array of Format objects.
     *
     * @param name A String that contains the name of the device.
     * @param locator The MediaLocator that uniquely specifies the device.
     * @param formats An array of the output formats supported by the device.
     * @param uid The device UID (unique identifier).
     * @param transportType The device transport type.
     */
    public ExtendedCaptureDeviceInfo(
            String name,
            MediaLocator locator,
            Format[] formats,
            String UID,
            String transportType)
    {
        super(name, locator, formats);

        this.UID = UID;
        this.transportType = transportType;
    }

    /**
     * Returns the device UID (unique identifier).
     *
     * @return The device UID (unique identifier).
     */
    public String getUID()
    {
        return this.UID;
    }

    /**
     * Returns the device transport type.
     *
     * @return The device transport type.
     */
    public String getTransportType()
    {
        return this.transportType;
    }

    /**
     * Returns if the transport type matches the one given in parameter.
     *
     * The transport type to compare with.
     *
     * @return True if the transport type matches the one given in parameter.
     * False otherwise.
     */
    public boolean isSameTransportType(String transportType)
    {
        if(this.transportType == null)
        {
            return (transportType == null);
        }
        return this.transportType.equals(transportType);
    }

    @Override
    public boolean equals(Object obj)
    {
        if(obj != null
                && obj instanceof ExtendedCaptureDeviceInfo)
        {
            return this.getIdentifier().equals(
                    ((ExtendedCaptureDeviceInfo) obj).getIdentifier());
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return this.getIdentifier().hashCode();
    }

    /**
     * Returns the device identifier used to save and load device preferences.
     * It is composed by the system UID if not null. Otherwise returns the
     * device name and (if not null) the transport type.
     *
     * @return The device identifier.
     */
    public String getIdentifier()
    {
        return (UID == null) ? name : UID;
    }
}
