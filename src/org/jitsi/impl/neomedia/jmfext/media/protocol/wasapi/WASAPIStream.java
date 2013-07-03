/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.VoiceCaptureDSP.*;
import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.WASAPI.*;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.util.*;

/**
 * Implements a <tt>PullBufferStream</tt> using Windows Audio Session API
 * (WASAPI) and related Core Audio APIs such as Multimedia Device (MMDevice)
 * API.
 *
 * @author Lyubomir Marinov
 */
public class WASAPIStream
    extends AbstractPushBufferStream<DataSource>
{
    /**
     * The <tt>Logger</tt> used by the <tt>WASAPIStream</tt> class and its
     * instances to log debug information.
     */
    private static Logger logger = Logger.getLogger(WASAPIStream.class);

    private static AudioFormat findClosestMatch(
            Format[] formats,
            AudioFormat format)
    {

        // Try to find the very specified format.
        AudioFormat match = findFirstMatch(formats, format);

        if (match == null)
        {
            /*
             * Relax the channels of the specified format because we are able to
             * translate between mono and stereo.
             */
            match
                = findFirstMatch(
                        formats,
                        new AudioFormat(
                                format.getEncoding(),
                                format.getSampleRate(),
                                format.getSampleSizeInBits(),
                                /* channels */ Format.NOT_SPECIFIED,
                                format.getEndian(),
                                format.getSigned(),
                                /* frameSizeInBits */ Format.NOT_SPECIFIED,
                                /* frameRate */ Format.NOT_SPECIFIED,
                                format.getDataType()));
            if (match == null)
            {
                /*
                 * Relax the sampleRate of the specified format as well because
                 * the voice capture DMO which implements the acoustic echo
                 * cancellation (AEC) feature is able to automatically resample.
                 */
                match
                    = findFirstMatch(
                            formats,
                            new AudioFormat(
                                    format.getEncoding(),
                                    /* sampleRate */ Format.NOT_SPECIFIED,
                                    format.getSampleSizeInBits(),
                                    /* channels */ Format.NOT_SPECIFIED,
                                    format.getEndian(),
                                    format.getSigned(),
                                    /* frameSizeInBits */ Format.NOT_SPECIFIED,
                                    /* frameRate */ Format.NOT_SPECIFIED,
                                    format.getDataType()));
            }
        }
        return match;
    }

    private static AudioFormat findFirstMatch(
            Format[] formats,
            AudioFormat format)
    {
        for (Format aFormat : formats)
            if (aFormat.matches(format))
                return (AudioFormat) aFormat.intersects(format);
        return null;
    }

    private static int IMediaObject_SetXXXputType(
            long iMediaObject,
            boolean inOrOut,
            int dwXXXputStreamIndex,
            AudioFormat audioFormat,
            int dwFlags)
        throws HResultException
    {
        int channels = audioFormat.getChannels();
        double sampleRate = audioFormat.getSampleRate();
        int sampleSizeInBits = audioFormat.getSampleSizeInBits();

        if (Format.NOT_SPECIFIED == channels)
            throw new IllegalArgumentException("audioFormat.channels");
        if (Format.NOT_SPECIFIED == sampleRate)
            throw new IllegalArgumentException("audioFormat.sampleRate");
        if (Format.NOT_SPECIFIED == sampleSizeInBits)
            throw new IllegalArgumentException("audioFormat.sampleSizeInBits");

        char nChannels = (char) channels;
        int nSamplesPerSec = (int) sampleRate;
        char wBitsPerSample = (char) sampleSizeInBits;
        char nBlockAlign = (char) ((nChannels * wBitsPerSample) / 8);
        char cbSize = 0;
        int hresult;

        long waveformatex = WAVEFORMATEX_alloc();

        if (waveformatex == 0)
            throw new OutOfMemoryError("WAVEFORMATEX_alloc");
        try
        {
            WAVEFORMATEX_fill(
                    waveformatex,
                    WAVE_FORMAT_PCM,
                    nChannels,
                    nSamplesPerSec,
                    nSamplesPerSec * nBlockAlign,
                    nBlockAlign,
                    wBitsPerSample,
                    cbSize);

            long pmt = MoCreateMediaType(/* cbFormat */ 0);

            if (pmt == 0)
                throw new OutOfMemoryError("MoCreateMediaType");
            try
            {
                int cbFormat = WAVEFORMATEX_sizeof() + cbSize;

                hresult
                    = DMO_MEDIA_TYPE_fill(
                            pmt,
                            /* majortype */ MEDIATYPE_Audio,
                            /* subtype */ MEDIASUBTYPE_PCM,
                            /* bFixedSizeSamples */ true,
                            /* bTemporalCompression */ false,
                            wBitsPerSample / 8,
                            /* formattype */ FORMAT_WaveFormatEx,
                            /* pUnk */ 0,
                            cbFormat,
                            waveformatex);
                if (FAILED(hresult))
                    throw new HResultException(hresult, "DMO_MEDIA_TYPE_fill");
                hresult
                    = inOrOut
                        ? VoiceCaptureDSP.IMediaObject_SetInputType(
                                iMediaObject,
                                dwXXXputStreamIndex,
                                pmt,
                                dwFlags)
                        : VoiceCaptureDSP.IMediaObject_SetOutputType(
                                iMediaObject,
                                dwXXXputStreamIndex,
                                pmt,
                                dwFlags);
                if (FAILED(hresult))
                {
                    throw new HResultException(
                            hresult,
                            inOrOut
                                ? "IMediaObject_SetInputType"
                                : "IMediaObject_SetOutputType");
                }
            }
            finally
            {
                /*
                 * XXX MoDeleteMediaType is documented to internally call
                 * MoFreeMediaType to free the format block but the format block
                 * has not been internally allocated by MoInitMediaType.
                 */
                DMO_MEDIA_TYPE_setCbFormat(pmt, 0);
                DMO_MEDIA_TYPE_setFormattype(pmt, FORMAT_None);
                DMO_MEDIA_TYPE_setPbFormat(pmt, 0);
                MoDeleteMediaType(pmt);
            }
        }
        finally
        {
            CoTaskMemFree(waveformatex);
        }
        return hresult;
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
    static void throwNewIOException(String message, HResultException hre)
        throws IOException
    {
        logger.error(message, hre);

        IOException ioe = new IOException(message);

        ioe.initCause(hre);
        throw ioe;
    }

    private int bufferMaxLength;

    /**
     * The size/length in bytes of the <tt>Buffer</tt> to be filled in an
     * invocation of {@link #read(Buffer)}.
     */
    private int bufferSize;

    private AudioCaptureClient capture;

    private int captureBufferMaxLength;

    private long captureIMediaBuffer;

    private boolean captureIsBusy;

    /**
     * The length in milliseconds of the interval between successive, periodic
     * processing passes by the audio engine on the data in the endpoint buffer.
     */
    private long devicePeriod;

    private long dmoOutputDataBuffer;

    /**
     * The <tt>AudioFormat</tt> of this <tt>SourceStream</tt>.
     */
    private AudioFormat format;

    private long iMediaBuffer;

    private long iMediaObject;

    /**
     * The <tt>MediaLocator</tt> which identifies the audio endpoint device this
     * <tt>SourceStream</tt> is to capture data from.
     */
    private MediaLocator locator;

    private byte[] processed;

    private int processedLength;

    private byte[] processInputBuffer;

    private Thread processThread;

    private AudioCaptureClient render;

    private int renderBufferMaxLength;

    private long renderIMediaBuffer;

    private boolean renderIsBusy;

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
    }

    /**
     * Performs optional configuration of the Voice Capture DSP that implements
     * acoustic echo cancellation (AEC).
     *
     * @param iPropertyStore a reference to the <tt>IPropertyStore</tt>
     * interface of the Voice Capture DSP that implements acoustic echo
     * cancellation (AEC)
     */
    private void configureAEC(long iPropertyStore)
        throws HResultException
    {
        /*
         * For example, use the IPropertyStore_SetValue methods of the
         * VoiceCaptureDSP class to set the MFPKEY_WMAAECMA_FEATURE_MODE
         * property to true and override the default settings on the
         * MFPKEY_WMAAECMA_FEATR_XXX properties of the Voice Capture DSP. 
         */
    }

    /**
     * Connects this <tt>SourceStream</tt> to the audio endpoint device
     * identified by {@link #locator} if disconnected.
     *
     * @throws IOException if this <tt>SourceStream</tt> is disconnected and
     * fails to connect to the audio endpoint device identified by
     * <tt>locator</tt>
     */
    private void connect()
        throws IOException
    {
        if (capture != null)
            return;

        try
        {
            doConnect();
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
            {
                logger.error(
                        "Failed to connect a WASAPIStream"
                            + " to an audio endpoint device.",
                        t);
                if (t instanceof IOException)
                    throw (IOException) t;
                else
                {
                    IOException ioe = new IOException();

                    ioe.initCause(t);
                    throw ioe;
                }
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
            uninitializeAEC();
            uninitializeRender();
            uninitializeCapture();

            /*
             * Make sure this AbstractPullBufferStream asks its DataSource for
             * the Format in which it is supposed to output audio data the next
             * time it is connected instead of using its Format from a previous
             * connect.
             */
            format = null;
        }
    }

    /**
     * Invoked by {@link #connect()} after a check that this
     * <tt>SourceStream</tt> really needs to connect to the associated audio
     * endpoint device has been passed i.e. it is certain that this instance is
     * disconnected.
     *
     * @throws Exception if the <tt>SourceStream</tt> fails to connect to the
     * associated audio endpoint device. The <tt>Exception</tt> is logged by the
     * <tt>connect()</tt> method.
     */
    private void doConnect()
        throws Exception
    {
        MediaLocator locator = getLocator();

        if (locator == null)
            throw new NullPointerException("No locator set.");

        AudioFormat thisFormat = (AudioFormat) getFormat();

        if (thisFormat == null)
            throw new NullPointerException("No format set.");
        if (dataSource.aec)
        {
            CaptureDeviceInfo2 renderDeviceInfo
                = dataSource.audioSystem.getSelectedDevice(
                        AudioSystem.DataFlow.PLAYBACK);

            if (renderDeviceInfo == null)
                throw new NullPointerException("No playback device set.");

            MediaLocator renderLocator = renderDeviceInfo.getLocator();

            /*
             * This SourceStream will output in an AudioFormat supported by the
             * voice capture DMO which implements the acoustic echo cancellation
             * (AEC) feature. The IAudioClients will be initialized with
             * AudioFormats based on thisFormat
             */
            AudioFormat captureFormat
                = findClosestMatchCaptureSupportedFormat(thisFormat);

            if (captureFormat == null)
            {
                throw new IllegalStateException(
                        "Failed to determine an AudioFormat with which to"
                            + " initialize IAudioClient for MediaLocator "
                            + locator + " based on AudioFormat " + thisFormat);
            }

            AudioFormat renderFormat
                = findClosestMatch(renderDeviceInfo.getFormats(), thisFormat);

            if (renderFormat == null)
            {
                throw new IllegalStateException(
                        "Failed to determine an AudioFormat with which to"
                            + " initialize IAudioClient for MediaLocator "
                            + renderLocator + " based on AudioFormat "
                            + thisFormat);
            }

            boolean uninitialize = true;

            initializeCapture(locator, captureFormat);
            try
            {
                initializeRender(renderLocator, renderFormat);
                try
                {
                    initializeAEC(captureFormat, renderFormat, thisFormat);
                    uninitialize = false;
                }
                finally
                {
                    if (uninitialize)
                        uninitializeRender();
                }
            }
            finally
            {
                if (uninitialize)
                    uninitializeCapture();
            }
        }
        else
            initializeCapture(locator, thisFormat);

        this.format = thisFormat;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Format doGetFormat()
    {
        return (format == null) ? super.doGetFormat() : format;
    }

    private AudioFormat findClosestMatchCaptureSupportedFormat(
            AudioFormat format)
    {
        return
            findClosestMatch(
                    dataSource.getIAudioClientSupportedFormats(),
                    format);
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

    private void initializeAEC(
            AudioFormat inFormat0, AudioFormat inFormat1,
            AudioFormat outFormat)
        throws Exception
    {
        long iMediaObject = dataSource.audioSystem.initializeAEC();

        if (iMediaObject == 0)
        {
            throw new ResourceUnavailableException(
                    "Failed to initialize a Voice Capture DSP for the purposes"
                        + " of acoustic echo cancellation (AEC).");
        }
        try
        {
            int hresult
                = IMediaObject_SetXXXputType(
                        iMediaObject,
                        /* IMediaObject_SetInputType */ true,
                        /* dwInputStreamIndex */ 0,
                        inFormat0,
                        /* dwFlags */ 0);

            if (FAILED(hresult))
            {
                throw new HResultException(
                        hresult,
                        "IMediaObject_SetInputType, dwOutputStreamIndex 0, "
                                + inFormat0);
            }
            hresult
                = IMediaObject_SetXXXputType(
                        iMediaObject,
                        /* IMediaObject_SetInputType */ true,
                        /* dwInputStreamIndex */ 1,
                        inFormat1,
                        /* dwFlags */ 0);
            if (FAILED(hresult))
            {
                throw new HResultException(
                        hresult,
                        "IMediaObject_SetInputType, dwOutputStreamIndex 1, "
                                + inFormat1);
            }
            hresult
                = IMediaObject_SetXXXputType(
                        iMediaObject,
                        /* IMediaObject_SetOutputType */ false,
                        /* dwOutputStreamIndex */ 0,
                        outFormat,
                        /* dwFlags */ 0);
            if (FAILED(hresult))
            {
                throw new HResultException(
                        hresult,
                        "IMediaObject_SetOutputType, " + outFormat);
            }

            long iPropertyStore
                = IMediaObject_QueryInterface(
                        iMediaObject,
                        IID_IPropertyStore);

            if (iPropertyStore == 0)
            {
                throw new RuntimeException(
                        "IMediaObject_QueryInterface IID_IPropertyStore");
            }
            try
            {
                hresult
                    = IPropertyStore_SetValue(
                            iPropertyStore,
                            MFPKEY_WMAAECMA_DMO_SOURCE_MODE,
                            false);
                if (FAILED(hresult))
                {
                    throw new HResultException(
                            hresult,
                            "IPropertyStore_SetValue"
                                + " MFPKEY_WMAAECMA_DMO_SOURCE_MODE");
                }
                configureAEC(iPropertyStore);

                long captureIMediaBuffer
                    = MediaBuffer_alloc(capture.bufferSize);

                if (captureIMediaBuffer == 0)
                    throw new OutOfMemoryError("MediaBuffer_alloc");
                try
                {
                    long renderIMediaBuffer
                        = MediaBuffer_alloc(render.bufferSize);

                    if (renderIMediaBuffer == 0)
                        throw new OutOfMemoryError("MediaBuffer_alloc");
                    try
                    {
                        int outFrameSize
                            = WASAPISystem.getSampleSizeInBytes(outFormat)
                                * outFormat.getChannels();
                        int outFrames
                            = (int)
                                (AudioCaptureClient.DEFAULT_BUFFER_DURATION
                                    * ((int) outFormat.getSampleRate())
                                    / 1000);
                        long iMediaBuffer
                            = MediaBuffer_alloc(outFrameSize * outFrames);

                        if (iMediaBuffer == 0)
                            throw new OutOfMemoryError("MediaBuffer_alloc");
                        try
                        {
                            long dmoOutputDataBuffer
                                = DMO_OUTPUT_DATA_BUFFER_alloc(
                                        iMediaBuffer,
                                        /* dwStatus */ 0,
                                        /* rtTimestamp */ 0,
                                        /* rtTimelength */ 0);

                            if (dmoOutputDataBuffer == 0)
                            {
                                throw new OutOfMemoryError(
                                        "DMO_OUTPUT_DATA_BUFFER_alloc");
                            }
                            try
                            {
                                bufferMaxLength
                                    = IMediaBuffer_GetMaxLength(iMediaBuffer);
                                captureBufferMaxLength
                                    = IMediaBuffer_GetMaxLength(
                                            captureIMediaBuffer);
                                renderBufferMaxLength
                                    = IMediaBuffer_GetMaxLength(
                                            renderIMediaBuffer);

                                processed = new byte[bufferMaxLength * 3];
                                processedLength = 0;

                                this.captureIMediaBuffer = captureIMediaBuffer;
                                captureIMediaBuffer = 0;
                                this.dmoOutputDataBuffer = dmoOutputDataBuffer;
                                dmoOutputDataBuffer = 0;
                                this.iMediaBuffer = iMediaBuffer;
                                iMediaBuffer = 0;
                                this.iMediaObject = iMediaObject;
                                iMediaObject = 0;
                                this.renderIMediaBuffer = renderIMediaBuffer;
                                renderIMediaBuffer = 0;
                            }
                            finally
                            {
                                if (dmoOutputDataBuffer != 0)
                                    CoTaskMemFree(dmoOutputDataBuffer);
                            }
                        }
                        finally
                        {
                            if (iMediaBuffer != 0)
                                IMediaBuffer_Release(iMediaBuffer);
                        }
                    }
                    finally
                    {
                        if (renderIMediaBuffer != 0)
                            IMediaBuffer_Release(renderIMediaBuffer);
                    }
                }
                finally
                {
                    if (captureIMediaBuffer != 0)
                        IMediaBuffer_Release(captureIMediaBuffer);
                }
            }
            finally
            {
                if (iPropertyStore != 0)
                    IPropertyStore_Release(iPropertyStore);
            }
        }
        finally
        {
            if (iMediaObject != 0)
                IMediaObject_Release(iMediaObject);
        }
    }

    private void initializeCapture(MediaLocator locator, AudioFormat format)
        throws Exception
    {
        capture
            = new AudioCaptureClient(
                    dataSource.audioSystem,
                    locator,
                    AudioSystem.DataFlow.CAPTURE,
                    /* streamFlags */ 0,
                    format,
                    new BufferTransferHandler()
                            {
                                public void transferData(
                                        PushBufferStream stream)
                                {
                                    transferCaptureData();
                                }
                            });
        bufferSize = capture.bufferSize;
        devicePeriod = capture.devicePeriod;
    }

    private void initializeRender(final MediaLocator locator, AudioFormat format)
        throws Exception
    {
        render
            = new AudioCaptureClient(
                    dataSource.audioSystem,
                    locator,
                    AudioSystem.DataFlow.PLAYBACK,
                    WASAPI.AUDCLNT_STREAMFLAGS_LOOPBACK,
                    format,
                    new BufferTransferHandler()
                            {
                                public void transferData(
                                        PushBufferStream stream)
                                {
                                    transferRenderData();
                                }
                            });
    }

    /**
     * Pops a specific number of bytes from {@link #processed}. For example,
     * because such a number of bytes have been read from <tt>processed</tt> and
     * written into a <tt>Buffer</tt>.
     *
     * @param length the number of bytes to pop from <tt>processed</tt>
     */
    private void popFromProcessed(int length)
    {
        processedLength
            = WASAPIRenderer.pop(processed, processedLength, length);
    }

    private int processInput(int dwInputStreamIndex)
    {
        long pBuffer;
        int maxLength;
        AudioCaptureClient audioCaptureClient;

        switch (dwInputStreamIndex)
        {
        case 0:
            pBuffer = captureIMediaBuffer;
            maxLength = captureBufferMaxLength;
            audioCaptureClient = capture;
            break;
        case 1:
            pBuffer = renderIMediaBuffer;
            maxLength = renderBufferMaxLength;
            audioCaptureClient = render;
            break;
        default:
            throw new IllegalArgumentException("dwInputStreamIndex");
        }

        int hresult = S_OK;
        int processed = 0;

        do
        {
            int dwFlags;

            try
            {
                dwFlags
                    = IMediaObject_GetInputStatus(
                            iMediaObject,
                            dwInputStreamIndex);
            }
            catch (HResultException hre)
            {
                dwFlags = 0;
                hresult = hre.getHResult();
                logger.error("IMediaObject_GetInputStatus", hre);
            }
            if ((dwFlags & DMO_INPUT_STATUSF_ACCEPT_DATA)
                    == DMO_INPUT_STATUSF_ACCEPT_DATA)
            {
                int toRead;

                try
                {
                    toRead = maxLength - IMediaBuffer_GetLength(pBuffer);
                }
                catch (HResultException hre)
                {
                    hresult = hre.getHResult();
                    toRead = 0;
                    logger.error("IMediaBuffer_GetLength", hre);
                }
                if (toRead > 0)
                {
                    if ((processInputBuffer == null)
                            || (processInputBuffer.length < toRead))
                        processInputBuffer = new byte[toRead];

                    int read;

                    try
                    {
                        read
                            = audioCaptureClient.read(
                                    processInputBuffer,
                                    0,
                                    toRead);
                    }
                    catch (IOException ioe)
                    {
                        read = 0;
                        logger.error(
                                "Failed to read from IAudioCaptureClient.",
                                ioe);
                    }
                    if (read > 0)
                    {
                        int written;

                        try
                        {
                            written
                                = MediaBuffer_push(
                                        pBuffer,
                                        processInputBuffer, 0, read);
                        }
                        catch (HResultException hre)
                        {
                            written = 0;
                            logger.error("MediaBuffer_push", hre);
                        }
                        if (written < read)
                        {
                            logger.error(
                                    "Failed to push/write "
                                        + ((written <= 0)
                                                ? read
                                                : (read - written))
                                        + " bytes into an IMediaBuffer.");
                        }
                        if (written > 0)
                            processed += written;
                    }
                }

                if (dwInputStreamIndex == 1)
                {
                    int length;

                    try
                    {
                        length = IMediaBuffer_GetLength(pBuffer);
                    }
                    catch (HResultException hre)
                    {
                        hresult = hre.getHResult();
                        length = 0;
                        logger.error("IMediaBuffer_GetLength", hre);
                    }

                    int silence = maxLength - length;

                    if (silence > 0)
                    {
                        if ((processInputBuffer == null)
                                || (processInputBuffer.length < silence))
                            processInputBuffer = new byte[silence];
                        Arrays.fill(processInputBuffer, 0, silence, (byte) 0);
                        try
                        {
                            MediaBuffer_push(
                                    pBuffer,
                                    processInputBuffer, 0, silence);
                        }
                        catch (HResultException hre)
                        {
                            logger.error("MediaBuffer_push", hre);
                        }
                    }
                }

                try
                {
                    hresult
                        = IMediaObject_ProcessInput(
                                iMediaObject,
                                dwInputStreamIndex,
                                pBuffer,
                                /* dwFlags */ 0,
                                /* rtTimestamp */ 0,
                                /* rtTimelength */ 0);
                }
                catch (HResultException hre)
                {
                    hresult = hre.getHResult();
                    if (hresult != DMO_E_NOTACCEPTING)
                        logger.error("IMediaObject_ProcessInput", hre);
                }
            }
            else
                break; // The input stream cannot accept more input data.
        }
        while (SUCCEEDED(hresult));

        return processed;
    }

    /**
     * {@inheritDoc}
     */
    public void read(Buffer buffer)
        throws IOException
    {
        // Reduce relocations as much as possible.
        int capacity = dataSource.aec ? bufferMaxLength : bufferSize;
        byte[] data
            = AbstractCodec2.validateByteArraySize(buffer, capacity, false);
        int length = 0;

        buffer.setLength(0);
        buffer.setOffset(0);

        do
        {
            String message;

            synchronized (this)
            {
                if ((capture == null) || (dataSource.aec && (render == null)))
                    message = getClass().getName() + " is disconnected.";
                else
                {
                    message = null;
                    captureIsBusy = true;
                    renderIsBusy = true;
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
                int toRead = capacity - length;

                if (render == null)
                    read = capture.read(data, length, toRead);
                else
                {
                    toRead = Math.min(toRead, processedLength);
                    if (toRead == 0)
                        read = 0;
                    else
                    {
                        System.arraycopy(processed, 0, data, length, toRead);
                        popFromProcessed(toRead);
                        read = toRead;
                    }
                }
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
                    captureIsBusy = false;
                    renderIsBusy = false;
                    notifyAll();
                }
            }
            if (cause == null)
            {
                if (length == 0)
                {
                    long timeStamp = System.nanoTime();

                    buffer.setFlags(Buffer.FLAG_SYSTEM_TIME);
                    buffer.setTimeStamp(timeStamp);
                }
                length += read;
                if ((length >= capacity) || (read == 0))
                {
                    if (format != null)
                        buffer.setFormat(format);
                    buffer.setLength(length);
                    break;
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

    private BufferTransferHandler runInProcessThread()
    {
        // ProcessInput
        processInput(/* capture */ 0);
        processInput(/* render */ 1);

        // ProcessOutput
        int dwStatus = 0;

        do
        {
            try
            {
                IMediaObject_ProcessOutput(
                        iMediaObject,
                        /* dwFlags */ 0,
                        1,
                        dmoOutputDataBuffer);
            }
            catch (HResultException hre)
            {
                dwStatus = 0;
                logger.error("IMediaObject_ProcessOutput", hre);
            }
            try
            {
                int toRead = IMediaBuffer_GetLength(iMediaBuffer);

                if (toRead > 0)
                {
                    /*
                     * Make sure there is enough room in processed to
                     * accommodate toRead.
                     */
                    int toPop = toRead - (processed.length - processedLength);

                    if (toPop > 0)
                        popFromProcessed(toPop);

                    int read
                        = MediaBuffer_pop(
                                iMediaBuffer,
                                processed, processedLength, toRead);

                    if (read > 0)
                        processedLength += read;
                }
            }
            catch (HResultException hre)
            {
                logger.error(
                        "Failed to read from acoustic echo cancellation (AEC)"
                            + " output IMediaBuffer.",
                        hre);
                break;
            }
        }
        while ((dwStatus & DMO_OUTPUT_DATA_BUFFERF_INCOMPLETE)
                == DMO_OUTPUT_DATA_BUFFERF_INCOMPLETE);

        /*
         * IMediaObject::ProcessOutput has completed which means that, as far as
         * it is concerned, it does not have any input data to process. Make
         * sure that the states of the IMediaBuffer instances are in accord.
         */
        try
        {
            /*
             * XXX Make sure that the IMediaObject releases any IMediaBuffer
             * references it holds.
             */
            int hresult = IMediaObject_Flush(iMediaObject);

            if (SUCCEEDED(hresult))
            {
                IMediaBuffer_SetLength(captureIMediaBuffer, 0);
                IMediaBuffer_SetLength(renderIMediaBuffer, 0);
            }
        }
        catch (HResultException hre)
        {
            logger.error("IMediaBuffer_SetLength", hre);
        }

        return (processedLength >= bufferMaxLength) ? transferHandler : null;
    }

    private void runInProcessThread(Thread processThread)
    {
        try
        {
            AbstractAudioRenderer.useAudioThreadPriority();

            do
            {
                BufferTransferHandler transferHandler;

                synchronized (this)
                {
                    if (!processThread.equals(this.processThread))
                        break;
                    if ((capture == null) || (render == null) || !started)
                        break;

                    waitWhileCaptureIsBusy();
                    waitWhileRenderIsBusy();
                    captureIsBusy = true;
                    renderIsBusy = true;
                }
                try
                {
                    transferHandler = runInProcessThread();
                }
                finally
                {
                    synchronized (this)
                    {
                        captureIsBusy = false;
                        renderIsBusy = false;
                        notifyAll();
                    }
                }

                if (transferHandler != null)
                {
                    try
                    {
                        transferHandler.transferData(this);
                        /*
                         * If the transferData implementation throws an
                         * exception, we will wait on a synchronization root in
                         * order to give the application time to recover.
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

                yield();
            }
            while (true);
        }
        finally
        {
            synchronized (this)
            {
                if (processThread.equals(this.processThread))
                {
                    this.processThread = null;
                    notifyAll();
                }
            }
        }
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
        if (capture != null)
        {
            waitWhileCaptureIsBusy();
            capture.start();
        }
        if (render != null)
        {
            waitWhileRenderIsBusy();
            render.start();
        }
        started = true;
        if ((capture != null) && (render != null) && (processThread == null))
        {
            processThread
                = new Thread(WASAPIStream.class + ".processThread")
                    {
                        @Override
                        public void run()
                        {
                            runInProcessThread(this);
                        }
                    };
            processThread.setDaemon(true);
            processThread.start();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop()
        throws IOException
    {
        if (capture != null)
        {
            waitWhileCaptureIsBusy();
            capture.stop();
        }
        if (render != null)
        {
            waitWhileRenderIsBusy();
            render.stop();
        }
        started = false;

        waitWhileProcessThread();
        processedLength = 0;
    }

    private void transferCaptureData()
    {
        if (dataSource.aec)
        {
            synchronized (this)
            {
                notifyAll();
            }
        }
        else
        {
            BufferTransferHandler transferHandler = this.transferHandler;

            if (transferHandler != null)
                transferHandler.transferData(this);
        }
    }

    private void transferRenderData()
    {
        synchronized (this)
        {
            notifyAll();
        }
    }

    private void uninitializeAEC()
    {
        if (iMediaObject != 0)
        {
            IMediaObject_Release(iMediaObject);
            iMediaObject = 0;
        }
        if (dmoOutputDataBuffer != 0)
        {
            CoTaskMemFree(dmoOutputDataBuffer);
            dmoOutputDataBuffer = 0;
        }
        if (iMediaBuffer != 0)
        {
            IMediaBuffer_Release(iMediaBuffer);
            iMediaBuffer = 0;
        }
        if (renderIMediaBuffer != 0)
        {
            IMediaBuffer_Release(renderIMediaBuffer);
            renderIMediaBuffer =0;
        }
        if (captureIMediaBuffer != 0)
        {
            IMediaBuffer_Release(captureIMediaBuffer);
            captureIMediaBuffer = 0;
        }
    }

    private void uninitializeCapture()
    {
        if (capture != null)
        {
            capture.close();
            capture = null;
        }
    }

    private void uninitializeRender()
    {
        if (render != null)
        {
            render.close();
            render = null;
        }
    }

    /**
     * Waits on this instance while the value of {@link #captureIsBusy} is equal
     * to <tt>true</tt>.
     */
    private synchronized void waitWhileCaptureIsBusy()
    {
        boolean interrupted = false;

        while (captureIsBusy)
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

    private synchronized void waitWhileProcessThread()
    {
        while (processThread != null)
            yield();
    }

    /**
     * Waits on this instance while the value of {@link #renderIsBusy} is equal
     * to <tt>true</tt>.
     */
    private synchronized void waitWhileRenderIsBusy()
    {
        boolean interrupted = false;

        while (renderIsBusy)
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
     * Causes the currently executing thread to temporarily pause and allow
     * other threads to execute.
     */
    private synchronized void yield()
    {
        boolean interrupted = false;

        try
        {
            wait(devicePeriod);
        }
        catch (InterruptedException ie)
        {
            interrupted = true;
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }
}
