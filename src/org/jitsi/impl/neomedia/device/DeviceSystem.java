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
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;

/**
 * Represents the base of a supported device system/backend such as DirectShow,
 * PortAudio, PulseAudio, QuickTime, video4linux2. A <tt>DeviceSystem</tt> is
 * initialized at a certain time (usually, during the initialization of the
 * <tt>MediaService</tt> implementation which is going to use it) and it
 * registers with FMJ the <tt>CaptureDevice</tt>s it will provide. In addition
 * to providing the devices for the purposes of capture, a <tt>DeviceSystem</tt>
 * also provides the devices on which playback is to be performed i.e. it acts
 * as a <tt>Renderer</tt> factory via its {@link #createRenderer(boolean)}
 * method.
 *
 * @author Lyubomir Marinov
 */
public abstract class DeviceSystem
    extends PropertyChangeNotifier
{
    /**
     * The <tt>Logger</tt> used by the <tt>DeviceSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(DeviceSystem.class);

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>DeviceSystem</tt> supports invoking its
     * {@link #initialize()} more than once.
     */
    public static final int FEATURE_REINITIALIZE = 1;

    public static final String LOCATOR_PROTOCOL_CIVIL = "civil";

    public static final String LOCATOR_PROTOCOL_DIRECTSHOW = "directshow";

    public static final String LOCATOR_PROTOCOL_IMGSTREAMING = "imgstreaming";

    public static final String LOCATOR_PROTOCOL_MEDIARECORDER = "mediarecorder";

    public static final String LOCATOR_PROTOCOL_QUICKTIME = "quicktime";

    public static final String LOCATOR_PROTOCOL_VIDEO4LINUX2 = "video4linux2";

    public static final String PROP_DEVICES = "devices";

    /**
     * The list of <tt>DeviceSystem</tt>s which have been initialized.
     */
    private static List<DeviceSystem> deviceSystems
        = new LinkedList<DeviceSystem>();

    protected static List<CaptureDeviceInfo> filterDeviceListByLocatorProtocol(
            List<CaptureDeviceInfo> deviceList,
            String locatorProtocol)
    {
        if ((deviceList != null) && (deviceList.size() > 0))
        {
            Iterator<CaptureDeviceInfo> deviceListIter = deviceList.iterator();

            while (deviceListIter.hasNext())
            {
                MediaLocator locator = deviceListIter.next().getLocator();

                if ((locator == null)
                        || !locatorProtocol.equalsIgnoreCase(
                                locator.getProtocol()))
                {
                    deviceListIter.remove();
                }
            }
        }
        return deviceList;
    }

    public static DeviceSystem[] getDeviceSystems(MediaType mediaType)
    {
        List<DeviceSystem> ret;

        synchronized (deviceSystems)
        {
            ret = new ArrayList<DeviceSystem>(deviceSystems.size());
            for (DeviceSystem deviceSystem : deviceSystems)
                if (deviceSystem.getMediaType().equals(mediaType))
                    ret.add(deviceSystem);
        }
        return ret.toArray(new DeviceSystem[ret.size()]);
    }

    /**
     * Initializes the <tt>DeviceSystem</tt> instances which are to represent
     * the supported device systems/backends such as DirectShow, PortAudio,
     * PulseAudio, QuickTime, video4linux2. The method may be invoked multiple
     * times. If a <tt>DeviceSystem</tt> has been initialized by a previous
     * invocation of the method, its {@link #initialize()} method will be called
     * again as part of the subsequent invocation only if the
     * <tt>DeviceSystem</tt> in question returns a set of flags from its
     * {@link #getFeatures()} method which contains the constant/flag
     * {@link #FEATURE_REINITIALIZE}.
     */
    public static void initializeDeviceSystems()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        /*
         * Detect the audio capture devices unless the configuration explicitly
         * states that they are to not be detected.
         */
        if (((cfg == null)
                || !cfg.getBoolean(
                        MediaServiceImpl.DISABLE_AUDIO_SUPPORT_PNAME,
                        false))
            && !Boolean.getBoolean(
                    MediaServiceImpl.DISABLE_AUDIO_SUPPORT_PNAME))
        {
            if (logger.isInfoEnabled())
                logger.info("Initializing audio devices");

            initializeDeviceSystems(MediaType.AUDIO);
        }

        /*
         * Detect the video capture devices unless the configuration explicitly
         * states that they are to not be detected.
         */
        if ((cfg == null)
                || !cfg.getBoolean(
                        MediaServiceImpl.DISABLE_VIDEO_SUPPORT_PNAME,
                        false))
        {
            if (logger.isInfoEnabled())
                logger.info("Initializing video devices");

            initializeDeviceSystems(MediaType.VIDEO);
        }
    }

    /**
     * Initializes the <tt>DeviceSystem</tt> instances which are to represent
     * the supported device systems/backends which are to capable of capturing
     * and playing back media of a specific type such as audio or video.
     *
     * @param mediaType the <tt>MediaType</tt> of the <tt>DeviceSystem</tt>s to
     * be initialized
     */
    public static void initializeDeviceSystems(MediaType mediaType)
    {
        /*
         * The list of supported DeviceSystem implementations if hard-coded. The
         * order of the classes is significant and represents a decreasing
         * preference with respect to which DeviceSystem is to be picked up as
         * the default one (for the specified mediaType, of course).
         */
        String[] classNames;

        switch (mediaType)
        {
        case AUDIO:
            classNames
                = new String[]
                {
                    OSUtils.IS_ANDROID ? ".AudioRecordSystem" : null,
                    OSUtils.IS_ANDROID ? ".OpenSLESSystem" : null,
                    OSUtils.IS_LINUX ? ".PulseAudioSystem" : null,
                    OSUtils.IS_ANDROID ? null : ".PortAudioSystem",
                    ".NoneAudioSystem"
                };
            break;
        case VIDEO:
            classNames
                = new String[]
                {
                    OSUtils.IS_ANDROID ? ".MediaRecorderSystem" : null,
                    OSUtils.IS_LINUX ? ".Video4Linux2System" : null,
                    OSUtils.IS_MAC ? ".QuickTimeSystem" : null,
                    OSUtils.IS_WINDOWS ? ".DirectShowSystem" : null,
                    ".ImgStreamingSystem"
                };
            break;
        default:
            throw new IllegalArgumentException("mediaType");
        }

        initializeDeviceSystems(classNames);
    }

    /**
     * Initializes the <tt>DeviceSystem</tt> instances specified by the names of
     * the classes which implement them. If a <tt>DeviceSystem</tt> instance has
     * already been initialized for a specific class name, no new instance of
     * the class in question will be initialized and rather the
     * {@link #initialize()} method of the existing <tt>DeviceSystem</tt>
     * instance will be invoked if the <tt>DeviceSystem</tt> instance returns a
     * set of flags from its {@link #getFeatures()} which contains
     * {@link #FEATURE_REINITIALIZE}.
     * 
     * @param classNames the names of the classes which extend the
     * <tt>DeviceSystem</tt> class and instances of which are to be initialized
     */
    private static void initializeDeviceSystems(String[] classNames)
    {
        synchronized (deviceSystems)
        {
            String packageName = null;

            for (String className : classNames)
            {
                if (className == null)
                    continue;

                if (className.startsWith("."))
                {
                    if (packageName == null)
                        packageName = DeviceSystem.class.getPackage().getName();
                    className = packageName + className;
                }

                // Initialize a single instance per className.
                DeviceSystem deviceSystem = null;

                for (DeviceSystem aDeviceSystem : deviceSystems)
                    if (aDeviceSystem.getClass().getName().equals(className))
                    {
                        deviceSystem = aDeviceSystem;
                        break;
                    }

                boolean reinitialize;

                if (deviceSystem == null)
                {
                    reinitialize = false;

                    Object o = null;

                    try
                    {
                        o = Class.forName(className).newInstance();
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                        else if (logger.isDebugEnabled())
                        {
                            logger.debug(
                                    "Failed to initialize " + className,
                                    t);
                        }
                    }
                    if (o instanceof DeviceSystem)
                    {
                        deviceSystem = (DeviceSystem) o;
                        if (!deviceSystems.contains(deviceSystem))
                            deviceSystems.add(deviceSystem);
                    }
                }
                else
                    reinitialize = true;

                // Reinitializing is an optional feature.
                if (reinitialize
                        && ((deviceSystem.getFeatures() & FEATURE_REINITIALIZE)
                                != 0))
                {
                    try
                    {
                        deviceSystem.initialize();
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                        else if (logger.isDebugEnabled())
                        {
                            logger.debug(
                                    "Failed to reinitialize " + className,
                                    t);
                        }
                    }
                }
            }
        }
    }

    /**
     * The set of flags indicating which optional features are supported by this
     * <tt>DeviceSystem</tt>. For example, the presence of the flag
     * {@link #FEATURE_REINITIALIZE} indicates that this instance is able to
     * deal with multiple consecutive invocations of its {@link #initialize()}
     * method.
     */
    private final int features;

    /**
     * The protocol of the <tt>MediaLocator</tt> of the
     * <tt>CaptureDeviceInfo</tt>s (to be) registered (with FMJ) by this
     * <tt>DeviceSystem</tt>. The protocol is a unique identifier of a
     * <tt>DeviceSystem</tt>.
     */
    private final String locatorProtocol;

    /**
     * The <tt>MediaType</tt> of this <tt>DeviceSystem</tt> i.e. the type of the
     * media that this instance supports for capture and playback such as audio
     * or video.
     */
    private final MediaType mediaType;

    protected DeviceSystem(MediaType mediaType, String locatorProtocol)
        throws Exception
    {
        this(mediaType, locatorProtocol, 0);
    }

    protected DeviceSystem(
            MediaType mediaType,
            String locatorProtocol,
            int features)
        throws Exception
    {
        if (mediaType == null)
            throw new NullPointerException("mediaType");
        if (locatorProtocol == null)
            throw new NullPointerException("locatorProtocol");

        this.mediaType = mediaType;
        this.locatorProtocol = locatorProtocol;
        this.features = features;

        initialize();
    }

    public Renderer createRenderer(boolean playback)
    {
        String className = getRendererClassName();

        if (className != null)
        {
            try
            {
                return (Renderer) Class.forName(className).newInstance();
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else
                {
                    logger.warn(
                            "Failed to initialize a new "
                                + className
                                + " instance",
                            t);
                }
            }
        }
        return null;
    }

    protected abstract void doInitialize()
        throws Exception;

    public final int getFeatures()
    {
        return features;
    }

    public final String getLocatorProtocol()
    {
        return locatorProtocol;
    }

    public final MediaType getMediaType()
    {
        return mediaType;
    }

    protected String getRendererClassName()
    {
        return null;
    }

    protected final void initialize()
        throws Exception
    {
        preInitialize();
        try
        {
            doInitialize();
        }
        finally
        {
            postInitialize();
        }
    }

    /**
     * Invoked as part of the execution of {@link #initialize()} after the
     * execution of {@link #doInitialize()} regardless of whether the latter
     * completed successfully. The implementation of <tt>DeviceSystem</tt> fires
     * a new <tt>PropertyChangeEvent</tt> to notify that the value of the
     * property {@link #PROP_DEVICES} of this instance may have changed i.e.
     * that the list of devices detected by this instance may have changed. 
     */
    protected void postInitialize()
    {
        firePropertyChange(PROP_DEVICES, null, null);
    }

    /**
     * Invoked as part of the execution of {@link #initialize()} before the
     * execution of {@link #doInitialize()}. The implementation of
     * <tt>DeviceSystem</tt> removes from FMJ's <tt>CaptureDeviceManager</tt>
     * the <tt>CaptureDeviceInfo</tt>s whose <tt>MediaLocator</tt> has the same
     * protocol as {@link #getLocatorProtocol()} of this instance.
     */
    protected void preInitialize()
    {
        Format format;

        switch (getMediaType())
        {
        case AUDIO:
            format = new AudioFormat(null);
            break;
        case VIDEO:
            format = new VideoFormat(null);
            break;
        default:
            format = null;
            break;
        }

        if (format != null)
        {
            @SuppressWarnings("unchecked")
            Vector<CaptureDeviceInfo> cdis
                = CaptureDeviceManager.getDeviceList(format);

            if ((cdis != null) && (cdis.size() > 0))
            {
                boolean commit = false;

                for (CaptureDeviceInfo cdi
                        : filterDeviceListByLocatorProtocol(
                                cdis,
                                getLocatorProtocol()))
                {
                    CaptureDeviceManager.removeDevice(cdi);
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
                        /*
                         * We do not really need commit but we have it for
                         * historical reasons.
                         */
                        if (logger.isDebugEnabled())
                        {
                            logger.debug(
                                    "Failed to commit CaptureDeviceManager",
                                    ioe);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns a human-readable representation of this <tt>DeviceSystem</tt>.
     *
     * @return a <tt>String</tt> which represents this <tt>DeviceSystem</tt> in
     * a human-readable form
     */
    @Override
    public String toString()
    {
        return getLocatorProtocol();
    }
}
