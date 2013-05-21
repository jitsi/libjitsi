/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.WASAPI.*;

import java.util.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;

/**
 * Implements an <tt>AudioSystem</tt> using Windows Audio Session API (WASAPI)
 * and related Core Audio APIs such as Multimedia Device (MMDevice) API.
 *
 * @author Lyubomir Marinov
 */
public class WASAPISystem
    extends AudioSystem
{
    /**
     * A GUID which identifies the audio session that streams belong to.
     */
    private static String audioSessionGuid;

    /**
     * The protocol of the <tt>MediaLocator</tt> identifying
     * <tt>CaptureDeviceInfo</tt> contributed by <tt>WASAPISystem</tt>.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_WASAPI;

    /**
     * The logger used by the <tt>WASAPISystem</tt> class and its instances to
     * log debugging information.
     */
    private static final Logger logger = Logger.getLogger(WASAPISystem.class);

    /**
     * The pointer to the native <tt>IMMDeviceEnumerator</tt> interface instance
     * which this <tt>WASAPISystem</tt> uses to enumerate the audio endpoint
     * devices. 
     */
    private long iMMDeviceEnumerator;

    /**
     * The <tt>IMMNotificationClient</tt> which is to notify this
     * <tt>WASAPISystem</tt> when an audio endpoint device is added or removed,
     * when the state or properties of an endpoint device change, or when there
     * is a change in the default role assigned to an endpoint device.
     */
    private IMMNotificationClient pNotify;

    /**
     * A <tt>WAVEFORMATEX</tt> instance allocated in {@link #preInitialize()},
     * freed in {@link #postInitialize()} and made available during the
     * execution of {@link #doInitialize()} in order to minimize memory
     * fragmentation.
     */
    private long waveformatex;

    /**
     * Initializes a new <tt>WASAPISystem</tt> instance.
     *
     * @throws Exception if anything goes wrong while initializing the new
     * <tt>WASAPISystem</tt> instance
     */
    WASAPISystem()
        throws Exception
    {
        super(
                LOCATOR_PROTOCOL,
                FEATURE_NOTIFY_AND_PLAYBACK_DEVICES | FEATURE_REINITIALIZE);
    }

    /**
     * Invokes the Windows API function <tt>CoInitializeEx</tt> (by way of
     * {@link WASAPI#CoInitializeEx(long, int)}) with arguments suitable to the
     * operation of <tt>WASAPIRenderer</tt>, <tt>WASAPIStream</tt> and
     * <tt>WASAPISystem</tt>.
     * <p>
     * Generally, the WASAPI integration is designed with
     * <tt>COINIT_MULTITHREADED</tt> in mind. However, it may turn out that it
     * works with <tt>COINIT_APARTMENTTHREADED</tt> as well.
     * </p>
     *
     * @return the value returned by the invocation of the Windows API function
     * <tt>CoInitializeEx</tt>
     * @throws HResultException if the invocation of the method
     * <tt>WASAPI.CoInitializeEx</tt> throws such an exception
     */
    public static int CoInitializeEx()
        throws HResultException
    {
        int hr;

        try
        {
            hr = WASAPI.CoInitializeEx(0, COINIT_MULTITHREADED);
        }
        catch (HResultException hre)
        {
            hr = hre.getHResult();
            switch (hr)
            {
            case RPC_E_CHANGED_MODE:
                hr = S_FALSE;
                // Do fall through.
            case S_FALSE:
            case S_OK:
                break;
            default:
                throw hre;
            }
        }
        return hr;
    }

    /**
     * {@inheritDoc}
     */
    protected void doInitialize()
        throws Exception
    {
        /*
         * XXX Multiple threads may invoke the initialization of a DeviceSystem
         * so we cannot be sure that the COM library has been initialized for
         * the current thread.
         */
        WASAPISystem.CoInitializeEx();

        if (iMMDeviceEnumerator == 0)
        {
            iMMDeviceEnumerator
                = CoCreateInstance(
                        CLSID_MMDeviceEnumerator,
                        0,
                        CLSCTX_ALL,
                        IID_IMMDeviceEnumerator);
            if (iMMDeviceEnumerator == 0)
                throw new IllegalStateException("iMMDeviceEnumerator");

            /*
             * Register this DeviceSystem to be notified when an audio endpoint
             * device is added or removed, when the state or properties of an
             * endpoint device change, or when there is a change in the default
             * role assigned to an endpoint device.
             */
            MMNotificationClient.RegisterEndpointNotificationCallback(pNotify);
        }

        long iMMDeviceCollection
            = IMMDeviceEnumerator_EnumAudioEndpoints(
                    iMMDeviceEnumerator,
                    eAll,
                    DEVICE_STATE_ACTIVE);
        List<CaptureDeviceInfo2> captureDevices;
        List<CaptureDeviceInfo2> playbackDevices;

        if (iMMDeviceCollection == 0)
        {
            throw new RuntimeException(
                    "IMMDeviceEnumerator_EnumAudioEndpoints");
        }
        try
        {
            int count = IMMDeviceCollection_GetCount(iMMDeviceCollection);

            captureDevices = new ArrayList<CaptureDeviceInfo2>(count);
            playbackDevices = new ArrayList<CaptureDeviceInfo2>(count);
            for (int i = 0; i < count; i++)
            {
                long iMMDevice
                    = IMMDeviceCollection_Item(iMMDeviceCollection, i);

                if (iMMDevice == 0)
                    throw new RuntimeException("IMMDeviceCollection_Item");
                try
                {
                    doInitializeIMMDevice(
                            iMMDevice,
                            captureDevices, playbackDevices);
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    /*
                     * We do not want the initialization of one IMMDevice to
                     * prevent the initialization of other IMMDevices.
                     */
                    logger.error(
                            "Failed to doInitialize for IMMDevice at index "
                                + i,
                            t);
                }
                finally
                {
                    IMMDevice_Release(iMMDevice);
                }
            }
        }
        finally
        {
            IMMDeviceCollection_Release(iMMDeviceCollection);
        }

        setCaptureDevices(captureDevices);
        setPlaybackDevices(playbackDevices);
    }

    /**
     * Implements the part of {@link #doInitialize()} related to a specific
     * <tt>IMMDevice</tt>.
     *
     * @param iMMDevice the <tt>IMMDevice</tt> to initialize as part of the
     * invocation of <tt>doInitialize()</tt> on this instance
     * @throws HResultException if an error occurs while initializing the
     * specified <tt>iMMDevice</tt> in a native WASAPI function which returns an
     * <tt>HRESULT</tt> value
     * @param captureDevices the state of the execution of
     * <tt>doInitialize()</tt> which stores the <tt>CaptureDeviceInfo2</tt>s of
     * the capture devices discovered by this <tt>WASAPISystem</tt>
     * @param playbackDevices the state of the execution of
     * <tt>doInitialize()</tt> which stores the <tt>CaptureDeviceInfo2</tt>s of
     * the playback devices discovered by this <tt>WASAPISystem</tt>
     */
    private void doInitializeIMMDevice(
            long iMMDevice,
            List<CaptureDeviceInfo2> captureDevices,
            List<CaptureDeviceInfo2> playbackDevices)
        throws HResultException
    {
        String id = IMMDevice_GetId(iMMDevice);

        /*
         * The ID of the IMMDevice is required because it will be used within
         * the MediaLocator of its representative CaptureDeviceInfo.
         */
        if (id == null)
            throw new RuntimeException("IMMDevice_GetId");

        long iAudioClient
            = IMMDevice_Activate(iMMDevice, IID_IAudioClient, CLSCTX_ALL, 0);
        List<AudioFormat> formats;

        if (iAudioClient == 0)
            throw new RuntimeException("IMMDevice_Activate");
        try
        {
            formats = getIAudioClientSupportedFormats(iAudioClient);
        }
        finally
        {
            IAudioClient_Release(iAudioClient);
        }
        if ((formats != null) && !formats.isEmpty())
        {
            String name = null;

            try
            {
                name = getIMMDeviceFriendlyName(iMMDevice);
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                logger.warn(
                        "Failed to retrieve the PKEY_Device_FriendlyName"
                            + " of IMMDevice " + id,
                        t);
            }
            if ((name == null) || (name.length() == 0))
                name = id;

            int dataFlow = getIMMDeviceDataFlow(iMMDevice);
            CaptureDeviceInfo2 cdi2
                = new CaptureDeviceInfo2(
                        name,
                        new MediaLocator(LOCATOR_PROTOCOL + ":" + id),
                        formats.toArray(new Format[formats.size()]),
                        id,
                        /* transportType */ null,
                        /* modelIdentifier */ null);

            switch (dataFlow)
            {
            case eCapture:
                captureDevices.add(cdi2);
                break;
            case eRender:
                playbackDevices.add(cdi2);
                break;
            default:
                logger.error(
                        "Failed to retrieve dataFlow from IMMEndpoint " + id);
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalize()
        throws Throwable
    {
        try
        {
            if (iMMDeviceEnumerator != 0)
            {
                IMMDeviceEnumerator_Release(iMMDeviceEnumerator);
                iMMDeviceEnumerator = 0;
            }
        }
        finally
        {
            super.finalize();
        }
    }

    /**
     * Gets an array of alternative <tt>AudioFormat</tt>s based on
     * <tt>format</tt> with which an attempt is to be made to initialize a new
     * <tt>IAudioClient</tt> instance.
     *
     * @param format the <tt>AudioFormat</tt> on which the alternative
     * <tt>AudioFormat</tt>s are to be based
     * @return an array of alternative <tt>AudioFormat</tt>s based on
     * <tt>format</tt> with which an attempt is to be made to initialize a new
     * <tt>IAudioClient</tt> instance
     */
    public static AudioFormat[] getFormatsToInitializeIAudioClient(
            AudioFormat format)
    {
        // We are able to convert between mono and stereo.
        int channels;

        switch (format.getChannels())
        {
        case 1:
            channels = 2;
            break;
        case 2:
            channels = 1;
            break;
        default:
            return new AudioFormat[] { format };
        }
        return
            new AudioFormat[]
                    {
                        /*
                         * Regardless of the differences in the states of the
                         * support of mono and stereo in the library at the time
                         * of this writing, try to initialize a new IAudioClient
                         * instance with a format which will not require
                         * conversion between mono and stereo.
                         */
                        format,
                        new AudioFormat(
                                format.getEncoding(),
                                format.getSampleRate(),
                                format.getSampleSizeInBits(),
                                channels,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED,
                                Format.NOT_SPECIFIED /* frameSizeInBits */,
                                Format.NOT_SPECIFIED /* frameRate */,
                                format.getDataType())
                    };
    }

    /**
     * Gets a <tt>List</tt> of the <tt>AudioFormat</tt>s supported by a specific
     * <tt>IAudioClient</tt>.
     *
     * @param iAudioClient the <tt>IAudioClient</tt> to get the <tt>List</tt> of
     * supported <tt>AudioFormat</tt>s of
     * @return a <tt>List</tt> of the <tt>AudioFormat</tt>s supported by the
     * specified <tt>iAudioClient</tt>
     * @throws HResultException if an error occurs while retrieving the
     * <tt>List</tt> of <tt>AudioFormat</tt>s supported by the specified
     * <tt>iAudioClient</tt> in a native WASAPI function which returns an
     * <tt>HRESULT</tt> value
     */
    private List<AudioFormat> getIAudioClientSupportedFormats(long iAudioClient)
        throws HResultException
    {
        char cbSize = 0;
        List<AudioFormat> supportedFormats = new ArrayList<AudioFormat>();

        for (char nChannels = 1; nChannels <= 2; nChannels++)
        {
            for (int i = 0; i < Constants.AUDIO_SAMPLE_RATES.length; i++)
            {
                int nSamplesPerSec = (int) Constants.AUDIO_SAMPLE_RATES[i];

                for (char wBitsPerSample = 16;
                        wBitsPerSample > 0;
                        wBitsPerSample -= 8)
                {
                    char nBlockAlign
                        = (char) ((nChannels * wBitsPerSample) / 8);

                    WASAPI.WAVEFORMATEX_fill(
                            waveformatex,
                            WAVE_FORMAT_PCM,
                            nChannels,
                            nSamplesPerSec,
                            nSamplesPerSec * nBlockAlign,
                            nBlockAlign,
                            wBitsPerSample,
                            cbSize);

                    long pClosestMatch
                        = IAudioClient_IsFormatSupported(
                                iAudioClient,
                                AUDCLNT_SHAREMODE_SHARED,
                                waveformatex);

                    if (pClosestMatch == 0) // not supported
                        continue;
                    try
                    {
                        /*
                         * Succeeded with a closest match to the specified
                         * format?
                         */
                        if (pClosestMatch != waveformatex)
                        {
                            // We support AutioFormat.LINEAR only.
                            if (WAVEFORMATEX_getWFormatTag(pClosestMatch)
                                    != WAVE_FORMAT_PCM)
                                continue;

                            nChannels
                                = WAVEFORMATEX_getNChannels(pClosestMatch);
                            nSamplesPerSec
                                = WAVEFORMATEX_getNSamplesPerSec(
                                        pClosestMatch);
                            wBitsPerSample
                                = WAVEFORMATEX_getWBitsPerSample(
                                        pClosestMatch);
                        }

                        AudioFormat supportedFormat;

                        /*
                         * We are able to convert between mono and stereo.
                         * Additionally, the stereo support within the library
                         * is not as advanced as the mono support at the time of
                         * this writing.
                         */
                        if (nChannels == 2)
                        {
                            supportedFormat
                                = new AudioFormat(
                                        AudioFormat.LINEAR,
                                        nSamplesPerSec,
                                        wBitsPerSample,
                                        /* channels */ 1,
                                        AudioFormat.LITTLE_ENDIAN,
                                        AudioFormat.SIGNED,
                                        /* frameSizeInBits */ Format.NOT_SPECIFIED,
                                        /* frameRate */ Format.NOT_SPECIFIED,
                                        Format.byteArray);
                            if (!supportedFormats.contains(supportedFormat))
                                supportedFormats.add(supportedFormat);
                        }

                        supportedFormat
                            = new AudioFormat(
                                    AudioFormat.LINEAR,
                                    nSamplesPerSec,
                                    wBitsPerSample,
                                    nChannels,
                                    AudioFormat.LITTLE_ENDIAN,
                                    AudioFormat.SIGNED,
                                    /* frameSizeInBits */ Format.NOT_SPECIFIED,
                                    /* frameRate */ Format.NOT_SPECIFIED,
                                    Format.byteArray);
                        if (!supportedFormats.contains(supportedFormat))
                            supportedFormats.add(supportedFormat);
                    }
                    finally
                    {
                        if (pClosestMatch != waveformatex)
                            CoTaskMemFree(pClosestMatch);
                    }
                }
            }
        }
        return supportedFormats;
    }

    /**
     * Gets an audio endpoint device that is identified by a specific endpoint
     * ID string.
     *
     * @param id the endpoing ID string which identifies the audio endpoint
     * device to be retrieved
     * @return an <tt>IMMDevice</tt> instance which represents the audio
     * endpoint device that is identified by the specified enpoint ID string
     * @throws HResultException if an error occurs while retrieving the audio
     * endpoint device that is identified by the specified endpoint ID string in
     * a native WASAPI function which returns an <tt>HRESULT</tt> value
     */
    public long getIMMDevice(String id)
        throws HResultException
    {
        long iMMDeviceEnumerator = this.iMMDeviceEnumerator;

        if (iMMDeviceEnumerator == 0)
            throw new IllegalStateException("iMMDeviceEnumerator");
        else
            return IMMDeviceEnumerator_GetDevice(iMMDeviceEnumerator, id);
    }

    /**
     * Gets the data flow of a specific <tt>IMMDevice</tt> in the form of an
     * <tt>EDataFlow</tt> value.
     *
     * @param iMMDevice the <tt>IMMDevice</tt> to get the data flow of
     * @return an <tt>EDataFlow</tt> value which represents the data flow of the
     * specified <tt>IMMDevice</tt>
     * @throws HResultException if an error occurs while retrieving the data
     * flow of the specified <tt>iMMDevice</tt> in a native WASAPI function
     * which returns an <tt>HRESULT</tt> value
     */
    public int getIMMDeviceDataFlow(long iMMDevice)
        throws HResultException
    {
        long iMMEndpoint = IMMDevice_QueryInterface(iMMDevice, IID_IMMEndpoint);
        int dataFlow;

        if (iMMEndpoint == 0)
            throw new RuntimeException("IMMDevice_QueryInterface");
        try
        {
            dataFlow = IMMEndpoint_GetDataFlow(iMMEndpoint);
        }
        finally
        {
            IMMEndpoint_Release(iMMEndpoint);
        }
        switch (dataFlow)
        {
        case eAll:
        case eCapture:
        case eRender:
            return dataFlow;
        default:
            throw new RuntimeException("IMMEndpoint_GetDataFlow");
        }
    }

    /**
     * Gets the <tt>PKEY_Device_FriendlyName</tt> of a specific
     * <tt>IMMDevice</tt> which represents the human-readable name of the device
     * (interface).
     *
     * @param iMMDevice the <tt>IMMDevice</tt> to get the
     * friendly/human-readable name of
     * @return the friendly/human-readable name of the specified
     * <tt>iMMDevice</tt>
     * @throws HResultException if an error occurs while retrieving the friendly
     * name of the specified <tt>iMMDevice</tt> in a native WASAPI function
     * which returns an <tt>HRESULT</tt> value
     */
    private String getIMMDeviceFriendlyName(long iMMDevice)
        throws HResultException
    {
        long iPropertyStore = IMMDevice_OpenPropertyStore(iMMDevice, STGM_READ);

        if (iPropertyStore == 0)
            throw new RuntimeException("IMMDevice_OpenPropertyStore");

        String deviceFriendlyName;

        try
        {
            deviceFriendlyName
                = IPropertyStore_GetString(
                        iPropertyStore,
                        PKEY_Device_FriendlyName);
        }
        finally
        {
            IPropertyStore_Release(iPropertyStore);
        }
        return deviceFriendlyName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getRendererClassName()
    {
        return WASAPIRenderer.class.getName();
    }

    /**
     * Gets the size in bytes of an audio sample of a specific
     * <tt>AudioFormat</tt>.
     *
     * @param format the <tt>AudioFormat</tt> to get the size in bytes of an
     * audio sample of
     * @return the size in bytes of an audio sample of the specified
     * <tt>format</tt>
     */
    public static int getSampleSizeInBytes(AudioFormat format)
    {
        int sampleSizeInBits = format.getSampleSizeInBits();

        switch (sampleSizeInBits)
        {
        case 8:
            return 1;
        case 16:
            return 2;
        default:
            return sampleSizeInBits / 8;
        }
    }

    /**
     * Initializes a new <tt>IAudioClient</tt> instance for an audio endpoint
     * device identified by a specific <tt>MediaLocator</tt>. The initialization
     * is performed to an extent suitable for the operation of
     * {@link WASAPIRenderer} and {@link WASAPIStream}.
     *
     * @param locator a <tt>MediaLocator</tt> which identifies the audio
     * endpoint device to initialize a new <tt>IAudioClient</tt> instance for
     * @param dataFlow the flow of media data to be supported by the audio
     * endpoint device identified by the specified <tt>locator</tt>
     * @param eventHandle
     * @param hnsBufferDuration
     * @param formats an array of alternative <tt>AudioFormat</tt>s with which
     * initialization of a new <tt>IAudioClient</tt> instance is to be
     * attempted. The first element of the <tt>formats</tt> array which is
     * supported by the new <tt>IAudioClient</tt> instance is used to initialize
     * it and any preceding elements are set to <tt>null</tt> to signify that
     * they are not supported and to make it possible to retrieve the
     * <tt>AudioFormat</tt> with which the new <tt>IAudioClient</tt> instance
     * has been initialized.
     * @return a new <tt>IAudioClient</tt> instance initialized for the audio
     * endpoint device identified by the specified <tt>locator</tt>
     * @throws HResultException if an error occurs while initializing a new
     * <tt>IAudioClient</tt> for the audio endpoint device identified by the
     * specified <tt>locator</tt> in a native WASAPI function which returns an
     * <tt>HRESULT</tt> value
     */
    public long initializeIAudioClient(
            MediaLocator locator,
            DataFlow dataFlow,
            long eventHandle,
            long hnsBufferDuration,
            AudioFormat[] formats)
        throws HResultException
    {

        /*
         * The Windows API function CoInitializeEx must be invoked on the
         * current thread. Generally, the COM library must be initialized on a
         * thread before calling any of the library functions (with a few
         * exceptions) on that thread. Technically, that general requirement is
         * not trivial to implement in the multi-threaded architecture of FMJ.
         * Practically, we will perform the invocations where we have seen the
         * return value CO_E_NOTINITIALIZED.
         */
        WASAPISystem.CoInitializeEx();

        String id = locator.getRemainder();
        long iMMDevice = getIMMDevice(id);

        if (iMMDevice == 0)
        {
            throw new RuntimeException(
                    "Failed to retrieve audio endpoint device "
                        + "with endpoint ID string " + id);
        }

        long ret = 0;

        try
        {
            /*
             * Assert that the audio endpoint device identified by the specified
             * locator supports the specified dataFlow.
             */
            int iMMDeviceDataFlow = getIMMDeviceDataFlow(iMMDevice);

            switch (dataFlow)
            {
            case CAPTURE:
                if ((iMMDeviceDataFlow != eAll)
                        && (iMMDeviceDataFlow != eCapture))
                    throw new IllegalArgumentException("dataFlow");
                break;
            case NOTIFY:
            case PLAYBACK:
                if ((iMMDeviceDataFlow != eAll)
                        && (iMMDeviceDataFlow != eRender))
                    throw new IllegalArgumentException("dataFlow");
                break;
            }

            long iAudioClient
                = IMMDevice_Activate(
                        iMMDevice,
                        IID_IAudioClient,
                        CLSCTX_ALL,
                        0);

            if (iAudioClient == 0)
                throw new RuntimeException("IMMDevice_Activate");
            try
            {
                long waveformatex = WAVEFORMATEX_alloc();

                if (waveformatex == 0)
                    throw new OutOfMemoryError("WAVEFORMATEX_alloc");
                try
                {
                    int shareMode = AUDCLNT_SHAREMODE_SHARED;
                    boolean waveformatexIsInitialized = false;

                    for (int i = 0; i < formats.length; i++)
                    {
                        WAVEFORMATEX_fill(waveformatex, formats[i]);

                        long pClosestMatch
                            = IAudioClient_IsFormatSupported(
                                    iAudioClient,
                                    shareMode,
                                    waveformatex);

                        if (pClosestMatch == 0) // not supported
                            formats[i] = null;
                        else
                        {
                            try
                            {
                                if (pClosestMatch == waveformatex)
                                {
                                    waveformatexIsInitialized = true;
                                    break;
                                }
                                else
                                {
                                    /*
                                     * Succeeded with a closest match to the
                                     * specified format.
                                     */
                                    formats[i] = null;
                                }
                            }
                            finally
                            {
                                if (pClosestMatch != waveformatex)
                                    CoTaskMemFree(pClosestMatch);
                            }
                        }
                    }
                    if (!waveformatexIsInitialized)
                        throw new IllegalArgumentException("formats");

                    int streamFlags = AUDCLNT_STREAMFLAGS_NOPERSIST;

                    if (eventHandle != 0)
                        eventHandle |= AUDCLNT_STREAMFLAGS_EVENTCALLBACK;

                    int hresult
                        = IAudioClient_Initialize(
                                iAudioClient,
                                shareMode,
                                streamFlags,
                                hnsBufferDuration,
                                /* hnsPeriodicity */ 0,
                                waveformatex,
                                audioSessionGuid);

                    if (hresult != S_OK)
                    {
                        /*
                         * The execution is not expected to reach here. Anyway,
                         * be prepared to handle even such a case for the sake
                         * of completeness.
                         */
                        throw new HResultException(hresult);
                    }
                    if (((streamFlags & AUDCLNT_STREAMFLAGS_EVENTCALLBACK)
                                == AUDCLNT_STREAMFLAGS_EVENTCALLBACK)
                            && (eventHandle != 0))
                    {
                        IAudioClient_SetEventHandle(iAudioClient, eventHandle);
                    }

                    ret = iAudioClient;
                    iAudioClient = 0;
                }
                finally
                {
                    CoTaskMemFree(waveformatex);
                }
            }
            finally
            {
                if (iAudioClient != 0)
                    IAudioClient_Release(iAudioClient);
            }
        }
        finally
        {
            if (iMMDevice != 0)
                IMMDevice_Release(iMMDevice);
        }
        return ret;
    }

    /**
     * {@inheritDoc}
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
            if (waveformatex != 0)
            {
                CoTaskMemFree(waveformatex);
                waveformatex = 0;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void preInitialize()
    {
        super.preInitialize();

        /*
         * Make sure a WAVEFORMATEX instance is available during the execution
         * of doInitialize(). The field has been introduced to minimize memory
         * fragmentation.
         */
        if (waveformatex != 0)
        {
            CoTaskMemFree(waveformatex);
            waveformatex = 0;
        }
        waveformatex = WAVEFORMATEX_alloc();
        if (waveformatex == 0)
            throw new OutOfMemoryError("WAVEFORMATEX_alloc");

        if (pNotify == null)
        {
            pNotify
                = new IMMNotificationClient()
                {
                    public void OnDefaultDeviceChanged(
                            int flow,
                            int role,
                            String pwstrDefaultDevice)
                    {
                    }

                    public void OnDeviceAdded(String pwstrDeviceId)
                    {
                        reinitialize(pwstrDeviceId);
                    }

                    public void OnDeviceRemoved(String pwstrDeviceId)
                    {
                        reinitialize(pwstrDeviceId);
                    }

                    public void OnDeviceStateChanged(
                            String pwstrDeviceId,
                            int dwNewState)
                    {
                        reinitialize(pwstrDeviceId);
                    }

                    public void OnPropertyValueChanged(
                            String pwstrDeviceId,
                            long key)
                    {
                    }
                };
        }

        /*
         * Generate a GUID to identify an audio session that steams to be
         * initialized will belong to.
         */
        if (audioSessionGuid == null)
        {
            try
            {
                audioSessionGuid = CoCreateGuid();
            }
            catch (HResultException hre)
            {
                /*
                 * The application/library will work with the default audio
                 * session GUID.
                 */
                logger.warn("Failed to generate a new audio session GUID", hre);
            }
        }
    }

    /**
     * Reinitializes this <tt>WASAPISystem</tt>. The implementation assumes that
     * the invocation is performed by the Multimedia Device (MMDevice) API and
     * swallows any thrown <tt>Exception</tt>.
     *
     * @param deviceId the endpoint ID string that identifies the audio endpoint
     * device which is related to the decision to reinitialize this
     * <tt>WASAPISystem</tt>
     */
    private void reinitialize(String deviceId)
    {
        try
        {
            /*
             * XXX Invoke the initialize() method asynchronously in order to
             * allow the Multimedia Device (MMDevice) callback to return
             * immediately. Otherwise, the execution will freeze in the
             * IAudioClient_Release function will freeze. Besides, the callback
             * dispatches the notifications after the respective changes have
             * been realized anyway.
             */
            invokeDeviceSystemInitialize(this, true);
        }
        catch (Exception e)
        {
            logger.error("Failed to reinitialize " + getClass().getName(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return "Windows Audio Session API (WASAPI)";
    }

    /**
     * Sets the fields of a specific <tt>WAVEFORMATEX</tt> instance from a
     * specific <tt>AudioFormat</tt> instance so that the two of them are
     * equivalent in terms of the formats of audio data that they describe.
     *
     * @param waveformatex the <tt>WAVEFORMATEX</tt> instance to set the fields
     * of from the specified <tt>audioFormat</tt>
     * @param audioFormat the <tt>AudioFormat</tt> instance to set the fields of
     * the specified <tt>waveformatex</tt> from
     */
    public static void WAVEFORMATEX_fill(
            long waveformatex,
            AudioFormat audioFormat)
    {
        if (!AudioFormat.LINEAR.equals(audioFormat.getEncoding()))
            throw new IllegalArgumentException("audioFormat.encoding");

        int channels = audioFormat.getChannels();

        if (channels == Format.NOT_SPECIFIED)
            throw new IllegalArgumentException("audioFormat.channels");

        int sampleRate = (int) audioFormat.getSampleRate();

        if (sampleRate == Format.NOT_SPECIFIED)
            throw new IllegalArgumentException("audioFormat.sampleRate");

        int sampleSizeInBits = audioFormat.getSampleSizeInBits();

        if (sampleSizeInBits == Format.NOT_SPECIFIED)
            throw new IllegalArgumentException("audioFormat.sampleSizeInBits");

        char nBlockAlign = (char) ((channels * sampleSizeInBits) / 8);

        WASAPI.WAVEFORMATEX_fill(
                waveformatex,
                WAVE_FORMAT_PCM,
                (char) channels,
                sampleRate,
                sampleRate * nBlockAlign,
                nBlockAlign,
                (char) sampleSizeInBits,
                /* cbSize */ (char) 0);
    }
}
