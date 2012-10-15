/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.util.*;

import javax.media.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;

/**
 * Manages the list of active (currently pluged-in) capture/notify/playback
 * devices and manages user preferences between all known devices (previously
 * and actually plugged-in).
 *
 * @author Vincent Lucas
 */
public abstract class Devices
{
    /**
     * The selected active device.
     */
    protected CaptureDeviceInfo device = null;

    /**
     * The list of device names saved by the congifuration service and
     * previously saved given user preference order.
     */
    protected List<String> devicePreferences = new ArrayList<String>();

    /**
     * The flags that can save if the FLAG_DEVICE_IS_NULL state is set.
     */
    private int flags;

    /**
     * The audio system managing this device list.
     */
    private AudioSystem audioSystem;

    /**
     * Initializes the device list managment.
     *
     * @param audioSystem The audio system managing this device list.
     */
    public Devices(AudioSystem audioSystem)
    {
        this.audioSystem = audioSystem;
    }

    /**
     * Gets the selected active device.
     *
     * @param locator The string representation of the locator.
     *
     * @return The selected active device.
     */
    public CaptureDeviceInfo getDevice(String locator)
    {
        List<CaptureDeviceInfo> devices = getDevices(locator);

        // Reinit the selected device if this one is no more in the active list.
        if (this.device != null)
        {
            if ((devices == null) || !devices.contains(this.device))
            {
                setDevice(locator, null, false);
            }
        }

        CaptureDeviceInfo device = this.device;

        // If the device is null and the device is not desactivated, then try to
        // find the user preferred one between the active devices.
        if ((device == null) && ((flags & getFlagDeviceIsNull()) == 0))
        {
            if ((devices != null) && (devices.size() > 0))
            {
                device = loadDevice(locator, getPropDevice(), devices);
                if (device == null)
                    device = devices.get(0);
            }
        }
        return device;
    }

    /**
     * Returns the list of the active devices.
     *
     * @param locator The string representation of the locator.
     *
     * @return The list of the active devices.
     */
    public abstract List<CaptureDeviceInfo> getDevices(String locator);

    /**
     * Loads the user's preference with respect to a <tt>CaptureDeviceInfo</tt>
     * among a specific list of <tt>CaptureDeviceInfo</tt>s from the
     * <tt>ConfigurationService</tt>.
     *
     * @param locator The string representation of the locator.
     * @param property the name of the <tt>ConfigurationService</tt> property
     * which specifies the user's preference with respect to a
     * <tt>CaptureDeviceInfo</tt> among the specified list of
     * <tt>CaptureDeviceInfo</tt>s 
     * @param devices the list of <tt>CaptureDeviceInfo</tt>s which are valid
     * selections for the user's preference
     * @return a <tt>CaptureDeviceInfo</tt> among the specified <tt>devices</tt>
     * which represents the user's preference stored in the
     * <tt>ConfigurationService</tt>
     */
    private CaptureDeviceInfo loadDevice(
            String locator,
            String property,
            List<CaptureDeviceInfo> devices)
    {
        loadDevicePreferences(locator, property);

        // Search if an active device is a new one (is not stored in the
        // preferences yet). If true, then active this device and set it as
        // default device.
        for(CaptureDeviceInfo device : devices)
        {
            if(!devicePreferences.contains(device.getName()))
            {
                // Adds the device in the preference list (to the end of the
                // list, but the save device will push it to the top of active
                // devices).
                devicePreferences.add(device.getName());
                this.saveDevice(locator, property, device, false);
            }
        }
        // Search if an active device match one of the previsouly configured in
        // the preferences.
        for(int i = 0; i < devicePreferences.size(); ++i)
        {
            for(CaptureDeviceInfo device : devices)
                if (devicePreferences.get(i).equals(device.getName()))
                    return device;
        }

        // If no active devices matches a configured one, then gets the first
        // active device available.
        if(devices.size() > 0)
        {
            return devices.get(0);
        }

        // Else if nothing was found, then returns null.
        return null;
    }

    /**
     * Loads device name ordered with user's preference from the
     * <tt>ConfigurationService</tt>.
     *
     * @param locator The string representation of the locator.
     * @param property the name of the <tt>ConfigurationService</tt> property
     * which specifies the user's preference.
     */
    private void loadDevicePreferences(String locator, String property)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            String new_property
                = DeviceConfiguration.PROP_AUDIO_SYSTEM
                    + "."
                    + locator
                    + "."
                    + property
                    + "_list";

            String deviceNamesString = cfg.getString(new_property);

