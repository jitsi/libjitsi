/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.lang.ref.*;
import java.util.*;
import java.util.regex.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.util.*;

/**
 * Creates MacCoreaudio capture devices by enumerating all host devices that
 * have input channels.
 *
 * @author Vincent Lucas
 */
public class MacCoreaudioSystem
    extends AudioSystem
{
    /**
     * Represents a listener which is to be notified before and after
     * MacCoreaudio's native function <tt>UpdateAvailableDeviceList()</tt> is
     * invoked.
     */
    public interface UpdateAvailableDeviceListListener
        extends EventListener
    {
        /**
         * Notifies this listener that MacCoreaudio's native function
         * <tt>UpdateAvailableDeviceList()</tt> was invoked.
         *
         * @throws Exception if this implementation encounters an error. Any
         * <tt>Throwable</tt> apart from <tt>ThreadDeath</tt> will be ignored
         * after it is logged for debugging purposes.
         */
        void didUpdateAvailableDeviceList()
            throws Exception;

        /**
         * Notifies this listener that MacCoreaudio's native function
         * <tt>UpdateAvailableDeviceList()</tt> will be invoked.
         *
         * @throws Exception if this implementation encounters an error. Any
         * <tt>Throwable</tt> apart from <tt>ThreadDeath</tt> will be ignored
         * after it is logged for debugging purposes.
         */
        void willUpdateAvailableDeviceList()
            throws Exception;
    }

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying MacCoreaudio
     * <tt>CaptureDevice</tt>s
     */
    private static final String LOCATOR_PROTOCOL
        = LOCATOR_PROTOCOL_MACCOREAUDIO;

    /**
     * The <tt>Logger</tt> used by the <tt>MacCoreaudioSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MacCoreaudioSystem.class);

    /**
     * The number of times that {@link #willPaOpenStream()} has been
     * invoked without an intervening {@link #didPaOpenStream()} i.e. the
     * number of MacCoreaudio clients which are currently executing
     * <tt>Pa_OpenStream</tt> and which are thus inhibiting
     * <tt>Pa_UpdateAvailableDeviceList</tt>.
     */
    private static int openStream = 0;

    /**
     * The <tt>Object</tt> which synchronizes that access to
     * {@link #paOpenStream} and {@link #updateAvailableDeviceList}.
     */
    private static final Object openStreamSyncRoot = new Object();

    /**
     * The number of times that {@link #willPaUpdateAvailableDeviceList()}
     * has been invoked without an intervening
     * {@link #didPaUpdateAvailableDeviceList()} i.e. the number of
     * MacCoreaudio clients which are currently executing
     * <tt>Pa_UpdateAvailableDeviceList</tt> and which are thus inhibiting
     * <tt>Pa_OpenStream</tt>.
     */
    private static int updateAvailableDeviceList = 0;

    /**
     * The list of <tt>PaUpdateAvailableDeviceListListener</tt>s which are to be
     * notified before and after MacCoreaudio's native function
     * <tt>Pa_UpdateAvailableDeviceList()</tt> is invoked.
     */
    private static final List<WeakReference<UpdateAvailableDeviceListListener>>
        updateAvailableDeviceListListeners
        = new LinkedList<WeakReference<UpdateAvailableDeviceListListener>>();

    /**
     * The <tt>Object</tt> which ensures that MacCoreaudio's native function
     * <tt>UpdateAvailableDeviceList()</tt> will not be invoked concurrently.
     * The condition should hold true on the native side but, anyway, it shoul
     * not hurt (much) to enforce it on the Java side as well.
     */
    private static final Object updateAvailableDeviceListSyncRoot
        = new Object();

    /**
     * Adds a listener which is to be notified before and after MacCoreaudio's
     * native function <tt>UpdateAvailableDeviceList()</tt> is invoked.
     * <p>
     * <b>Note</b>: The <tt>MacCoreaudioSystem</tt> class keeps a
     * <tt>WeakReference</tt> to the specified <tt>listener</tt> in order to
     * avoid memory leaks.
     * </p>
     *
     * @param listener the <tt>UpdateAvailableDeviceListListener</tt> to be
     * notified before and after MacCoreaudio's native function
     * <tt>UpdateAvailableDeviceList()</tt> is invoked
     */
    public static void addUpdateAvailableDeviceListListener(
            UpdateAvailableDeviceListListener listener)
    {
        if(listener == null)
            throw new NullPointerException("listener");

        synchronized(updateAvailableDeviceListListeners)
        {
            Iterator<WeakReference<UpdateAvailableDeviceListListener>> i
                = updateAvailableDeviceListListeners.iterator();
            boolean add = true;

            while(i.hasNext())
            {
                UpdateAvailableDeviceListListener l = i.next().get();

                if(l == null)
                    i.remove();
                else if(l.equals(listener))
                    add = false;
            }
            if(add)
            {
                updateAvailableDeviceListListeners.add(
                        new WeakReference<UpdateAvailableDeviceListListener>(
                                listener));
            }
        }
    }

    /**
     * Notifies <tt>MacCoreaudioSystem</tt> that a MacCoreaudio client finished
     * executing <tt>OpenStream</tt>.
     */
    public static void didOpenStream()
    {
        synchronized (openStreamSyncRoot)
        {
            openStream--;
            if (openStream < 0)
                openStream = 0;

            openStreamSyncRoot.notifyAll();
        }
    }

    /**
     * Notifies <tt>MacCoreaudioSystem</tt> that a MacCoreaudio client finished
     * executing <tt>UpdateAvailableDeviceList</tt>.
     */
    private static void didUpdateAvailableDeviceList()
    {
        synchronized(openStreamSyncRoot)
        {
            updateAvailableDeviceList--;
            if (updateAvailableDeviceList < 0)
                updateAvailableDeviceList = 0;

            openStreamSyncRoot.notifyAll();
        }

        fireUpdateAvailableDeviceListEvent(false);
    }

    /**
     * Notifies the registered <tt>UpdateAvailableDeviceListListener</tt>s
     * that MacCoreaudio's native function
     * <tt>UpdateAvailableDeviceList()</tt> will be or was invoked.
     *
     * @param will <tt>true</tt> if MacCoreaudio's native function
     * <tt>UpdateAvailableDeviceList()</tt> will be invoked or <tt>false</tt>
     * if it was invoked
     */
    private static void fireUpdateAvailableDeviceListEvent(boolean will)
    {
        try
        {
            List<WeakReference<UpdateAvailableDeviceListListener>> ls;

            synchronized(updateAvailableDeviceListListeners)
            {
                ls = new
                    ArrayList<WeakReference<UpdateAvailableDeviceListListener>>(
                            updateAvailableDeviceListListeners);
            }

            for(WeakReference<UpdateAvailableDeviceListListener> wr : ls)
            {
                UpdateAvailableDeviceListListener l = wr.get();
                if(l != null)
                {
                    try
                    {
                        if(will)
                            l.willUpdateAvailableDeviceList();
                        else
                            l.didUpdateAvailableDeviceList();
                    }
                    catch (Throwable t)
                    {
                        if(t instanceof ThreadDeath)
                            throw(ThreadDeath) t;
                        else
                        {
                            logger.error(
                                    "UpdateAvailableDeviceListListener."
                                    + (will ? "will" : "did")
                                    + "UpdateAvailableDeviceList failed.",
                                    t);
                        }
                    }
                }
            }
        }
        catch(Throwable t)
        {
            if(t instanceof ThreadDeath)
                throw(ThreadDeath) t;
        }
    }

    /**
     * Gets a sample rate supported by a MacCoreaudio device with a specific
     * device index with which it is to be registered with JMF.
     *
     * @param input <tt>true</tt> if the supported sample rate is to be
     * retrieved for the MacCoreaudio device with the specified device index as
     * an input device or <tt>false</tt> for an output device
     * @param deviceUID The device identifier.
     * @param channelCount number of channel
     * @param sampleFormat sample format
     *
     * @return a sample rate supported by the MacCoreaudio device with the
     * specified device index with which it is to be registered with JMF
     */
    private static double getSupportedSampleRate(
            boolean input,
            String deviceUID)
    {
        double supportedSampleRate = MacCoreAudioDevice.DEFAULT_SAMPLE_RATE;
        double defaultSampleRate
            = MacCoreAudioDevice.getNominalSampleRate(deviceUID);

        if (defaultSampleRate >= MediaUtils.MAX_AUDIO_SAMPLE_RATE)
        {
            supportedSampleRate = defaultSampleRate;
        }

        return supportedSampleRate;
    }

    /**
     * Waits for all MacCoreaudio clients to finish executing
     * <tt>OpenStream</tt>.
     */
    private static void waitForOpenStream()
    {
        boolean interrupted = false;

        while(openStream > 0)
        {
            try
            {
                openStreamSyncRoot.wait();
            }
            catch(InterruptedException ie)
            {
                interrupted = true;
            }
        }
        if(interrupted)
            Thread.currentThread().interrupt();
    }

    /**
     * Waits for all MacCoreaudio clients to finish executing
     * <tt>UpdateAvailableDeviceList</tt>.
     */
    private static void waitForUpdateAvailableDeviceList()
    {
        boolean interrupted = false;

        while (updateAvailableDeviceList > 0)
        {
            try
            {
                openStreamSyncRoot.wait();
            }
            catch (InterruptedException ie)
            {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    /**
     * Notifies <tt>MacCoreaudioSystem</tt> that a MacCoreaudio client will
     * start executing <tt>OpenStream</tt>.
     */
    public static void willOpenStream()
    {
        synchronized (openStreamSyncRoot)
        {
            waitForUpdateAvailableDeviceList();

            openStream++;
            openStreamSyncRoot.notifyAll();
        }
    }

    /**
     * Notifies <tt>MacCoreaudioSystem</tt> that a MacCoreaudio client will
     * start executing <tt>UpdateAvailableDeviceList</tt>.
     */
    private static void willUpdateAvailableDeviceList()
    {
        synchronized(openStreamSyncRoot)
        {
            waitForOpenStream();

            updateAvailableDeviceList++;
            openStreamSyncRoot.notifyAll();
        }

        fireUpdateAvailableDeviceListEvent(true);
    }

    private Runnable devicesChangedCallback;

    /**
     * Initializes a new <tt>MacCoreaudioSystem</tt> instance which creates
     * MacCoreaudio capture and playback devices by enumerating all host devices
     * with input channels.
     *
     * @throws Exception if anything wrong happens while creating the
     * MacCoreaudio capture and playback devices
     */
    MacCoreaudioSystem()
        throws Exception
    {
        super(
                LOCATOR_PROTOCOL,
                0 | FEATURE_NOTIFY_AND_PLAYBACK_DEVICES | FEATURE_REINITIALIZE);
    }

    /**
     * Sorts a specific list of <tt>CaptureDeviceInfo2</tt>s so that the
     * ones representing USB devices appear at the beginning/top of the
     * specified list.
     *
     * @param devices the list of <tt>CaptureDeviceInfo2</tt>s to be
     * sorted so that the ones representing USB devices appear at the
     * beginning/top of the list
     */
    private void bubbleUpUsbDevices(List<CaptureDeviceInfo2> devices)
    {
        if(!devices.isEmpty())
        {
            List<CaptureDeviceInfo2> nonUsbDevices
                = new ArrayList<CaptureDeviceInfo2>(devices.size());

            for(Iterator<CaptureDeviceInfo2> i = devices.iterator();
                    i.hasNext();)
            {
                CaptureDeviceInfo2 d = i.next();

                if(!d.isSameTransportType("USB"))
                {
                    nonUsbDevices.add(d);
                    i.remove();
                }
            }
            if(!nonUsbDevices.isEmpty())
            {
                for (CaptureDeviceInfo2 d : nonUsbDevices)
                    devices.add(d);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doInitialize()
        throws Exception
    {
        if(!CoreAudioDevice.isLoaded)
        {
            String message = "MacOSX CoreAudio library is not loaded";
            if (logger.isInfoEnabled())
            {
                logger.info(message);
            }
            throw new Exception(message);
        }

        // Initializes the library only at the first run.
        if(devicesChangedCallback == null)
        {
            CoreAudioDevice.initDevices();
        }

        int channels = 1;
        int sampleSizeInBits = 16;
        String defaultInputdeviceUID
            = MacCoreAudioDevice.getDefaultInputDeviceUID();
        String defaultOutputdeviceUID
            = MacCoreAudioDevice.getDefaultOutputDeviceUID();
        List<CaptureDeviceInfo2> captureAndPlaybackDevices
            = new LinkedList<CaptureDeviceInfo2>();
        List<CaptureDeviceInfo2> captureDevices
            = new LinkedList<CaptureDeviceInfo2>();
        List<CaptureDeviceInfo2> playbackDevices
            = new LinkedList<CaptureDeviceInfo2>();
        final boolean loggerIsDebugEnabled = logger.isDebugEnabled();


        String[] deviceUIDList = MacCoreAudioDevice.getDeviceUIDList();
        for(int i = 0; i < deviceUIDList.length; ++i)
        {
            String deviceUID = deviceUIDList[i];
            String name = CoreAudioDevice.getDeviceName(deviceUID);
            boolean isInputDevice = MacCoreAudioDevice.isInputDevice(deviceUID);
            boolean isOutputDevice
                = MacCoreAudioDevice.isOutputDevice(deviceUID);
            String transportType
                = MacCoreAudioDevice.getTransportType(deviceUID);
            String modelIdentifier = null;
            String locatorRemainder = name;

            if (deviceUID != null)
            {
                modelIdentifier
                    = CoreAudioDevice.getDeviceModelIdentifier(deviceUID);
                locatorRemainder = deviceUID;
            }

            /*
             * TODO The intention of reinitialize() was to perform the
             * initialization from scratch. However, AudioSystem was later
             * changed to disobey. But we should at least search through both
             * CAPTURE_INDEX and PLAYBACK_INDEX.
             */
            List<CaptureDeviceInfo2> existingCdis
                = getDevices(DataFlow.CAPTURE);
            CaptureDeviceInfo2 cdi = null;

            if (existingCdis != null)
            {
                for (CaptureDeviceInfo2 existingCdi : existingCdis)
                {
                    /*
                     * The deviceUID is optional so a device may be identified
                     * by deviceUID if it is available or by name if the
                     * deviceUID is not available.
                     */
                    String id = existingCdi.getIdentifier();

                    if (id.equals(deviceUID) || id.equals(name))
                    {
                        cdi = existingCdi;
                        break;
                    }
                }
            }

            if (cdi == null)
            {
                cdi
                    = new CaptureDeviceInfo2(
                            name,
                            new MediaLocator(
                                LOCATOR_PROTOCOL + ":#" + locatorRemainder),
                            new Format[]
                            {
                                new AudioFormat(
                                    AudioFormat.LINEAR,
                                    isInputDevice
                                    ? getSupportedSampleRate(
                                        true,
                                        deviceUID)
                                    : MacCoreAudioDevice.DEFAULT_SAMPLE_RATE,
                                    sampleSizeInBits,
                                    channels,
                                    AudioFormat.LITTLE_ENDIAN,
                                    AudioFormat.SIGNED,
                                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                                    Format.NOT_SPECIFIED /* frameRate */,
                                    Format.byteArray)
                            },
                    deviceUID,
                    transportType,
                    modelIdentifier);
            }

            boolean isDefaultInputDevice
                = deviceUID.equals(defaultInputdeviceUID);
            boolean isDefaultOutputDevice
                = deviceUID.equals(defaultOutputdeviceUID);

            /*
             * When we perform automatic selection of capture and
             * playback/notify devices, we would like to pick up devices from
             * one and the same hardware because that sound like a natural
             * expectation from the point of view of the user. In order to
             * achieve that, we will bring the devices which support both
             * capture and playback to the top.
             */
            if(isInputDevice)
            {
                List<CaptureDeviceInfo2> devices;

                if(isOutputDevice)
                    devices = captureAndPlaybackDevices;
                else
                    devices = captureDevices;

                if(isDefaultInputDevice
                        || (isOutputDevice && isDefaultOutputDevice))
                {
                    devices.add(0, cdi);
                    if (loggerIsDebugEnabled)
                        logger.debug("Added default capture device: " + name);
                }
                else
                {
                    devices.add(cdi);
                    if (loggerIsDebugEnabled)
                        logger.debug("Added capture device: " + name);
                }

                if(loggerIsDebugEnabled && isInputDevice)
                {
                    if(isDefaultOutputDevice)
                        logger.debug("Added default playback device: " + name);
                    else
                        logger.debug("Added playback device: " + name);
                }
            }
            else if(isOutputDevice)
            {
                if(isDefaultOutputDevice)
                {
                    playbackDevices.add(0, cdi);
                    if (loggerIsDebugEnabled)
                        logger.debug("Added default playback device: " + name);
                }
                else
                {
                    playbackDevices.add(cdi);
                    if (loggerIsDebugEnabled)
                        logger.debug("Added playback device: " + name);
                }
            }
        }

        /*
         * Make sure that devices which support both capture and playback are
         * reported as such and are preferred over devices which support either
         * capture or playback (in order to achieve our goal to have automatic
         * selection pick up devices from one and the same hardware).
         */
        bubbleUpUsbDevices(captureDevices);
        bubbleUpUsbDevices(playbackDevices);
        if(!captureDevices.isEmpty() && !playbackDevices.isEmpty())
        {
            /*
             * Event if we have not been provided with the information regarding
             * the matching of the capture and playback/notify devices from one
             * and the same hardware, we may still be able to deduce it by
             * examining their names.
             */
            matchDevicesByName(captureDevices, playbackDevices);
        }
        /*
         * Of course, of highest reliability is the fact that a specific
         * instance supports both capture and playback.
         */
        if(!captureAndPlaybackDevices.isEmpty())
        {
            bubbleUpUsbDevices(captureAndPlaybackDevices);
            for (int i = captureAndPlaybackDevices.size() - 1; i >= 0; i--)
            {
                CaptureDeviceInfo2 cdi = captureAndPlaybackDevices.get(i);

                captureDevices.add(0, cdi);
                playbackDevices.add(0, cdi);
            }
        }

        setCaptureDevices(captureDevices);
        setPlaybackDevices(playbackDevices);

        if(devicesChangedCallback == null)
        {
            devicesChangedCallback
                = new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            reinitialize();
                        }
                        catch (Throwable t)
                        {
                            if (t instanceof ThreadDeath)
                                throw (ThreadDeath) t;

                            logger.warn(
                                "Failed to reinitialize MacCoreaudio devices",
                                t);
                        }
                    }
                };
            CoreAudioDevice.setDevicesChangedCallback(
                    devicesChangedCallback);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getRendererClassName()
    {
        return MacCoreaudioRenderer.class.getName();
    }

    /**
     * Attempts to reorder specific lists of capture and playback/notify
     * <tt>CaptureDeviceInfo2</tt>s so that devices from the same
     * hardware appear at the same indices in the respective lists. The judgment
     * with respect to the belonging to the same hardware is based on the names
     * of the specified <tt>CaptureDeviceInfo2</tt>s. The implementation
     * is provided as a fallback to stand in for scenarios in which more
     * accurate relevant information is not available.
     *
     * @param captureDevices
     * @param playbackDevices
     */
    private void matchDevicesByName(
            List<CaptureDeviceInfo2> captureDevices,
            List<CaptureDeviceInfo2> playbackDevices)
    {
        Iterator<CaptureDeviceInfo2> captureIter
            = captureDevices.iterator();
        Pattern pattern
            = Pattern.compile(
                    "array|headphones|microphone|speakers|\\p{Space}|\\(|\\)",
                    Pattern.CASE_INSENSITIVE);
        LinkedList<CaptureDeviceInfo2> captureDevicesWithPlayback
            = new LinkedList<CaptureDeviceInfo2>();
        LinkedList<CaptureDeviceInfo2> playbackDevicesWithCapture
            = new LinkedList<CaptureDeviceInfo2>();
        int count = 0;

        while (captureIter.hasNext())
        {
            CaptureDeviceInfo2 captureDevice = captureIter.next();
            String captureName = captureDevice.getName();

            if (captureName != null)
            {
                captureName = pattern.matcher(captureName).replaceAll("");
                if (captureName.length() != 0)
                {
                    Iterator<CaptureDeviceInfo2> playbackIter
                        = playbackDevices.iterator();
                    CaptureDeviceInfo2 matchingPlaybackDevice = null;

                    while (playbackIter.hasNext())
                    {
                        CaptureDeviceInfo2 playbackDevice
                            = playbackIter.next();
                        String playbackName = playbackDevice.getName();

                        if (playbackName != null)
                        {
                            playbackName
                                = pattern
                                    .matcher(playbackName)
                                        .replaceAll("");
                            if (captureName.equals(playbackName))
                            {
                                playbackIter.remove();
                                matchingPlaybackDevice = playbackDevice;
                                break;
                            }
                        }
                    }
                    if (matchingPlaybackDevice != null)
                    {
                        captureIter.remove();
                        captureDevicesWithPlayback.add(captureDevice);
                        playbackDevicesWithCapture.add(
                                matchingPlaybackDevice);
                        count++;
                    }
                }
            }
        }

        for (int i = count - 1; i >= 0; i--)
        {
            captureDevices.add(0, captureDevicesWithPlayback.get(i));
            playbackDevices.add(0, playbackDevicesWithCapture.get(i));
        }
    }

    /**
     * Reinitializes this <tt>MacCoreaudioSystem</tt> in order to bring it up to
     * date with possible changes in the MacCoreaudio devices. Invokes
     * <tt>Pa_UpdateAvailableDeviceList()</tt> to update the devices on the
     * native side and then {@link #initialize()} to reflect any changes on the
     * Java side. Invoked by MacCoreaudio when it detects that the list of
     * devices has changed.
     *
     * @throws Exception if there was an error during the invocation of
     * <tt>Pa_UpdateAvailableDeviceList()</tt> and
     * <tt>DeviceSystem.initialize()</tt>
     */
    private void reinitialize()
        throws Exception
    {
        synchronized (updateAvailableDeviceListSyncRoot)
        {
            willUpdateAvailableDeviceList();
            didUpdateAvailableDeviceList();
        }

        /*
         * XXX We will likely minimize the risk of crashes on the native side
         * even further by invoking initialize() with
         * Pa_UpdateAvailableDeviceList locked. Unfortunately, that will likely
         * increase the risks of deadlocks on the Java side.
         */
        invokeDeviceSystemInitialize(this);
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>MacCoreaudioSystem</tt> always returns
     * &quot;MacCoreaudio&quot;.
     */
    @Override
    public String toString()
    {
        return "MacCoreaudio";
    }
}
