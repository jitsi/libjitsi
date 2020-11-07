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
package org.jitsi.impl.neomedia.jmfext.media.protocol.portaudio;

import java.io.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.impl.neomedia.portaudio.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.logging.*;

/**
 * Implements <tt>PullBufferStream</tt> for PortAudio.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class PortAudioStream
    extends AbstractPullBufferStream<DataSource>
{
    /**
     * The <tt>Logger</tt> used by the <tt>PortAudioStream</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(PortAudioStream.class);

    /**
     * The constant which expresses a non-existent time in milliseconds for the
     * purposes of {@link #readIsMalfunctioningSince}.
     */
    private static final long NEVER = DiagnosticsControl.NEVER;

    /**
     * Causes the currently executing thread to temporarily pause and allow
     * other threads to execute.
     */
    public static void yield()
    {
        boolean interrupted = false;

        try
        {
            Thread.sleep(Pa.DEFAULT_MILLIS_PER_BUFFER);
        }
        catch (InterruptedException ie)
        {
            interrupted = true;
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    /**
     * The indicator which determines whether audio quality improvement is
     * enabled for this <tt>PortAudioStream</tt> in accord with the preferences
     * of the user.
     */
    private final boolean audioQualityImprovement;

    /**
     * The number of bytes to read from a native PortAudio stream in a single
     * invocation. Based on {@link #framesPerBuffer}.
     */
    private int bytesPerBuffer;

    /**
     * The device identifier (the device UID, or if not available, the device
     * name) of the PortAudio device read through this
     * <tt>PullBufferStream</tt>.
     */
    private String deviceID;

    /**
     * The <tt>DiagnosticsControl</tt> implementation of this instance which
     * allows the diagnosis of the functional health of <tt>Pa_ReadStream</tt>.
     */
    private final DiagnosticsControl diagnosticsControl
        = new DiagnosticsControl()
        {
            /**
             * {@inheritDoc}
             *
             * <tt>PortAudioStream</tt>'s <tt>DiagnosticsControl</tt>
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
                return readIsMalfunctioningSince;
            }

            /**
             * {@inheritDoc}
             *
             * Returns the identifier of the PortAudio device read through this
             * <tt>PortAudioStream</tt>.
             */
            @Override
            public String toString()
            {
                String id = PortAudioStream.this.deviceID;
                String name = null;

                if (deviceID != null)
                {
                    int index
                        = Pa.getDeviceIndex(
                                id,
                                /* minInputChannels */ 1,
                                /* minOutputChannels */ 0);

                    if (index != Pa.paNoDevice)
                    {
                        long info = Pa.GetDeviceInfo(index);

                        if (info != 0)
                            name = Pa.DeviceInfo_getName(info);
                    }
                }
                return name;
            }
        };

    /**
     * The last-known <tt>Format</tt> of the media data made available by this
     * <tt>PullBufferStream</tt>.
     */
    private AudioFormat format;

    /**
     * The number of frames to read from a native PortAudio stream in a single
     * invocation.
     */
    private int framesPerBuffer;

    /**
     * The <tt>GainControl</tt> through which the volume/gain of captured media
     * is controlled.
     */
    private final GainControl gainControl;

    /**
     * Native pointer to a PaStreamParameters object.
     */
    private long inputParameters = 0;

    private final UpdateAvailableDeviceListListener
        paUpdateAvailableDeviceListListener
            = new UpdateAvailableDeviceListListener()
            {
                /**
                 * The device ID (could be deviceUID or name but that is not
                 * really of concern to PortAudioStream) used before and after
                 * (if still available) the update.
                 */
                private String deviceID = null;

                private boolean start = false;

                @Override
                public void didUpdateAvailableDeviceList()
                    throws Exception
                {
                    synchronized (PortAudioStream.this)
                    {
                        try
                        {
                            waitWhileStreamIsBusy();
                            /*
                             * The stream should be closed. If it is not, then
                             * something else happened in the meantime and we
                             * cannot be sure that restoring the old state of
                             * this PortAudioStream is the right thing to do in
                             * its new state.
                             */
                            if (stream == 0)
                            {
                                setDeviceID(deviceID);
                                if (start)
                                    start();
                            }
                        }
                        finally
                        {
                            /*
                             * If we had to attempt to restore the state of
                             * this PortAudioStream, we just did attempt to.
                             */
                            deviceID = null;
                            start = false;
                        }
                    }
                }

                @Override
                public void willUpdateAvailableDeviceList()
                    throws Exception
                {
                    synchronized (PortAudioStream.this)
                    {
                        waitWhileStreamIsBusy();
                        if (stream == 0)
                        {
                            deviceID = null;
                            start = false;
                        }
                        else
                        {
                            deviceID = PortAudioStream.this.deviceID;
                            start = PortAudioStream.this.started;

                            boolean disconnected = false;

                            try
                            {
                                setDeviceID(null);
                                disconnected = true;
                            }
                            finally
                            {
                                /*
                                 * If we failed to disconnect this
                                 * PortAudioStream, we will not attempt to
                                 * restore its state later on.
                                 */
                                if (!disconnected)
                                {
                                    deviceID = null;
                                    start = false;
                                }
                            }
                        }
                    }
                }
            };

    /**
     * The time in milliseconds at which <tt>Pa_ReadStream</tt> has started
     * malfunctioning. For example, <tt>Pa_ReadStream</tt> returning
     * <tt>paTimedOut</tt> and/or Windows Multimedia reporting
     * <tt>MMSYSERR_NODRIVER</tt> (may) indicate abnormal functioning.
     */
    private long readIsMalfunctioningSince = NEVER;

    /**
     * Current sequence number.
     */
    private int sequenceNumber = 0;

    private boolean started = false;

    /**
     * The input PortAudio stream represented by this instance.
     */
    private long stream = 0;

    /**
     * The indicator which determines whether {@link #stream} is busy and should
     * not, for example, be closed.
     */
    private boolean streamIsBusy = false;

    /**
     * Initializes a new <tt>PortAudioStream</tt> instance which is to have its
     * <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     * @param audioQualityImprovement <tt>true</tt> to enable audio quality
     * improvement for the new instance in accord with the preferences of the
     * user or <tt>false</tt> to completely disable audio quality improvement
     */
    public PortAudioStream(
            DataSource dataSource,
            FormatControl formatControl,
            boolean audioQualityImprovement)
    {
        super(dataSource, formatControl);

        this.audioQualityImprovement = audioQualityImprovement;

        MediaServiceImpl mediaServiceImpl
            = NeomediaServiceUtils.getMediaServiceImpl();

        gainControl
            = (mediaServiceImpl == null)
                ? null
                : (GainControl) mediaServiceImpl.getInputVolumeControl();

        /*
         * XXX We will add a UpdateAvailableDeviceListListener and will not
         * remove it because we will rely on PortAudioSystem's use of
         * WeakReference.
         */
        AudioSystem2 audioSystem
            = (AudioSystem2)
                AudioSystem.getAudioSystem(
                        AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO);

        if (audioSystem != null)
        {
            audioSystem.addUpdateAvailableDeviceListListener(
                    paUpdateAvailableDeviceListListener);
        }
    }

    private void connect()
        throws IOException
    {
        int deviceIndex
            = Pa.getDeviceIndex(
                    deviceID,
                    /* minInputChannels */ 1, /* minOutputChannels */ 0);

        if (deviceIndex == Pa.paNoDevice)
        {
            throw new IOException(
                    "The audio device " + deviceID
                        + " appears to be disconnected.");
        }

        AudioFormat format = (AudioFormat) getFormat();
        int channels = format.getChannels();

        if (channels == Format.NOT_SPECIFIED)
            channels = 1;

        int sampleSizeInBits = format.getSampleSizeInBits();
        long sampleFormat = Pa.getPaSampleFormat(sampleSizeInBits);
        double sampleRate = format.getSampleRate();
        int framesPerBuffer
            = (int)
                ((sampleRate * Pa.DEFAULT_MILLIS_PER_BUFFER)
                    / (channels * 1000));

        try
        {
            inputParameters
                = Pa.StreamParameters_new(
                        deviceIndex,
                        channels,
                        sampleFormat,
                        Pa.getSuggestedLatency());

            stream
                = Pa.OpenStream(
                        inputParameters,
                        0 /* outputParameters */,
                        sampleRate,
                        framesPerBuffer,
                        Pa.STREAM_FLAGS_CLIP_OFF | Pa.STREAM_FLAGS_DITHER_OFF,
                        null /* streamCallback */);
        }
        catch (PortAudioException paex)
        {
            logger.error("Failed to open " + getClass().getSimpleName(), paex);

            IOException ioex = new IOException(paex.getLocalizedMessage());

            ioex.initCause(paex);
            throw ioex;
        }
        finally
        {
            if ((stream == 0) && (inputParameters != 0))
            {
                Pa.StreamParameters_free(inputParameters);
                inputParameters = 0;
            }
        }
        if (stream == 0)
            throw new IOException("Pa_OpenStream");

        this.framesPerBuffer = framesPerBuffer;
        bytesPerBuffer
            = Pa.GetSampleSize(sampleFormat) * channels * framesPerBuffer;

        /*
         * Know the Format in which this PortAudioStream will output audio
         * data so that it can report it without going through its
         * DataSource.
         */
        this.format
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        sampleRate,
                        sampleSizeInBits,
                        channels,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.byteArray);

        boolean denoise = false;
        boolean echoCancel = false;
        long echoCancelFilterLengthInMillis
            = DeviceConfiguration
                .DEFAULT_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS;

        if (audioQualityImprovement)
        {
            AudioSystem audioSystem
                = AudioSystem.getAudioSystem(
                        AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO);

            if (audioSystem != null)
            {
                denoise = audioSystem.isDenoise();
                echoCancel = audioSystem.isEchoCancel();

                if (echoCancel)
                {
                    MediaServiceImpl mediaServiceImpl
                        = NeomediaServiceUtils.getMediaServiceImpl();

                    if (mediaServiceImpl != null)
                    {
                        DeviceConfiguration devCfg
                            = mediaServiceImpl.getDeviceConfiguration();

                        if (devCfg != null)
                        {
                            echoCancelFilterLengthInMillis
                                = devCfg.getEchoCancelFilterLengthInMillis();
                        }
                    }
                }
            }
        }

        Pa.setDenoise(stream, denoise);
        Pa.setEchoFilterLengthInMillis(
                stream,
                echoCancel ? echoCancelFilterLengthInMillis : 0);

        // Pa_ReadStream has not been invoked yet.
        if (readIsMalfunctioningSince != NEVER)
            setReadIsMalfunctioning(false);
    }

    /**
     * Gets the <tt>Format</tt> of this <tt>PullBufferStream</tt> as directly
     * known by it.
     *
     * @return the <tt>Format</tt> of this <tt>PullBufferStream</tt> as directly
     * known by it or <tt>null</tt> if this <tt>PullBufferStream</tt> does not
     * directly know its <tt>Format</tt> and it relies on the
     * <tt>PullBufferDataSource</tt> which created it to report its
     * <tt>Format</tt>
     * @see AbstractPullBufferStream#doGetFormat()
     */
    @Override
    protected Format doGetFormat()
    {
        return (format == null) ? super.doGetFormat() : format;
    }

    /**
     * Reads media data from this <tt>PullBufferStream</tt> into a specific
     * <tt>Buffer</tt> with blocking.
     *
     * @param buffer the <tt>Buffer</tt> in which media data is to be read from
     * this <tt>PullBufferStream</tt>
     * @throws IOException if anything goes wrong while reading media data from
     * this <tt>PullBufferStream</tt> into the specified <tt>buffer</tt>
     */
    public void read(Buffer buffer)
        throws IOException
    {
        String message;

        synchronized (this)
        {
            if (stream == 0)
                message = getClass().getName() + " is disconnected.";
            else if (!started)
                message = getClass().getName() + " is stopped.";
            else
            {
                message = null;
                streamIsBusy = true;
            }

            if (message != null)
            {
                /*
                 * There is certainly a problem but it is other than a
                 * malfunction in Pa_ReadStream.
                 */
                if (readIsMalfunctioningSince != NEVER)
                    setReadIsMalfunctioning(false);
            }
        }

        /*
         * The caller shouldn't call #read(Buffer) if this instance is
         * disconnected or stopped. Additionally, if she does, she may be
         * persistent. If we do not slow her down, she may hog the CPU.
         */
        if (message != null)
        {
            yield();
            throw new IOException(message);
        }

        long errorCode = Pa.paNoError;
        Pa.HostApiTypeId hostApiType = null;

        try
        {
            /*
             * Reuse the data of buffer in order to not perform unnecessary
             * allocations.
             */
            byte[] data
                = AbstractCodec2.validateByteArraySize(
                        buffer,
                        bytesPerBuffer,
                        false);

            try
            {
                Pa.ReadStream(stream, data, framesPerBuffer);
            }
            catch (PortAudioException pae)
            {
                errorCode = pae.getErrorCode();
                hostApiType = pae.getHostApiType();

                logger.error("Failed to read from PortAudio stream.", pae);

                IOException ioe = new IOException(pae.getLocalizedMessage());

                ioe.initCause(pae);
                throw ioe;
            }

            /*
             * Take into account the user's preferences with respect to the
             * input volume.
             */
            if (gainControl != null)
            {
                BasicVolumeControl.applyGain(
                        gainControl,
                        data, 0, bytesPerBuffer);
            }

            long bufferTimeStamp = System.nanoTime();

            buffer.setFlags(Buffer.FLAG_SYSTEM_TIME);
            if (format != null)
                buffer.setFormat(format);
            buffer.setHeader(null);
            buffer.setLength(bytesPerBuffer);
            buffer.setOffset(0);
            buffer.setSequenceNumber(sequenceNumber++);
            buffer.setTimeStamp(bufferTimeStamp);
        }
        finally
        {
            /*
             * If a timeout has occurred in the method Pa.ReadStream, give the
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
                    // Pa_ReadStream appears to function normally.
                    if (readIsMalfunctioningSince != NEVER)
                        setReadIsMalfunctioning(false);
                }
                else if ((Pa.paTimedOut == errorCode)
                        || (Pa.HostApiTypeId.paMME.equals(hostApiType)
                                && (Pa.MMSYSERR_NODRIVER == errorCode)))
                {
                    if (readIsMalfunctioningSince == NEVER)
                        setReadIsMalfunctioning(true);
                    yield = true;
                }
            }

            if (yield)
                yield();
        }
    }

    /**
     * Sets the device index of the PortAudio device to be read through this
     * <tt>PullBufferStream</tt>.
     *
     * @param deviceID The ID of the device used to be read trough this
     * PortAudioStream.  This String contains the deviceUID, or if not
     * available, the device name.  If set to null, then there was no device
     * used before the update.
     *
     * @throws IOException if input/output error occurred
     */
    synchronized void setDeviceID(String deviceID)
        throws IOException
    {
        /*
         * We should better not short-circuit because the deviceID may be the
         * same but it eventually resolves to a deviceIndex and may have changed
         * after hotplugging.
         */

        // DataSource#disconnect
        if (this.deviceID != null)
        {
            /*
             * Just to be on the safe side, make sure #read(Buffer) is not
             * currently executing.
             */
            waitWhileStreamIsBusy();

            if (stream != 0)
            {
                /*
                 * For the sake of completeness, attempt to stop this instance
                 * before disconnecting it.
                 */
                if (started)
                {
                    try
                    {
                        stop();
                    }
                    catch (IOException ioe)
                    {
                        /*
                         * The exception should have already been logged by the
                         * method #stop(). Additionally and as said above, we
                         * attempted it out of courtesy.
                         */
                    }
                }

                boolean closed = false;

                try
                {
                    Pa.CloseStream(stream);
                    closed = true;
                }
                catch (PortAudioException pae)
                {
                    /*
                     * The function Pa_CloseStream is not supposed to time out
                     * under normal execution. However, we have modified it to
                     * do so under exceptional circumstances on Windows at least
                     * in order to overcome endless loops related to
                     * hotplugging. In such a case, presume the native PortAudio
                     * stream closed in order to maybe avoid a crash at the risk
                     * of a memory leak.
                     */
                    long errorCode = pae.getErrorCode();

                    if ((errorCode == Pa.paTimedOut)
                            || (Pa.HostApiTypeId.paMME.equals(
                                        pae.getHostApiType())
                                    && (errorCode == Pa.MMSYSERR_NODRIVER)))
                    {
                        closed = true;
                    }

                    if (!closed)
                    {
                        logger.error(
                                "Failed to close " + getClass().getSimpleName(),
                                pae);

                        IOException ioe
                            = new IOException(pae.getLocalizedMessage());

                        ioe.initCause(pae);
                        throw ioe;
                    }
                }
                finally
                {
                    if (closed)
                    {
                        stream = 0;

                        if (inputParameters != 0)
                        {
                            Pa.StreamParameters_free(inputParameters);
                            inputParameters = 0;
                        }

                        /*
                         * Make sure this AbstractPullBufferStream asks its
                         * DataSource for the Format in which it is supposed to
                         * output audio data the next time it is opened instead
                         * of using its Format from a previous open.
                         */
                        this.format = null;

                        if (readIsMalfunctioningSince != NEVER)
                            setReadIsMalfunctioning(false);
                    }
                }
            }
        }

        this.deviceID = deviceID;
        this.started = false;

        // DataSource#connect
        if (this.deviceID != null)
        {
            AudioSystem2 audioSystem
                = (AudioSystem2)
                    AudioSystem.getAudioSystem(
                            AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO);

            if (audioSystem != null)
                audioSystem.willOpenStream();
            try
            {
                connect();
            }
            finally
            {
                if (audioSystem != null)
                    audioSystem.didOpenStream();
            }
        }
    }

    /**
     * Indicates whether <tt>Pa_ReadStream</tt> is malfunctioning.
     *
     * @param malfunctioning <tt>true</tt> if <tt>Pa_ReadStream</tt> is
     * malfunctioning; otherwise, <tt>false</tt>
     */
    private void setReadIsMalfunctioning(boolean malfunctioning)
    {
        if (malfunctioning)
        {
            if (readIsMalfunctioningSince == NEVER)
            {
                readIsMalfunctioningSince = System.currentTimeMillis();
                PortAudioSystem.monitorFunctionalHealth(diagnosticsControl);
            }
        }
        else
            readIsMalfunctioningSince = NEVER;
    }

    /**
     * Starts the transfer of media data from this <tt>PullBufferStream</tt>.
     *
     * @throws IOException if anything goes wrong while starting the transfer of
     * media data from this <tt>PullBufferStream</tt>
     */
    @Override
    public synchronized void start()
        throws IOException
    {
        if (stream != 0)
        {
            waitWhileStreamIsBusy();

            try
            {
                Pa.StartStream(stream);
                started = true;
            }
            catch (PortAudioException paex)
            {
                logger.error(
                        "Failed to start " + getClass().getSimpleName(),
                        paex);

                IOException ioex = new IOException(paex.getLocalizedMessage());

                ioex.initCause(paex);
                throw ioex;
            }
        }
    }

    /**
     * Stops the transfer of media data from this <tt>PullBufferStream</tt>.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of
     * media data from this <tt>PullBufferStream</tt>
     */
    @Override
    public synchronized void stop()
        throws IOException
    {
        if (stream != 0)
        {
            waitWhileStreamIsBusy();

            try
            {
                Pa.StopStream(stream);
                started = false;

                if (readIsMalfunctioningSince != NEVER)
                    setReadIsMalfunctioning(false);
            }
            catch (PortAudioException paex)
            {
                logger.error(
                        "Failed to stop " + getClass().getSimpleName(),
                        paex);

                IOException ioex = new IOException(paex.getLocalizedMessage());

                ioex.initCause(paex);
                throw ioex;
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

        while ((stream != 0) && streamIsBusy)
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
