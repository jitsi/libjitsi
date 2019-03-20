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
import java.util.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;
import org.jitsi.utils.*;

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
     * The list of <tt>DeviceSystem</tt>s which have been initialized.
     */
    private static List<DeviceSystem> deviceSystems
        = new LinkedList<DeviceSystem>();

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>DeviceSystem</tt> supports invoking its
     * {@link #initialize()} more than once.
     */
    public static final int FEATURE_REINITIALIZE = 1;

    public static final String LOCATOR_PROTOCOL_ANDROIDCAMERA = "androidcamera";

    public static final String LOCATOR_PROTOCOL_CIVIL = "civil";

    public static final String LOCATOR_PROTOCOL_DIRECTSHOW = "directshow";

    public static final String LOCATOR_PROTOCOL_IMGSTREAMING = "imgstreaming";

    public static final String LOCATOR_PROTOCOL_MEDIARECORDER = "mediarecorder";

    public static final String LOCATOR_PROTOCOL_QUICKTIME = "quicktime";

    public static final String LOCATOR_PROTOCOL_VIDEO4LINUX2 = "video4linux2";

    /**
     * The <tt>Logger</tt> used by the <tt>DeviceSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(DeviceSystem.class);

    /**
     * The list of <tt>CaptureDeviceInfo</tt>s representing the devices of this
     * instance at the time its {@link #preInitialize()} method was last
     * invoked.
     */
    private static List<CaptureDeviceInfo> preInitializeDevices;

    public static final String PROP_DEVICES = "devices";

    /**
     * Returns a <tt>List</tt> of <tt>CaptureDeviceInfo</tt>s which are elements
     * of a specific <tt>List</tt> of <tt>CaptureDeviceInfo</tt>s and have a
     * specific <tt>MediaLocator</tt> protocol.
     *
     * @param deviceList the <tt>List</tt> of <tt>CaptureDeviceInfo</tt> which
     * are to be filtered based on the specified <tt>MediaLocator</tt> protocol
     * @param locatorProtocol the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt>s which are to be returned
     * @return a <tt>List</tt> of <tt>CaptureDeviceInfo</tt>s which are elements
     * of the specified <tt>deviceList</tt> and have the specified
     * <tt>locatorProtocol</tt>
     */
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
        /*
         * Detect the audio capture devices unless the configuration explicitly
         * states that they are to not be detected.
         */
        if (MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.AUDIO))
        {
            if (logger.isInfoEnabled())
                logger.info("Initializing audio devices");

            initializeDeviceSystems(MediaType.AUDIO);
        }

        /*
         * Detect the video capture devices unless the configuration explicitly
         * states that they are to not be detected.
         */
        if (MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.VIDEO))
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
                    (OSUtils.IS_LINUX || OSUtils.IS_FREEBSD)
                        ? ".PulseAudioSystem"
                        : null,
                    OSUtils.IS_WINDOWS ? ".WASAPISystem" : null,
                    OSUtils.IS_MAC ? ".MacCoreaudioSystem" : null,
                    OSUtils.IS_ANDROID ? null : ".PortAudioSystem",
                    ".AudioSilenceSystem",
                    ".NoneAudioSystem"
                };
            break;
        case VIDEO:
            classNames
                = new String[]
                {
                    OSUtils.IS_ANDROID ? ".MediaRecorderSystem" : null,
                    OSUtils.IS_ANDROID ? ".AndroidCameraSystem" : null,
                    (OSUtils.IS_LINUX || OSUtils.IS_FREEBSD)
                        ? ".Video4Linux2System"
                        : null,
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

                // we can explicitly disable an audio system
                if (Boolean.getBoolean(className + ".disabled"))
                    continue;

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
                        else
                        {
                            logger.warn(
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
                        invokeDeviceSystemInitialize(deviceSystem);
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                        else
                        {
                            logger.warn(
                                    "Failed to reinitialize " + className,
                                    t);
                        }
                    }
                }
            }
        }
    }

    /**
     * Invokes {@link #initialize()} on a specific <tt>DeviceSystem</tt>. The
     * method returns after the invocation returns.
     *
     * @param deviceSystem the <tt>DeviceSystem</tt> to invoke
     * <tt>initialize()</tt> on
     * @throws Exception if an error occurs during the initialization of
     * <tt>initialize()</tt> on the specified <tt>deviceSystem</tt>
     */
    static void invokeDeviceSystemInitialize(DeviceSystem deviceSystem)
        throws Exception
    {
        invokeDeviceSystemInitialize(deviceSystem, false);
    }

    /**
     * Invokes {@link #initialize()} on a specific <tt>DeviceSystem</tt>.
     *
     * @param deviceSystem the <tt>DeviceSystem</tt> to invoke
     * <tt>initialize()</tt> on
     * @param asynchronous <tt>true</tt> if the invocation is to be performed in
     * a separate thread and the method is to return immediately without waiting
     * for the invocation to return; otherwise, <tt>false</tt>
     * @throws Exception if an error occurs during the initialization of
     * <tt>initialize()</tt> on the specified <tt>deviceSystem</tt>
     */
    static void invokeDeviceSystemInitialize(
            final DeviceSystem deviceSystem,
            boolean asynchronous)
        throws Exception
    {
        if (OSUtils.IS_WINDOWS || asynchronous)
        {
            /*
             * The use of Component Object Model (COM) technology is common on
             * Windows. The initialization of the COM library is done per
             * thread. However, there are multiple concurrency models which may
             * interfere among themselves. Dedicate a new thread on which the
             * COM library has surely not been initialized per invocation of
             * initialize().
             */

            final String className = deviceSystem.getClass().getName();
            final Throwable[] exception = new Throwable[1];
            Thread thread
                = new Thread(className + ".initialize()")
                {
                    @Override
                    public void run()
                    {
                        boolean loggerIsTraceEnabled = logger.isTraceEnabled();

                        try
                        {
                            if (loggerIsTraceEnabled)
                                logger.trace("Will initialize " + className);

                            deviceSystem.initialize();

                            if (loggerIsTraceEnabled)
                                logger.trace("Did initialize " + className);
                        }
                        catch (Throwable t)
                        {
                            exception[0] = t;
                            if (t instanceof ThreadDeath)
                                throw (ThreadDeath) t;
                        }
                    }
                };

            thread.setDaemon(true);
            thread.start();

            if (asynchronous)
                return;

            /*
             * Wait for the initialize() invocation on deviceSystem to return
             * i.e. the thread to die.
             */
            boolean interrupted = false;

            while (thread.isAlive())
            {
                try
                {
                    thread.join();
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();

            /* Re-throw any exception thrown by the thread. */
            Throwable t = exception[0];

            if (t != null)
            {
                if (t instanceof Exception)
                    throw (Exception) t;
                else
                    throw new UndeclaredThrowableException(t);
            }
        }
        else
            deviceSystem.initialize();
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

        invokeDeviceSystemInitialize(this);
    }

    /**
     * Initializes a new <tt>Renderer</tt> instance which is to perform playback
     * on a device contributed by this system.
     *
     * @return a new <tt>Renderer</tt> instance which is to perform playback on
     * a device contributed by this system or <tt>null</tt>
     */
    public Renderer createRenderer()
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
                    logger.error(
                            "Failed to initialize a new " + className
                                + " instance",
                            t);
                }
            }
        }
        return null;
    }

    /**
     * Invoked by {@link #initialize()} to perform the very logic of the
     * initialization of this <tt>DeviceSystem</tt>. This instance has been
     * prepared for initialization by an earlier call to
     * {@link #preInitialize()} and the initialization will be completed with a
     * subsequent call to {@link #postInitialize()}.
     *
     * @throws Exception if an error occurs during the initialization of this
     * instance. The initialization of this instance will be completed with a
     * subsequent call to <tt>postInitialize()</tt> regardless of any
     * <tt>Exception</tt> thrown by <tt>doInitialize()</tt>.
     */
    protected abstract void doInitialize()
        throws Exception;

    /**
     * Gets the flags indicating the optional features supported by this
     * <tt>DeviceSystem</tt>.
     *
     * @return the flags indicating the optional features supported by this
     * <tt>DeviceSystem</tt>. The possible flags are among the
     * <tt>FEATURE_XXX</tt> constants defined by the <tt>DeviceSystem</tt> class
     * and its extenders.
     */
    public final int getFeatures()
    {
        return features;
    }

    /**
     * Returns the format depending on the media type: AudioFormat for AUDIO,
     * VideoFormat for VIDEO. Otherwise, returns null.
     *
     * @return The format depending on the media type: AudioFormat for AUDIO,
     * VideoFormat for VIDEO. Otherwise, returns null.
     */
    public Format getFormat()
    {
        Format format = null;

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

        return format;
    }

    /**
     * Gets the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt>s (to be) registered (with FMJ) by this
     * <tt>DeviceSystem</tt>. The protocol is a unique identifier of a
     * <tt>DeviceSystem</tt>.
     *
     * @return the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt>s (to be) registered (with FMJ) by this
     * <tt>DeviceSystem</tt>
     */
    public final String getLocatorProtocol()
    {
        return locatorProtocol;
    }

    public final MediaType getMediaType()
    {
        return mediaType;
    }

    /**
     * Gets the name of the class which implements the <tt>Renderer</tt>
     * interface to render media on a playback or notification device associated
     * with this <tt>DeviceSystem</tt>. Invoked by
     * {@link #createRenderer(boolean)}.
     *
     * @return the name of the class which implements the <tt>Renderer</tt>
     * interface to render media on a playback or notification device associated
     * with this <tt>DeviceSystem</tt> or <tt>null</tt> if no <tt>Renderer</tt>
     * instance is to be created by the <tt>DeviceSystem</tt> implementation or
     * <tt>createRenderer(boolean) is overridden.
     */
    protected String getRendererClassName()
    {
        return null;
    }

    /**
     * Initializes this <tt>DeviceSystem</tt> i.e. represents the native/system
     * devices in the terms of the application so that they may be utilized. For
     * example, the capture devices are represented as
     * <tt>CaptureDeviceInfo</tt> instances registered with FMJ.
     * <p>
     * <b>Note</b>: The method is synchronized on this instance in order to
     * guarantee that the whole initialization procedure (which includes
     * {@link #doInitialize()}) executes once at any given time.
     * </p>
     *
     * @throws Exception if an error occurs during the initialization of this
     * <tt>DeviceSystem</tt>
     */
    protected final synchronized void initialize()
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
        throws Exception
    {
        try
        {
            Format format = getFormat();

            if (format != null)
            {
                /*
                 * Calculate the lists of old and new devices and report them in
                 * a PropertyChangeEvent about PROP_DEVICES.
                 */
                @SuppressWarnings("unchecked")
                List<CaptureDeviceInfo> cdis
                    = CaptureDeviceManager.getDeviceList(format);
                List<CaptureDeviceInfo> postInitializeDevices
                    = new ArrayList<CaptureDeviceInfo>(cdis);

                if (preInitializeDevices != null)
                {
                    for (Iterator<CaptureDeviceInfo> preIter
                                = preInitializeDevices.iterator();
                            preIter.hasNext();)
                    {
                        if (postInitializeDevices.remove(preIter.next()))
                            preIter.remove();
                    }
                }

                /*
                 * Fire a PropertyChangeEvent but only if there is an actual
                 * change in the value of the property.
                 */
                int preInitializeDeviceCount
                    = (preInitializeDevices == null)
                        ? 0
                        : preInitializeDevices.size();

                if (preInitializeDeviceCount != 0
                        || postInitializeDevices.size() != 0)
                {
                    firePropertyChange(
                            PROP_DEVICES,
                            preInitializeDevices,
                            postInitializeDevices);
                }
            }
        }
        finally
        {
            preInitializeDevices = null;
        }
    }

    /**
     * Invoked as part of the execution of {@link #initialize()} before the
     * execution of {@link #doInitialize()}. The implementation of
     * <tt>DeviceSystem</tt> removes from FMJ's <tt>CaptureDeviceManager</tt>
     * the <tt>CaptureDeviceInfo</tt>s whose <tt>MediaLocator</tt> has the same
     * protocol as {@link #getLocatorProtocol()} of this instance.
     */
    protected void preInitialize()
        throws Exception
    {
        Format format = getFormat();

        if (format != null)
        {
            @SuppressWarnings("unchecked")
            List<CaptureDeviceInfo> cdis
                = CaptureDeviceManager.getDeviceList(format);

            preInitializeDevices = new ArrayList<CaptureDeviceInfo>(cdis);

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
     * The implementation of <tt>DeviceSystem</tt> returns the protocol of the
     * <tt>MediaLocator</tt>s of the <tt>CaptureDeviceInfo</tt>s (to be)
     * registered by this <tt>DeviceSystem</tt>.
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
