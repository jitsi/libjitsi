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
package org.jitsi.impl.neomedia.jmfext.media.renderer.audio;

import java.beans.*;
import java.lang.reflect.*;
import java.util.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.portaudio.*;
import org.jitsi.impl.neomedia.portaudio.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.logging.*;

/**
 * Implements an audio <tt>Renderer</tt> which uses Pa.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class PortAudioRenderer
    extends AbstractAudioRenderer<PortAudioSystem>
{
    /**
     * The <tt>Logger</tt> used by the <tt>PortAudioRenderer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(PortAudioRenderer.class);

    /**
     * The constant which represents an empty array with
     * <tt>Format</tt> element type. Explicitly defined in order to
     * reduce unnecessary allocations.
     */
    private static final Format[] EMPTY_SUPPORTED_INPUT_FORMATS
        = new Format[0];

    /**
     * The flag which indicates that {@link #open()} has been called on a
     * <tt>PortAudioRenderer</tt> without an intervening {@link #close()}. The
     * state it represents is from the public point of view. The private point
     * of view is represented by {@link #stream}.
     */
    private static final byte FLAG_OPEN = 1;

    /**
     * The flag which indicates that {@link #start()} has been called on a
     * <tt>PortAudioRenderer</tt> without an intervening {@link #stop()}. The
     * state it represents is from the public point of view. The private point
     * of view is represented by {@link #started}.
     */
    private static final byte FLAG_STARTED = 2;

    /**
     * The human-readable name of the <tt>PortAudioRenderer</tt> JMF plug-in.
     */
    private static final String PLUGIN_NAME = "PortAudio Renderer";

    /**
     * The list of JMF <tt>Format</tt>s of audio data which
     * <tt>PortAudioRenderer</tt> instances are capable of rendering.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of the sample rates supported by <tt>PortAudioRenderer</tt> as
     * input.
     */
    private static final double[] SUPPORTED_INPUT_SAMPLE_RATES
        = new double[] { 8000, 11025, 16000, 22050, 32000, 44100, 48000 };

    static
    {
        int count = SUPPORTED_INPUT_SAMPLE_RATES.length;

        SUPPORTED_INPUT_FORMATS = new Format[count];
        for (int i = 0; i < count; i++)
        {
            SUPPORTED_INPUT_FORMATS[i]
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_INPUT_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        Format.NOT_SPECIFIED /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.byteArray);
        }
    }

    /**
     * The audio samples left unwritten by a previous call to
     * {@link #process(Buffer)}. As {@link #bytesPerBuffer} number of
     * bytes are always written, the number of the unwritten audio samples is
     * always less than that.
     */
    private byte[] bufferLeft;

    /**
     * The number of bytes in {@link #bufferLeft} representing unwritten audio
     * samples.
     */
    private int bufferLeftLength = 0;

    /**
     * The number of bytes to write to the native PortAudio stream represented
     * by this instance with a single invocation. Based on
     * {@link #framesPerBuffer}.
     */
    private int bytesPerBuffer;

    /**
     * The <tt>DiagnosticsControl</tt> implementation of this instance which
     * allows the diagnosis of the functional health of <tt>Pa_WriteStream</tt>.
     */
    private final DiagnosticsControl diagnosticsControl
        = new DiagnosticsControl()
        {
            /**
             * {@inheritDoc}
             *
             * <tt>PortAudioRenderer</tt>'s <tt>DiagnosticsControl</tt>
             * implementation does not provide its own user interface and always
             * returns <tt>null</tt>.
             */
            public java.awt.Component getControlComponent()
            {
                return null;
            }

            /**
             * {@inheritDoc}
             */
            public long getMalfunctioningSince()
            {
                return writeIsMalfunctioningSince;
            }

            /**
             * {@inheritDoc}
             *
             * Returns the identifier of the PortAudio device written through
             * this <tt>PortAudioRenderer</tt>.
             */
            @Override
            public String toString()
            {
                MediaLocator locator = getLocator();
                String name = null;

                if (locator != null)
                {
                    String id = DataSource.getDeviceID(locator);

                    if (id != null)
                    {
                        int index
                            = Pa.getDeviceIndex(
                                    id,
                                    /* minInputChannels */ 0,
                                    /* minOutputChannels */ 1);

                        if (index != Pa.paNoDevice)
                        {
                            long info = Pa.GetDeviceInfo(index);

                            if (info != 0)
                                name = Pa.DeviceInfo_getName(info);
                        }
                    }
                }
                return name;
            }
        };

    /**
     * The flags which represent certain state of this
     * <tt>PortAudioRenderer</tt>. Acceptable values are among the
     * <tt>FLAG_XXX</tt> constants defined by the <tt>PortAudioRenderer</tt>
     * class. For example, {@link #FLAG_OPEN} indicates that from the public
     * point of view {@link #open()} has been invoked on this <tt>Renderer</tt>
     * without an intervening {@link #close()}.
     */
    private byte flags = 0;

    /**
     * The number of frames to write to the native PortAudio stream represented
     * by this instance with a single invocation.
     */
    private int framesPerBuffer;

    private long outputParameters = 0;

    /**
     * The <tt>PaUpdateAvailableDeviceListListener</tt> which is to be notified
     * before and after PortAudio's native function
     * <tt>Pa_UpdateAvailableDeviceList()</tt> is invoked. It will close
     * {@link #stream} before the invocation in order to mitigate memory
     * corruption afterwards and it will attempt to restore the state of this
     * <tt>Renderer</tt> after the invocation.
     */
    private final UpdateAvailableDeviceListListener
        paUpdateAvailableDeviceListListener
            = new UpdateAvailableDeviceListListener()
            {
                @Override
                public void didUpdateAvailableDeviceList()
                    throws Exception
                {
                    synchronized (PortAudioRenderer.this)
                    {
                        waitWhileStreamIsBusy();

                        /*
                         * PortAudioRenderer's field flags represents its open
                         * and started state from the public point of view. We
                         * will automatically open and start this Renderer i.e.
                         * we will be modifying the state from the private point
                         * of view only and, consequently, we have to make sure
                         * that we will not modify it from the public point of
                         * view.
                         */
                        byte flags = PortAudioRenderer.this.flags;

                        try
                        {
                            if ((FLAG_OPEN & flags) == FLAG_OPEN)
                            {
                                open();
                                if ((FLAG_STARTED & flags) == FLAG_STARTED)
                                    start();
                            }
                        }
                        finally
                        {
                            PortAudioRenderer.this.flags = flags;
                        }
                    }
                }

                @Override
                public void willUpdateAvailableDeviceList()
                    throws Exception
                {
                    synchronized (PortAudioRenderer.this)
                    {
                        waitWhileStreamIsBusy();

                        /*
                         * PortAudioRenderer's field flags represents its open
                         * and started state from the public point of view. We
                         * will automatically close this Renderer i.e. we will
                         * be modifying the state from the private point of view
                         * only and, consequently, we have to make sure that we
                         * will not modify it from the public point of view.
                         */
                        byte flags = PortAudioRenderer.this.flags;

                        try
                        {
                            if (stream != 0)
                                close();
                        }
                        finally
                        {
                            PortAudioRenderer.this.flags = flags;
                        }
                    }
                }
            };

    /**
     * The indicator which determines whether this <tt>Renderer</tt> is started.
     */
    private boolean started = false;

    /**
     * The output PortAudio stream represented by this instance.
     */
    private long stream = 0;

    /**
     * The indicator which determines whether {@link #stream} is busy and should
     * not, for example, be closed.
     */
    private boolean streamIsBusy = false;

    /**
     * Array of supported input formats.
     */
    private Format[] supportedInputFormats;

    /**
     * The time in milliseconds at which <tt>Pa_WriteStream</tt> has started
     * malfunctioning. For example, <tt>Pa_WriteStream</tt> returning
     * <tt>paTimedOut</tt> and/or Windows Multimedia reporting
     * <tt>MMSYSERR_NODRIVER</tt> (may) indicate abnormal functioning.
     */
    private long writeIsMalfunctioningSince = DiagnosticsControl.NEVER;

    /**
     * Initializes a new <tt>PortAudioRenderer</tt> instance.
     */
    public PortAudioRenderer()
    {
        this(true);
    }

    /**
     * Initializes a new <tt>PortAudioRenderer</tt> instance which is to either
     * perform playback or sound a notification.
     *
     * @param playback <tt>true</tt> if the new instance is to perform playback
     * or <tt>false</tt> if the new instance is to sound a notification
     */
    public PortAudioRenderer(boolean enableVolumeControl)
    {
        super(
                AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO,
                enableVolumeControl
                    ? AudioSystem.DataFlow.PLAYBACK
                    : AudioSystem.DataFlow.NOTIFY);

        /*
         * XXX We will add a PaUpdateAvailableDeviceListListener and will not
         * remove it because we will rely on PortAudioSystem's use of
         * WeakReference.
         */
        if (audioSystem != null)
        {
            audioSystem.addUpdateAvailableDeviceListListener(
                    paUpdateAvailableDeviceListListener);
        }
    }

    /**
     * Closes this <tt>PlugIn</tt>.
     */
    @Override
    public synchronized void close()
    {
        try
        {
            stop();
        }
        finally
        {
            if (stream != 0)
            {
                try
                {
                    Pa.CloseStream(stream);
                    stream = 0;
                    started = false;
                    flags &= ~(FLAG_OPEN | FLAG_STARTED);

                    if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                        setWriteIsMalfunctioning(false);
                }
                catch (PortAudioException paex)
                {
                    logger.error("Failed to close PortAudio stream.", paex);
                }
            }
            if ((stream == 0) && (outputParameters != 0))
            {
                Pa.StreamParameters_free(outputParameters);
                outputParameters = 0;
            }

            super.close();
        }
    }

    /**
     * Gets the descriptive/human-readable name of this JMF plug-in.
     *
     * @return the descriptive/human-readable name of this JMF plug-in
     */
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Gets the list of JMF <tt>Format</tt>s of audio data which this
     * <tt>Renderer</tt> is capable of rendering.
     *
     * @return an array of JMF <tt>Format</tt>s of audio data which this
     * <tt>Renderer</tt> is capable of rendering
     */
    @Override
    public Format[] getSupportedInputFormats()
    {
        if (supportedInputFormats == null)
        {
            MediaLocator locator = getLocator();
            String deviceID;
            int deviceIndex;
            long deviceInfo;

            if ((locator == null)
                    || ((deviceID = DataSource.getDeviceID(locator)) == null)
                    || (deviceID.length() == 0)
                    || ((deviceIndex
                                = Pa.getDeviceIndex(
                                        deviceID,
                                        /* minInputChannels */ 0,
                                        /* minOutputChannels */ 1))
                            == Pa.paNoDevice)
                    || ((deviceInfo = Pa.GetDeviceInfo(deviceIndex)) == 0))
            {
                supportedInputFormats = SUPPORTED_INPUT_FORMATS;
            }
            else
            {
                int minOutputChannels = 1;
                /*
                 * The maximum output channels may be a lot and checking all of
                 * them will take a lot of time. Besides, we currently support
                 * at most 2.
                 */
                int maxOutputChannels
                    = Math.min(
                            Pa.DeviceInfo_getMaxOutputChannels(deviceInfo),
                            2);
                List<Format> supportedInputFormats
                    = new ArrayList<Format>(SUPPORTED_INPUT_FORMATS.length);

                for (Format supportedInputFormat : SUPPORTED_INPUT_FORMATS)
                {
                    getSupportedInputFormats(
                            supportedInputFormat,
                            deviceIndex,
                            minOutputChannels,
                            maxOutputChannels,
                            supportedInputFormats);
                }
                this.supportedInputFormats
                    = supportedInputFormats.isEmpty()
                        ? EMPTY_SUPPORTED_INPUT_FORMATS
                        : supportedInputFormats.toArray(
                                EMPTY_SUPPORTED_INPUT_FORMATS);
            }
        }
        return
            (supportedInputFormats.length == 0)
                ? EMPTY_SUPPORTED_INPUT_FORMATS
                : supportedInputFormats.clone();
    }

    private void getSupportedInputFormats(
            Format format,
            int deviceIndex,
            int minOutputChannels, int maxOutputChannels,
            List<Format> supportedInputFormats)
    {
        AudioFormat audioFormat = (AudioFormat) format;
        int sampleSizeInBits = audioFormat.getSampleSizeInBits();
        long sampleFormat = Pa.getPaSampleFormat(sampleSizeInBits);
        double sampleRate = audioFormat.getSampleRate();

        for (int channels = minOutputChannels;
                channels <= maxOutputChannels;
                channels++)
        {
            long outputParameters
                = Pa.StreamParameters_new(
                        deviceIndex,
                        channels,
                        sampleFormat,
                        Pa.LATENCY_UNSPECIFIED);

            if (outputParameters != 0)
            {
                try
                {
                    if (Pa.IsFormatSupported(
                            0,
                            outputParameters,
                            sampleRate))
                    {
                        supportedInputFormats.add(
                                new AudioFormat(
                                        audioFormat.getEncoding(),
                                        sampleRate,
                                        sampleSizeInBits,
                                        channels,
                                        audioFormat.getEndian(),
                                        audioFormat.getSigned(),
                                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                                        Format.NOT_SPECIFIED /* frameRate */,
                                        audioFormat.getDataType()));
                    }
                }
                finally
                {
                    Pa.StreamParameters_free(outputParameters);
                }
            }
        }
    }

    /**
     * Opens the PortAudio device and output stream represented by this instance
     * which are to be used to render audio.
     *
     * @throws ResourceUnavailableException if the PortAudio device or output
     * stream cannot be created or opened
     */
    @Override
    public synchronized void open()
        throws ResourceUnavailableException
    {
        try
        {
            audioSystem.willOpenStream();
            try
            {
                doOpen();
            }
            finally
            {
                audioSystem.didOpenStream();
            }
        }
        catch (Throwable t)
        {
            /*
             * Log the problem because FMJ may swallow it and thus make
             * debugging harder than necessary.
             */
            if (logger.isDebugEnabled())
                logger.debug("Failed to open PortAudioRenderer", t);

            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else if (t instanceof ResourceUnavailableException)
                throw (ResourceUnavailableException) t;
            else
            {
                ResourceUnavailableException rue
                    = new ResourceUnavailableException();

                rue.initCause(t);
                throw rue;
            }
        }

        super.open();
    }

    /**
     * Opens the PortAudio device and output stream represented by this instance
     * which are to be used to render audio.
     *
     * @throws ResourceUnavailableException if the PortAudio device or output
     * stream cannot be created or opened
     */
    private void doOpen()
        throws ResourceUnavailableException
    {
        if (stream == 0)
        {
            MediaLocator locator = getLocator();

            if (locator == null)
            {
                throw new ResourceUnavailableException(
                        "No locator/MediaLocator is set.");
            }

            String deviceID = DataSource.getDeviceID(locator);
            int deviceIndex
                = Pa.getDeviceIndex(
                        deviceID,
                        /* minInputChannels */ 0,
                        /* minOutputChannels */ 1);

            if (deviceIndex == Pa.paNoDevice)
            {
                throw new ResourceUnavailableException(
                        "The audio device "
                            + deviceID
                            + " appears to be disconnected.");
            }

            AudioFormat inputFormat = this.inputFormat;

            if (inputFormat == null)
                throw new ResourceUnavailableException("inputFormat not set");

            int channels = inputFormat.getChannels();

            if (channels == Format.NOT_SPECIFIED)
                channels = 1;

            long sampleFormat
                = Pa.getPaSampleFormat(
                    inputFormat.getSampleSizeInBits());
            double sampleRate = inputFormat.getSampleRate();

            framesPerBuffer
                = (int)
                    ((sampleRate * Pa.DEFAULT_MILLIS_PER_BUFFER)
                        / (channels * 1000));

            try
            {
                outputParameters
                    = Pa.StreamParameters_new(
                            deviceIndex,
                            channels,
                            sampleFormat,
                            Pa.getSuggestedLatency());

                stream
                    = Pa.OpenStream(
                            0 /* inputParameters */,
                            outputParameters,
                            sampleRate,
                            framesPerBuffer,
                            Pa.STREAM_FLAGS_CLIP_OFF
                                | Pa.STREAM_FLAGS_DITHER_OFF,
                            null /* streamCallback */);
            }
            catch (PortAudioException paex)
            {
                logger.error("Failed to open PortAudio stream.", paex);
                throw new ResourceUnavailableException(paex.getMessage());
            }
            finally
            {
                started = false;
                if (stream == 0)
                {
                    flags &= ~(FLAG_OPEN | FLAG_STARTED);

                    if (outputParameters != 0)
                    {
                        Pa.StreamParameters_free(outputParameters);
                        outputParameters = 0;
                    }
                }
                else
                {
                    flags |= (FLAG_OPEN | FLAG_STARTED);
                }
            }
            if (stream == 0)
                throw new ResourceUnavailableException("Pa_OpenStream");

            bytesPerBuffer
                = Pa.GetSampleSize(sampleFormat)
                    * channels
                    * framesPerBuffer;

            // Pa_WriteStream has not been invoked yet.
            if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                setWriteIsMalfunctioning(false);
        }
    }

    /**
     * Notifies this instance that the value of the
     * {@link AudioSystem#PROP_PLAYBACK_DEVICE} property of its associated
     * <tt>AudioSystem</tt> has changed.
     *
     * @param ev a <tt>PropertyChangeEvent</tt> which specifies details about
     * the change such as the name of the property and its old and new values
     */
    @Override
    protected synchronized void playbackDevicePropertyChange(
            PropertyChangeEvent ev)
    {
        /*
         * Stop, close, re-open and re-start this Renderer (performing whichever
         * of these in order to bring it into the same state) in order to
         * reflect the change in the selection with respect to the playback
         * device.
         */

        waitWhileStreamIsBusy();

        /*
         * From the public point of view, the state of this PortAudioRenderer
         * remains the same.
         */
        byte flags = this.flags;

        try
        {
            if ((FLAG_OPEN & flags) == FLAG_OPEN)
            {
                close();

                try
                {
                    open();
                }
                catch (ResourceUnavailableException rue)
                {
                    throw new UndeclaredThrowableException(rue);
                }
                if ((FLAG_STARTED & flags) == FLAG_STARTED)
                    start();
            }
        }
        finally
        {
            this.flags = flags;
        }
    }

    /**
     * Renders the audio data contained in a specific <tt>Buffer</tt> onto the
     * PortAudio device represented by this <tt>Renderer</tt>.
     *
     * @param buffer the <tt>Buffer</tt> which contains the audio data to be
     * rendered
     * @return <tt>BUFFER_PROCESSED_OK</tt> if the specified <tt>buffer</tt> has
     * been successfully processed
     */
    public int process(Buffer buffer)
    {
        synchronized (this)
        {
            if (!started || (stream == 0))
            {
                /*
                 * The execution is somewhat abnormal but it is not because of a
                 * malfunction in Pa_WriteStream.
                 */
                if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                    setWriteIsMalfunctioning(false);

                return BUFFER_PROCESSED_OK;
            }
            else
                streamIsBusy = true;
        }

        long errorCode = Pa.paNoError;
        Pa.HostApiTypeId hostApiType = null;

        try
        {
            process(
                (byte[]) buffer.getData(),
                buffer.getOffset(),
                buffer.getLength());
        }
        catch (PortAudioException pae)
        {
            errorCode = pae.getErrorCode();
            hostApiType = pae.getHostApiType();

            logger.error("Failed to process Buffer.", pae);
        }
        finally
        {
            /*
             * If a timeout has occurred in the method Pa.WriteStream, give the
             * application a little time to allow it to possibly get its act
             * together. The same treatment sounds appropriate on Windows as
             * soon as the wmme host API starts reporting that no device driver
             * is present.
             */
            boolean yield = false;

            synchronized (this)
            {
                streamIsBusy = false;
                notifyAll();

                if (errorCode == Pa.paNoError)
                {
                    // Pa_WriteStream appears to function normally.
                    if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                        setWriteIsMalfunctioning(false);
                }
                else if ((Pa.paTimedOut == errorCode)
                        || (Pa.HostApiTypeId.paMME.equals(hostApiType)
                                && (Pa.MMSYSERR_NODRIVER == errorCode)))
                {
                    if (writeIsMalfunctioningSince == DiagnosticsControl.NEVER)
                        setWriteIsMalfunctioning(true);
                    yield = true;
                }
            }

            if (yield)
                PortAudioStream.yield();
        }
        return BUFFER_PROCESSED_OK;
    }

    private void process(byte[] buffer, int offset, int length)
        throws PortAudioException
    {

        /*
         * If there are audio samples left unwritten from a previous write,
         * prepend them to the specified buffer. If it's possible to write them
         * now, do it.
         */
        if ((bufferLeft != null) && (bufferLeftLength > 0))
        {
            int numberOfBytesInBufferLeftToBytesPerBuffer
                = bytesPerBuffer - bufferLeftLength;
            int numberOfBytesToCopyToBufferLeft
                = (numberOfBytesInBufferLeftToBytesPerBuffer < length)
                    ? numberOfBytesInBufferLeftToBytesPerBuffer
                    : length;

            System.arraycopy(
                    buffer,
                    offset,
                    bufferLeft,
                    bufferLeftLength,
                    numberOfBytesToCopyToBufferLeft);
            offset += numberOfBytesToCopyToBufferLeft;
            length -= numberOfBytesToCopyToBufferLeft;
            bufferLeftLength += numberOfBytesToCopyToBufferLeft;

            if (bufferLeftLength == bytesPerBuffer)
            {
                Pa.WriteStream(stream, bufferLeft, framesPerBuffer);
                bufferLeftLength = 0;
            }
        }

        // Write the audio samples from the specified buffer.
        int numberOfWrites = length / bytesPerBuffer;

        if (numberOfWrites > 0)
        {
            /*
             * Take into account the user's preferences with respect to the
             * output volume.
             */
            GainControl gainControl = getGainControl();

            if (gainControl != null)
            {
                BasicVolumeControl.applyGain(
                        gainControl,
                        buffer, offset, length);
            }

            Pa.WriteStream(
                    stream,
                    buffer, offset, framesPerBuffer,
                    numberOfWrites);

            int bytesWritten = numberOfWrites * bytesPerBuffer;

            offset += bytesWritten;
            length -= bytesWritten;
        }

        // If anything was left unwritten, remember it for next time.
        if (length > 0)
        {
            if (bufferLeft == null)
                bufferLeft = new byte[bytesPerBuffer];
            System.arraycopy(buffer, offset, bufferLeft, 0, length);
            bufferLeftLength = length;
        }
    }

    /**
     * Sets the <tt>MediaLocator</tt> which specifies the device index of the
     * PortAudio device to be used by this instance for rendering.
     *
     * @param locator a <tt>MediaLocator</tt> which specifies the device index
     * of the PortAudio device to be used by this instance for rendering
     */
    @Override
    public void setLocator(MediaLocator locator)
    {
        super.setLocator(locator);

        supportedInputFormats = null;
    }

    /**
     * Indicates whether <tt>Pa_WriteStream</tt> is malfunctioning.
     *
     * @param writeIsMalfunctioning <tt>true</tt> if <tt>Pa_WriteStream</tt> is
     * malfunctioning; otherwise, <tt>false</tt>
     */
    private void setWriteIsMalfunctioning(boolean writeIsMalfunctioning)
    {
        if (writeIsMalfunctioning)
        {
            if (writeIsMalfunctioningSince == DiagnosticsControl.NEVER)
            {
                writeIsMalfunctioningSince = System.currentTimeMillis();
                PortAudioSystem.monitorFunctionalHealth(diagnosticsControl);
            }
        }
        else
            writeIsMalfunctioningSince = DiagnosticsControl.NEVER;
    }

    /**
     * Starts the rendering process. Any audio data available in the internal
     * resources associated with this <tt>PortAudioRenderer</tt> will begin
     * being rendered.
     */
    public synchronized void start()
    {
        if (!started && (stream != 0))
        {
            try
            {
                Pa.StartStream(stream);
                started = true;
                flags |= FLAG_STARTED;
            }
            catch (PortAudioException paex)
            {
                logger.error("Failed to start PortAudio stream.", paex);
            }
        }
    }

    /**
     * Stops the rendering process.
     */
    public synchronized void stop()
    {
        waitWhileStreamIsBusy();
        if (started && (stream != 0))
        {
            try
            {
                Pa.StopStream(stream);
                started = false;
                flags &= ~FLAG_STARTED;

                bufferLeft = null;
                if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                    setWriteIsMalfunctioning(false);
            }
            catch (PortAudioException paex)
            {
                logger.error("Failed to close PortAudio stream.", paex);
            }
        }
    }

    /**
     * Waits on this instance while {@link #streamIsBusy} is equal to
     * <tt>true</tt> i.e. until it becomes <tt>false</tt>. The method should
     * only be called by a thread that is the owner of this object's monitor.
     */
    private void waitWhileStreamIsBusy()
    {
        boolean interrupted = false;

        while (streamIsBusy)
        {
            try
            {
                wait();
            }
            catch (InterruptedException iex)
            {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }
}
