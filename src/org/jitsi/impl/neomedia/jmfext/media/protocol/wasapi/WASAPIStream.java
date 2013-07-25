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
     * The zero-based index of the input stream of the <tt>IMediaObject</tt>
     * that represents the Voice Capture DSP implementing the acoustic echo
     * cancellation (AEC) feature that delivers the audio samples from the
     * microphone.
     */
    private static final int CAPTURE_INPUT_STREAM_INDEX = 0;

    /**
     * The default value of the property
     * <tt>MFPKEY_WMAAECMA_DMO_SOURCE_MODE</tt> to be set on the Voice Capture
     * DSP.
     */
    private static final boolean DEFAULT_SOURCE_MODE = true;

    /**
     * The <tt>Logger</tt> used by the <tt>WASAPIStream</tt> class and its
     * instances to log debug information.
     */
    private static Logger logger = Logger.getLogger(WASAPIStream.class);

    /**
     * The zero-based index of the input stream of the <tt>IMediaObject</tt>
     * that represents the Voice Capture DSP implementing the acoustic echo
     * cancellation (AEC) feature that delivers the audio samples from the
     * speaker (line).
     */
    private static final int RENDER_INPUT_STREAM_INDEX = 1;

    /**
     * Finds an <tt>AudioFormat</tt> in a specific list of <tt>Format</tt>s
     * which is as similar to a specific <tt>AudioFormat</tt> as possible.
     *
     * @param formats the list of <tt>Format</tt>s into which an
     * <tt>AudioFormat</tt> as similar to the specified <tt>format</tt> as
     * possible is to be found 
     * @param format the <tt>AudioFormat</tt> for which a similar
     * <tt>AudioFormat</tt> is to be found in <tt>formats</tt>
     * @param clazz the runtime type of the matches to be considered or
     * <tt>null</tt> if the runtime type is to not be limited
     * @return an <tt>AudioFormat</tt> which is an element of <tt>formats</tt>
     * and is as similar to the specified <tt>format</tt> as possible or
     * <tt>null</tt> if no similarity could be established
     */
    private static AudioFormat findClosestMatch(
            Format[] formats,
            AudioFormat format,
            Class<? extends AudioFormat> clazz)
    {
        // Try to find the very specified format.
        AudioFormat match = findFirstMatch(formats, format, clazz);

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
                                format.getDataType()),
                        clazz);
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
                                    format.getDataType()),
                            clazz);
            }
        }
        return match;
    }

    /**
     * Finds the first element of a specific array of <tt>Format</tt>s which
     * matches in the sense of {@link Format#matches(Format)} a specific
     * <tt>AudioFormat</tt>.
     *
     * @param formats the array of <tt>Format</tt>s which si to be searched
     * @param format the <tt>AudioFormat</tt> for which a match is to be found
     * in the specified <tt>formats</tt>
     * @param clazz the runtime type of the matches to be considered or
     * <tt>null</tt> if the runtime type is to not be limited
     * @return the first element of <tt>formats</tt> which matches the specified
     * <tt>format</tt> or <tt>null</tt> if no match could be found
     */
    private static AudioFormat findFirstMatch(
            Format[] formats,
            AudioFormat format,
            Class<? extends AudioFormat> clazz)
    {
        for (Format aFormat : formats)
        {
            if (aFormat.matches(format)
                    && ((clazz == null) || clazz.isInstance(aFormat)))
            {
                return (AudioFormat) aFormat.intersects(format);
            }
        }
        return null;
    }

    /**
     * Sets the media type of an input or output stream of a specific
     * <tt>IMediaObject</tt>.
     * 
     * @param iMediaObject the <tt>IMediaObject</tt> to set the media type of
     * @param inOrOut <tt>true</tt> if the media type of an input stream of the
     * specified <tt>iMediaObject</tt> is to be set or <tt>false</tt> if the
     * media type of an output stream of the specified <tt>iMediaObject</tT> is
     * to be set
     * @param dwXXXputStreamIndex the zero-based index of the input or output
     * stream on the specified <tt>iMediaObject</tt> of which the media type is
     * to be set
     * @param audioFormat the <tt>AudioFormat</tt> to be set on the specified
     * stream of the DMO
     * @param dwFlags bitwise combination of zero or more
     * <tt>DMO_SET_TYPEF_XXX</tt> flags (defined by the <tt>VoiceCaptureDSP</tt>
     * class
     * @return an <tt>HRESULT</tt> value indicating whether the specified
     * <tt>audioFormat</tt> is acceptable and/or whether it has been set
     * successfully
     * @throws HResultException if setting the media type of the specified
     * stream of the specified <tt>iMediaObject</tt> fails 
     */
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
     * Invokes {@link IMediaBuffer#GetLength()} and logs and swallows any
     * <tt>IOException</tt>.
     *
     * @param iMediaBuffer the <tt>IMediaBuffer</tt> on which the method
     * <tt>GetLength</tt> is to be invoked
     * @return the length of the specified <tt>iMediaBuffer</tt>. If the method
     * <tt>GetLength</tt> fails, returns <tt>0</tt>.
     */
    private static int maybeIMediaBufferGetLength(IMediaBuffer iMediaBuffer)
    {
        int length;

        try
        {
            length = iMediaBuffer.GetLength();
        }
        catch (IOException ioe)
        {
            length = 0;
            logger.error("IMediaBuffer.GetLength", ioe);
        }
        return length;
    }

    /**
     * Invokes {@link VoiceCaptureDSP#IMediaBuffer_GetLength(long)} and logs and
     * swallows any <tt>HResultException</tt>.
     *
     * @param iMediaBuffer the <tt>IMediaBuffer</tt> on which the function
     * <tt>IMediaBuffer_GetLength</tt> is to be invoked
     * @return the length of the specified <tt>iMediaBuffer</tt>. If the
     * function <tt>IMediaBuffer_GetLength</tt> fails, returns <tt>0</tt>.
     */
    @SuppressWarnings("unused")
    private static int maybeIMediaBufferGetLength(long iMediaBuffer)
    {
        int length;

        try
        {
            length = IMediaBuffer_GetLength(iMediaBuffer);
        }
        catch (HResultException hre)
        {
            length = 0;
            logger.error("IMediaBuffer_GetLength", hre);
        }
        return length;
    }

    /**
     * Invokes {@link VoiceCaptureDSP#MediaBuffer_push(long, byte[], int, int)}
     * on a specific <tt>IMediaBuffer</tt> and logs and swallows any
     * <tt>HResultException</tT>.
     *
     * @param pBuffer the <tt>IMediaBuffer</tt> into which the specified bytes
     * are to be pushed/written
     * @param buffer the bytes to be pushed/written into the specified
     * <tt>pBuffer</tt>
     * @param offset the offset in <tt>buffer</tt> at which the bytes to be
     * pushed/written into the specified <tt>pBuffer</tt> start
     * @param length the number of bytes in <tt>buffer</tt> starting at
     * <tt>offset</tt> to be pushed/written into the specified <tt>pBuffer</tt>
     * @return the number of bytes from the specified <tt>buffer</tt>
     * pushed/written into the specified <tt>pBuffer</tt>  
     */
    private static int maybeMediaBufferPush(
            long pBuffer,
            byte[] buffer, int offset, int length)
    {
        int written;
        Throwable exception;

        try
        {
            written = MediaBuffer_push(pBuffer, buffer, offset, length);
            exception = null;
        }
        catch (HResultException hre)
        {
            written = 0;
            exception = hre;
        }
        if ((exception != null) || (written != length))
        {
            logger.error(
                    "Failed to push/write "
                        + ((written <= 0) ? length : (length - written))
                        + " bytes into an IMediaBuffer.",
                    exception);
        }
        return written;
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

    /**
     * The indicator which determines whether this <tt>SourceStream</tt> has
     * been connected with acoustic echo cancellation (AEC) enabled.
     */
    private boolean aec;

    /**
     * The maximum capacity/number of bytes of {@link #iMediaBuffer}.
     */
    private int bufferMaxLength;

    /**
     * The size/length in bytes of the <tt>Buffer</tt> to be filled in an
     * invocation of {@link #read(Buffer)}.
     */
    private int bufferSize;

    /**
     * The abstraction which delivers audio samples from the capture endpoint
     * device into this instance.
     */
    private AudioCaptureClient capture;

    /**
     * The maximum capacity/number of bytes of {@link #captureIMediaBuffer}.
     */
    private int captureBufferMaxLength;

    /**
     * The <tt>IMediaBuffer</tt> instance which delivers audio samples from the
     * capture endpoint device i.e. {@link #capture} into the voice capture DMO
     * that implements the acoustic echo cancellation (AEC) feature i.e.
     * {@link #iMediaBuffer}.
     */
    private PtrMediaBuffer captureIMediaBuffer;

    /**
     * The indicator which determines whether {@link #capture} and its
     * associated resources/states are busy and, consequently, should not be
     * modified. For example, <tt>capture</tt> is busy during the execution of
     * {@link #read(Buffer)}.
     */
    private boolean captureIsBusy;

    /**
     * The number of nonoseconds of audio encoded in the <tt>outFormat</tt> of
     * {@link #capture} represented by a <tt>byte</tt>.
     */
    private double captureNanosPerByte;

    /**
     * The length in milliseconds of the interval between successive, periodic
     * processing passes by the audio engine on the data in the endpoint buffer.
     */
    private long devicePeriod;

    /**
     * The <tt>DMO_OUTPUT_DATA_BUFFER</tt> which provides {@link #iMediaBuffer}
     * to
     * {@link VoiceCaptureDSP#IMediaObject_ProcessOutput(long, int, int, long)}.
     */
    private long dmoOutputDataBuffer;

    /**
     * The <tt>AudioFormat</tt> of this <tt>SourceStream</tt>.
     */
    private AudioFormat format;

    /**
     * The <tt>IMediaBuffer</tt> which receives the output of
     * {@link #iMediaObject} i.e. the acoustic echo cancellation.
     */
    private long iMediaBuffer;

    /**
     * The <tt>IMediaObject</tt> reference to the Voice Capture DSP that
     * implements the acoustic echo cancellation (AEC) feature.
     */
    private long iMediaObject;

    /**
     * The <tt>MediaLocator</tt> which identifies the audio endpoint device this
     * <tt>SourceStream</tt> is to capture data from.
     */
    private MediaLocator locator;

    /**
     * The buffer which stores the result/output of the processing performed by
     * {@link #iMediaObject} i.e. the acoustic echo cancellation.
     */
    private byte[] processed;

    /**
     * The number of bytes in {@link #processed} which represent actual audio
     * data/samples.
     */
    private int processedLength;

    /**
     * An array of <tt>byte</tt>s utilized by {@link #processInput(int, int)}
     * and cached in order to reduce the effects of the garbage collector. 
     */
    private byte[] processInputBuffer;

    /**
     * The background thread which invokes
     * {@link VoiceCaptureDSP#IMediaObject_ProcessInput(long, int, long, int, long, long)}
     * and
     * {@link VoiceCaptureDSP#IMediaObject_ProcessOutput(long, int, int, long)}
     * i.e. delivers audio samples from the capture and render endpoint devices
     * into the voice capture DMO, invokes the acoustic echo cancellation and
     * stores the result/output in {@link #processed} so that it may later be
     * read out of this instance via {@link #read(Buffer)}.  
     */
    private Thread processThread;

    /**
     * The abstraction which delivers audio samples from the render endpoint
     * device into this instance (for the purposes of acoustic echo
     * cancellation).
     */
    private AudioCaptureClient render;

    /**
     * The maximum capacity/number of bytes of {@link #renderIMediaBuffer}.
     */
    private int renderBufferMaxLength;

    /**
     * The number of bytes of audio encoded in the <tt>outFormat</tt> of
     * {@link #render} which represent a duration of one nanosecond.
     */
    private double renderBytesPerNano;

    /**
     * The <tt>IMediaBuffer</tt> instance which delivers audio samples from the
     * render endpoint device i.e. {@link #render} into the voice capture DMO
     * that implements the acoustic echo cancellation (AEC) feature i.e.
     * {@link #iMediaBuffer}.
     */
    private PtrMediaBuffer renderIMediaBuffer;

    /**
     * The indicator which determines whether {@link #render} and its associated
     * resources/states are busy and, consequently, should not be modified. For
     * example, <tt>render</tt> is busy during the execution of
     * {@link #read(Buffer)}.
     */
    private boolean renderIsBusy;

    /**
     * The <tt>WASAPIRenderer</tt> which maintains an active stream on the
     * rendering device selected in the Voice Capture DSP implementing acoustic
     * echo cancellation (AEC) in order to ensure that the DSP can produce
     * output.
     */
    private Renderer renderer;

    /**
     * The indicator which determines whether no reading from {@link #render} is
     * to be performed until it reaches a certain threshold of availability. 
     */
    private boolean replenishRender;

    /**
     * The indicator which determined whether {@link #iMediaObject} has been
     * initialized to operate in source (as opposed to filter) mode.
     */
    private boolean sourceMode;

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
     * Computes/determines the duration in nanoseconds of audio samples which
     * are represented by a specific number of bytes and which are in encoded in
     * the <tt>outFormat</tt> of {@link #capture}.
     *
     * @param length the number of bytes comprising the audio samples of which
     * the duration in nanoseconds is to be computed/determined
     * @return the duration in nanoseconds of audio samples which are
     * represented by the specified number of bytes and which are encoded in the
     * <tt>outFormat</tt> of <tt>capture</tt>
     */
    private long computeCaptureDuration(int length)
    {
        return (long) (length * captureNanosPerByte);
    }

    /**
     * Computes/determines the number of bytes of a specific duration in
     * nanoseconds of audio samples  encoded in the <tt>outFormat</tt> of
     * {@link #render}.
     *
     * @param duration the duration in nanoseconds of the audio samples of which
     * the number of bytes is to be computed/determined
     * @return the number of bytes of the specified duration in nanoseconds of
     * audio samples encoded in the <tt>outFormat</tt> of <tt>render</tt>
     */
    private int computeRenderLength(long duration)
    {
        return (int) (duration * renderBytesPerNano);
    }

    /**
     * Performs optional configuration on the Voice Capture DSP that implements
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
        try
        {
            if (MFPKEY_WMAAECMA_FEATURE_MODE != 0)
            {
                IPropertyStore_SetValue(
                        iPropertyStore,
                        MFPKEY_WMAAECMA_FEATURE_MODE, true);

                AudioSystem audioSystem = dataSource.audioSystem;

                /*
                 * Perform acoustic echo suppression (AEC) on the residual
                 * signal a maximum number of times.
                 */
                if (MFPKEY_WMAAECMA_FEATR_AES != 0)
                {
                    IPropertyStore_SetValue(
                            iPropertyStore,
                            MFPKEY_WMAAECMA_FEATR_AES,
                            audioSystem.isEchoCancel() ? 2 : 0);
                }
                // Perform automatic gain control (AGC).
                if (MFPKEY_WMAAECMA_FEATR_AGC != 0)
                {
                    IPropertyStore_SetValue(
                            iPropertyStore,
                            MFPKEY_WMAAECMA_FEATR_AGC,
                            true);
                }
                // Perform noise suppression (NS).
                if (MFPKEY_WMAAECMA_FEATR_NS != 0)
                {
                    IPropertyStore_SetValue(
                            iPropertyStore,
                            MFPKEY_WMAAECMA_FEATR_NS,
                            audioSystem.isDenoise() ? 1 : 0);
                }
                if (MFPKEY_WMAAECMA_FEATR_ECHO_LENGTH != 0)
                {
                    IPropertyStore_SetValue(
                            iPropertyStore,
                            MFPKEY_WMAAECMA_FEATR_ECHO_LENGTH,
                            256);
                }
            }
        }
        catch (HResultException hre)
        {
            logger.error(
                    "Failed to perform optional configuration on the Voice"
                        + " Capture DSP that implements acoustic echo"
                        + " cancellation (AEC).",
                    hre);
        }
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
            sourceMode = false;
        }
    }

    /**
     * Invoked by {@link #connect()} after a check that this
     * <tt>SourceStream</tt> really needs to connect to the associated audio
     * endpoint device has been passed i.e. it is certain that this instance is
     * disconnected.
     *
     * @throws Exception if this <tt>SourceStream</tt> fails to connect to the
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
            aec = true;
            try
            {
                CaptureDeviceInfo2 captureDevice
                    = dataSource.audioSystem.getDevice(
                            AudioSystem.DataFlow.CAPTURE,
                            locator);

                /*
                 * If the information about the capture device cannot be found,
                 * acoustic echo cancellation (AEC) cannot be enabled. That
                 * should not happen because the locator/MediaLocator is sure to
                 * be set. Anyway, leave the error detection to the non-AEC
                 * branch.
                 */
                if (captureDevice != null)
                {
                    /*
                     * If the information about the render endpoint device
                     * cannot be found, AEC cannot be enabled. Period.
                     */
                    CaptureDeviceInfo2 renderDevice
                        = dataSource.audioSystem.getSelectedDevice(
                                AudioSystem.DataFlow.PLAYBACK);

                    if (renderDevice != null)
                    {
                        boolean sourceMode = DEFAULT_SOURCE_MODE;

                        if (sourceMode)
                        {
                            doConnectInSourceMode(
                                    captureDevice,
                                    renderDevice,
                                    thisFormat);
                        }
                        else
                        {
                            doConnectInFilterMode(
                                    captureDevice,
                                    renderDevice,
                                    thisFormat);
                        }

                        this.sourceMode = sourceMode;
                    }
                }
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else
                {
                    logger.error(
                            "Failed to enable acoustic echo cancellation (AEC)."
                                + " Will try without it.",
                            t);
                }
            }
        }
        if (iMediaObject == 0)
        {
            aec = false;
            initializeCapture(locator, thisFormat);
        }

        this.format = thisFormat;
    }

    /**
     * Invoked by {@link #doConnect()} after it has been determined that
     * acoustic echo cancellation (AEC) is to be utilized and the implementing
     * Voice Capture DSP is to be initialized in filter mode.
     *
     * @param captureDevice a <tt>CaptureDeviceInfo2</tt> which identifies the
     * capture endpoint device to be used
     * @param renderDevice a <tt>CaptureDeviceInfo2</tt> which identifies the
     * render endpoint device to be used
     * @param outFormat the <tt>Format</tt> of the media data in which the Voice
     * Capture DSP is to output
     * @throws Exception if this <tt>SourceStream</tt> fails to connect to the
     * associated audio endpoint device. The <tt>Exception</tt> is logged by the
     * <tt>connect()</tt> method.
     */
    private void doConnectInFilterMode(
            CaptureDeviceInfo2 captureDevice,
            CaptureDeviceInfo2 renderDevice,
            AudioFormat outFormat)
        throws Exception
    {
        /*
         * This SourceStream will output in an AudioFormat supported by the
         * voice capture DMO which implements the acoustic echo cancellation
         * (AEC) feature. The IAudioClients will be initialized with
         * AudioFormats based on outFormat.
         */
        AudioFormat captureFormat
            = findClosestMatchCaptureSupportedFormat(outFormat);

        if (captureFormat == null)
        {
            throw new IllegalStateException(
                    "Failed to determine an AudioFormat with which to"
                        + " initialize IAudioClient for MediaLocator " + locator
                        + " based on AudioFormat " + outFormat);
        }

        MediaLocator renderLocator;
        AudioFormat renderFormat;

        if (renderDevice == null)
        {
            /*
             * We explicitly want to support the case in which the user has
             * selected "none" for the playback/render endpoint device.
             */
            renderLocator = null;
            renderFormat = captureFormat;
        }
        else
        {
            renderLocator = renderDevice.getLocator();
            if (renderLocator == null)
            {
                throw new IllegalStateException(
                        "A CaptureDeviceInfo2 instance which describes a"
                            + " Windows Audio Session API (WASAPI) render"
                            + " endpoint device and which does not have an"
                            + " actual locator/MediaLocator is illegal.");
            }
            else
            {
                renderFormat
                    = findClosestMatch(
                            renderDevice.getFormats(),
                            outFormat,
                            NativelySupportedAudioFormat.class);
                if (renderFormat == null)
                {
                    throw new IllegalStateException(
                            "Failed to determine an AudioFormat with which to"
                                + " initialize IAudioClient for MediaLocator "
                                + renderLocator + " based on AudioFormat "
                                + outFormat);
                }
            }
        }

        boolean uninitialize = true;

        initializeCapture(locator, captureFormat);
        try
        {
            if (renderLocator != null)
                initializeRender(renderLocator, renderFormat);
            try
            {
                initializeAEC(
                        /* sourceMode */ false,
                        captureDevice,
                        captureFormat,
                        renderDevice,
                        renderFormat,
                        outFormat);
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

    /**
     * Invoked by {@link #doConnect()} after it has been determined that
     * acoustic echo cancellation (AEC) is to be utilized and the implementing
     * Voice Capture DSP is to be initialized in filter mode.
     *
     * @param captureDevice a <tt>CaptureDeviceInfo2</tt> which identifies the
     * capture endpoint device to be used
     * @param renderDevice a <tt>CaptureDeviceInfo2</tt> which identifies the
     * render endpoint device to be used
     * @param outFormat the <tt>Format</tt> of the media data in which the Voice
     * Capture DSP is to output
     * @throws Exception if this <tt>SourceStream</tt> fails to connect to the
     * associated audio endpoint device. The <tt>Exception</tt> is logged by the
     * <tt>connect()</tt> method.
     */
    private void doConnectInSourceMode(
            CaptureDeviceInfo2 captureDevice,
            CaptureDeviceInfo2 renderDevice,
            AudioFormat outFormat)
        throws Exception
    {
        initializeAEC(
                /* sourceMode */ true,
                captureDevice,
                /* captureFormat */ null,
                renderDevice,
                /* renderFormat */ null,
                outFormat);
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
     * Finds an <tt>AudioFormat</tt> in the list of supported <tt>Format</tt>s
     * of the associated capture endpoint device which is as similar to a
     * specific <tt>AudioFormat</tt> as possible.
     *
     * @param format the <tt>AudioFormat</tt> for which a similar
     * <tt>AudioFormat</tt> is to be found in the list of supported formats of
     * the associated capture endpoint device
     * @return an <tt>AudioFormat</tt> which is an element of the list of
     * supported formats of the associated capture endpoint device and is as
     * similar to the specified <tt>format</tt> as possible or <tt>null</tt> if
     * no similarity could be established
     */
    private AudioFormat findClosestMatchCaptureSupportedFormat(
            AudioFormat format)
    {
        return
            findClosestMatch(
                    dataSource.getIAudioClientSupportedFormats(),
                    format,
                    NativelySupportedAudioFormat.class);
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
     * Initializes the <tt>IMediaObject</tt> which is to perform acoustic echo
     * cancellation.
     *
     * @param sourceMode <tt>true</tt> if the Voice Capture DSP is to be
     * initialized in source mode or <tt>false</tt> if the Voice Capture DSP is
     * to be initialized in filter mode
     * @param captureDevice a <tt>CaptureDeviceInfo2</tt> which identifies the
     * capture endpoint device to be used
     * @param captureFormat the <tt>AudioFormat</tt> of the media which will be
     * delivered to the input stream representing the audio from the microphone
     * @param renderDevice  <tt>CaptureDeviceInfo2</tt> which identifies the
     * render endpoint device to be used
     * @param renderFormat the <tt>AudioFormat</tt> of the media which will be
     * delivered to the input stream representing the audio from the speaker
     * (line)
     * @param outFormat the <tt>AudioFormat</tt> of the media which is to be
     * output by the <tt>IMediaObject</tt>/acoustic echo cancellation
     * @throws Exception if the initialization of the <tt>IMediaObject</tt>
     * implementing acoustic echo cancellation fails
     */
    private void initializeAEC(
            boolean sourceMode,
            CaptureDeviceInfo2 captureDevice,
            AudioFormat captureFormat,
            CaptureDeviceInfo2 renderDevice,
            AudioFormat renderFormat,
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
            long iPropertyStore
                = IMediaObject_QueryInterface(iMediaObject, IID_IPropertyStore);

            if (iPropertyStore == 0)
            {
                throw new RuntimeException(
                        "IMediaObject_QueryInterface IID_IPropertyStore");
            }
            try
            {
                int hresult
                    = IPropertyStore_SetValue(
                            iPropertyStore,
                            MFPKEY_WMAAECMA_DMO_SOURCE_MODE,
                            sourceMode);

                if (FAILED(hresult))
                {
                    throw new HResultException(
                            hresult,
                            "IPropertyStore_SetValue"
                                + " MFPKEY_WMAAECMA_DMO_SOURCE_MODE");
                }
                configureAEC(iPropertyStore);

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

                int outFrameSize
                    = WASAPISystem.getSampleSizeInBytes(outFormat)
                        * outFormat.getChannels();
                int outFrames
                    = (int)
                        (WASAPISystem.DEFAULT_BUFFER_DURATION
                            * ((int) outFormat.getSampleRate()) / 1000);
                long iMediaBuffer = MediaBuffer_alloc(outFrameSize * outFrames);

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

                        processed = new byte[bufferMaxLength * 3];
                        processedLength = 0;

                        if (sourceMode)
                        {
                            initializeAECInSourceMode(
                                    iPropertyStore,
                                    captureDevice,
                                    renderDevice,
                                    outFormat);
                        }
                        else
                        {
                            initializeAECInFilterMode(
                                    iMediaObject,
                                    captureFormat,
                                    renderFormat,
                                    outFormat);
                        }

                        this.dmoOutputDataBuffer = dmoOutputDataBuffer;
                        dmoOutputDataBuffer = 0;
                        this.iMediaBuffer = iMediaBuffer;
                        iMediaBuffer = 0;
                        this.iMediaObject = iMediaObject;
                        iMediaObject = 0;
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

    /**
     * Initializes the Voice Capture DSP which is to perform acoustic echo
     * cancellation. The method is invoked in case the Voice Capture DSP is to
     * be used in filter mode.
     *
     * @param iMediaObject the <tt>IMediaObject</tt> interface to the Voice
     * Capture DSP to be initialized in filter mode
     * @param inFormat0 the <tt>AudioFormat</tt> of the media which will be
     * delivered to the input stream representing the audio from the microphone
     * @param inFormat1 the <tt>AudioFormat</tt> of the media which will be
     * delivered to the input stream representing the audio from the speaker
     * (line)
     * @param outFormat the <tt>AudioFormat</tt> of the media which is to be
     * output by the <tt>IMediaObject</tt>/acoustic echo cancellation
     * @throws Exception if the initialization of the <tt>IMediaObject</tt>
     * implementing acoustic echo cancellation fails
     */
    private void initializeAECInFilterMode(
            long iMediaObject,
            AudioFormat inFormat0, AudioFormat inFormat1,
            AudioFormat outFormat)
        throws Exception
    {
        int dwInputStreamIndex = CAPTURE_INPUT_STREAM_INDEX;
        int hresult
            = IMediaObject_SetXXXputType(
                    iMediaObject,
                    /* IMediaObject_SetInputType */ true,
                    dwInputStreamIndex,
                    inFormat0,
                    /* dwFlags */ 0);

        if (FAILED(hresult))
        {
            throw new HResultException(
                    hresult,
                    "IMediaObject_SetInputType, dwInputStreamIndex "
                        + dwInputStreamIndex + ", " + inFormat0);
        }
        dwInputStreamIndex = RENDER_INPUT_STREAM_INDEX;
        hresult
            = IMediaObject_SetXXXputType(
                    iMediaObject,
                    /* IMediaObject_SetInputType */ true,
                    dwInputStreamIndex,
                    inFormat1,
                    /* dwFlags */ 0);
        if (FAILED(hresult))
        {
            throw new HResultException(
                    hresult,
                    "IMediaObject_SetInputType, dwInputStreamIndex "
                        + dwInputStreamIndex + ", " + inFormat1);
        }

        long captureIMediaBuffer
            = MediaBuffer_alloc(capture.bufferSize);

        if (captureIMediaBuffer == 0)
            throw new OutOfMemoryError("MediaBuffer_alloc");
        try
        {
            /*
             * We explicitly want to support the case in which the user has
             * selected "none" for the playback/render endpoint device.
             */
            long renderIMediaBuffer
                = MediaBuffer_alloc(
                        ((render == null) ? capture : render).bufferSize);

            if (renderIMediaBuffer == 0)
                throw new OutOfMemoryError("MediaBuffer_alloc");
            try
            {
                captureBufferMaxLength
                    = IMediaBuffer_GetMaxLength(captureIMediaBuffer);
                renderBufferMaxLength
                    = IMediaBuffer_GetMaxLength(renderIMediaBuffer);

                this.captureIMediaBuffer
                    = new PtrMediaBuffer(captureIMediaBuffer);
                captureIMediaBuffer = 0;
                this.renderIMediaBuffer
                    = new PtrMediaBuffer(renderIMediaBuffer);
                renderIMediaBuffer = 0;

                /*
                 * Prepare to be ready to compute/determine the duration in
                 * nanoseconds of a specific number of bytes representing audio
                 * samples encoded in the outFormat of capture.
                 */
                {
                    AudioFormat af = capture.outFormat;
                    double sampleRate = af.getSampleRate();
                    int sampleSizeInBits = af.getSampleSizeInBits();
                    int channels = af.getChannels();

                    captureNanosPerByte
                        = (8d * 1000d * 1000d * 1000d)
                            / (sampleRate * sampleSizeInBits * channels);
                }
                /*
                 * Prepare to be ready to compute/determine the number of bytes
                 * representing a specific duration in nanoseconds of audio
                 * samples encoded in the outFormat of render.
                 */
                {
                    /*
                     * We explicitly want to support the case in which the user
                     * has selected "none" for the playback/render endpoint
                     * device.
                     */
                    AudioFormat af
                        = ((render == null) ? capture : render).outFormat;
                    double sampleRate = af.getSampleRate();
                    int sampleSizeInBits = af.getSampleSizeInBits();
                    int channels = af.getChannels();

                    renderBytesPerNano
                        = (sampleRate * sampleSizeInBits * channels)
                            / (8d * 1000d * 1000d * 1000d);
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

    /**
     * Initializes the Voice Capture DSP which is to perform acoustic echo
     * cancellation. The method is invoked in case the Voice Capture DSP is to
     * be used in source mode.
     *
     * @param iPropertyStore the <tt>IPropertyStore</tt> interface to the Voice
     * Capture DSP to be initialized in source mode
     * @param captureDevice <tt>CaptureDeviceInfo2</tt> which identifies the
     * capture endpoint device to be used
     * @param renderDevice <tt>CaptureDeviceInfo2</tt> which identifies the
     * render endpoint device to be used
     * @param outFormat the <tt>AudioFormat</tt> of the media which is to be
     * output by the <tt>IMediaObject</tt>/acoustic echo cancellation
     * @throws Exception if the initialization of the <tt>IMediaObject</tt>
     * implementing acoustic echo cancellation fails
     */
    private void initializeAECInSourceMode(
            long iPropertyStore,
            CaptureDeviceInfo2 captureDevice,
            CaptureDeviceInfo2 renderDevice,
            AudioFormat outFormat)
        throws Exception
    {
        WASAPISystem audioSystem = dataSource.audioSystem;
        int captureDeviceIndex
            = audioSystem.getIMMDeviceIndex(
                    captureDevice.getLocator().getRemainder(),
                    eCapture);

        if (captureDeviceIndex == -1)
        {
            throw new IllegalStateException(
                    "Acoustic echo cancellation (AEC) cannot be initialized"
                        + " without a microphone.");
        }

        MediaLocator renderLocator = renderDevice.getLocator();
        int renderDeviceIndex
            = audioSystem.getIMMDeviceIndex(
                    renderLocator.getRemainder(),
                    eRender);

        if (renderDeviceIndex == -1)
        {
            throw new IllegalStateException(
                    "Acoustic echo cancellation (AEC) cannot be initialized"
                        + " without a speaker (line).");
        }

        int hresult
            = IPropertyStore_SetValue(
                    iPropertyStore,
                    MFPKEY_WMAAECMA_DEVICE_INDEXES,
                    ((0x0000ffff & captureDeviceIndex))
                        | ((0x0000ffff & renderDeviceIndex) << 16));

        if (FAILED(hresult))
        {
            throw new HResultException(
                    hresult,
                    "IPropertyStore_SetValue MFPKEY_WMAAECMA_DEVICE_INDEXES");
        }

        /*
         * If the selected rendering device does not have an active stream, the
         * DSP cannot process any output.
         */
        AbstractAudioRenderer<?> renderer = new WASAPIRenderer();

        renderer.setLocator(renderLocator);

        Format[] rendererSupportedInputFormats
            = renderer.getSupportedInputFormats();

        if ((rendererSupportedInputFormats != null)
                && (rendererSupportedInputFormats.length != 0))
        {
            renderer.setInputFormat(rendererSupportedInputFormats[0]);
        }
        renderer.open();

        devicePeriod = WASAPISystem.DEFAULT_DEVICE_PERIOD / 2;
        this.renderer = renderer;
    }

    /**
     * Initializes the delivery of audio data/samples from a capture endpoint
     * device identified by a specific <tt>MediaLocator</tt> into this instance.
     *
     * @param locator the <tt>MediaLocator</tt> identifying the capture endpoint
     * device from which this instance is to read
     * @param format the <tt>AudioFormat</tt> of the media to be read from the
     * specified capture endpoint device
     * @throws Exception if the initialization of the delivery of audio samples
     * from the specified capture endpoint into this instance fails
     */
    private void initializeCapture(
            MediaLocator locator,
            AudioFormat format)
        throws Exception
    {
        long hnsBufferDuration
            = aec
                ? Format.NOT_SPECIFIED
                : WASAPISystem.DEFAULT_BUFFER_DURATION;
        BufferTransferHandler transferHandler
            = new BufferTransferHandler()
                    {
                        public void transferData(PushBufferStream stream)
                        {
                            transferCaptureData();
                        }
                    };

        capture
            = new AudioCaptureClient(
                    dataSource.audioSystem,
                    locator,
                    AudioSystem.DataFlow.CAPTURE,
                    /* streamFlags */ 0,
                    hnsBufferDuration,
                    format,
                    transferHandler);
        bufferSize = capture.bufferSize;
        devicePeriod = capture.devicePeriod;
    }

    /**
     * Initializes the delivery of audio data/samples from a render endpoint
     * device identified by a specific <tt>MediaLocator</tt> into this instance
     * for the purposes of acoust echo cancellation (AEC).
     *
     * @param locator the <tt>MediaLocator</tt> identifying the render endpoint
     * device from which this instance is to read
     * @param format the <tt>AudioFormat</tt> of the media to be read from the
     * specified render endpoint device
     * @throws Exception if the initialization of the delivery of audio samples
     * from the specified render endpoint into this instance for the purposes of
     * acoustic echo cancellation (AEC) fails
     */
    private void initializeRender(MediaLocator locator, AudioFormat format)
        throws Exception
    {
        /*
         * XXX The method transferRenderData does not read any data from render
         * at this time. If the transferHandler (which will normally invoke
         * transferRenderData) was non-null, it would cause excessive CPU use.
         */
        BufferTransferHandler transferHandler
            = new BufferTransferHandler()
                    {
                        public void transferData(PushBufferStream stream)
                        {
                            transferRenderData();
                        }
                    };

        render
            = new AudioCaptureClient(
                    dataSource.audioSystem,
                    locator,
                    AudioSystem.DataFlow.PLAYBACK,
                    WASAPI.AUDCLNT_STREAMFLAGS_LOOPBACK,
                    WASAPISystem.DEFAULT_BUFFER_DURATION,
                    format,
                    transferHandler);
        replenishRender = true;
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

    /**
     * Inputs audio samples from {@link #capture} or {@link #render} and
     * delivers them to {@link #iMediaObject} which implements the acoustic echo
     * cancellation (AEC) feature.
     *
     * @param dwInputStreamIndex the zero-based index of the input stream on
     * <tt>iMediaObject</tt> to which audio samples are to be delivered
     * @param maxLength the maximum number of bytes to the delivered through the
     * specified input stream. Ignored if negative or greater than the actual
     * capacity/maximum length of the <tt>IMediaBuffer</tt> associated with the
     * specified <tt>dwInputStreamIndex</tt>.
     */
    private void processInput(int dwInputStreamIndex, int maxLength)
    {
        PtrMediaBuffer oBuffer;
        int bufferMaxLength;
        AudioCaptureClient audioCaptureClient;

        switch (dwInputStreamIndex)
        {
        case CAPTURE_INPUT_STREAM_INDEX:
            oBuffer = captureIMediaBuffer;
            bufferMaxLength = captureBufferMaxLength;
            audioCaptureClient = capture;
            break;
        case RENDER_INPUT_STREAM_INDEX:
            oBuffer = renderIMediaBuffer;
            bufferMaxLength = renderBufferMaxLength;
            audioCaptureClient = render;
            break;
        default:
            throw new IllegalArgumentException("dwInputStreamIndex");
        }
        if ((maxLength < 0) || (maxLength > bufferMaxLength))
            maxLength = bufferMaxLength;

        long pBuffer = oBuffer.ptr;
        int hresult = S_OK;

        do
        {
            /*
             * Attempt to deliver audio samples to the specified input stream
             * only if it accepts input data at this time.
             */
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
                /*
                 * The specified input stream reports that it accepts input data
                 * at this time so read audio samples from the associated
                 * AudioCaptureClient and then deliver them to the specified
                 * input stream.
                 */
                int toRead = Format.NOT_SPECIFIED;

                if (dwInputStreamIndex == RENDER_INPUT_STREAM_INDEX)
                {
                    /*
                     * We explicitly want to support the case in which the user
                     * has selected "none" for the playback/render endpoint
                     * device.
                     */
                    if (audioCaptureClient == null)
                        toRead = 0;
                    else if (replenishRender)
                    {
                        int replenishThreshold
                            = (3 * renderBufferMaxLength / 2);

                        if (audioCaptureClient.getAvailableLength()
                                < replenishThreshold)
                            toRead = 0;
                        else
                            replenishRender = false;
                    }
                }
                if (toRead == Format.NOT_SPECIFIED)
                {
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
                }
                if (toRead > 0)
                {
                    /*
                     * Read audio samples from the associated
                     * AudioCaptureClient.
                     */
                    try
                    {
                        int read = audioCaptureClient.read(oBuffer, toRead);

                        if ((dwInputStreamIndex == RENDER_INPUT_STREAM_INDEX)
                                && (read == 0))
                            replenishRender = true;
                    }
                    catch (IOException ioe)
                    {
                        logger.error(
                                "Failed to read from IAudioCaptureClient.",
                                ioe);
                    }
                }

                /*
                 * If the capture endpoint device has delivered audio samples,
                 * they have to go through the acoustic echo cancellation (AEC)
                 * regardless of whether the render endpoint device has
                 * delivered audio samples. Additionally, the duration of the
                 * audio samples delivered by the render has to be the same as
                 * the duration of the audio samples delivered by the capture in
                 * order to have the audio samples delivered by the capture pass
                 * through the voice capture DSO in entirety. To achieve the
                 * above, read from the render endpoint device as many audio
                 * samples as possible and pad with silence if necessary.
                 */
                if (dwInputStreamIndex == RENDER_INPUT_STREAM_INDEX)
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
                        maybeMediaBufferPush(
                                pBuffer,
                                processInputBuffer, 0, silence);
                    }
                }

                /*
                 * Deliver the audio samples read from the associated
                 * AudioCaptureClient to the specified input stream.
                 */
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
                break; // XXX We risk a busy wait unless we break here.
            }
            else
                break; // The input stream cannot accept more input data.
        }
        while (SUCCEEDED(hresult));
    }

    /**
     * Invokes <tt>IMediaObject::ProcessOutput</tt> on {@link #iMediaObject}
     * that represents the Voice Capture DSP implementing the acoustic echo
     * cancellation (AEC) feature.
     */
    private void processOutput()
    {
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
                dwStatus
                    = DMO_OUTPUT_DATA_BUFFER_getDwStatus(dmoOutputDataBuffer);
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
    }

    /**
     * {@inheritDoc}
     */
    public void read(Buffer buffer)
        throws IOException
    {
        // Reduce relocations as much as possible.
        int capacity = aec ? bufferMaxLength : bufferSize;
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
                /*
                 * We explicitly want to support the case in which the user has
                 * selected "none" for the playback/render endpoint device.
                 * Otherwise, we could have added a check
                 * (dataSource.aec && (render == null)). 
                 */
                boolean connected = (capture != null) || sourceMode;

                if (connected)
                {
                    message = null;
                    captureIsBusy = true;
                    renderIsBusy = true;
                }
                else
                    message = getClass().getName() + " is disconnected.";
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

                /*
                 * We explicitly want to support the case in which the user has
                 * selected "none" for the playback/render endpoint device.
                 * Otherwise, we could have used a check (render == null).
                 */
                boolean aec = (iMediaObject != 0);

                if (aec)
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
                else
                    read = capture.read(data, length, toRead);
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
                else
                {
                    /*
                     * TODO The implementation of PushBufferStream.read(Buffer)
                     * should not block, it should return with whatever is
                     * available.
                     */
                    yield();
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
     * Executed by {@link #processThread} and invoked by
     * {@link #runInProcessThread(Thread)}, inputs audio samples from
     * {@link #capture} and {@link #render}, delivers them to
     * {@link #iMediaBuffer} which implements the acoustic echo cancellation
     * (AEC) features and produces output and caches the output so that it can
     * be read out of this instance via {@link #read(Buffer)}.
     *
     * @return a <tt>BufferTransferHandler</tt> to be invoked if the method has
     * made available audio samples to be read out of this instance; otherwise,
     * <tt>null</tt>
     */
    private BufferTransferHandler runInProcessThread()
    {
        int captureMaxLength = this.captureBufferMaxLength;

        do
        {
            boolean flush;

            if (sourceMode)
            {
                flush = false;
            }
            else
            {
                processInput(CAPTURE_INPUT_STREAM_INDEX, captureMaxLength);

                /*
                 * If the capture endpoint device has not made any audio samples
                 * available, there is no input to be processed. Moreover,
                 * inputting from the render endpoint device in such a case will
                 * be inappropriate because it will (repeatedly) introduce
                 * random skew in the audio delivered by the render endpoint
                 * device.
                 */
                int captureLength
                    = maybeIMediaBufferGetLength(captureIMediaBuffer);

                if (captureLength < captureMaxLength)
                    flush = false;
                else
                {
                    int renderMaxLength
                        = computeRenderLength(
                                computeCaptureDuration(captureLength));

                    processInput(RENDER_INPUT_STREAM_INDEX, renderMaxLength);
                    flush = true;
                }
            }

            processOutput();

            /*
             * IMediaObject::ProcessOutput has completed which means that, as
             * far as it is concerned, it does not have any input data to
             * process. Make sure that the states of the IMediaBuffer instances
             * are in accord.
             */
            try
            {
                /*
                 * XXX Make sure that the IMediaObject releases any IMediaBuffer
                 * references it holds.
                 */
                if (SUCCEEDED(IMediaObject_Flush(iMediaObject)) && flush)
                {
                    captureIMediaBuffer.SetLength(0);
                    renderIMediaBuffer.SetLength(0);
                }
            }
            catch (HResultException hre)
            {
                logger.error("IMediaBuffer_Flush", hre);
            }
            catch (IOException ioe)
            {
                logger.error("IMediaBuffer.SetLength", ioe);
            }

            if (!flush)
            {
                BufferTransferHandler transferHandler = this.transferHandler;

                if ((transferHandler != null)
                        && (processedLength >= bufferMaxLength))
                    return transferHandler;
                else
                    break;
            }
        }
        while (true);

        return null;
    }

    /**
     * Executed by {@link #processThread}, inputs audio samples from
     * {@link #capture} and {@link #render}, delivers them to
     * {@link #iMediaBuffer} which implements the acoustic echo cancellation
     * (AEC) features and produces output and caches the output so that it can
     * be read out of this instance via {@link #read(Buffer)}.
     *
     * @param processThread the <tt>Thread</tt> which is executing the method
     */
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
                    /*
                     * We explicitly want to support the case in which the user
                     * has selected "none" for the playback/render endpoint
                     * device. Otherwise, we could have added a check
                     * (render == null).
                     */
                    boolean connected = (capture != null) || sourceMode;

                    if (!connected || !started)
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
        /*
         * We explicitly want to support the case in which the user has selected
         * "none" for the playback/render endpoint device. Otherwise, we could
         * have replaced the dataSource.aec check with (render != null).
         */
        if (aec
                && ((capture != null) || sourceMode)
                && (processThread == null))
        {
            /*
             * If the selected rendering device does not have an active stream,
             * the DSP cannot process any output in source mode.
             */
            if (renderer != null)
                renderer.start();

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
            replenishRender = true;
        }
        started = false;

        waitWhileProcessThread();
        processedLength = 0;
        /*
         * If the selected rendering device does not have an active stream, the
         * DSP cannot process any output in source mode.
         */
        if (renderer != null)
            renderer.stop();
    }

    /**
     * Notifies this instance that audio data has been made available in
     * {@link #capture}.
     */
    private void transferCaptureData()
    {
        if (aec)
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

    /**
     * Notifies this instance that audio data has been made available in
     * {@link #render}.
     */
    private void transferRenderData()
    {
        /*
         * This is a CaptureDevice and its goal is to push the audio samples
         * delivered by the capture endpoint device out in their entirety. When
         * the render endpoint device pushes and whether it pushes frequently
         * and sufficiently enough to stay in sync with the capture endpoint
         * device for the purposes of the acoustic echo cancellation (AEC) is a
         * separate question.
         */
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
        if (renderIMediaBuffer != null)
        {
            renderIMediaBuffer.Release();
            renderIMediaBuffer = null;
        }
        if (captureIMediaBuffer != null)
        {
            captureIMediaBuffer.Release();
            captureIMediaBuffer = null;
        }

        Renderer renderer = this.renderer;

        this.renderer = null;
        if (renderer != null)
            renderer.close();
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

    /**
     * Waits on this instance while the value of {@link #precessThread} is not
     * equal to <tt>null</tt>.
     */
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
