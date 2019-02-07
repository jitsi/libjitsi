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
import java.net.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.conference.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Extends <tt>MediaDeviceImpl</tt> with audio-specific functionality.
 *
 * @author Lyubomir Marinov
 */
public class AudioMediaDeviceImpl
    extends MediaDeviceImpl
{

    /**
     * The <tt>Logger</tt> used by the <tt>AudioMediaDeviceImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AudioMediaDeviceImpl.class);

    /**
     * The <tt>AudioMixer</tt> which enables sharing an exclusive
     * <tt>CaptureDevice</tt> such as JavaSound between multiple
     * <tt>CaptureDevice</tt> users.
     */
    private AudioMixer captureDeviceSharing;

    /**
     * The <tt>List</tt> of RTP extensions supported by this device (at the time
     * of writing this list is only filled for audio devices and is
     * <tt>null</tt> otherwise).
     */
    private List<RTPExtension> rtpExtensions = null;

    /**
     * Initializes a new <tt>AudioMediaDeviceImpl</tt> instance which represents
     * a <tt>MediaDevice</tt> with <tt>MediaType</tt> <tt>AUDIO</tt> and a
     * <tt>MediaDirection</tt> which does not allow sending.
     */
    public AudioMediaDeviceImpl()
    {
        super(MediaType.AUDIO);
    }

    /**
     * Initializes a new <tt>AudioMediaDeviceImpl</tt> which is to provide an
     * implementation of <tt>MediaDevice</tt> with <tt>MediaType</tt>
     * <tt>AUDIO</tt> to a <tt>CaptureDevice</tt> with a specific
     * <tt>CaptureDeviceInfo</tt>.
     *
     * @param captureDeviceInfo the <tt>CaptureDeviceInfo</tt> of the
     * <tt>CaptureDevice</tt> to which the new instance is to provide an
     * implementation of <tt>MediaDevice</tt>
     */
    public AudioMediaDeviceImpl(CaptureDeviceInfo captureDeviceInfo)
    {
        super(captureDeviceInfo, MediaType.AUDIO);
    }

    /**
     * Connects to a specific <tt>CaptureDevice</tt> given in the form of a
     * <tt>DataSource</tt>.
     *
     * @param captureDevice the <tt>CaptureDevice</tt> to be connected to
     * @throws IOException if anything wrong happens while connecting to the
     * specified <tt>captureDevice</tt>
     * @see AbstractMediaDevice#connect(DataSource)
     */
    @Override
    public void connect(DataSource captureDevice)
        throws IOException
    {
        super.connect(captureDevice);

        /*
         * 1. Changing the buffer length to 30 ms. The default buffer size (for
         * JavaSound) is 125 ms (i.e. 1/8 sec). On Mac OS X, this leads to an
         * exception and no audio capture. A value of 30 ms for the buffer
         * length fixes the problem and is OK when using some PSTN gateways.
         *
         * 2. Changing to 60 ms. When it is 30 ms, there are some issues with
         * Asterisk and NAT (we don't start to send a/the stream and Asterisk's
         * RTP functionality doesn't notice that we're behind NAT).
         *
         * 3. Do not set buffer length on Linux as it completely breaks audio
         * capture.
         */
        if(!OSUtils.IS_LINUX)
        {
            BufferControl bufferControl
                = (BufferControl)
                    captureDevice.getControl(BufferControl.class.getName());

            if (bufferControl != null)
                bufferControl.setBufferLength(60 /* millis */);
        }
    }

    /**
     * Creates the JMF <tt>CaptureDevice</tt> this instance represents and
     * provides an implementation of <tt>MediaDevice</tt> for.
     *
     * @return the JMF <tt>CaptureDevice</tt> this instance represents and
     * provides an implementation of <tt>MediaDevice</tt> for; <tt>null</tt>
     * if the creation fails
     */
    @Override
    protected synchronized CaptureDevice createCaptureDevice()
    {
        CaptureDevice captureDevice = null;

        if (getDirection().allowsSending())
        {
            if (captureDeviceSharing == null)
            {
                String protocol = getCaptureDeviceInfoLocatorProtocol();
                boolean createCaptureDeviceIfNull = true;

                if (AudioSystem.LOCATOR_PROTOCOL_JAVASOUND.equalsIgnoreCase(
                            protocol)
                        || AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO
                                .equalsIgnoreCase(protocol))
                {
                    captureDevice = superCreateCaptureDevice();
                    createCaptureDeviceIfNull = false;
                    if (captureDevice != null)
                    {
                        captureDeviceSharing
                            = createCaptureDeviceSharing(captureDevice);
                        captureDevice
                            = captureDeviceSharing.createOutDataSource();
                    }
                }
                if ((captureDevice == null) && createCaptureDeviceIfNull)
                    captureDevice = superCreateCaptureDevice();
            }
            else
                captureDevice = captureDeviceSharing.createOutDataSource();
        }
        return captureDevice;
    }

    /**
     * Creates a new <tt>AudioMixer</tt> which is to enable the sharing of a
     * specific explicit <tt>CaptureDevice</tt>
     *
     * @param captureDevice an exclusive <tt>CaptureDevice</tt> for which
     * sharing is to be enabled
     * @return a new <tt>AudioMixer</tt> which enables the sharing of the
     * specified exclusive <tt>captureDevice</tt>
     */
    private AudioMixer createCaptureDeviceSharing(CaptureDevice captureDevice)
    {
        return
            new AudioMixer(captureDevice)
            {
                @Override
                protected void connect(
                        DataSource dataSource,
                        DataSource inputDataSource)
                    throws IOException
                {
                    /*
                     * CaptureDevice needs special connecting as defined by
                     * AbstractMediaDevice and, especially, MediaDeviceImpl.
                     */
                    if (inputDataSource == captureDevice)
                        AudioMediaDeviceImpl.this.connect(dataSource);
                    else
                        super.connect(dataSource, inputDataSource);
                }
            };
    }

    /**
     * {@inheritDoc}
     *
     * Tries to delegate the initialization of a new <tt>Renderer</tt> instance
     * to the <tt>AudioSystem</tt> which provides the <tt>CaptureDevice</tt> of
     * this instance. This way both the capture and the playback are given a
     * chance to happen within the same <tt>AudioSystem</tt>. If the discovery
     * of the delegate fails, the implementation of <tt>MediaDeviceImpl</tt> is
     * executed and it currently leaves it to FMJ to choose a <tt>Renderer</tt>
     * irrespective of this <tt>MediaDevice</tt>.
     */
    @Override
    protected Renderer createRenderer()
    {
        Renderer renderer = null;

        try
        {
            String locatorProtocol = getCaptureDeviceInfoLocatorProtocol();

            if (locatorProtocol != null)
            {
                AudioSystem audioSystem
                    = AudioSystem.getAudioSystem(locatorProtocol);

                if (audioSystem != null)
                    renderer = audioSystem.createRenderer(true);
            }
        }
        finally
        {
            if (renderer == null)
                renderer = super.createRenderer();
        }
        return renderer;
    }

    /**
     * Returns a <tt>List</tt> containing extension descriptor indicating
     * <tt>RECVONLY</tt> support for mixer-to-client audio levels,
     * and extension descriptor indicating <tt>SENDRECV</tt> support for
     * client-to-mixer audio levels.
     * We add the ssrc audio levels as first element, in order when making offer
     * to be the first one (id 1) as some other systems have
     * this hardcoded it as 1 (jicofo).
     *
     * @return a <tt>List</tt> containing the <tt>CSRC_AUDIO_LEVEL_URN</tt>
     * and  <tt>SSRC_AUDIO_LEVEL_URN</tt> extension descriptor.
     */
    @Override
    public List<RTPExtension> getSupportedExtensions()
    {
        if (rtpExtensions == null)
        {
            rtpExtensions = new ArrayList<RTPExtension>(1);

            URI ssrcAudioLevelURN;
            URI csrcAudioLevelURN;
            try
            {
                ssrcAudioLevelURN = new URI(RTPExtension.SSRC_AUDIO_LEVEL_URN);
                csrcAudioLevelURN = new URI(RTPExtension.CSRC_AUDIO_LEVEL_URN);
            }
            catch (URISyntaxException e)
            {
                // can't happen since CSRC_AUDIO_LEVEL_URN is a valid URI and
                // never changes.
                if (logger.isInfoEnabled())
                    logger.info("Aha! Someone messed with the source!", e);
                ssrcAudioLevelURN = null;
                csrcAudioLevelURN = null;
            }
            if (ssrcAudioLevelURN != null)
            {
                rtpExtensions.add(
                    new RTPExtension(
                        ssrcAudioLevelURN,
                        MediaDirection.SENDRECV));
            }
            if (csrcAudioLevelURN != null)
            {
                rtpExtensions.add(
                        new RTPExtension(
                                csrcAudioLevelURN,
                                MediaDirection.RECVONLY));
            }
        }

        return rtpExtensions;
    }

    private boolean isLessThanOrEqualToMaxAudioFormat(Format format)
    {
        if (format instanceof AudioFormat)
        {
            AudioFormat audioFormat = (AudioFormat) format;
            int channels = audioFormat.getChannels();

            if ((channels == Format.NOT_SPECIFIED)
                    || (MediaUtils.MAX_AUDIO_CHANNELS == Format.NOT_SPECIFIED)
                    || (channels <= MediaUtils.MAX_AUDIO_CHANNELS))
            {
                double sampleRate = audioFormat.getSampleRate();

                if ((sampleRate == Format.NOT_SPECIFIED)
                        || (MediaUtils.MAX_AUDIO_SAMPLE_RATE
                                == Format.NOT_SPECIFIED)
                        || (sampleRate <= MediaUtils.MAX_AUDIO_SAMPLE_RATE))
                {
                    int sampleSizeInBits
                        = audioFormat.getSampleSizeInBits();

                    if ((sampleSizeInBits == Format.NOT_SPECIFIED)
                            || (MediaUtils.MAX_AUDIO_SAMPLE_SIZE_IN_BITS
                                    == Format.NOT_SPECIFIED)
                            || (sampleSizeInBits
                                    <= MediaUtils
                                            .MAX_AUDIO_SAMPLE_SIZE_IN_BITS))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Invokes the super (with respect to the <tt>AudioMediaDeviceImpl</tt>
     * class) implementation of {@link MediaDeviceImpl#createCaptureDevice()}.
     * Allows this instance to customize the very <tt>CaptureDevice</tt> which
     * is to be possibly further wrapped by this instance.
     *
     * @return the <tt>CaptureDevice</tt> returned by the call to the super
     * implementation of <tt>MediaDeviceImpl#createCaptureDevice</tt>.
     */
    protected CaptureDevice superCreateCaptureDevice()
    {
        CaptureDevice captureDevice = super.createCaptureDevice();

        if (captureDevice != null)
        {
            /*
             * Try to default the captureDevice to a Format which does not
             * exceed the maximum quality known to MediaUtils.
             */
            try
            {
                FormatControl[] formatControls
                    = captureDevice.getFormatControls();

                if ((formatControls != null) && (formatControls.length != 0))
                {
                    for (FormatControl formatControl : formatControls)
                    {
                        Format format = formatControl.getFormat();

                        if ((format != null)
                                && isLessThanOrEqualToMaxAudioFormat(format))
                            continue;

                        Format[] supportedFormats
                            = formatControl.getSupportedFormats();
                        AudioFormat supportedFormatToSet = null;

                        if ((supportedFormats != null)
                                && (supportedFormats.length != 0))
                        {
                            for (Format supportedFormat : supportedFormats)
                            {
                                if (isLessThanOrEqualToMaxAudioFormat(
                                        supportedFormat))
                                {
                                    supportedFormatToSet
                                        = (AudioFormat) supportedFormat;
                                    break;
                                }
                            }
                        }

                        if (!supportedFormatToSet.matches(format))
                        {
                            int channels = supportedFormatToSet.getChannels();
                            double sampleRate
                                = supportedFormatToSet.getSampleRate();
                            int sampleSizeInBits
                                = supportedFormatToSet.getSampleSizeInBits();

                            if (channels == Format.NOT_SPECIFIED)
                                channels = MediaUtils.MAX_AUDIO_CHANNELS;
                            if (sampleRate == Format.NOT_SPECIFIED)
                                sampleRate = MediaUtils.MAX_AUDIO_SAMPLE_RATE;
                            if (sampleSizeInBits == Format.NOT_SPECIFIED)
                            {
                                sampleSizeInBits
                                    = MediaUtils.MAX_AUDIO_SAMPLE_SIZE_IN_BITS;
                                /*
                                 * TODO A great deal of the neomedia-contributed
                                 * audio Codecs, CaptureDevices, DataSources and
                                 * Renderers deal with 16-bit samples.
                                 */
                                if (sampleSizeInBits == Format.NOT_SPECIFIED)
                                    sampleSizeInBits = 16;
                            }

                            if ((channels != Format.NOT_SPECIFIED)
                                    && (sampleRate != Format.NOT_SPECIFIED)
                                    && (sampleSizeInBits
                                            != Format.NOT_SPECIFIED))
                            {
                                AudioFormat formatToSet
                                    = new AudioFormat(
                                            supportedFormatToSet.getEncoding(),
                                            sampleRate,
                                            sampleSizeInBits,
                                            channels);

                                if (supportedFormatToSet.matches(formatToSet))
                                    formatControl.setFormat(
                                            supportedFormatToSet.intersects(
                                                    formatToSet));
                            }
                        }
                    }
                }
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                /*
                 * We tried to default the captureDevice to a Format which does
                 * not exceed the maximum quality known to MediaUtils and we
                 * failed but it does not mean that the captureDevice will not
                 * be successfully used.
                 */
            }
        }

        return captureDevice;
    }
}
