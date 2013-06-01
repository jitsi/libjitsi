/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.renderer.audio;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.WASAPI.*;

import java.beans.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implements an audio <tt>Renderer</tt> using Windows Audio Session API
 * (WASAPI) and related Core Audio APIs such as Multimedia Device (MMDevice)
 * API.
 *
 * @author Lyubomir Marinov
 */
public class WASAPIRenderer
    extends AbstractAudioRenderer<WASAPISystem>
{
    /**
     * The default base of the endpoint buffer capacity in milliseconds to
     * initialize new <tt>IAudioClient</tt> instances with.
     */
    private static final long DEFAULT_BUFFER_DURATION = 60;

    /**
     * The <tt>Logger</tt> used by the <tt>WASAPIRenderer</tt> class and its
     * instances to log debug information.
     */
    private static final Logger logger = Logger.getLogger(WASAPIRenderer.class);

    /**
     * The human-readable name of the <tt>WASAPIRenderer</tt> <tt>PlugIn</tt>
     * implementation instances.
     */
    private static final String PLUGIN_NAME
        = "Windows Audio Session API (WASAPI) Renderer";

    /**
     * The indicator which determines whether the audio stream represented by
     * this instance, {@link #iAudioClient} and {@link #iAudioRenderClient} is
     * busy and, consequently, its state should not be modified. For example,
     * the audio stream is busy during the execution of
     * {@link #process(Buffer)}.
     */
    private boolean busy;

    /**
     * The length in milliseconds of the interval between successive, periodic
     * processing passes by the audio engine on the data in the endpoint buffer.
     */
    private long devicePeriod = WASAPISystem.DEFAULT_DEVICE_PERIOD;

    /**
     * The value of {@link #devicePeriod} expressed in terms of numbers of
     * frames (i.e. takes the sample rate into account).
     */
    private int devicePeriodInFrames;

    /**
     * The number of channels with which {@link #iAudioClient} has been
     * initialized.
     */
    private int dstChannels;

    /**
     * The sample size in bytes with which {@link #iAudioClient} has been
     * initialized.
     */
    private int dstSampleSize;

    /**
     * The event handle that the system signals when an audio buffer is ready to
     * be processed by the client.
     */
    private long eventHandle;

    /**
     * The <tt>Runnable</tt> which is scheduled by this <tt>WASAPIRenderer</tt>
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
     * The WASAPI <tt>IAudioClient</tt> instance which enables this
     * <tt>Renderer</tt> to create and initialize an audio stream between this
     * <tt>Renderer</tt> and the audio engine of the associated audio endpoint
     * device.
     */
    private long iAudioClient;

    /**
     * The WASAPI <tt>IAudioRenderClient</tt> obtained from
     * {@link #iAudioClient} which enables this <tt>Renderer</tt> to write
     * output data to the rendering endpoint buffer.
     */
    private long iAudioRenderClient;

    /**
     * The maximum capacity in frames of the endpoint buffer.
     */
    private int numBufferFrames;

    /**
     * The data which has remained unwritten during earlier invocations of
     * {@link #process(Buffer)} because it represents frames which are few
     * enough to be accepted on their own for writing by
     * {@link #iAudioRenderClient}.
     */
    private byte[] remainder;

    /**
     * The number of bytes in {@link #remainder} which represent valid audio
     * data to be written by {@link #iAudioRenderClient}.
     */
    private int remainderLength;

    /**
     * The number of channels which which this <tt>Renderer</tt> has been
     * opened.
     */
    private int srcChannels;

    /**
     * The frame size in bytes with which this <tt>Renderer</tt> has been
     * opened. It is the product of {@link #srcSampleSize} and
     * {@link #srcChannels}.
     */
    private int srcFrameSize;

    /**
     * The sample size in bytes with which this <tt>Renderer</tt> has been
     * opened.
     */
    private int srcSampleSize;

    /**
     * The indicator which determines whether this <tt>Renderer</tt> is started
     * i.e. there has been a successful invocation of {@link #start()} without
     * an intervening invocation of {@link #stop()}.
     */
    private boolean started;

    /**
     * Initializes a new <tt>WASAPIRenderer</tt> instance which is to perform
     * playback (as opposed to sound a notification).
     */
    public WASAPIRenderer()
    {
        this(AudioSystem.DataFlow.PLAYBACK);
    }

    /**
     * Initializes a new <tt>WASAPIRenderer</tt> instance which is to either
     * perform playback or sound a notification.
     *
     * @param dataFlow {@link AudioSystem.DataFlow#PLAYBACK} if the new instance
     * is to perform playback or {@link AudioSystem.DataFlow#NOTIFY} if the new
     * instance is to sound a notification
     */
    public WASAPIRenderer(AudioSystem.DataFlow dataFlow)
    {
        super(AudioSystem.LOCATOR_PROTOCOL_WASAPI, dataFlow);
    }

    /**
     * Initializes a new <tt>WASAPIRenderer</tt> instance which is to either
     * perform playback or sound a notification.
     *
     * @param playback <tt>true</tt> if the new instance is to perform playback
     * or <tt>false</tt> if the new instance is to sound a notification
     */
    public WASAPIRenderer(boolean playback)
    {
        this(
                playback
                    ? AudioSystem.DataFlow.PLAYBACK
                    : AudioSystem.DataFlow.NOTIFY);
    }

    /**
     * {@inheritDoc}
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
            if (iAudioRenderClient != 0)
            {
                IAudioRenderClient_Release(iAudioRenderClient);
                iAudioRenderClient = 0;
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

            super.close();
        }
    }

    /**
     * Gets an array of alternative <tt>AudioFormat</tt>s based on
     * <tt>inputFormat</tt> with which an attempt is to be made to initialize a
     * new <tt>IAudioClient</tt> instance.
     *
     * @return an array of alternative <tt>AudioFormat</tt>s based on
     * <tt>inputFormat</tt> with which an attempt is to be made to initialize a
     * new <tt>IAudioClient</tt> instance
     */
    private AudioFormat[] getFormatsToInitializeIAudioClient()
    {
        AudioFormat inputFormat = this.inputFormat;

        if (inputFormat == null)
            throw new NullPointerException("No inputFormat set.");
        else
            return WASAPISystem.getFormatsToInitializeIAudioClient(inputFormat);
    }

    /**
     * {@inheritDoc}
     */
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void open()
        throws ResourceUnavailableException
    {
        if (this.iAudioClient != 0)
            return;

        try
        {
            MediaLocator locator = getLocator();

            if (locator == null)
                throw new NullPointerException("No locator/MediaLocator set.");

            // Assert that inputFormat is set.
            AudioFormat[] formats = getFormatsToInitializeIAudioClient();
            long eventHandle = CreateEvent(0, false, false, null);

            try
            {
                long hnsBufferDuration = DEFAULT_BUFFER_DURATION * 10000;
                long iAudioClient
                    = audioSystem.initializeIAudioClient(
                            locator,
                            dataFlow,
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
                    AudioFormat format = null;

                    for (AudioFormat aFormat : formats)
                    {
                        if (aFormat != null)
                        {
                            format = aFormat;
                            break;
                        }
                    }

                    long iAudioRenderClient
                        = IAudioClient_GetService(
                                iAudioClient,
                                IID_IAudioRenderClient);

                    if (iAudioRenderClient == 0)
                    {
                        throw new ResourceUnavailableException(
                                "IAudioClient_GetService"
                                    + "(IID_IAudioRenderClient)");
                    }
                    try
                    {
                        /*
                         * The value hnsDefaultDevicePeriod is documented to
                         * specify the default scheduling period for a
                         * shared-mode stream.
                         */
                        devicePeriod
                            = IAudioClient_GetDefaultDevicePeriod(iAudioClient)
                                / 10000L;
                        numBufferFrames
                            = IAudioClient_GetBufferSize(iAudioClient);

                        int sampleRate = (int) format.getSampleRate();

                        /*
                         * We will very likely be inefficient if we fail to
                         * synchronize with the scheduling period of the audio
                         * engine but we have to make do with what we have.
                         */
                        if (devicePeriod <= 1)
                        {
                            long bufferDuration
                                = numBufferFrames * 1000L / sampleRate;

                            devicePeriod = bufferDuration / 2;
                            if ((devicePeriod
                                        > WASAPISystem.DEFAULT_DEVICE_PERIOD)
                                    || (devicePeriod <= 1))
                                devicePeriod
                                    = WASAPISystem.DEFAULT_DEVICE_PERIOD;
                        }
                        devicePeriodInFrames
                            = (int) (devicePeriod * sampleRate / 1000L);

                        dstChannels = format.getChannels();
                        dstSampleSize
                            = WASAPISystem.getSampleSizeInBytes(format);

                        /*
                         * The remainder/residue in frames of
                         * IAudioRenderClient_Write cannot be more than the
                         * maximum capacity of the endpoint buffer.
                         */
                        remainder = new byte[numBufferFrames * srcFrameSize];
                        /*
                         * Introduce latency in order to decrease the likelihood
                         * of underflow.
                         */
                        remainderLength = remainder.length;

                        this.eventHandle = eventHandle;
                        eventHandle = 0;
                        this.iAudioClient = iAudioClient;
                        iAudioClient = 0;
                        this.iAudioRenderClient = iAudioRenderClient;
                        iAudioRenderClient = 0;
                    }
                    finally
                    {
                        if (iAudioRenderClient != 0)
                            IAudioRenderClient_Release(iAudioRenderClient);
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
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else if (t instanceof ResourceUnavailableException)
                throw (ResourceUnavailableException) t;
            else
            {
                logger.error(
                        "Failed to open a WASAPIRenderer"
                            + " on an audio endpoint device.",
                        t);

                ResourceUnavailableException rue
                    = new ResourceUnavailableException();

                rue.initCause(t);
                throw rue;
            }
        }

        super.open();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void playbackDevicePropertyChange(
            PropertyChangeEvent ev)
    {
        /*
         * Stop, close, re-open and re-start this Renderer (performing whichever
         * of these in order to bring it into the same state) in order to
         * reflect the change in the selection with respect to the playback or
         * notify device.
         */

        waitWhileBusy();

        boolean open = ((iAudioClient != 0) && (iAudioRenderClient != 0));

        if (open)
        {
            boolean start = started;

            close();

            try
            {
                open();
            }
            catch (ResourceUnavailableException rue)
            {
                throw new UndeclaredThrowableException(rue);
            }
            if (start)
                start();
        }
    }

    /**
     * Pops a specific number of bytes from {@link #remainder}. For example,
     * because such a number of bytes have been read from <tt>remainder</tt> and
     * written into the rendering endpoint buffer.
     *
     * @param length the number of bytes to pop from <tt>remainder</tt>
     */
    private void popFromRemainder(int length)
    {
        remainderLength = pop(remainder, remainderLength, length);
    }

    public static int pop(byte[] array, int arrayLength, int length)
    {
        if (length < 0)
            throw new IllegalArgumentException("length");
        if (length == 0)
            return arrayLength;

        int newArrayLength = arrayLength - length;

        if (newArrayLength > 0)
        {
            for (int i = 0, j = length; i < newArrayLength; i++, j++)
                array[i] = array[j];
        }
        else
            newArrayLength = 0;
        return newArrayLength;
    }

    /**
     * {@inheritDoc}
     */
    public int process(Buffer buffer)
    {
        int length = buffer.getLength();

        if (length < 1)
            return BUFFER_PROCESSED_OK;

        byte[] data = (byte[]) buffer.getData();
        int offset = buffer.getOffset();

        synchronized (this)
        {
            if ((iAudioClient == 0) || (iAudioRenderClient == 0) || !started)
                return BUFFER_PROCESSED_FAILED;
            else
            {
                waitWhileBusy();
                busy = true;
            }
        }

        int ret = BUFFER_PROCESSED_OK;
        long sleep = 0;

        try
        {
            int numPaddingFrames;

            if (eventHandle == 0)
            {
                try
                {
                    numPaddingFrames
                        = IAudioClient_GetCurrentPadding(iAudioClient);
                }
                catch (HResultException hre)
                {
                    numPaddingFrames = 0;
                    ret = BUFFER_PROCESSED_FAILED;
                    logger.error("IAudioClient_GetCurrentPadding", hre);
                }
            }
            else
            {
                /*
                 * The process method will not write into the rendering endpoint
                 * buffer, the runInEventHandleCmd method will.
                 */
                numPaddingFrames = numBufferFrames;
            }
            if (ret != BUFFER_PROCESSED_FAILED)
            {
                int numFramesRequested = numBufferFrames - numPaddingFrames;

                if (numFramesRequested == 0)
                {
                    if (eventHandle == 0)
                    {
                        /*
                         * There is NO available space in the rendering endpoint
                         * buffer into which this Renderer can write data.
                         */
                        ret |= INPUT_BUFFER_NOT_CONSUMED;
                        sleep = devicePeriod;
                    }
                    else
                    {
                        /*
                         * The process method will write into remainder, the
                         * runInEventHandleCmd will read from remainder and
                         * write into the rendering endpoint buffer.
                         */
                        int toCopy = remainder.length - remainderLength;

                        if (toCopy > 0)
                        {
                            if (toCopy > length)
                                toCopy = length;
                            System.arraycopy(
                                    data, offset,
                                    remainder, remainderLength,
                                    toCopy);
                            remainderLength += toCopy;

                            if (length > toCopy)
                            {
                                buffer.setLength(length - toCopy);
                                buffer.setOffset(offset + toCopy);
                                ret |= INPUT_BUFFER_NOT_CONSUMED;
                            }
                        }
                        else
                        {
                            ret |= INPUT_BUFFER_NOT_CONSUMED;
                            sleep = devicePeriod;
                        }
                    }
                }
                else
                {
                    /*
                     * There is available space in the rendering endpoint
                     * buffer into which this Renderer can write data.
                     */
                    int effectiveLength = remainderLength + length;
                    int toWrite
                        = Math.min(
                                effectiveLength,
                                numFramesRequested * srcFrameSize);
                    byte[] effectiveData;
                    int effectiveOffset;

                    if (remainderLength > 0)
                    {
                        /*
                         * There is remainder/residue from earlier invocations
                         * of the method. This Renderer will feed
                         * iAudioRenderClient from remainder.
                         */
                        effectiveData = remainder;
                        effectiveOffset = 0;

                        int toCopy = toWrite - remainderLength;

                        if (toCopy <= 0)
                            ret |= INPUT_BUFFER_NOT_CONSUMED;
                        else
                        {
                            if (toCopy > length)
                                toCopy = length;
                            System.arraycopy(
                                    data, offset,
                                    remainder, remainderLength,
                                    toCopy);
                            remainderLength += toCopy;

                            if (toWrite > remainderLength)
                                toWrite = remainderLength;

                            if (length > toCopy)
                            {
                                buffer.setLength(length - toCopy);
                                buffer.setOffset(offset + toCopy);
                                ret |= INPUT_BUFFER_NOT_CONSUMED;
                            }
                        }
                    }
                    else
                    {
                        /*
                         * There is no remainder/residue from earlier
                         * invocations of the method. This Renderer will feed
                         * iAudioRenderClient from data.
                         */
                        effectiveData = data;
                        effectiveOffset = offset;
                    }

                    int written;

                    if ((toWrite / srcFrameSize) == 0)
                        written = 0;
                    else
                    {
                        /*
                         * Take into account the user's preferences with respect
                         * to the output volume.
                         */
                        GainControl gainControl = getGainControl();

                        if (gainControl != null)
                        {
                            BasicVolumeControl.applyGain(
                                    gainControl,
                                    effectiveData, effectiveOffset, toWrite);
                        }

                        try
                        {
                            written
                                = IAudioRenderClient_Write(
                                        iAudioRenderClient,
                                        effectiveData, effectiveOffset, toWrite,
                                        srcSampleSize, srcChannels,
                                        dstSampleSize, dstChannels);
                        }
                        catch (HResultException hre)
                        {
                            written = 0;
                            ret = BUFFER_PROCESSED_FAILED;
                            logger.error("IAudioRenderClient_Write", hre);
                        }
                    }
                    if (ret != BUFFER_PROCESSED_FAILED)
                    {
                        if (effectiveData == data)
                        {
                            // We have consumed frames from data.
                            if (written == 0)
                            {
                                /*
                                 * The available number of frames appear to be
                                 * too few for IAudioRenderClient to accept.
                                 * They will have to be prepended to the next
                                 * input Buffer.
                                 */
                                System.arraycopy(
                                        data, offset,
                                        remainder, remainderLength,
                                        toWrite);
                                remainderLength += toWrite;
                                written = toWrite;
                            }
                            if (length > written)
                            {
                                buffer.setLength(length - written);
                                buffer.setOffset(offset + written);
                                ret |= INPUT_BUFFER_NOT_CONSUMED;
                            }
                        }
                        else if (written > 0)
                        {
                            // We have consumed frames from remainder.
                            popFromRemainder(written);
                        }
                    }
                }
            }
        }
        finally
        {
            synchronized (this)
            {
                busy = false;
                notifyAll();
            }
        }
        /*
         * If there was no available space in the rendering endpoint buffer, we
         * will want to wait a bit for such space to be made available.
         */
        if (((ret & INPUT_BUFFER_NOT_CONSUMED) == INPUT_BUFFER_NOT_CONSUMED)
                && (sleep > 0))
        {
            boolean interrupted = false;

            synchronized (this)
            {
                /*
                 * Spurious wake-ups should not be a big issue here. While this
                 * Renderer may check for available space in the rendering
                 * endpoint buffer more often than practically necessary (which
                 * may very well classify as a case of performance loss), the
                 * ability to unblock this Renderer is considered more
                 * important.
                 */
                try
                {
                    wait(sleep);
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
        return ret;
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
            useAudioThreadPriority();

            do
            {
                long eventHandle;

                synchronized (this)
                {
                    /*
                     * Does this WASAPIRender still want eventHandleCmd to
                     * execute?
                     */
                    if (!eventHandleCmd.equals(this.eventHandleCmd))
                        break;
                    // Is this WASAPIRenderer still opened and started?
                    if ((iAudioClient == 0)
                            || (iAudioRenderClient == 0)
                            || !started)
                        break;

                    /*
                     * The value of eventHandle will remain valid while this
                     * WASAPIRenderer wants eventHandleCmd to execute.
                     */
                    eventHandle = this.eventHandle;
                    if (eventHandle == 0)
                        throw new IllegalStateException("eventHandle");

                    waitWhileBusy();
                    busy = true;
                }
                try
                {
                    int numPaddingFrames;

                    try
                    {
                        numPaddingFrames
                            = IAudioClient_GetCurrentPadding(iAudioClient);
                    }
                    catch (HResultException hre)
                    {
                        numPaddingFrames = numBufferFrames;
                        logger.error("IAudioClient_GetCurrentPadding", hre);
                    }

                    int numFramesRequested = numBufferFrames - numPaddingFrames;

                    /*
                     * If there is no available space in the rendering endpoint
                     * buffer, wait for the system to signal when an audio
                     * buffer is ready to be processed by the client.
                     */
                    if (numFramesRequested > 0)
                    {
                        /*
                         * Write as much from remainder as possible while
                         * minimizing the risk of audio glitches and the amount
                         * of artificial/induced silence.
                         */
                        int remainderFrames = remainderLength / srcFrameSize;

                        if ((numFramesRequested > remainderFrames)
                                && (remainderFrames >= devicePeriodInFrames))
                            numFramesRequested = remainderFrames;

                        // Pad with silence in order to avoid underflows.
                        int toWrite = numFramesRequested * srcFrameSize;
                        int silence = toWrite - remainderLength;

                        if (silence > 0)
                        {
                            Arrays.fill(
                                    remainder,
                                    remainderLength, toWrite,
                                    (byte) 0);
                            remainderLength = toWrite;
                        }

                        /*
                         * Take into account the user's preferences with respect
                         * to the output volume.
                         */
                        GainControl gainControl = getGainControl();

                        if ((gainControl != null) && (toWrite != 0))
                        {
                            BasicVolumeControl.applyGain(
                                    gainControl,
                                    remainder, 0, toWrite);
                        }

                        int written;

                        try
                        {
                            written
                                = IAudioRenderClient_Write(
                                        iAudioRenderClient,
                                        remainder, 0, toWrite,
                                        srcSampleSize, srcChannels,
                                        dstSampleSize, dstChannels);
                        }
                        catch (HResultException hre)
                        {
                            written = 0;
                            logger.error("IAudioRenderClient_Write", hre);
                        }
                        popFromRemainder(written);
                    }
                }
                finally
                {
                    synchronized (this)
                    {
                        busy = false;
                        notifyAll();
                    }
                }

                int wfso;

                try
                {
                    wfso = WaitForSingleObject(eventHandle, devicePeriod);
                }
                catch (HResultException hre)
                {
                    wfso = WAIT_FAILED;
                    logger.error("WaitForSingleObject", hre);
                }
                /*
                 * If the function WaitForSingleObject fails once, it will very
                 * likely fail forever. Bail out of a possible busy wait.
                 */
                if (wfso == WAIT_FAILED)
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
     * {@inheritDoc}
     */
    @Override
    public Format setInputFormat(Format format)
    {
        AudioFormat oldValue = this.inputFormat;
        Format setInputFormat = super.setInputFormat(format);
        AudioFormat newValue = this.inputFormat;

        if ((newValue != null) && !newValue.equals(oldValue))
        {
            srcChannels = newValue.getChannels();
            srcSampleSize = WASAPISystem.getSampleSizeInBytes(newValue);
            srcFrameSize = srcSampleSize * srcChannels;
        }
        return setInputFormat;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void start()
    {
        if (iAudioClient != 0)
        {
            waitWhileBusy();
            waitWhileEventHandleCmd();

            /*
             * Introduce latency in order to decrease the likelihood of
             * underflow.
             */
            if (remainder != null)
            {
                if (remainderLength > 0)
                {
                    /*
                     * Shift the valid audio data to the end of remainder so
                     * that silence can be written at the beginning.
                     */
                    for (int i = remainder.length - 1, j = remainderLength - 1;
                            j >= 0;
                            i--, j--)
                    {
                        remainder[i] = remainder[j];
                    }
                }
                else if (remainderLength < 0)
                    remainderLength = 0;

                /*
                 * If there is valid audio data in remainder, it has been
                 * shifted to the end to make room for silence at the beginning.
                 */
                int silence = remainder.length - remainderLength;

                if (silence > 0)
                    Arrays.fill(remainder, 0, silence, (byte) 0);
                remainderLength = remainder.length;
            }

            try
            {
                IAudioClient_Start(iAudioClient);
                started = true;

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
                    logger.error("IAudioClient_Start", hre);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void stop()
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
            }
            catch (HResultException hre)
            {
                logger.error("IAudioClient_Stop", hre);
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
