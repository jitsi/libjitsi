/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.util.*;

import org.jitsi.service.neomedia.*;

/**
 * Represents a <tt>DeviceSystem</tt> which provides support for the devices to
 * capture and play back audio (media). Examples include implementations which
 * integrate the native PortAudio, PulseAudio libraries.
 *
 * @author Lyubomir Marinov
 */
public abstract class AudioSystem
    extends DeviceSystem
{
    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> supports toggling its
     * denoise functionality between on and off. The UI will look for the
     * presence of the flag in order to determine whether a check box is to be
     * shown to the user to enable toggling the denoise functionality.
     */
    public static final int FEATURE_DENOISE = 2;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> supports toggling its
     * echo cancellation functionality between on and off. The UI will look for
     * the presence of the flag in order to determine whether a check box is to
     * be shown to the user to enable toggling the echo cancellation
     * functionality.
     */
    public static final int FEATURE_ECHO_CANCELLATION = 4;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> differentiates between
     * playback and notification audio devices. The UI, for example, will look
     * for the presence of the flag in order to determine whether separate combo
     * boxes are to be shown to the user to allow the configuration of the
     * preferred playback and notification audio devices.
     */
    public static final int FEATURE_NOTIFY_AND_PLAYBACK_DEVICES = 8;

    public static final String LOCATOR_PROTOCOL_AUDIORECORD = "audiorecord";
    
    public static final String LOCATOR_PROTOCOL_JAVASOUND = "javasound";

    public static final String LOCATOR_PROTOCOL_OPENSLES = "opensles";

    public static final String LOCATOR_PROTOCOL_PORTAUDIO = "portaudio";

    public static final String LOCATOR_PROTOCOL_PULSEAUDIO = "pulseaudio";

    public static AudioSystem getAudioSystem(String locatorProtocol)
    {
        AudioSystem[] audioSystems = getAudioSystems();
        AudioSystem audioSystemWithLocatorProtocol = null;

        if (audioSystems != null)
        {
            for (AudioSystem audioSystem : audioSystems)
            {
                if (audioSystem.getLocatorProtocol().equalsIgnoreCase(
                        locatorProtocol))
                {
                    audioSystemWithLocatorProtocol = audioSystem;
                    break;
                }
            }
        }
        return audioSystemWithLocatorProtocol;
    }

    public static AudioSystem[] getAudioSystems()
    {
        DeviceSystem[] deviceSystems
            = DeviceSystem.getDeviceSystems(MediaType.AUDIO);
        List<AudioSystem> audioSystems;

        if (deviceSystems == null)
            audioSystems = null;
        else
        {
            audioSystems = new ArrayList<AudioSystem>(deviceSystems.length);
            for (DeviceSystem deviceSystem : deviceSystems)
                if (deviceSystem instanceof AudioSystem)
                    audioSystems.add((AudioSystem) deviceSystem);
        }
        return
            (audioSystems == null)
                ? null
                : audioSystems.toArray(new AudioSystem[audioSystems.size()]);
    }

    /**
     * The index of the capture devices.
     */
    public static final int CAPTURE_INDEX = 0;

    /**
     * The index of the notify devices.
     */
    public static final int NOTIFY_INDEX = 1;

    /**
     * The index of the playback devices.
     */
    public static final int PLAYBACK_INDEX = 2;

    /**
     * The list of devices detected by this <tt>AudioSystem</tt> indexed by
     * their category which is among {@link #CAPTURE_INDEX},
     * {@link #NOTIFY_INDEX} and {@link #PLAYBACK_INDEX}.
     */
    private Devices[] devices;

    protected AudioSystem(String locatorProtocol)
        throws Exception
    {
        this(locatorProtocol, 0);
    }

    protected AudioSystem(String locatorProtocol, int features)
        throws Exception
    {
        super(MediaType.AUDIO, locatorProtocol, features);
    }

    /**
     * Gets the list of a kind of devices: capture, notify or playback.
     *
     * @param index The index of the specific devices: capture, notify or
     * playback.
     *
     * @return The list of a kind of devices: capture, notify or playback.
     */
    public List<ExtendedCaptureDeviceInfo> getDevices(int index)
    {
        return devices[index].getDevices();
    }

    /**
     * Gets the selected device for a specific kind: capture, notify or
     * playback.
     *
     * @param index The index of the specific devices: capture, notify or
     * playback.
     * @return The selected device for a specific kind: capture, notify or
     * playback.
     */
    public ExtendedCaptureDeviceInfo getDevice(int index)
    {
        return
            devices[index].getDevice(getLocatorProtocol(), getDevices(index));
    }

    /**
     * Sets the list of a kind of devices: capture, notify or playback.
     *
     * @param activeCaptureDevices The list of a kind of devices: capture,
     * notify or playback.
     */
    protected void setCaptureDevices(
            List<ExtendedCaptureDeviceInfo> activeCaptureDevices)
    {
        devices[CAPTURE_INDEX].setActiveDevices(activeCaptureDevices);
    }

    /**
     * Selects the active device.
     *
     * @param index The index corresponding to a specific device kind:
     * capture/notify/playback.
     * @param device The selected active device.
     * @param save Flag set to true in order to save this choice in the
     * configuration. False otherwise.
     */
    public void setDevice(
            int index,
            ExtendedCaptureDeviceInfo device,
            boolean save)
    {
        devices[index].setDevice(
                getLocatorProtocol(),
                device,
                save,
                getDevices(index));
    }

    /**
     * Sets the list of the active devices.
     *
     * @param activePlaybackDevices The list of the active devices.
     */
    protected void setPlaybackDevices(
            List<ExtendedCaptureDeviceInfo> activePlaybackDevices)
    {
        devices[PLAYBACK_INDEX].setActiveDevices(activePlaybackDevices);
        // The active notify device list is a copy of the playback one.
        devices[NOTIFY_INDEX].setActiveDevices(activePlaybackDevices);
    }

    /**
     * {@inheritDoc}
     *
     * Removes any capture, playback and notification devices previously
     * detected by this <tt>AudioSystem</tt> and prepares it for the execution
     * of its {@link DeviceSystem#doInitialize()} implementation (which detects
     * all devices to be provided by this instance).
     */
    @Override
    protected void preInitialize()
    {
        super.preInitialize();

        if (devices == null)
        {
            devices = new Devices[3];
            devices[CAPTURE_INDEX] = new CaptureDevices(this);
            devices[NOTIFY_INDEX] = new NotifyDevices(this);
            devices[PLAYBACK_INDEX] = new PlaybackDevices(this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Because <tt>AudioSystem</tt> may support playback and notification audio
     * devices apart from capture audio devices, fires more specific
     * <tt>PropertyChangeEvent</tt>s than <tt>DeviceSystem</tt>
     */
    @Override
    protected void postInitialize()
    {
        try
        {
            super.postInitialize();
        }
        finally
        {
            try
            {
                postInitializeSpecificDevices(CAPTURE_INDEX);
            }
            finally
            {
                if ((FEATURE_NOTIFY_AND_PLAYBACK_DEVICES & getFeatures()) != 0)
                {
                    try
                    {
                        postInitializeSpecificDevices(NOTIFY_INDEX);
                    }
                    finally
                    {
                        postInitializeSpecificDevices(PLAYBACK_INDEX);
                    }
                }
            }
        }
    }

    /**
     * Sets the device lists after the different audio systems (PortAudio,
     * PulseAudio, etc) have finished to detects the devices.
     *
     * @param index The index corresponding to a specific device kind:
     * capture/notify/playback.
     */
    protected void postInitializeSpecificDevices(int index)
    {
        // Gets all current active devices.
        List<ExtendedCaptureDeviceInfo> activeDevices = getDevices(index);
        // Gets the default device.
        Devices devices = this.devices[index];
        ExtendedCaptureDeviceInfo selectedActiveDevice
            = devices.getDevice(getLocatorProtocol(), activeDevices);
        // Sets the default device as selected (this function will only fire a
        // property change if the device has changed from previous
        // configuration).
        // This "set" part is important because only the fire property event
        // provides a way to get hot plugged devices working during a call.
        devices.setDevice(
                getLocatorProtocol(),
                selectedActiveDevice,
                false,
                activeDevices);
    }

    /**
     * Fires a new <tt>PropertyChangeEvent</tt> to the
     * <tt>PropertyChangeListener</tt>s registered with this
     * <tt>PropertyChangeNotifier</tt> in order to notify about a change in the
     * value of a specific property which had its old value modified to a
     * specific new value. <tt>PropertyChangeNotifier</tt> does not check
     * whether the specified <tt>oldValue</tt> and <tt>newValue</tt> are indeed
     * different.
     * 
     * @param property the name of the property of this
     * <tt>PropertyChangeNotifier</tt> which had its value changed
     * @param oldValue the value of the property with the specified name before
     * the change
     * @param newValue the value of the property with the specified name after
     * the change
     */
    public void propertyChange(
            String property,
            Object oldValue,
            Object newValue)
    {
        firePropertyChange(property, oldValue, newValue);
    }
}
