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
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.WASAPI.*;

import java.io.*;
import java.util.concurrent.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.utils.logging.*;

/**
 * Abstracts the initialization of an <tt>IAudioCaptureClient</tt> instance from
 * a <tt>MediaLocator</tt>, the input of data from that
 * <tt>IAudioCaptureClient</tt>, the buffering of that data, the starting,
 * stopping and closing of the <tt>IAudioCaptureClient</tt>. Allows
 * {@link WASAPIStream} to simultaneously utilize multiple
 * <tt>IAudioCaptureClient</tt> instances (e.g. in the case of acoustic echo
 * cancellation in which audio is input from both the capture and the render
 * endpoint devices).
 *
 * @author Lyubomir Marinov
 */
public class AudioCaptureClient
{
    /**
     * The <tt>Logger</tt> used by the <tt>AudioCaptureClient</tt> class and its
     * instances to log debug information.
     */
    private static final Logger logger
        = Logger.getLogger(AudioCaptureClient.class);

    /**
     * Invokes {@link WASAPI#IAudioCaptureClient_GetNextPacketSize(long)} on a
     * specific <tt>IAudioCaptureClient</tt> and logs and swallows any
     * <tt>HResultException</tt>.
     *
     * @param iAudioCaptureClient the <tt>IAudioCaptureClient</tt> of which to
     * retrieve the number of frames in the next data packet
     * @return the number of frames in the next data packet in the capture
     * endpoint buffer associated with the specified
     * <tt>iAudioCaptureClient</tt>. If the function/method
     * <tt>IAudioCaptureClient_GetNextPacketSize</tt> throws an
     * <tt>HResultException</tt>, return <tt>0</tt>.
     */
    private static int maybeIAudioCaptureClientGetNextPacketSize(
            long iAudioCaptureClient)
    {
        int numFramesInNextPacket;

        try
        {
            numFramesInNextPacket
                = IAudioCaptureClient_GetNextPacketSize(iAudioCaptureClient);
        }
        catch (HResultException hre)
        {
            numFramesInNextPacket = 0; // Silence the compiler.
            logger.error("IAudioCaptureClient_GetNextPacketSize", hre);
        }
        return numFramesInNextPacket;
    }

    /**
     * The internal buffer of this instance in which audio data is read from the
     * associated <tt>IAudioCaptureClient</tt> by the instance and awaits to be
     * read out of this instance via {@link #read(byte[], int, int)}.
     */
    private byte[] available;

    /**
     * The number of bytes in {@link #available} which represent valid audio
     * data read from the associated <tt>IAudioCaptureClient</tt> by this
     * instance.
     */
    private int availableLength;

    /**
     * The number of audio frames to be filled in a <tt>byte[]</tt> in an
     * invocation of {@link #read(byte[], int, int)}. The method
     * {@link #runInEventHandleCmd(Runnable)} will push via
     * {@link BufferTransferHandler#transferData(PushBufferStream)} when
     * {@link #iAudioClient} has made at least that many audio frames available.
     */
    private int bufferFrames;

    /**
     * The size/length in bytes of the <tt>byte[]</tt> to be filled in an
     * invocation of {@link #read(byte[], int, int)}.
     */
    final int bufferSize;

    /**
     * The indicator which determines whether the audio stream represented by
     * this instance, {@link #iAudioClient} and {@link #iAudioCaptureClient} is
     * busy and, consequently, its state should not be modified. For example,
     * the audio stream is busy during the execution of
     * {@link #read(byte[], int, int)}.
     */
    private boolean busy;

    /**
     * The length in milliseconds of the interval between successive, periodic
     * processing passes by the audio engine on the data in the endpoint buffer.
     */
    final long devicePeriod;

    /**
     * The number of channels of the audio data made available by this instance.
     */
    private int dstChannels;

    /**
     * The frame size in bytes of the audio data made available by this
     * instance. It is the product of {@link #dstSampleSize} and
     * {@link #dstChannels}.
     */
    private int dstFrameSize;

    /**
     * The sample size in bytes of the audio data made available by this
     * instance.
     */
    private int dstSampleSize;

    /**
     * The event handle that the system signals when an audio buffer is ready to
     * be processed by the client.
     */
    private long eventHandle;

