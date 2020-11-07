/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.impl.neomedia.device;

import javax.media.*;

/**
 * Adds some important information (i.e. device type, UID.) to FMJ
 * <tt>CaptureDeviceInfo</tt>.
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 */
public class CaptureDeviceInfo2
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
     * Initializes a new <tt>CaptureDeviceInfo2</tt> instance from a
     * specific <tt>CaptureDeviceInfo</tt> instance and additional information
     * specific to the <tt>CaptureDeviceInfo2</tt> class. Because the
     * properties of the specified <tt>captureDeviceInfo</tt> are copied into
     * the new instance, the constructor is to be used when a
     * <tt>CaptureDeviceInfo</tt> exists for other purposes already; otherwise,
     * it is preferable to use
     * {@link #CaptureDeviceInfo2(String, MediaLocator, Format[], String,
     * String, String)}.
     *
     * @param captureDeviceInfo the <tt>CaptureDeviceInfo</tt> whose properties
     * are to be copied into the new instance
     * @param uid the unique identifier of the hardware device (interface) which
     * is to be represented by the new instance
     * @param transportType the transport type (e.g. USB) of the device to be
     * represented by the new instance
     * @param modelIdentifier the persistent identifier of the model of the
     * hardware device to be represented by the new instance
     */
    public CaptureDeviceInfo2(
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
     * Initializes a new <tt>CaptureDeviceInfo2</tt> instance with the
     * specified name, media locator, and array of Format objects.
     *
     * @param name the human-readable name of the new instance
     * @param locator the <tt>MediaLocator</tt> which uniquely identifies the
     * device to be described by the new instance
     * @param formats an array of the <tt>Format</tt>s supported by the device
     * to be described by the new instance
     * @param uid the unique identifier of the hardware device (interface) which
     * is to be represented by the new instance
     * @param transportType the transport type (e.g. USB) of the device to be
     * represented by the new instance
     * @param modelIdentifier the persistent identifier of the model of the
     * hardware device to be represented by the new instance
     */
    public CaptureDeviceInfo2(
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
        if (obj == null)
            return false;
        else if (obj == this)
            return true;
        else if (obj instanceof CaptureDeviceInfo2)
        {
            CaptureDeviceInfo2 cdi2 = (CaptureDeviceInfo2) obj;

            // locator
            MediaLocator locator = getLocator();
            MediaLocator cdi2Locator = cdi2.getLocator();

            if (locator == null)
            {
                if (cdi2Locator != null)
                    return false;
            }
            else if (cdi2Locator == null)
                return false;
            else
            {
                // protocol
                String protocol = locator.getProtocol();
                String cdi2Protocol = cdi2Locator.getProtocol();

                if (protocol == null)
                {
                    if (cdi2Protocol != null)
                        return false;
                }
                else if (cdi2Protocol == null)
                    return false;
                else if (!protocol.equals(cdi2Protocol))
                    return false;
            }

            // identifier
            return getIdentifier().equals(cdi2.getIdentifier());
        }
        else
            return false;
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
