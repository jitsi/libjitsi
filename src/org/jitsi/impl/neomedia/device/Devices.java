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
     * @param newDeviceIdentifier The identifier of the device to add int first
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
     * @param activeDevices the list of the active devices
     * @return the selected active device
     */
    public CaptureDeviceInfo2 getSelectedDevice(
            List<CaptureDeviceInfo2> activeDevices)
    {
        if (activeDevices != null)
        {
            String property = getPropDevice();

            loadDevicePreferences(property);
            renameOldFashionedIdentifier(activeDevices);

            boolean isEmptyList = devicePreferences.isEmpty();

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

                    // When initiates the first list (when there is no user
                    // preferences yet), set the Bluetooh and Airplay to the end
                    // of the list (this corresponds to move all other type
                    // of devices on top of the preference list).
                    if(isEmptyList
                            && !activeDevice.isSameTransportType("Bluetooth")
                            && !activeDevice.isSameTransportType("AirPlay"))
                    {
                        isSelected = true;
                    }

                    // Adds the device in the preference list (to the end of the
                    // list, or on top if selected.
                    saveDevice(property, activeDevice, isSelected);
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
     * @param property the name of the <tt>ConfigurationService</tt> property
     * which specifies the user's preference.
     */
    private void loadDevicePreferences(String property)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            String newProperty
                = audioSystem.getPropertyName(property + "_list");
            String deviceIdentifiersString = cfg.getString(newProperty);

            synchronized(devicePreferences)
            {
                if (deviceIdentifiersString != null)
                {
                    devicePreferences.clear();
                    // Parse the string into a device list.
                    String[] deviceIdentifiers
                        = deviceIdentifiersString
                            .substring(2, deviceIdentifiersString.length() - 2)
                                .split("\", \"");

                    for (String deviceIdentifier : deviceIdentifiers)
                        devicePreferences.add(deviceIdentifier);
                }
                else
                {
                    // Use the old/legacy property to load the last preferred
                    // device.
                    String oldProperty = audioSystem.getPropertyName(property);

                    deviceIdentifiersString = cfg.getString(oldProperty);
                    if ((deviceIdentifiersString != null)
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
     * @param property the name of the <tt>ConfigurationService</tt> property
     * into which the user's preference with respect to the specified
     * <tt>CaptureDeviceInfo</tt> is to be saved
     * @param device The device selected by the user.
     * @param isSelected True if the device is the selected one.
     */
    private void saveDevice(
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
        writeDevicePreferences(property);
    }

    /**
     * Selects the active device.
     *
     * @param device the selected active device
     * @param save <tt>true</tt> to save the choice in the configuration;
     * <tt>false</tt>, otherwise
     */
    public void setDevice(
            CaptureDeviceInfo2 device,
            boolean save)
    {
        // Checks if there is a change.
        if ((device == null) || !device.equals(this.device))
        {
            String property = getPropDevice();
            CaptureDeviceInfo2 oldValue = this.device;

            // Saves the new selected device in top of the user preferences.
            if (save)
                saveDevice(property, device, true);

            this.device = device;

            audioSystem.propertyChange(property, oldValue, this.device);
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
     * @param property the name of the <tt>ConfigurationService</tt> property
     */
    private void writeDevicePreferences(String property)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            property = audioSystem.getPropertyName(property + "_list");

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
