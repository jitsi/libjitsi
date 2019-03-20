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

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import javax.media.*;
import javax.sound.sampled.*;

import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.resources.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;

/**
 * Represents a <tt>DeviceSystem</tt> which provides support for the devices to
 * capture and play back audio (media). Examples include implementations which
 * integrate the native PortAudio, PulseAudio libraries.
 *
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 * @author Timothy Price
 */
public abstract class AudioSystem
    extends DeviceSystem
{
    /**
     * Enumerates the different types of media data flow of
     * <tt>CaptureDeviceInfo2</tt>s contributed by an <tt>AudioSystem</tt>.
     *
     * @author Lyubomir Marinov
     */
    public enum DataFlow
    {
        CAPTURE,
        NOTIFY,
        PLAYBACK
    }

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> supports toggling its
     * automatic gain control (AGC) functionality between on and off. The UI
     * will look for the presence of the flag in order to determine whether a
     * check box is to be shown to the user to enable toggling the automatic
     * gain control (AGC) functionality.
     */
    public static final int FEATURE_AGC = 1 << 4;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> supports toggling its
     * denoise functionality between on and off. The UI will look for the
     * presence of the flag in order to determine whether a check box is to be
     * shown to the user to enable toggling the denoise functionality.
     */
    public static final int FEATURE_DENOISE = 1 << 1;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> supports toggling its
     * echo cancellation functionality between on and off. The UI will look for
     * the presence of the flag in order to determine whether a check box is to
     * be shown to the user to enable toggling the echo cancellation
     * functionality.
     */
    public static final int FEATURE_ECHO_CANCELLATION = 1 << 2;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> differentiates between
     * playback and notification audio devices. The UI, for example, will look
     * for the presence of the flag in order to determine whether separate combo
     * boxes are to be shown to the user to allow the configuration of the
     * preferred playback and notification audio devices.
     */
    public static final int FEATURE_NOTIFY_AND_PLAYBACK_DEVICES = 1 << 3;

    public static final String LOCATOR_PROTOCOL_AUDIORECORD = "audiorecord";

    public static final String LOCATOR_PROTOCOL_AUDIOSILENCE = "audiosilence";

    public static final String LOCATOR_PROTOCOL_JAVASOUND = "javasound";

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying
     * <tt>CaptureDeviceInfo</tt>s contributed by <tt>MacCoreaudioSystem</tt>.
     */
    public static final String LOCATOR_PROTOCOL_MACCOREAUDIO = "maccoreaudio";

    public static final String LOCATOR_PROTOCOL_OPENSLES = "opensles";

    public static final String LOCATOR_PROTOCOL_PORTAUDIO = "portaudio";

    public static final String LOCATOR_PROTOCOL_PULSEAUDIO = "pulseaudio";

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying
     * <tt>CaptureDeviceInfo</tt>s contributed by <tt>WASAPISystem</tt>.
     */
    public static final String LOCATOR_PROTOCOL_WASAPI = "wasapi";

    /**
     * The <tt>Logger</tt> used by this instance for logging output.
     */
    private static Logger logger = Logger.getLogger(AudioSystem.class);

    /**
     * The (base) name of the <tt>ConfigurationService</tt> property which
     * indicates whether automatic gain control (AGC) is to be performed for the
     * captured audio. 
     */
    private static final String PNAME_AGC = "automaticgaincontrol";

    /**
     * The (base) name of the <tt>ConfigurationService</tt> property which
     * indicates whether noise suppression is to be performed for the captured
     * audio.
     */
    protected static final String PNAME_DENOISE = "denoise";

    /**
     * The (base) name of the <tt>ConfigurationService</tt> property which
     * indicates whether noise cancellation is to be performed for the captured
     * audio.
     */
    protected static final String PNAME_ECHOCANCEL = "echocancel";

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
     * {@inheritDoc}
     *
     * Delegates to {@link #createRenderer(boolean)} with the value of the
     * <tt>playback</tt> argument set to true.
     */
    @Override
    public Renderer createRenderer()
    {
        return createRenderer(true);
    }

    /**
     * Initializes a new <tt>Renderer</tt> instance which is to either perform
     * playback on or sound a notification through a device contributed by this
     * system. The (default) implementation of <tt>AudioSystem</tt> ignores the
     * value of the <tt>playback</tt> argument and delegates to
     * {@link DeviceSystem#createRenderer()}.
     *
     * @param playback <tt>true</tt> if the new instance is to perform playback
     * or <tt>false</tt> if the new instance is to sound a notification
     * @return a new <tt>Renderer</tt> instance which is to either perform
     * playback on or sound a notification through a device contributed by this
     * system
     */
    public Renderer createRenderer(boolean playback)
    {
        String className = getRendererClassName();
        Renderer renderer;

        if (className == null)
        {
            /*
             * There is no point in delegating to the super's createRenderer()
             * because it will not have a class to instantiate.
             */
            renderer = null;
        }
        else
        {
            Class<?> clazz;

            try
            {
                clazz = Class.forName(className);
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else
                {
                    clazz = null;
                    logger.error("Failed to get class " + className, t);
                }
            }
            if (clazz == null)
            {
                /*
                 * There is no point in delegating to the super's
                 * createRenderer() because it will fail to get the class.
                 */
                renderer = null;
            }
            else if (!Renderer.class.isAssignableFrom(clazz))
            {
                /*
                 * There is no point in delegating to the super's
                 * createRenderer() because it will fail to cast the new
                 * instance to a Renderer.
                 */
                renderer = null;
            }
            else
            {
                boolean superCreateRenderer;

                if (((getFeatures() & FEATURE_NOTIFY_AND_PLAYBACK_DEVICES) != 0)
                        && AbstractAudioRenderer.class.isAssignableFrom(clazz))
                {
                    Constructor<?> constructor = null;

                    try
                    {
                        constructor = clazz.getConstructor(boolean.class);
                    }
                    catch (NoSuchMethodException nsme)
                    {
                        /*
                         * Such a constructor is optional so the failure to get
                         * it will be swallowed and the super's
                         * createRenderer() will be invoked.
                         */
                    }
                    catch (SecurityException se)
                    {
                    }
                    if ((constructor != null))
                    {
                        superCreateRenderer = false;
                        try
                        {
                            renderer
                                = (Renderer) constructor.newInstance(playback);
                        }
                        catch (Throwable t)
                        {
                            if (t instanceof ThreadDeath)
                                throw (ThreadDeath) t;
                            else
                            {
                                renderer = null;
                                logger.error(
                                        "Failed to initialize a new "
                                            + className + " instance",
                                        t);
                            }
                        }
                        if ((renderer != null) && !playback)
                        {
                            CaptureDeviceInfo device
                                = getSelectedDevice(DataFlow.NOTIFY);

                            if (device == null)
                            {
                                /*
                                 * If there is no notification device, then no
                                 * notification is to be sounded.
                                 */
                                renderer = null;
                            }
                            else
                            {
                                MediaLocator locator = device.getLocator();

                                if (locator != null)
                                {
                                    ((AbstractAudioRenderer<?>) renderer)
                                        .setLocator(locator);
                                }
                            }
                        }
                    }
                    else
                    {
                        /*
                         * The super's createRenderer() will be invoked because
                         * either there is no non-default constructor or it is
                         * not meant to be invoked by the public.
                         */
                        superCreateRenderer = true;
                        renderer = null;
                    }
                }
                else
                {
                    /*
                     * The super's createRenderer() will be invoked because
                     * either this AudioSystem does not distinguish between
                     * playback and notify data flows or the Renderer
                     * implementation class in not familiar.
                     */
                    superCreateRenderer = true;
                    renderer = null;
                }

                if (superCreateRenderer && (renderer == null))
                    renderer = super.createRenderer();
            }
        }
        return renderer;
    }

    /**
     * Obtains an audio input stream from the URL provided.
     * @param uri a valid uri to a sound resource.
     * @return the input stream to audio data.
     * @throws IOException if an I/O exception occurs
     */
    public InputStream getAudioInputStream(String uri)
        throws IOException
    {
        ResourceManagementService resources
            = LibJitsi.getResourceManagementService();
        URL url
            = (resources == null)
                ? null
                : resources.getSoundURLForPath(uri);
        AudioInputStream audioStream = null;

        try
        {
            // Not found by the class loader? Perhaps it is a local file.
            if (url == null)
                url = new URL(uri);

            audioStream
                = javax.sound.sampled.AudioSystem.getAudioInputStream(url);
        }
        catch (MalformedURLException murle)
        {
            // Do nothing, the value of audioStream will remain equal to null.
        }
        catch (UnsupportedAudioFileException uafe)
        {
            logger.error("Unsupported format of audio stream " + url, uafe);
        }

        return audioStream;
    }

    /**
     * Gets a <tt>CaptureDeviceInfo2</tt> which has been contributed by this
     * <tt>AudioSystem</tt>, supports a specific flow of media data (i.e.
     * capture, notify or playback) and is identified by a specific
     * <tt>MediaLocator</tt>.
     *
     * @param dataFlow the flow of the media data supported by the
     * <tt>CaptureDeviceInfo2</tt> to be returned
     * @param locator the <tt>MediaLocator</tt> of the
     * <tt>CaptureDeviceInfo2</tt> to be returned
     * @return a <tt>CaptureDeviceInfo2</tt> which has been contributed by this
     * instance, supports the specified <tt>dataFlow</tt> and is identified by
     * the specified <tt>locator</tt>
     */
    public CaptureDeviceInfo2 getDevice(DataFlow dataFlow, MediaLocator locator)
    {
        return devices[dataFlow.ordinal()].getDevice(locator);
    }

    /**
     * Gets the list of devices with a specific data flow: capture, notify or
     * playback.
     *
     * @param dataFlow the data flow of the devices to retrieve: capture, notify
     * or playback
     * @return the list of devices with the specified <tt>dataFlow</tt>
     */
    public List<CaptureDeviceInfo2> getDevices(DataFlow dataFlow)
    {
        return devices[dataFlow.ordinal()].getDevices();
    }

    /**
     * Returns the FMJ format of a specific <tt>InputStream</tt> providing audio
     * media.
     *
     * @param audioInputStream the <tt>InputStream</tt> providing audio media to
     * determine the FMJ format of
     * @return the FMJ format of the specified <tt>audioInputStream</tt> or
     * <tt>null</tt> if such an FMJ format could not be determined
     */
    public javax.media.format.AudioFormat getFormat(
            InputStream audioInputStream)
    {
        if ((audioInputStream instanceof AudioInputStream))
        {
            AudioFormat af = ((AudioInputStream) audioInputStream).getFormat();

            return
                new javax.media.format.AudioFormat(
                        javax.media.format.AudioFormat.LINEAR,
                        af.getSampleRate(),
                        af.getSampleSizeInBits(),
                        af.getChannels());
        }
        return null;
    }

    /**
     * Gets the (full) name of the <tt>ConfigurationService</tt> property which
     * is associated with a (base) <tt>AudioSystem</tt>-specific property name.
     * 
     * @param basePropertyName the (base) <tt>AudioSystem</tt>-specific property
     * name of which the associated (full) <tt>ConfigurationService</tt>
     * property name is to be returned
     * @return the (full) name of the <tt>ConfigurationService</tt> property
     * which is associated with the (base) <tt>AudioSystem</tt>-specific
     * property name
     */
    protected String getPropertyName(String basePropertyName)
    {
        return
            DeviceConfiguration.PROP_AUDIO_SYSTEM + "." + getLocatorProtocol()
                + "." + basePropertyName;
    }

    /**
     * Gets the selected device for a specific data flow: capture, notify or
     * playback.
     *
     * @param dataFlow the data flow of the selected device to retrieve:
     * capture, notify or playback.
     * @return the selected device for the specified <tt>dataFlow</tt>
     */
    public CaptureDeviceInfo2 getSelectedDevice(DataFlow dataFlow)
    {
        return
            devices[dataFlow.ordinal()].getSelectedDevice(getDevices(dataFlow));
    }

    /**
     * Gets the indicator which determines whether automatic gain control (AGC) 
     * is to be performed for captured audio.
     * 
     * @return <tt>true</tt> if automatic gain control (AGC) is to be performed
     * for captured audio; otherwise, <tt>false</tt>
     */
    public boolean isAutomaticGainControl()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean value = ((getFeatures() & FEATURE_AGC) == FEATURE_AGC);
        
        if (cfg != null)
            value = cfg.getBoolean(getPropertyName(PNAME_AGC), value);
        return value;
    }

    /**
     * Gets the indicator which determines whether noise suppression is to be
     * performed for captured audio.
     *
     * @return <tt>true</tt> if noise suppression is to be performed for
     * captured audio; otherwise, <tt>false</tt>
     */
    public boolean isDenoise()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean value = ((getFeatures() & FEATURE_DENOISE) == FEATURE_DENOISE);

        if (cfg != null)
            value = cfg.getBoolean(getPropertyName(PNAME_DENOISE), value);
        return value;
    }

    /**
     * Gets the indicator which determines whether echo cancellation is to be
     * performed for captured audio.
     *
     * @return <tt>true</tt> if echo cancellation is to be performed for
     * captured audio; otherwise, <tt>false</tt>
     */
    public boolean isEchoCancel()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean value
            = ((getFeatures() & FEATURE_ECHO_CANCELLATION)
                    == FEATURE_ECHO_CANCELLATION);

        if (cfg != null)
            value = cfg.getBoolean(getPropertyName(PNAME_ECHOCANCEL), value);
        return value;
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
        throws Exception
    {
        try
        {
            try
            {
                postInitializeSpecificDevices(DataFlow.CAPTURE);
            }
            finally
            {
                if ((FEATURE_NOTIFY_AND_PLAYBACK_DEVICES & getFeatures()) != 0)
                {
                    try
                    {
                        postInitializeSpecificDevices(DataFlow.NOTIFY);
                    }
                    finally
                    {
                        postInitializeSpecificDevices(DataFlow.PLAYBACK);
                    }
                }
            }
        }
        finally
        {
            super.postInitialize();
        }
    }

    /**
     * Sets the device lists after the different audio systems (PortAudio,
     * PulseAudio, etc) have finished detecting their devices.
     *
     * @param dataFlow the data flow of the devices to perform
     * post-initialization on
     */
    protected void postInitializeSpecificDevices(DataFlow dataFlow)
    {
        // Gets all current active devices.
        List<CaptureDeviceInfo2> activeDevices = getDevices(dataFlow);
        // Gets the default device.
        Devices devices = this.devices[dataFlow.ordinal()];
        CaptureDeviceInfo2 selectedActiveDevice
            = devices.getSelectedDevice(activeDevices);

        // Sets the default device as selected. The function will fire a
        // property change only if the device has changed from a previous
        // configuration. The "set" part is important because only the fired
        // property event provides a way to get the hotplugged devices working
        // during a call.
        devices.setDevice(selectedActiveDevice, false);
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
        throws Exception
    {
        super.preInitialize();

        if (devices == null)
        {
            devices = new Devices[3];
            devices[DataFlow.CAPTURE.ordinal()] = new CaptureDevices(this);
            devices[DataFlow.NOTIFY.ordinal()] = new NotifyDevices(this);
            devices[DataFlow.PLAYBACK.ordinal()] = new PlaybackDevices(this);
        }
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
    void propertyChange(String property, Object oldValue, Object newValue)
    {
        firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Sets the indicator which determines whether automatic gain control (AGC) 
     * is to be performed for captured audio.
     *
     * @param automaticGainControl <tt>true</tt> if automatic gain control (AGC)
     * is to be performed for captured audio; otherwise, <tt>false</tt>
     */
    public void setAutomaticGainControl(boolean automaticGainControl)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
            cfg.setProperty(getPropertyName(PNAME_AGC), automaticGainControl);
    }

    /**
     * Sets the list of a kind of devices: capture, notify or playback.
     *
     * @param captureDevices The list of a kind of devices: capture, notify or
     * playback.
     */
    protected void setCaptureDevices(List<CaptureDeviceInfo2> captureDevices)
    {
        devices[DataFlow.CAPTURE.ordinal()].setDevices(captureDevices);
    }

    /**
     * Sets the indicator which determines whether noise suppression is to be
     * performed for captured audio.
     *
     * @param denoise <tt>true</tt> if noise suppression is to be performed for
     * captured audio; otherwise, <tt>false</tt>
     */
    public void setDenoise(boolean denoise)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
            cfg.setProperty(getPropertyName(PNAME_DENOISE), denoise);
    }

    /**
     * Selects the active device.
     *
     * @param dataFlow the data flow of the device to set: capture, notify or
     * playback
     * @param device The selected active device.
     * @param save Flag set to true in order to save this choice in the
     * configuration. False otherwise.
     */
    public void setDevice(
            DataFlow dataFlow,
            CaptureDeviceInfo2 device,
            boolean save)
    {
        devices[dataFlow.ordinal()].setDevice(device,save);
    }

    /**
     * Sets the indicator which determines whether echo cancellation is to be
     * performed for captured audio.
     *
     * @param echoCancel <tt>true</tt> if echo cancellation is to be performed
     * for captured audio; otherwise, <tt>false</tt>
     */
    public void setEchoCancel(boolean echoCancel)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
            cfg.setProperty(getPropertyName(PNAME_ECHOCANCEL), echoCancel);
    }

    /**
     * Sets the list of the active devices.
     *
     * @param playbackDevices The list of the active devices.
     */
    protected void setPlaybackDevices(List<CaptureDeviceInfo2> playbackDevices)
    {
        devices[DataFlow.PLAYBACK.ordinal()].setDevices(playbackDevices);
        // The notify devices are the same as the playback devices.
        devices[DataFlow.NOTIFY.ordinal()].setDevices(playbackDevices);
    }
}
