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
 * Manages the list of active (currently plugged-in) capture/notify/playback
 * devices and manages user preferences between all known devices (previously
 * and actually plugged-in).
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 */
public abstract class Devices
{
    /**
     * The name of the <tt>ConfigurationService</tt> <tt>boolean</tt> property
     * which indicates whether the automatic selection of USB devices must be
     * disabled. The default value is <tt>false</tt>.
     */
    private static final String PROP_DISABLE_USB_DEVICE_AUTO_SELECTION
        = "org.jitsi.impl.neomedia.device.disableUsbDeviceAutoSelection";

    /**
     * The audio system managing this device list.
     */
    private final AudioSystem audioSystem;

    /**
     * The selected active device.
     */
    private CaptureDeviceInfo2 device;

    /**
     * The list of device ID/names saved by the configuration service and
     * previously saved given user preference order.
     */
    private final List<String> devicePreferences = new ArrayList<String>();

    /**
     * The list of <tt>CaptureDeviceInfo2</tt>s which are active/plugged-in.
     */
    private List<CaptureDeviceInfo2> devices;

    /**
     * Initializes the device list management.
     *
     * @param audioSystem The audio system managing this device list.
     */
    public Devices(AudioSystem audioSystem)
    {
        this.audioSystem = audioSystem;
    }

    /**
     * Adds a new device in the preferences (at the first active position if the
     * isSelected argument is true).
     *
     * @param newsDeviceIdentifier The identifier of the device to add int first
     * active position of the preferences.
     * @param isSelected True if the device is the selected one.
     */
    private void addToDevicePreferences(
            String newDeviceIdentifier,
            boolean isSelected)
    {
        synchronized(devicePreferences)
        {
            devicePreferences.remove(newDeviceIdentifier);
            // A selected device is placed on top of the list: this is the new
            // preferred device.
            if(isSelected)
            {
                devicePreferences.add(0, newDeviceIdentifier);
            }
            // If there is no active device or the device is not selected, then
            // set the new device to the end of the device preference list.
            else
            {
                devicePreferences.add(newDeviceIdentifier);
            }
        }
    }

    /**
     * Gets a <tt>CapatureDeviceInfo2</tt> which is known to this instance and
     * is identified by a specific <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> of the
     * <tt>CaptureDeviceInfo2</tt> to be returned
     * @return a <tt>CaptureDeviceInfo2</tt> which is known to this instance and
     * is identified by the specified <tt>locator</tt>
     */
    public CaptureDeviceInfo2 getDevice(MediaLocator locator)
    {
        CaptureDeviceInfo2 device = null;

        if (devices != null)
        {
            for (CaptureDeviceInfo2 aDevice : devices)
            {
                MediaLocator aLocator = aDevice.getLocator();

                if (locator.equals(aLocator))
                {
                    device = aDevice;
                    break;
                }
            }
        }
        return device;
    }

    /**
     * Returns the list of the <tt>CaptureDeviceInfo2</tt>s which are
     * active/plugged-in.
     *
     * @return the list of the <tt>CaptureDeviceInfo2</tt>s which are
     * active/plugged-in
     */
    public List<CaptureDeviceInfo2> getDevices()
    {
        List<CaptureDeviceInfo2> devices;

        if (this.devices == null)
            devices = Collections.emptyList();
        else
            devices = new ArrayList<CaptureDeviceInfo2>(this.devices);
        return devices;
    }

    /**
     * Returns the property of the capture devices.
     *
     * @return The property of the capture devices.
     */
    protected abstract String getPropDevice();