            if (deviceNamesString != null
                    && !NoneAudioSystem.LOCATOR_PROTOCOL.equalsIgnoreCase(
                        deviceNamesString))
            {
                devicePreferences.clear();
                // We must parce the string in order to load the device list.
                String[] deviceNames = deviceNamesString
                    .substring(2, deviceNamesString.length() - 2)
                    .split("\", \"");
                for(int i = 0; i < deviceNames.length; ++i)
                {
                    devicePreferences.add(deviceNames[i]);
                }
            }
            // Else, use the old property to load the last preferred device.
            // This whole "else" block may be removed in the future.
            else
            {
                String old_property
                    = DeviceConfiguration.PROP_AUDIO_SYSTEM
                        + "."
                        + locator
                        + "."
                        + property;

                deviceNamesString = cfg.getString(old_property);

                if (deviceNamesString != null
                        && !NoneAudioSystem.LOCATOR_PROTOCOL.equalsIgnoreCase(
                            deviceNamesString))
                {
                    devicePreferences.clear();
                    devicePreferences.add(deviceNamesString);
                }
            }
        }
    }

    /**
     * Saves the new selected device in top of the user preferences.
     *
     * @param locator The string representation of the locator.
     * @param property the name of the <tt>ConfigurationService</tt> property
     * into which the user's preference with respect to the specified
     * <tt>CaptureDeviceInfo</tt> is to be saved
     * @param device the <tt>CaptureDeviceInfo</tt> selected by the user.
     * @param isNull <tt>true</tt> if the user's preference with respect to the
     * specified <tt>device</tt> is <tt>null</tt>; otherwise, <tt>false</tt>
     */
    private void saveDevice(
            String locator,
            String property,
            CaptureDeviceInfo device,
            boolean isNull)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            property
                = DeviceConfiguration.PROP_AUDIO_SYSTEM
                    + "." + locator
                    + "." + property
                    + "_list";
            if(device == null)
            {
                if(isNull)
                {
                    cfg.setProperty(property, NoneAudioSystem.LOCATOR_PROTOCOL);
                }
                else
                {
                    cfg.removeProperty(property);
                }
            }
            else
            {
                // Sorts the user preferences to put the selected device on top.
                ArrayList resultList
                    = new ArrayList<String>(devicePreferences.size() + 1);
                List<CaptureDeviceInfo> devices = getDevices(locator);
                boolean firstActiveFound = false;
                for(int i = 0; i < devicePreferences.size(); ++i)
                {
                    // Checks if this element is an active device.
                    for(int j = 0; !firstActiveFound && j < devices.size(); ++j)
                    {
                        if(devicePreferences.get(i).equals(
                                    devices.get(j).getName()))
                        {
                            // The first active device is found, then place the
                            // selected device at the previous place.
                            resultList.add(device.getName());
                            firstActiveFound = true;
                        }
                    }

                    // Checks that we do not add dupplicate of the selected
                    // device>
                    if(!devicePreferences.get(i).equals(device.getName()))
                    {
                        resultList.add(devicePreferences.get(i));
                    }
                }
                // Updates the preferences list with the ew one computed.
                devicePreferences = resultList;

                // Saves the user preferences.
                StringBuffer value = new StringBuffer("[\"");
                if(devicePreferences.size() > 0)
                {
                    value.append(devicePreferences.get(0));
                    for(int i = 1; i < devicePreferences.size(); ++i)
                    {
                        value.append("\", \"" + devicePreferences.get(i));
                    }
                }
                value.append("\"]");
                cfg.setProperty(property, value.toString());
            }
        }
    }

    /**
     * Selects the active device.
     *
     * @param locator The string representation of the locator.
     * @param device The selected active device.
     * @param save Flag set to true in order to save this choice in the
     * configuration. False otherwise.
     */
    public void setDevice(
            String locator,
            CaptureDeviceInfo device,
            boolean save)
    {
        // Checks if there is a change.
        if ((this.device != device) || (device == null))
        {
            CaptureDeviceInfo oldValue = this.device;
            this.device = device;

            // Saves the new selected device in top of the user preferences.
            if (save)
            {
                boolean isNull = (this.device == null);

                if (isNull)
                    flags |= getFlagDeviceIsNull();
                else
                    flags &= ~getFlagDeviceIsNull();

                saveDevice(locator, getPropDevice(), this.device, isNull);
            }

            CaptureDeviceInfo newValue = getDevice(locator);

            if (oldValue != newValue)
            {
                if(this.audioSystem != null)
                {
                    this.audioSystem
                        .propertyChange(getPropDevice(), oldValue, newValue);
                }
            }
        }
    }

    /**
     * Sets the list of the active devices.
     *
     * @param activeDevices The list of the active devices.
     */
    public abstract void setActiveDevices(
            List<CaptureDeviceInfo> activeDevices);

    /**
     * Returns the flag nuber if the capture device is null.
     *
     * @return The flag nuber if the capture device is null.
     */
    protected abstract int getFlagDeviceIsNull();

    /**
     * Returns the property of the capture devices.
     *
     * @return The property of the capture devices.
     */
    protected abstract String getPropDevice();
}
