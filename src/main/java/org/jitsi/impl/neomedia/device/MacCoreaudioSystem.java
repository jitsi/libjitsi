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
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.utils.logging.*;

/**
 * Creates MacCoreaudio capture devices by enumerating all host devices that
 * have input channels.
 *
 * @author Vincent Lucas
 */
public class MacCoreaudioSystem
    extends AudioSystem2
{
    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying MacCoreaudio
     * <tt>CaptureDevice</tt>s.
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
     * Gets a sample rate supported by a MacCoreaudio device with a specific
     * device index with which it is to be registered with JMF.
     *
     * @param input <tt>true</tt> if the supported sample rate is to be
     * retrieved for the MacCoreaudio device with the specified device index as
     * an input device or <tt>false</tt> for an output device
     * @param deviceUID The device identifier.
     * @param isEchoCancel True if the echo canceller is activated.
     *
     * @return a sample rate supported by the MacCoreaudio device with the
     * specified device index with which it is to be registered with JMF
     */
    private static double getSupportedSampleRate(
            boolean input,
            String deviceUID,
            boolean isEchoCancel)
    {
        double supportedSampleRate
            = MacCoreAudioDevice.getNominalSampleRate(
                    deviceUID,
                    false,
                    isEchoCancel);

        if(supportedSampleRate >= MediaUtils.MAX_AUDIO_SAMPLE_RATE)
            supportedSampleRate = MacCoreAudioDevice.DEFAULT_SAMPLE_RATE;
        return supportedSampleRate;
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
                FEATURE_NOTIFY_AND_PLAYBACK_DEVICES
                    | FEATURE_REINITIALIZE
                    | FEATURE_ECHO_CANCELLATION);
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
                logger.info(message);
            throw new Exception(message);
        }

        // Initializes the library only at the first run.
        if(devicesChangedCallback == null)
            CoreAudioDevice.initDevices();

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
                        double rate
                            = ((AudioFormat) existingCdi.getFormats()[0])
                                .getSampleRate();

                        if(rate
                                == getSupportedSampleRate(
                                        true,
                                        deviceUID,
                                        isEchoCancel()))
                        {
                            cdi = existingCdi;
                            break;
                        }
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
                                        deviceUID,
                                        isEchoCancel())
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
     * Sets the indicator which determines whether echo cancellation is to be
     * performed for captured audio.
     *
     * @param echoCancel <tt>true</tt> if echo cancellation is to be performed
     * for captured audio; otherwise, <tt>false</tt>
     */
    @Override
    public void setEchoCancel(boolean echoCancel)
    {
        super.setEchoCancel(echoCancel);

        try
        {
            reinitialize();
        }
        catch (Exception e)
        {
            logger.warn("Failed to reinitialize MacCoreaudio devices", e);
        }
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
        return "Core Audio";
    }

    @Override
    protected void updateAvailableDeviceList()
    {
        // TODO Auto-generated method stub
    }
}
