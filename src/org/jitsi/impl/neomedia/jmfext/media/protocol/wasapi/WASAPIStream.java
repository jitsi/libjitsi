/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.WASAPI.*;

import java.io.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.util.*;

/**
 * Implements a <tt>PullBufferStream</tt> using Windows Audio Session API
 * (WASAPI) and related Core Audio APIs such as Multimedia Device (MMDevice)
 * API.
 *
 * @author Lyubomir Marinov
 */
public class WASAPIStream
    extends AbstractPullBufferStream
{
    /**
     * The default duration of the audio data in milleseconds to be read from
     * <tt>WASAPIStream</tt> in an invocation of {@link #read(Buffer)}.
     */
    private static final long DEFAULT_BUFFER_DURATION = 20;

    /**
     * The <tt>Logger</tt> used by the <tt>WASAPIStream</tt> class and its
     * instances to log debug information.
     */
    private static Logger logger = Logger.getLogger(WASAPIStream.class);

    /**
     * The <tt>WASAPISystem</tt> instance which has contributed the capture
     * endpoint device identified by {@link #locator}.
     */
    private final WASAPISystem audioSystem;

    /**
     * The length in bytes of the <tt>Buffer</tt> to be filled in an invocation
     * of {@link #read(Buffer)}.
     */
    private int bufferLength;

    /**
     * The indicator which determines whether the audio stream represented by
     * this instance, {@link #iAudioClient} and {@link #iAudioCaptureClient} is
     * busy and, consequently, its state should not be modified. For example,
     * the audio stream is busy during the execution of {@link #read(Buffer)}.
     */
    private boolean busy;

    /**
     * The number of channels which which this <tt>SourceStream</tt> has been
     * connected.
     */
    private int dstChannels;

    /**
     * The frame size in bytes with which this <tt>SourceStream</tt> has been
     * connected. It is the product of {@link #dstSampleSize} and
     * {@link #dstChannels}.
     */
    private int dstFrameSize;

    /**
     * The sample size in bytes with which this <tt>SourceStream</tt> has been
     * connected.
     */
    private int dstSampleSize;

    /**
     * The <tt>AudioFormat</tt> of this <tt>SourceStream</tt>.
     */
    private AudioFormat format;

    /**
     * The WASAPI <tt>IAudioCaptureClient</tt> obtained from
     * {@link #iAudioClient} which enables this <tt>SourceStream</tt> to read
     * input data from the capture endpoint buffer.
     */
    private long iAudioCaptureClient;

    /**
     * The WASAPI <tt>IAudioClient</tt> instance which enables this
     * <tt>SourceStream</tt> to create and initialize an audio stream between
     * this <tt>SourceStream</tt> and the audio engine of the associated audio
     * endpoint device.
     */
    private long iAudioClient;

    /**
     * The <tt>MediaLocator</tt> which identifies the audio endpoint device this
     * <tt>SourceStream</tt> is to capture data from.
     */
    private MediaLocator locator;

    /**
     * The length in milliseconds of the periodic interval separating successive
     * processing passes by the audio engine on the data in the endpoint buffer.
     */
    private long periodicity = DEFAULT_BUFFER_DURATION / 2;

    /**
     * The number of channels with which {@link #iAudioClient} has been
     * initialized.
     */
    private int srcChannels;

    /**
     * The sample size in bytes with which {@link #iAudioClient} has been
     * initialized.
     */
    private int srcSampleSize;

    /**
     * The indicator which determines whether this <tt>SourceStream</tt> is
     * started i.e. there has been a successful invocation of {@link #start()}
     * without an intervening invocation of {@link #stop()}.
     */
    private boolean started;

    /**
     * Initializes a new <tt>WASAPIStream</tt> instance which is to have its
     * <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is initializing the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     */
    public WASAPIStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);

        audioSystem
            = (WASAPISystem)
                AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_WASAPI);
        if (audioSystem == null)
            throw new IllegalStateException("audioSystem");
    }

    /**
     * Connects this <tt>SourceStream</tt> to the audio endpoint device
     * identified by {@link #locator}.
     *
     * @throws IOException if anything goes wrong while this
     * <tt>SourceStream</tt> connects to the audio endpoint device identified by
     * <tt>locator</tt>
     */
    private void connect()
        throws IOException
    {
        if (this.iAudioClient != 0)
            return;

        try
        {
            MediaLocator locator = getLocator();
            AudioSystem.DataFlow dataFlow = AudioSystem.DataFlow.CAPTURE;
            long hnsBufferDuration = 2 * DEFAULT_BUFFER_DURATION * 10000;
            AudioFormat thisFormat = (AudioFormat) getFormat();
            AudioFormat[] formats
                = WASAPISystem.getFormatsToInitializeIAudioClient(thisFormat);
            long iAudioClient
                = audioSystem.initializeIAudioClient(
                        locator,
                        dataFlow,
                        /* eventHandle */ 0,
                        hnsBufferDuration,
                        formats);

            if (iAudioClient == 0)
            {
                throw new ResourceUnavailableException(
                        "Failed to initialize IAudioClient for MediaLocator "
                            + locator + " and AudioSystem.DataFlow "
                            + dataFlow);
            }
            try
            {
                /*
                 * Determine the AudioFormat with which the iAudioClient has
                 * been initialized.
                 */
                AudioFormat format = null;

                for (AudioFormat aFormat : formats)
                {
                    if (aFormat != null)
                    {
                        format = aFormat;
                        break;
                    }
                }

                long iAudioCaptureClient
                    = IAudioClient_GetService(
                            iAudioClient,
                            IID_IAudioCaptureClient);

                if (iAudioCaptureClient == 0)
                {
                    throw new ResourceUnavailableException(
                            "IAudioClient_GetService(IID_IAudioCaptureClient)");
                }
                try
                {
                    /*
                     * The value hnsDefaultDevicePeriod is documented to specify
                     * the default scheduling period for a shared-mode stream.
                     */
                    periodicity
                        = IAudioClient_GetDefaultDevicePeriod(iAudioClient);

                    int numBufferFrames
                        = IAudioClient_GetBufferSize(iAudioClient);
                    long bufferDuration
                        = numBufferFrames * 1000 / (int) format.getSampleRate();

                    periodicity /= 10000;
                    /*
                     * We will very likely be inefficient if we fail to
                     * synchronize with the scheduling period of the audio
                     * engine but we have to make do with what we have.
                     */
                    if (periodicity <= 1)
                    {
                        periodicity = bufferDuration / 4;
                        if (periodicity < DEFAULT_BUFFER_DURATION / 2)
                            periodicity = DEFAULT_BUFFER_DURATION / 2;
                    }

                    srcChannels = format.getChannels();
                    srcSampleSize = WASAPISystem.getSampleSizeInBytes(format);

                    dstChannels = thisFormat.getChannels();
                    dstSampleSize
                        = WASAPISystem.getSampleSizeInBytes(thisFormat);

                    dstFrameSize = dstSampleSize * dstChannels;
                    bufferLength
                        = (int)
                            (DEFAULT_BUFFER_DURATION
                                    * dstFrameSize
                                    * ((int) thisFormat.getSampleRate())
                                / 1000);

                    this.format = thisFormat;

                    this.iAudioClient = iAudioClient;
                    iAudioClient = 0;
                    this.iAudioCaptureClient = iAudioCaptureClient;
                    iAudioCaptureClient = 0;
                }
                finally
                {
                    if (iAudioCaptureClient != 0)
                        IAudioCaptureClient_Release(iAudioCaptureClient);
                }
            }
            finally
            {
                if (iAudioClient != 0)
                    IAudioClient_Release(iAudioClient);
            }
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else if (t instanceof IOException)
                throw (IOException) t;
            else
            {
                logger.error(
                        "Failed to connect a WASAPIStream"
                            + " to an audio endpoint device.",
                        t);

                IOException ioe = new IOException();

                ioe.initCause(t);
                throw ioe;
            }
        }
    }

    /**
     * Disconnects this <tt>SourceStream</tt> from the audio endpoint device it
     * has previously connected to during the execution of {@link #connect()}.
     *
     * @throws IOException if anything goes wrong while this
     * <tt>SourceStream</tt> disconnects from the audio endpoint device it has
     * previously connected to during the execution of <tt>connect()</tt>
     */
    private void disconnect()
        throws IOException
    {
        try
        {
            stop();
        }
        finally
        {
            if (iAudioCaptureClient != 0)
            {
                IAudioCaptureClient_Release(iAudioCaptureClient);
                iAudioCaptureClient = 0;
            }
            if (iAudioClient != 0)
            {
                IAudioClient_Release(iAudioClient);
                iAudioClient = 0;
            }

            /*
             * Make sure this AbstractPullBufferStream asks its DataSource for
             * the Format in which it is supposed to output audio data the next
             * time it is connected instead of using its Format from a previous
             * connect.
             */
            format = null;
            started = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Format doGetFormat()
    {
        return (format == null) ? super.doGetFormat() : format;
    }

    /**
     * Reads the next data packet from the capture endpoint buffer into a
     * specific <tt>Buffer</tt>.
     *
     * @param buffer the <tt>Buffer</tt> to read the next data packet from the
     * capture endpoint buffer into
     * @return the number of bytes read from the capture endpoint buffer into
     * the value of the <tt>data</tt> property of <tt>buffer</tt>
     * @throws IOException if an I/O error occcurs
     */
    public int doRead(Buffer buffer)
        throws IOException
    {
        /*
         * Determine the size in bytes of the next data packet in the
         * capture endpoint buffer.
         */
        int numFramesInNextPacket;

        try
        {
            numFramesInNextPacket
                = IAudioCaptureClient_GetNextPacketSize(iAudioCaptureClient);
        }
        catch (HResultException hre)
        {
            numFramesInNextPacket = 0; // Silence the compiler.
            throwNewIOException("IAudioCaptureClient_GetNextPacketSize", hre);
        }

        int offset = buffer.getOffset() + buffer.getLength();
        int toRead = numFramesInNextPacket * dstFrameSize;
        byte[] data
            = AbstractCodec2.validateByteArraySize(
                    buffer,
                    offset + toRead,
                    true);

        int numPaddingFrames;

        try
        {
            numPaddingFrames = IAudioClient_GetCurrentPadding(iAudioClient);
        }
        catch (HResultException hre)
        {
            numPaddingFrames = 0; // Silence the compiler.
            throwNewIOException("IAudioClient_GetCurrentPadding", hre);
        }

        int read;

        if ((numFramesInNextPacket != 0)
                && (numFramesInNextPacket <= numPaddingFrames))
        {
            try
            {
                read
                    = IAudioCaptureClient_Read(
                            iAudioCaptureClient,
                            data, offset, toRead,
                            srcSampleSize, srcChannels,
                            dstSampleSize, dstChannels);
            }
            catch (HResultException hre)
            {
                read = 0; // Silence the compiler.
                throwNewIOException("IAudioCaptureClient_Read", hre);
            }

            if ((read != 0) && (offset == 0))
            {
                long timeStamp = System.nanoTime();

                buffer.setFlags(Buffer.FLAG_SYSTEM_TIME);
                buffer.setTimeStamp(timeStamp);
            }
        }
        else
        {
            /*
             * The next data packet in the capture endpoint buffer is not
             * available yet.
             */
            read = 0;
        }

        return read;
    }

    /**
     * Gets the <tt>MediaLocator</tt> of this instance which identifies the
     * audio endpoint device this <tt>SourceStream</tt> is to capture data from.
     *
     * @return the <tt>MediaLocator</tt> of this instance which identifies the
     * audio endpoint device this <tt>SourceStream</tt> is to capture data from
     */
    private MediaLocator getLocator()
    {
        return locator;
    }

    /**
     * {@inheritDoc}
     */
    public void read(Buffer buffer)
        throws IOException
    {
        if (bufferLength != 0) // Reduce relocation as much as possible.
            AbstractCodec2.validateByteArraySize(buffer, bufferLength, false);
        buffer.setLength(0);
        buffer.setOffset(0);

        do
        {
            String message;

            synchronized (this)
            {
                if ((iAudioClient == 0) || (iAudioCaptureClient == 0))
                    message = getClass().getName() + " is disconnected.";
                else if (!started)
                    message = getClass().getName() + " is stopped.";
                else
                {
                    message = null;
                    busy = true;
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

            int read;
            Throwable cause;

            try
            {
                read = doRead(buffer);
                cause = null;
            }
            catch (Throwable t)
            {
                /*
                 * The exception will be rethrown after we exit the busy block
                 * of this Renderer.
                 */
                read = 0;
                cause = t;
            }
            finally
            {
                synchronized (this)
                {
                    busy = false;
                    notifyAll();
                }
            }

            if (cause == null)
            {
                if (read == 0)
                {
                    /*
                     * The next data packet in the capture endpoint buffer is
                     * (very likely) not available yet, we will want to wait a
                     * bit for it to be made available.
                     */
                    boolean interrupted = false;

                    synchronized (this)
                    {
                        /*
                         * Spurious wake-ups should not be a big issue here.
                         * While this SourceStream may query the availability of
                         * the next data packet in the capture endpoint buffer
                         * more often than practically necessary (which may very
                         * well classify as a case of performance loss), the
                         * ability to unblock this SourceStream is considered
                         * more important.
                         */
                        try
                        {
                            wait(periodicity);
                        }
                        catch (InterruptedException ie)
                        {
                            interrupted = true;
                        }
                    }
                    if (interrupted)
                        Thread.currentThread().interrupt();
                }
                else
                {
                    int length = buffer.getLength() + read;

                    buffer.setLength(length);
                    if (length >= bufferLength)
                    {
                        if (format != null)
                            buffer.setFormat(format);
                        break;
                    }
                }
            }
            else
            {
                if (cause instanceof ThreadDeath)
                    throw (ThreadDeath) cause;
                else if (cause instanceof IOException)
                    throw (IOException) cause;
                else
                {
                    IOException ioe = new IOException();

                    ioe.initCause(cause);
                    throw ioe;
                }
            }
        }
        while (true);
    }

    /**
     * Sets the <tt>MediaLocator</tt> of this instance which identifies the
     * audio endpoint device this <tt>SourceStream</tt> is to capture data from.
     *
     * @param locator a <tt>MediaLocator</tt> which identifies the audio
     * endpoint device this <tt>SourceStream</tt> is to capture data from
     * @throws IOException if anything goes wrong while setting the specified
     * <tt>locator</tt> on this instance
     */
    void setLocator(MediaLocator locator)
        throws IOException
    {
        if (this.locator != locator)
        {
            if (this.locator != null)
                disconnect();

            this.locator = locator;

            if (this.locator != null)
                connect();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start()
        throws IOException
    {
        if (iAudioClient != 0)
        {
            waitWhileBusy();

            try
            {
                IAudioClient_Start(iAudioClient);
                started = true;
            }
            catch (HResultException hre)
            {
                /*
                 * If IAudioClient_Start is invoked multiple times without
                 * intervening IAudioClient_Stop, it will likely return/throw
                 * AUDCLNT_E_NOT_STOPPED.
                 */
                if (hre.getHResult() != AUDCLNT_E_NOT_STOPPED)
                    throwNewIOException("IAudioClient_Start", hre);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop()
        throws IOException
    {
        if (iAudioClient != 0)
        {
            waitWhileBusy();

            try
            {
                /*
                 * If IAudioClient_Stop is invoked multiple times without
                 * intervening IAudioClient_Start, it is documented to return
                 * S_FALSE.
                 */
                IAudioClient_Stop(iAudioClient);
                started = false;
            }
            catch (HResultException hre)
            {
                throwNewIOException("IAudioClient_Stop", hre);
            }
        }
    }

    /**
     * Throws a new <tt>IOException</tt> instance initialized with a specific
     * <tt>String</tt> message and a specific <tt>HResultException</tt> cause.
     *
     * @param message the message to initialize the new <tt>IOException</tt>
     * instance with
     * @param hre an <tt>HResultException</tt> which is to be set as the
     * <tt>cause</tt> of the new <tt>IOException</tt> instance
     */
    private static void throwNewIOException(
            String message,
            HResultException hre)
        throws IOException
    {
        logger.error(message, hre);

        IOException ioe = new IOException(message);

        ioe.initCause(hre);
        throw ioe;
    }

    /**
     * Waits on this instance while the value of {@link #busy} is equal to
     * <tt>true</tt>.
     */
    private synchronized void waitWhileBusy()
    {
        boolean interrupted = false;

        while (busy)
        {
            try
            {
                wait(periodicity);
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
     * Causes the currently executing thread to temporarily pause and allow
     * other threads to execute.
     */
    public static void yield()
    {
        boolean interrupted = false;

        try
        {
            Thread.sleep(DEFAULT_BUFFER_DURATION);
        }
        catch (InterruptedException ie)
        {
            interrupted = true;
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }
}