    /**
     * The <tt>Runnable</tt> which is scheduled by this instance and executed by
     * {@link #eventHandleExecutor} and waits for {@link #eventHandle} to be
     * signaled.
     */
    private Runnable eventHandleCmd;

    /**
     * The <tt>Executor</tt> implementation which is to execute
     * {@link #eventHandleCmd}.
     */
    private Executor eventHandleExecutor;

    /**
     * The WASAPI <tt>IAudioCaptureClient</tt> obtained from
     * {@link #iAudioClient} which enables this instance to read input data from
     * the capture endpoint buffer.
     */
    private long iAudioCaptureClient;

    /**
     * The WASAPI <tt>IAudioClient</tt> instance which enables this
     * <tt>AudioCaptureClient</tt> to create and initialize an audio stream
     * between this instance and the audio engine of the associated audio
     * endpoint device.
     */
    private long iAudioClient;

    /**
     * The <tt>AudioFormat</tt> of the data output/made available by this
     * <tt>AudioCaptureClient</tt>.
     */
    final AudioFormat outFormat;

    /**
     * The indicator which reports whether audio samples have been read out of
     * this instance since the indicator has been set to <tt>false</tt>. For
     * example, determines whether audio samples have been read out of this
     * instance since this instance has given control to its associated
     * <tt>BufferTransferHandler</tt> (if any) and thus resolves a condition
     * similar to a busy wait which arises if the associated
     * <tt>BufferTransferHandler</tt> does not read any data.
     */
    private boolean read;

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
     * The indicator which determines whether this <tt>AudioCaptureClient</tt>
     * is started i.e. there has been a successful invocation of
     * {@link #start()} without an intervening invocation of {@link #stop()}.
     */
    private boolean started;

    /**
     * The <tt>BufferTransferHandler</tt> which is to be invoked when this
     * instance has made audio data available to be read via
     * {@link #read(byte[], int, int)}.
     * {@link BufferTransferHandler#transferData(PushBufferStream)} will be
     * called with a <tt>null</tt> argument because <tt>AudioCaptureClient</tt>
     * does not implement <tt>PushBufferStream</tt> and has rather been
     * refactored out of a <tt>PushBufferStream</tt> implementation (i.e.
     * <tt>WASAPIStream</tt>).
     */
    private final BufferTransferHandler transferHandler;

    /**
     * Initializes a new <tt>AudioCaptureClient</tt> instance.
     *
     * @param audioSystem the <tt>WASAPISystem</tt> instance which has
     * contributed <tt>locator</tt> 
     * @param locator a <tt>MediaLocator</tt> which identifies the audio
     * endpoint device to be opened and read by the new instance 
     * @param dataFlow the <tt>AudioSystem.DataFlow</tt> of the audio endpoint
     * device identified by <tt>locator</tt>. If
     * <tt>AudioSystem.DataFlow.PLAYBACK</tt> and <tt>streamFlags</tt> includes
     * {@link WASAPI#AUDCLNT_STREAMFLAGS_LOOPBACK}, allows opening a render
     * endpoint device in loopback mode and inputing the data that is being
     * written on that render endpoint device
     * @param streamFlags zero or more of the <tt>AUDCLNT_STREAMFLAGS_XXX</tt>
     * flags defined by the <tt>WASAPI</tt> class
     * @param hnsBufferDuration the base of the duration in milliseconds of the
     * buffer that the audio application will share with the audio engine. If
     * {@link Format#NOT_SPECIFIED}, the method uses the default interval
     * between periodic passes by the audio engine.
     * @param outFormat the <tt>AudioFormat</tt> of the data to be made
     * available by the new instance. Eventually, the
     * <tt>IAudioCaptureClient</tt> to be represented by the new instance may be
     * initialized with a different <tt>AudioFormat</tt> in which case the new
     * instance will automatically transcode the data input from the
     * <tt>IAudioCaptureClient</tt> into the specified <tt>outFormat</tt>.
     * @param transferHandler the <tt>BufferTransferHandler</tt> to be invoked
     * when the new instance has made data available to be read via
     * {@link #read(byte[], int, int)}
     * @throws Exception if the initialization of the new instance fails
     */
    public AudioCaptureClient(
            WASAPISystem audioSystem,
            MediaLocator locator,
            AudioSystem.DataFlow dataFlow,
            int streamFlags,
            long hnsBufferDuration,
            AudioFormat outFormat,
            BufferTransferHandler transferHandler)
        throws Exception
    {
        AudioFormat[] formats
            = WASAPISystem.getFormatsToInitializeIAudioClient(outFormat);
        long eventHandle = CreateEvent(0, false, false, null);

        if (eventHandle == 0)
            throw new IOException("CreateEvent");
        try
        {
            /*
             * Presently, we attempt to have the same buffer length in
             * WASAPIRenderer and WASAPIStream. There is no particular
             * reason/requirement to do so.
             */
            long iAudioClient
                = audioSystem.initializeIAudioClient(
                        locator,
                        dataFlow,
                        streamFlags,
                        eventHandle,
                        hnsBufferDuration,
                        formats);

            if (iAudioClient == 0)
            {
                throw new ResourceUnavailableException(
                        "Failed to initialize IAudioClient"
                            + " for MediaLocator " + locator
                            + " and AudioSystem.DataFlow " + dataFlow);
            }
            try
            {
                /*
                 * Determine the AudioFormat with which the iAudioClient has
                 * been initialized.
                 */
                AudioFormat inFormat = null;

                for (AudioFormat aFormat : formats)
                {
                    if (aFormat != null)
                    {
                        inFormat = aFormat;
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
                            "IAudioClient_GetService"
                                + "(IID_IAudioCaptureClient)");
                }
                try
                {
                    /*
                     * The value hnsDefaultDevicePeriod is documented to
                     * specify the default scheduling period for a
                     * shared-mode stream.
                     */
                    long devicePeriod
                        = IAudioClient_GetDefaultDevicePeriod(iAudioClient)
                            / 10000L;

                    int numBufferFrames
                        = IAudioClient_GetBufferSize(iAudioClient);
                    int sampleRate = (int) inFormat.getSampleRate();
                    long bufferDuration
                        = numBufferFrames * 1000 / sampleRate;

                    /*
                     * We will very likely be inefficient if we fail to
                     * synchronize with the scheduling period of the audio
                     * engine but we have to make do with what we have.
                     */
                    if (devicePeriod <= 1)
                    {
                        devicePeriod = bufferDuration / 2;
                        if ((devicePeriod
                                    > WASAPISystem.DEFAULT_DEVICE_PERIOD)
                                || (devicePeriod <= 1))
                            devicePeriod
                                = WASAPISystem.DEFAULT_DEVICE_PERIOD;
                    }
                    this.devicePeriod = devicePeriod;
                    if (hnsBufferDuration == Format.NOT_SPECIFIED)
                        hnsBufferDuration = devicePeriod;

                    srcChannels = inFormat.getChannels();
                    srcSampleSize
                        = WASAPISystem.getSampleSizeInBytes(inFormat);

                    dstChannels = outFormat.getChannels();
                    dstSampleSize
                        = WASAPISystem.getSampleSizeInBytes(outFormat);

                    dstFrameSize = dstSampleSize * dstChannels;
                    bufferFrames
                        = (int) (hnsBufferDuration * sampleRate / 1000);
                    bufferSize = dstFrameSize * bufferFrames;

                    available = new byte[numBufferFrames * dstFrameSize];
                    availableLength = 0;

                    this.eventHandle = eventHandle;
                    eventHandle = 0;
                    this.iAudioClient = iAudioClient;
                    iAudioClient = 0;
                    this.iAudioCaptureClient = iAudioCaptureClient;
                    iAudioCaptureClient = 0;

                    this.outFormat = outFormat;
                    this.transferHandler = transferHandler;
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
        finally
        {
            if (eventHandle != 0)
                CloseHandle(eventHandle);
        }
    }

    /**
     * Releases the resources acquired by this instance throughout its lifetime
     * and prepares it to be garbage collected.
     */
    public void close()
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
        if (eventHandle != 0)
        {
            try
            {
                CloseHandle(eventHandle);
            }
            catch (HResultException hre)
            {
                // The event HANDLE will be leaked.
                logger.warn("Failed to close event HANDLE.", hre);
            }
            eventHandle = 0;
        }

        available = null;
        availableLength = 0;
        started = false;
    }

    /**
     * Reads audio data from the internal buffer of this instance which has
     * previously/already been read by this instance from the associated
     * <tt>IAudioCaptureClient</tt>. Invoked by {@link #read(byte[], int, int)}.
     *
     * @param buffer the <tt>byte</tt> array into which the audio data read from
     * the internal buffer of this instance is to be written
     * @param offset the offset into <tt>buffer</tt> at which the writing of the
     * audio data is to begin
     * @param length the maximum number of bytes in <tt>buffer</tt> starting at
     * <tt>offset</tt> to be written
     * @return the number of bytes read from the internal buffer of this
     * instance and written into the specified <tt>buffer</tt>
     * @throws IOException if the reading from the internal buffer of this
     * instance or writing into the specified <tt>buffer</tt> fails
     */
    private int doRead(
            IMediaBuffer iMediaBuffer,
            byte[] buffer, int offset,
            int length)
        throws IOException
    {
        int toRead = Math.min(length, availableLength);
        int read;

        if (toRead == 0)
            read = 0;
        else
        {
            if (iMediaBuffer == null)
            {
                read = toRead;
                System.arraycopy(available, 0, buffer, offset, toRead);
            }
            else
                read = iMediaBuffer.push(available, 0, toRead);
            popFromAvailable(read);
        }
        return read;
    }

    /**
     * Gets the number of bytes of audio samples which have been read from the
     * associated <tt>IAudioCaptureClient</tt> by this instance and are
     * available to be read out of this instance via
     * {@link #read(byte[], int, int)}.
     *
     * @return the number of bytes of audio samples which have been read from
     * the associated <tt>IAudioCaptureClient</tt> by this instance and are
     * available to be read out of this instance via
     * <tt>read(byte[], int, int)</tt>
     */
    int getAvailableLength()
    {
        return availableLength;
    }

    /**
     * Pops a specific number of bytes from {@link #available}. For example,
     * because such a number of bytes have been read from <tt>available</tt> and
     * written into a <tt>Buffer</tt>.
     *
     * @param length the number of bytes to pop from <tt>available</tt>
     */
    private void popFromAvailable(int length)
    {
        availableLength
            = WASAPIRenderer.pop(available, availableLength, length);
    }

    /**
     * Reads audio data from this instance into a specific <tt>byte</tt> array.
     *
     * @param buffer the <tt>byte</tt> array into which the audio data read from
     * this instance is to be written
     * @param offset the offset in <tt>buffer</tt> at which the writing of the
     * audio data is to start
     * @param length the maximum number of bytes in <tt>buffer</tt> starting at
     * <tt>offset</tt> to be written
     * @return the number of bytes read from this instance and written into the
     * specified <tt>buffer</tt>
     * @throws IOException if the reading from this instance or the writing into
     * the specified <tt>buffer</tt> fails
     */
    public int read(byte[] buffer, int offset, int length)
        throws IOException
    {
        return read(/* iMediaBuffer */ null, buffer, offset, length);
    }

    /**
     * Reads audio data from this instance into a specific <tt>IMediaBuffer</tt>
     * or a specific <tt>byte</tt> array.
     *
     * @param iMediaBuffer the <tt>IMediaBuffer</tt> into which the audio data
     * read from this instance is to be written. If <tt>null</tt>, the writing
     * occurs on <tt>buffer</tt> starting at <tt>offset</tt>. If
     * non-<tt>null</tt>, <tt>buffer</tt> and <tt>offset</tt> are ignored.
     * @param buffer the <tt>byte</tt> array into which the audio data read from
     * this instance is to be written if <tt>iMediaBuffer</tt> is <tt>null</tt>;
     * otherwise, <tt>buffer</tt> and <tt>offset</tt> are ignored
     * @param offset the offset in <tt>buffer</tt> at which the writing of the
     * audio data is to start if <tt>iMediaBuffer</tt> is <tt>null</tt>;
     * otherwise, <tt>buffer</tt> and <tt>offset</tt> are ignored
     * @param length the maximum number of bytes to be read from this instance
     * into the specified <tt>iMediaBuffer</tt> or the specified <tt>buffer</tt>
     * starting at <tt>offset</tt>
     * @return the number of bytes read from this instance into the specified
     * <tt>iMediaBuffer</tt> or the specified <tt>buffer</tt> starting at
     * <tt>offset</tt>
     * @throws IOException if the reading of audio data from this instance fails
     */
    private int read(
            IMediaBuffer iMediaBuffer,
            byte[] buffer, int offset,
            int length)
        throws IOException
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
        if (message != null)
            throw new IOException(message);

        int read;
        Throwable cause;

        try
        {
            read = doRead(iMediaBuffer, buffer, offset, length);
            cause = null;
            if (read > 0)
                this.read = true;
        }
        catch (Throwable t)
        {
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
        if (cause != null)
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
        return read;
    }

    /**
     * Reads audio data from this instance into a specific
     * <tt>IMediaBuffer</tt>.
     *
     * @param iMediaBuffer the <tt>IMediaBuffer</tt> into which the audio data
     * read from this instance is to be written
     * @param length the maximum number of bytes to read from this instance and
     * write into the specified <tt>iMediaBuffer</tt>
     * @return the number of bytes read from this instance and written into the
     * specified <tt>iMediaBuffer</tt>
     * @throws IOException if the reading of audio data from this instance or
     * the writing into the specified <tt>iMediaBuffer</tt> fails
     */
    public int read(IMediaBuffer iMediaBuffer, int length)
        throws IOException
    {
        return read(iMediaBuffer, /* buffer */ null, /* offset */ 0, length);
    }

    /**
     * Reads from {@link #iAudioCaptureClient} into {@link #available} and
     * returns a non-<tt>null</tt> <tt>BufferTransferHandler</tt> if this
     * instance is to push audio data.
     *
     * @return a <tt>BufferTransferHandler</tt> if this instance is to push
     * audio data; otherwise, <tt>null</tt>
     */
    private BufferTransferHandler readInEventHandleCmd()
    {
        /*
         * Determine the size in bytes of the next data packet in the capture
         * endpoint buffer.
         */
        int numFramesInNextPacket
            = maybeIAudioCaptureClientGetNextPacketSize(iAudioCaptureClient);

        if (numFramesInNextPacket != 0)
        {
            int toRead = numFramesInNextPacket * dstFrameSize;

            /*
             * Make sure there is enough room in available to accommodate
             * toRead.
             */
            int toPop = toRead - (available.length - availableLength);

            if (toPop > 0)
                popFromAvailable(toPop);

            try
            {
                int read
                    = IAudioCaptureClient_Read(
                            iAudioCaptureClient,
                            available, availableLength, toRead,
                            srcSampleSize, srcChannels,
                            dstSampleSize, dstChannels);

                availableLength += read;
            }
            catch (HResultException hre)
            {
                logger.error("IAudioCaptureClient_Read", hre);
            }
        }

        return (availableLength >= bufferSize) ? transferHandler : null;
    }

    /**
     * Runs/executes in the thread associated with a specific <tt>Runnable</tt>
     * initialized to wait for {@link #eventHandle} to be signaled.
     *
     * @param eventHandleCmd the <tt>Runnable</tt> which has been initialized to
     * wait for <tt>eventHandle</tt> to be signaled and in whose associated
     * thread the method is invoked
     */
    private void runInEventHandleCmd(Runnable eventHandleCmd)
    {
        try
        {
            AbstractAudioRenderer.useAudioThreadPriority();

            do
            {
                long eventHandle;
                BufferTransferHandler transferHandler;
                int numFramesInNextPacket;

                synchronized (this)
                {
                    /*
                     * Does this WASAPIStream still want eventHandleCmd to
                     * execute?
                     */
                    if (!eventHandleCmd.equals(this.eventHandleCmd))
                        break;
                    // Is this WASAPIStream still connected and started?
                    if ((iAudioClient == 0)
                            || (iAudioCaptureClient == 0)
                            || !started)
                        break;

                    /*
                     * The value of eventHandle will remain valid while this
                     * WASAPIStream wants eventHandleCmd to execute.
                     */
                    eventHandle = this.eventHandle;
                    if (eventHandle == 0)
                        throw new IllegalStateException("eventHandle");

                    waitWhileBusy();
                    busy = true;
                }
                try
                {
                    transferHandler = readInEventHandleCmd();
                    if (transferHandler != null)
                        read = false;
                    /*
                     * If the audio engine has more samples to deliver to the
                     * application, deliver them as soon as the transferHandler,
                     * if any, has been given a chance to read (from) the
                     * available samples.
                     */
                    numFramesInNextPacket
                        = maybeIAudioCaptureClientGetNextPacketSize(
                                iAudioCaptureClient);
                }
                finally
                {
                    synchronized (this)
                    {
                        busy = false;
                        notifyAll();
                    }
                }

                if (transferHandler != null)
                {
                    try
                    {
                        transferHandler.transferData(null);
                        /*
                         * If the transferData implementation throws an
                         * exception, we will WaitForSingleObject in order to
                         * give the application time to recover.
                         */
                        if (read)
                            continue;
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                        else
                        {
                            logger.error(
                                    "BufferTransferHandler.transferData",
                                    t);
                        }
                    }
                }
                /*
                 * The audio engine may already have more samples to deliver to
                 * the application.
                 */
                if (numFramesInNextPacket != 0)
                    continue;

                int wfso;

                try
                {
                    wfso = WaitForSingleObject(eventHandle, devicePeriod);
                }
                catch (HResultException hre)
                {
                    /*
                     * WaitForSingleObject will throw HResultException only in
                     * the case of WAIT_FAILED. Event if it didn't, it would
                     * still be a failure from our point of view.
                     */
                    wfso = WAIT_FAILED;
                    logger.error("WaitForSingleObject", hre);
                }
                /*
                 * If the function WaitForSingleObject fails once, it will very
                 * likely fail forever. Bail out of a possible busy wait.
                 */
                if ((wfso == WAIT_FAILED) || (wfso == WAIT_ABANDONED))
                    break;
            }
            while (true);
        }
        finally
        {
            synchronized (this)
            {
                if (eventHandleCmd.equals(this.eventHandleCmd))
                {
                    this.eventHandleCmd = null;
                    notifyAll();
                }
            }
        }
    }

    /**
     * Starts the transfer of media from the <tt>IAudioCaptureClient</tt>
     * identified by the <tt>MediaLocator</tt> with which this instance has
     * been initialized.
     *
     * @throws IOException if the starting of the transfer of media fails
     */
    public synchronized void start()
        throws IOException
    {
        if (iAudioClient != 0)
        {
            waitWhileBusy();
            waitWhileEventHandleCmd();

            try
            {
                IAudioClient_Start(iAudioClient);
                started = true;

                availableLength = 0;
                if ((eventHandle != 0) && (this.eventHandleCmd == null))
                {
                    Runnable eventHandleCmd
                        = new Runnable()
                        {
                            public void run()
                            {
                                runInEventHandleCmd(this);
                            }
                        };
                    boolean submitted = false;

                    try
                    {
                        if (eventHandleExecutor == null)
                        {
                            eventHandleExecutor
                                = Executors.newSingleThreadExecutor();
                        }

                        this.eventHandleCmd = eventHandleCmd;
                        eventHandleExecutor.execute(eventHandleCmd);
                        submitted = true;
                    }
                    finally
                    {
                        if (!submitted
                                && eventHandleCmd.equals(this.eventHandleCmd))
                            this.eventHandleCmd = null;
                    }
                }
            }
            catch (HResultException hre)
            {
                /*
                 * If IAudioClient_Start is invoked multiple times without
                 * intervening IAudioClient_Stop, it will likely return/throw
                 * AUDCLNT_E_NOT_STOPPED.
                 */
                if (hre.getHResult() != AUDCLNT_E_NOT_STOPPED)
                    WASAPIStream.throwNewIOException("IAudioClient_Start", hre);
            }
        }
    }

    /**
     * Stops the transfer of media from the <tt>IAudioCaptureClient</tt>
     * identified by the <tt>MediaLocator</tt> with which this instance has
     * been initialized.
     *
     * @throws IOException if the stopping of the transfer of media fails
     */
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

                waitWhileEventHandleCmd();
                availableLength = 0;
            }
            catch (HResultException hre)
            {
                WASAPIStream.throwNewIOException("IAudioClient_Stop", hre);
            }
        }
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
                wait(devicePeriod);
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
     * Waits on this instance while the value of {@link #eventHandleCmd} is
     * non-<tt>null</tt>.
     */
    private synchronized void waitWhileEventHandleCmd()
    {
        if (eventHandle == 0)
            throw new IllegalStateException("eventHandle");

        boolean interrupted = false;

        while (eventHandleCmd != null)
        {
            try
            {
                wait(devicePeriod);
            }
            catch (InterruptedException ie)
            {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }
}
