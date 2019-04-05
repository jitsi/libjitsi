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
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.impl.neomedia.portaudio.*;
import org.jitsi.utils.logging.*;

/**
 * Creates PortAudio capture devices by enumerating all host devices that have
 * input channels.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class PortAudioSystem
    extends AudioSystem2
{
    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying PortAudio
     * <tt>CaptureDevice</tt>s.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_PORTAUDIO;

    /**
     * The <tt>Logger</tt> used by the <tt>PortAudioSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(PortAudioSystem.class);

    /**
     * Gets a sample rate supported by a PortAudio device with a specific device
     * index with which it is to be registered with JMF.
     *
     * @param input <tt>true</tt> if the supported sample rate is to be retrieved for
     * the PortAudio device with the specified device index as an input device
     * or <tt>false</tt> for an output device
     * @param deviceIndex the device index of the PortAudio device for which a
     * supported sample rate is to be retrieved
     * @param channelCount number of channel
     * @param sampleFormat sample format
     * @return a sample rate supported by the PortAudio device with the
     * specified device index with which it is to be registered with JMF
     */
    private static double getSupportedSampleRate(
            boolean input,
            int deviceIndex,
            int channelCount,
            long sampleFormat)
    {
        long deviceInfo = Pa.GetDeviceInfo(deviceIndex);
        double supportedSampleRate;

        if (deviceInfo != 0)
        {
            double defaultSampleRate
                = Pa.DeviceInfo_getDefaultSampleRate(deviceInfo);

            if (defaultSampleRate >= MediaUtils.MAX_AUDIO_SAMPLE_RATE)
                supportedSampleRate = defaultSampleRate;
            else
            {
                long streamParameters
                    = Pa.StreamParameters_new(
                            deviceIndex,
                            channelCount,
                            sampleFormat,
                            Pa.LATENCY_UNSPECIFIED);

                if (streamParameters == 0)
                    supportedSampleRate = defaultSampleRate;
                else
                {
                    try
                    {
                        long inputParameters;
                        long outputParameters;

                        if (input)
                        {
                            inputParameters = streamParameters;
                            outputParameters = 0;
                        }
                        else
                        {
                            inputParameters = 0;
                            outputParameters = streamParameters;
                        }

                        boolean formatIsSupported
                            = Pa.IsFormatSupported(
                                    inputParameters,
                                    outputParameters,
                                    Pa.DEFAULT_SAMPLE_RATE);

                        supportedSampleRate
                            = formatIsSupported
                                ? Pa.DEFAULT_SAMPLE_RATE
                                : defaultSampleRate;
                    }
                    finally
                    {
                        Pa.StreamParameters_free(streamParameters);
                    }
                }
            }
        }
        else
            supportedSampleRate = Pa.DEFAULT_SAMPLE_RATE;
        return supportedSampleRate;
    }

    /**
     * Places a specific <tt>DiagnosticsControl</tt> under monitoring of its
     * functional health because of a malfunction in its procedure/process. The
     * monitoring will automatically cease after the procedure/process resumes
     * executing normally or is garbage collected.
     *
     * @param diagnosticsControl the <tt>DiagnosticsControl</tt> to be placed
     * under monitoring of its functional health because of a malfunction in its
     * procedure/process
     */
    public static void monitorFunctionalHealth(
            DiagnosticsControl diagnosticsControl)
    {
        // TODO Auto-generated method stub
    }

    private Runnable devicesChangedCallback;

    /**
     * Initializes a new <tt>PortAudioSystem</tt> instance which creates
     * PortAudio capture and playback devices by enumerating all host devices
     * with input channels.
     *
     * @throws Exception if anything wrong happens while creating the PortAudio
     * capture and playback devices
     */
    PortAudioSystem()
        throws Exception
    {
        super(
                LOCATOR_PROTOCOL,
                FEATURE_DENOISE
                    | FEATURE_ECHO_CANCELLATION
                    | FEATURE_NOTIFY_AND_PLAYBACK_DEVICES
                    | FEATURE_REINITIALIZE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doInitialize()
        throws Exception
    {
        /*
         * If PortAudio fails to initialize because of, for example, a missing
         * native counterpart, it will throw an exception here and the PortAudio
         * Renderer will not be initialized.
         */
        int deviceCount = Pa.GetDeviceCount();
        int channels = 1;
        int sampleSizeInBits = 16;
        long sampleFormat = Pa.getPaSampleFormat(sampleSizeInBits);
        int defaultInputDeviceIndex = Pa.GetDefaultInputDevice();
        int defaultOutputDeviceIndex = Pa.GetDefaultOutputDevice();
        List<CaptureDeviceInfo2> captureAndPlaybackDevices
            = new LinkedList<CaptureDeviceInfo2>();
        List<CaptureDeviceInfo2> captureDevices
            = new LinkedList<CaptureDeviceInfo2>();
        List<CaptureDeviceInfo2> playbackDevices
            = new LinkedList<CaptureDeviceInfo2>();
        final boolean loggerIsDebugEnabled = logger.isDebugEnabled();

        if(CoreAudioDevice.isLoaded)
            CoreAudioDevice.initDevices();
        for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++)
        {
            long deviceInfo = Pa.GetDeviceInfo(deviceIndex);
            String name = Pa.DeviceInfo_getName(deviceInfo);

            if (name != null)
                name = name.trim();

            int maxInputChannels
                = Pa.DeviceInfo_getMaxInputChannels(deviceInfo);
            int maxOutputChannels
                = Pa.DeviceInfo_getMaxOutputChannels(deviceInfo);
            String transportType
                = Pa.DeviceInfo_getTransportType(deviceInfo);
            String deviceUID
                = Pa.DeviceInfo_getDeviceUID(deviceInfo);
            String modelIdentifier;
            String locatorRemainder;

            if (deviceUID == null)
            {
                modelIdentifier = null;
                locatorRemainder = name;
            }
            else
            {
                modelIdentifier
                    = CoreAudioDevice.isLoaded
                        ? CoreAudioDevice.getDeviceModelIdentifier(deviceUID)
                        : null;
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
                                        (maxInputChannels > 0)
                                            ? getSupportedSampleRate(
                                                    true,
                                                    deviceIndex,
                                                    channels,
                                                    sampleFormat)
                                            : Pa.DEFAULT_SAMPLE_RATE,
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

            /*
             * When we perform automatic selection of capture and
             * playback/notify devices, we would like to pick up devices from
             * one and the same hardware because that sound like a natural
             * expectation from the point of view of the user. In order to
             * achieve that, we will bring the devices which support both
             * capture and playback to the top.
             */
            if (maxInputChannels > 0)
            {
                List<CaptureDeviceInfo2> devices;

                if (maxOutputChannels > 0)
                    devices = captureAndPlaybackDevices;
                else
                    devices = captureDevices;

                if ((deviceIndex == defaultInputDeviceIndex)
                        || ((maxOutputChannels > 0)
                                && (deviceIndex == defaultOutputDeviceIndex)))
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
                if (loggerIsDebugEnabled)
                {
                    if (deviceIndex == defaultOutputDeviceIndex)
                        logger.debug("Added default playback device: " + name);
                    else
                        logger.debug("Added playback device: " + name);
                }
            }
            else if (maxOutputChannels > 0)
            {
                if (deviceIndex == defaultOutputDeviceIndex)
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
        if(CoreAudioDevice.isLoaded)
            CoreAudioDevice.freeDevices();

        /*
         * Make sure that devices which support both capture and playback are
         * reported as such and are preferred over devices which support either
         * capture or playback (in order to achieve our goal to have automatic
         * selection pick up devices from one and the same hardware).
         */
        bubbleUpUsbDevices(captureDevices);
        bubbleUpUsbDevices(playbackDevices);
        if (!captureDevices.isEmpty() && !playbackDevices.isEmpty())
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
        if (!captureAndPlaybackDevices.isEmpty())
        {
            bubbleUpUsbDevices(captureAndPlaybackDevices);
            for (int i = captureAndPlaybackDevices.size() - 1; i >= 0; i--)
            {
                CaptureDeviceInfo2 cdi
                    = captureAndPlaybackDevices.get(i);

                captureDevices.add(0, cdi);
                playbackDevices.add(0, cdi);
            }
        }

        setCaptureDevices(captureDevices);
        setPlaybackDevices(playbackDevices);

        if (devicesChangedCallback == null)
        {
            devicesChangedCallback
                = new Runnable()
                {
                    @Override
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
                                    "Failed to reinitialize PortAudio devices",
                                    t);
                        }
                    }
                };
            Pa.setDevicesChangedCallback(devicesChangedCallback);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getRendererClassName()
    {
        return PortAudioRenderer.class.getName();
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>PortAudioSystem</tt> always returns
     * &quot;PortAudio&quot;.
     */
    @Override
    public String toString()
    {
        return "PortAudio";
    }

    @Override
    protected void updateAvailableDeviceList()
    {
        Pa.UpdateAvailableDeviceList();
    }
}