    /**
     * Gets the selected active device.
     *
     * @param locator The string representation of the locator.
     * @param activeDevices The list of the active devices.
     *
     * @return The selected active device.
     */
    public CaptureDeviceInfo2 getSelectedDevice(
            String locator,
            List<CaptureDeviceInfo2> activeDevices)
    {
        if (activeDevices != null)
        {
            String property = getPropDevice();
            loadDevicePreferences(locator, property);
            renameOldFashionedIdentifier(activeDevices);

            // Search if an active device is a new one (is not stored in the
            // preferences yet). If true, then active this device and set it as
            // default device (only for USB devices since the user has
            // deliberately plugged in the device).
            for(int i = activeDevices.size() - 1; i >= 0; i--)
            {
                CaptureDeviceInfo2 activeDevice = activeDevices.get(i);

                if(!devicePreferences.contains(
                            activeDevice.getModelIdentifier()))
                {
                    // By default, select automatically the USB devices.
                    boolean isSelected
                        = activeDevice.isSameTransportType("USB");
                    ConfigurationService cfg
                        = LibJitsi.getConfigurationService();
                    // Desactivate the USB device automatic selection if the
                    // property is set to true.
                    if(cfg != null
                            && cfg.getBoolean(
                                PROP_DISABLE_USB_DEVICE_AUTO_SELECTION,
                                false))
                    {
                        isSelected = false;
                    }

                    // Adds the device in the preference list (to the end of the
                    // list, or on top if selected.
                    saveDevice(
                            locator,
                            property,
                            activeDevice,
                            isSelected);
                }
            }

            // Search if an active device match one of the previously configured
            // in the preferences.
            synchronized(devicePreferences)
            {
                for(String devicePreference : devicePreferences)
                {
                    for(CaptureDeviceInfo2 activeDevice : activeDevices)
                    {
                        // If we have found the "preferred" device among active
                        // device.
                        if(devicePreference.equals(
                                activeDevice.getModelIdentifier()))
                        {
                            return activeDevice;
                        }
                        // If the "none" device is the "preferred" device among
                        // "active" device.
                        else if(devicePreference.equals(
                                    NoneAudioSystem.LOCATOR_PROTOCOL))
                        {
                            return null;
                        }
                    }
                }
            }
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

            String deviceIdentifiersString = cfg.getString(new_property);

            synchronized(devicePreferences)
            {
                if (deviceIdentifiersString != null)
                {
                    devicePreferences.clear();
                    // We must parse the string in order to load the device
                    // list.
                    String[] deviceIdentifiers = deviceIdentifiersString
                        .substring(2, deviceIdentifiersString.length() - 2)
                        .split("\", \"");
                    for(int i = 0; i < deviceIdentifiers.length; ++i)
                    {
                        devicePreferences.add(deviceIdentifiers[i]);
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

                    deviceIdentifiersString = cfg.getString(old_property);

                    if (deviceIdentifiersString != null
                            && !NoneAudioSystem.LOCATOR_PROTOCOL
                                .equalsIgnoreCase(deviceIdentifiersString))
                    {
                        devicePreferences.clear();
                        devicePreferences.add(deviceIdentifiersString);
                    }
                }
            }
        }
    }

    /**
     * Renames the old fashioned identifier (name only), into new fashioned one
     * (UID, or name + transport type).
     *
     * @param activeDevices The list of the active devices.
     */
    private void renameOldFashionedIdentifier(
            List<CaptureDeviceInfo2> activeDevices)
    {
        // Renames the old fashioned device identifier for all active devices.
        for(CaptureDeviceInfo2 activeDevice : activeDevices)
        {
            String name = activeDevice.getName();
            String id = activeDevice.getModelIdentifier();

            // We can only switch to the new fashioned notation, only if the OS
            // API gives us a unique identifier (different from the device
            // name).
            if(!name.equals(id))
            {
                synchronized(devicePreferences)
                {
                    do
                    {
                        int nameIndex = devicePreferences.indexOf(name);

                        // If there is one old fashioned identifier.
                        if(nameIndex == -1)
                            break;
                        else
                        {
                            int idIndex = devicePreferences.indexOf(id);

                            // If the corresponding new fashioned identifier
                            // does not exist, then renames the old one into
                            // the new one.
                            if(idIndex == -1)
                                devicePreferences.set(nameIndex, id);
                            else // Remove the duplicate.
                                devicePreferences.remove(nameIndex);
                        }
                    }
                    while(true);
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
     * @param selectedDevice The device selected by the user.
     * @param isSelected True if the device is the selected one.
     */
    private void saveDevice(
            String locator,
            String property,
            CaptureDeviceInfo2 device,
            boolean isSelected)
    {
        String selectedDeviceIdentifier
            = (device == null)
                ? NoneAudioSystem.LOCATOR_PROTOCOL
                : device.getModelIdentifier();

        // Sorts the user preferences to put the selected device on top.
        addToDevicePreferences(
                selectedDeviceIdentifier,
                isSelected);

        // Saves the user preferences.
        writeDevicePreferences(locator, property);
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
            CaptureDeviceInfo2 device,
            boolean save)
    {
        // Checks if there is a change.
        if ((device == null) || !device.equals(this.device))
        {
            CaptureDeviceInfo2 oldValue = this.device;

            // Saves the new selected device in top of the user preferences.
            if (save)
            {
                saveDevice(
                        locator,
                        getPropDevice(),
                        device,
                        true);
            }
            this.device = device;

            audioSystem.propertyChange(getPropDevice(), oldValue, this.device);
        }
    }

    /**
     * Sets the list of <tt>CaptureDeviceInfo2</tt>s which are
     * active/plugged-in.
     *
     * @param devices the list of <tt>CaptureDeviceInfo2</tt>s which are
     * active/plugged-in
     */
    public void setDevices(List<CaptureDeviceInfo2> devices)
    {
        this.devices
            = (devices == null)
                ? null
                : new ArrayList<CaptureDeviceInfo2>(devices);
    }

    /**
     * Saves the device preferences and write it to the configuration file.
     *
     * @param locator The string representation of the locator.
     * @param property the name of the <tt>ConfigurationService</tt> property
     */
    private void writeDevicePreferences(String locator, String property)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            property
                = DeviceConfiguration.PROP_AUDIO_SYSTEM
                    + "." + locator
                    + "." + property
                    + "_list";

            StringBuilder value = new StringBuilder("[\"");

            synchronized(devicePreferences)
            {
                int devicePreferenceCount = devicePreferences.size();

                if(devicePreferenceCount != 0)
                {
                    value.append(devicePreferences.get(0));
                    for(int i = 1; i < devicePreferenceCount; i++)
                        value.append("\", \"").append(devicePreferences.get(i));
                }
            }
            value.append("\"]");

            cfg.setProperty(property, value.toString());
        }
    }
}
