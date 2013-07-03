/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
import org.jitsi.util.*;

public class AudioCaptureClient
{
    /**
     * The default duration of the audio data in milliseconds to be read from
     * <tt>WASAPIStream</tt> in an invocation of {@link #read(Buffer)}.
     */
    static final long DEFAULT_BUFFER_DURATION = 20;

    /**
     * The <tt>Logger</tt> used by the <tt>AudioCaptureClient</tt> class and its
     * instances to log debug information.
     */
    private static final Logger logger
        = Logger.getLogger(AudioCaptureClient.class);

    /**
     * The number of frames to be filled in a <tt>Buffer</tt> in an invocation
     * of {@link #read(Buffer)}. If this instance implements the
     * <tt>PushBufferStream</tt> interface,
     * {@link #runInEventHandleCmd(Runnable)} will push via
     * {@link BufferTransferHandler#transferData(PushBufferStream)} when
     * {@link #iAudioClient} has made at least that many frames available.
     */
    private int bufferFrames;

    /**
     * The size/length in bytes of the <tt>Buffer</tt> to be filled in an
     * invocation of {@link #read(Buffer)}.
     */
    final int bufferSize;

    /**
     * The indicator which determines whether the audio stream represented by
     * this instance, {@link #iAudioClient} and {@link #iAudioCaptureClient} is
     * busy and, consequently, its state should not be modified. For example,
     * the audio stream is busy during the execution of {@link #read(Buffer)}.
     */
    private boolean busy;

    /**
     * The length in milliseconds of the interval between successive, periodic
     * processing passes by the audio engine on the data in the endpoint buffer.
     */
    final long devicePeriod;

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
     * The event handle that the system signals when an audio buffer is ready to
     * be processed by the client.
     */
    private long eventHandle;

    /**
     * The <tt>Runnable</tt> which is scheduled by this <tt>WASAPIStream</tt>
     * and executed by {@link #eventHandleExecutor} and waits for
     * {@link #eventHandle} to be signaled.
     */
    private Runnable eventHandleCmd;

    /**
     * The <tt>Executor</tt> implementation which is to execute
     * {@link #eventHandleCmd}.
     */
    private Executor eventHandleExecutor;

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
     * The <tt>AudioFormat</tt> of the data output by this
     * <tt>AudioCaptureClient</tt>.
     */
    final AudioFormat outFormat;

    private byte[] remainder;

    private int remainderLength;

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

    private final BufferTransferHandler transferHandler;

    public AudioCaptureClient(
            WASAPISystem audioSystem,
            MediaLocator locator,
            AudioSystem.DataFlow dataFlow,
            int streamFlags,
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
            long hnsBufferDuration = 3 * DEFAULT_BUFFER_DURATION * 10000;
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

                    srcChannels = inFormat.getChannels();
                    srcSampleSize
                        = WASAPISystem.getSampleSizeInBytes(inFormat);

                    dstChannels = outFormat.getChannels();
                    dstSampleSize
                        = WASAPISystem.getSampleSizeInBytes(outFormat);

                    dstFrameSize = dstSampleSize * dstChannels;
                    bufferFrames
                        = (int)
                            (DEFAULT_BUFFER_DURATION * sampleRate / 1000);
                    bufferSize = dstFrameSize * bufferFrames;

                    remainder = new byte[numBufferFrames * dstFrameSize];
                    remainderLength = 0;

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

        remainder = null;
        remainderLength = 0;
        started = false;
    }

    private int doRead(byte[] buffer, int offset, int length)
        throws IOException
    {
        int toRead = Math.min(length, remainderLength);
        int read;

        if (toRead == 0)
            read = 0;
        else
        {
            System.arraycopy(remainder, 0, buffer, offset, toRead);
            popFromRemainder(toRead);
            read = toRead;
        }
        return read;
    }

    /**
     * Pops a specific number of bytes from {@link #remainder}. For example,
     * because such a number of bytes have been read from <tt>remainder</tt> and
     * written into a <tt>Buffer</tt>.
     *
     * @param length the number of bytes to pop from <tt>remainder</tt>
     */
    private void popFromRemainder(int length)
    {
        remainderLength
            = WASAPIRenderer.pop(remainder, remainderLength, length);
    }

    public int read(byte[] buffer, int offset, int length)
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
            read = doRead(buffer, offset, length);
            cause = null;
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
     * Reads from {@link #iAudioCaptureClient} into {@link #remainder} and
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

        if (numFramesInNextPacket != 0)
        {
            int toRead = numFramesInNextPacket * dstFrameSize;

            /*
             * Make sure there is enough room in remainder to accommodate
             * toRead.
             */
            int toPop = toRead - (remainder.length - remainderLength);

            if (toPop > 0)
                popFromRemainder(toPop);

            try
            {
                int read
                    = IAudioCaptureClient_Read(
                            iAudioCaptureClient,
                            remainder, remainderLength, toRead,
                            srcSampleSize, srcChannels,
                            dstSampleSize, dstChannels);

                remainderLength += read;
            }
            catch (HResultException hre)
            {
                logger.error("IAudioCaptureClient_Read", hre);
            }
        }

        return (remainderLength >= bufferSize) ? transferHandler : null;
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

                remainderLength = 0;
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
                remainderLength = 0;
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
