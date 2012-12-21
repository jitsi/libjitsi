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
 * <tt>CaptureDeviceInfo</tt>.
 *
 * @author Vincent Lucas
 */
public class ExtendedCaptureDeviceInfo
    extends CaptureDeviceInfo
{
    /**
     * The device transport type.
     */
    private final String transportType;

    /**
     * The device UID (unique identifier).
     */
    private final String uid;

    /**
     * The persistent identifier for the model of this device.
     */
    private final String modelIdentifier;

    /**
     * Constructs a CaptureDeviceInfo object with the specified name, media
     * locator, and array of Format objects.
     *
     * @param captureDeiceInfo the device info.
     * @param uid The device UID (unique identifier).
     * @param transportType The device transport type.
     * @param modelIdentifier The persistent identifier for the model of this
     * device.
     */
    public ExtendedCaptureDeviceInfo(
            CaptureDeviceInfo captureDeviceInfo,
            String uid,
            String transportType,
            String modelIdentifier)
    {
        this(
                captureDeviceInfo.getName(),
                captureDeviceInfo.getLocator(),
                captureDeviceInfo.getFormats(),
                uid,
                transportType,
                modelIdentifier);
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
     * @param modelIdentifier The persistent identifier for the model of this
     * device.
     */
    public ExtendedCaptureDeviceInfo(
            String name,
            MediaLocator locator,
            Format[] formats,
            String uid,
            String transportType,
            String modelIdentifier)
    {
        super(name, locator, formats);

        this.uid = uid;
        this.transportType = transportType;
        this.modelIdentifier = modelIdentifier;
    }

    /**
     * Determines whether a specific <tt>Object</tt> is equal (by value) to this
     * instance.
     *
     * @param obj the <tt>Object</tt> to be determined whether it is equal (by
     * value) to this instance
     * @return <tt>true</tt> if the specified <tt>obj</tt> is equal (by value)
     * to this instance; otherwise, <tt>false</tt>
     */
    @Override
    public boolean equals(Object obj)
    {
        return
            (obj != null)
                && (obj instanceof ExtendedCaptureDeviceInfo)
                && getIdentifier().equals(
                        ((ExtendedCaptureDeviceInfo) obj).getIdentifier());
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
        return (uid == null) ? name : uid;
    }

    /**
     * Returns the device transport type of this instance.
     *
     * @return the device transport type of this instance
     */
    public String getTransportType()
    {
        return transportType;
    }

    /**
     * Returns the device UID (unique identifier) of this instance.
     *
     * @return the device UID (unique identifier) of this instance
     */
    public String getUID()
    {
        return uid;
    }

    /**
     * Returns the model identifier of this instance.
     *
     * @return the model identifier of this instance
     */
    public String getModelIdentifier()
    {
        return (modelIdentifier == null) ? name : modelIdentifier;
    }

    /**
     * Returns a hash code value for this object for the benefit of hashtables.
     *
     * @return a hash code value for this object for the benefit of hashtables
     */
    @Override
    public int hashCode()
    {
        return getIdentifier().hashCode();
    }

    /**
     * Determines whether a specific transport type is equal to/the same as the
     * transport type of this instance.
     *
     * @param transportType the transport type to compare to the transport type
     * of this instance
     * @return <tt>true</tt> if the specified <tt>transportType</tt> is equal
     * to/the same as the transport type of this instance; otherwise,
     * <tt>false</tt>
     */
    public boolean isSameTransportType(String transportType)
    {
        return
            (this.transportType == null)
                ? (transportType == null)
                : this.transportType.equals(transportType);
    }
}
