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
package org.jitsi.impl.neomedia.conference;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import javax.media.*;
import javax.media.Controls;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.util.*;
import org.jitsi.utils.logging.*;

/**
 * Represents an audio mixer which manages the mixing of multiple audio streams
 * i.e. it is able to output a single audio stream which contains the audio of
 * multiple input audio streams.
 * <p>
 * The input audio streams are provided to the <tt>AudioMixer</tt> through
 * {@link #addInDataSource(DataSource)} in the form of input
 * <tt>DataSource</tt>s giving access to one or more input
 * <tt>SourceStreams</tt>.
 * </p>
 * <p>
 * The output audio stream representing the mix of the multiple input audio
 * streams is provided by the <tt>AudioMixer</tt> in the form of a
 * <tt>AudioMixingPushBufferDataSource</tt> giving access to a
 * <tt>AudioMixingPushBufferStream</tt>. Such an output is obtained through
 * {@link #createOutDataSource()}. The <tt>AudioMixer</tt> is able to provide
 * multiple output audio streams at one and the same time, though, each of them
 * containing the mix of a subset of the input audio streams.
 * </p>
 *
 * @author Lyubomir Marinov
 */
public class AudioMixer
{

    /**
     * The default output <tt>AudioFormat</tt> in which <tt>AudioMixer</tt>,
     * <tt>AudioMixingPushBufferDataSource</tt> and
     * <tt>AudioMixingPushBufferStream</tt> output audio.
     */
    private static final AudioFormat DEFAULT_OUTPUT_FORMAT
        = new AudioFormat(
                AudioFormat.LINEAR,
                8000,
                16,
                1,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED);

    /**
     * The <tt>Logger</tt> used by the <tt>AudioMixer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(AudioMixer.class);

    /**
     * Gets the <tt>Format</tt> in which a specific <tt>DataSource</tt>
     * provides stream data.
     *
     * @param dataSource the <tt>DataSource</tt> for which the <tt>Format</tt>
     * in which it provides stream data is to be determined
     * @return the <tt>Format</tt> in which the specified <tt>dataSource</tt>
     * provides stream data if it was determined; otherwise, <tt>null</tt>
     */
    private static Format getFormat(DataSource dataSource)
    {
        FormatControl formatControl
            = (FormatControl)
                dataSource.getControl(FormatControl.class.getName());

        return (formatControl == null) ? null : formatControl.getFormat();
    }

    /**
     * Gets the <tt>Format</tt> in which a specific <tt>SourceStream</tt>
     * provides data.
     *
     * @param stream the <tt>SourceStream</tt> for which the <tt>Format</tt> in
     * which it provides data is to be determined
     * @return the <tt>Format</tt> in which the specified <tt>SourceStream</tt>
     * provides data if it was determined; otherwise, <tt>null</tt>
     */
    private static Format getFormat(SourceStream stream)
    {
        if (stream instanceof PushBufferStream)
            return ((PushBufferStream) stream).getFormat();
        if (stream instanceof PullBufferStream)
            return ((PullBufferStream) stream).getFormat();
        return null;
    }

    /**
     * The <tt>BufferControl</tt> of this instance and, respectively, its
     * <tt>AudioMixingPushBufferDataSource</tt>s.
     */
    private BufferControl bufferControl;

    /**
     * The <tt>CaptureDevice</tt> capabilities provided by the
     * <tt>AudioMixingPushBufferDataSource</tt>s created by this
     * <tt>AudioMixer</tt>. JMF's
     * <tt>Manager.createMergingDataSource(DataSource[])</tt> requires the
     * interface implementation for audio if it is implemented for video and it
     * is indeed the case for our use case of
     * <tt>AudioMixingPushBufferDataSource</tt>.
     */
    protected final CaptureDevice captureDevice;

    /**
     * The number of output <tt>AudioMixingPushBufferDataSource</tt>s reading
     * from this <tt>AudioMixer</tt> which are connected. When the value is
     * greater than zero, this <tt>AudioMixer</tt> is connected to the input
     * <tt>DataSource</tt>s it manages.
     */
    private int connected;

    /**
     * The collection of input <tt>DataSource</tt>s this instance reads audio
     * data from.
     */
    private final List<InDataSourceDesc> inDataSources
        = new ArrayList<InDataSourceDesc>();

    /**
     * The <tt>AudioMixingPushBufferDataSource</tt> which contains the mix of
     * <tt>inDataSources</tt> excluding <tt>captureDevice</tt> and is thus
     * meant for playback on the local peer in a call.
     */
    private final AudioMixingPushBufferDataSource localOutDataSource;

    /**
     * The output <tt>AudioMixerPushBufferStream</tt> through which this
     * instance pushes audio sample data to
     * <tt>AudioMixingPushBufferStream</tt>s to be mixed.
     */
    private AudioMixerPushBufferStream outStream;

    /**
     * The number of output <tt>AudioMixingPushBufferDataSource</tt>s reading
     * from this <tt>AudioMixer</tt> which are started. When the value is
     * greater than zero, this <tt>AudioMixer</tt> is started and so are the
     * input <tt>DataSource</tt>s it manages.
     */
    private int started;

    /**
     * The greatest generation with which
     * {@link #start(AudioMixerPushBufferStream, long)} or
     * {@link #stop(AudioMixerPushBufferStream, long)} has been invoked. 
     */
    private long startedGeneration;

    /**
     * Initializes a new <tt>AudioMixer</tt> instance. Because JMF's
     * <tt>Manager.createMergingDataSource(DataSource[])</tt> requires the
     * implementation of <tt>CaptureDevice</tt> for audio if it is implemented
     * for video and it is indeed the cause for our use case of
     * <tt>AudioMixingPushBufferDataSource</tt>, the new <tt>AudioMixer</tt>
     * instance provides specified <tt>CaptureDevice</tt> capabilities to the
     * <tt>AudioMixingPushBufferDataSource</tt>s it creates. The specified
     * <tt>CaptureDevice</tt> is also added as the first input
     * <tt>DataSource</tt> of the new instance.
     *
     * @param captureDevice the <tt>CaptureDevice</tt> capabilities to be
     * provided to the <tt>AudioMixingPushBufferDataSource</tt>s created by the
     * new instance and its first input <tt>DataSource</tt>
     */
    public AudioMixer(CaptureDevice captureDevice)
    {
        /*
         * AudioMixer provides PushBufferDataSources so it needs a way to push
         * them. It does the pushing by using the pushes of its CaptureDevice
         * i.e. it has to be a PushBufferDataSource.
         */
        if (captureDevice instanceof PullBufferDataSource)
        {
            captureDevice
                = new PushBufferDataSourceAdapter(
                        (PullBufferDataSource) captureDevice);
        }

        // Try to enable tracing on captureDevice.
        if (logger.isTraceEnabled())
        {
            captureDevice
                = MediaDeviceImpl.createTracingCaptureDevice(
                        captureDevice,
                        logger);
        }

        this.captureDevice = captureDevice;

        this.localOutDataSource = createOutDataSource();
        addInDataSource(
                (DataSource) this.captureDevice,
                this.localOutDataSource);
    }

    /**
     * Adds a new input <tt>DataSource</tt> to the collection of input
     * <tt>DataSource</tt>s from which this instance reads audio. If the
     * specified <tt>DataSource</tt> indeed provides audio, the respective
     * contributions to the mix are always included.
     *
     * @param inDataSource a new <tt>DataSource</tt> to input audio to this
     * instance
     */
    public void addInDataSource(DataSource inDataSource)
    {
        addInDataSource(inDataSource, null);
    }

    /**
     * Adds a new input <tt>DataSource</tt> to the collection of input
     * <tt>DataSource</tt>s from which this instance reads audio. If the
     * specified <tt>DataSource</tt> indeed provides audio, the respective
     * contributions to the mix will be excluded from the mix output provided
     * through a specific <tt>AudioMixingPushBufferDataSource</tt>.
     *
     * @param inDataSource a new <tt>DataSource</tt> to input audio to this
     * instance
     * @param outDataSource the <tt>AudioMixingPushBufferDataSource</tt> to
     * not include the audio contributions of <tt>inDataSource</tt> in the
     * mix it outputs
     */
    void addInDataSource(
            DataSource inDataSource,
            AudioMixingPushBufferDataSource outDataSource)
    {
        if (inDataSource == null)
            throw new NullPointerException("inDataSource");

        synchronized (inDataSources)
        {
            for (InDataSourceDesc inDataSourceDesc : inDataSources)
                if (inDataSource.equals(inDataSourceDesc.inDataSource))
                    throw new IllegalArgumentException("inDataSource");

            InDataSourceDesc inDataSourceDesc
                = new InDataSourceDesc(
                        inDataSource,
                        outDataSource);
            boolean added = inDataSources.add(inDataSourceDesc);

            if (added)
            {
                if (logger.isTraceEnabled())
                {
                    logger.trace(
                            "Added input DataSource with hashCode "
                                + inDataSource.hashCode());
                }

                /*
                 * If the other inDataSources have already been connected,
                 * connect to the new one as well.
                 */
                if (connected > 0)
                {
                    try
                    {
                        inDataSourceDesc.connect(this);
                    }
                    catch (IOException ioex)
                    {
                        throw new UndeclaredThrowableException(ioex);
                    }
                }

                // Update outStream with any new inStreams.
                if (outStream != null)
                    getOutStream();

                /*
                 * If the other inDataSources have been started, start the
                 * new one as well.
                 */
                if (started > 0)
                {
                    try
                    {
                        inDataSourceDesc.start();
                    }
                    catch (IOException ioe)
                    {
                        throw new UndeclaredThrowableException(ioe);
                    }
                }
            }
        }
    }

    /**
     * Notifies this <tt>AudioMixer</tt> that an output
     * <tt>AudioMixingPushBufferDataSource</tt> reading from it has been
     * connected. The first of the many
     * <tt>AudioMixingPushBufferDataSource</tt>s reading from this
     * <tt>AudioMixer</tt> which gets connected causes it to connect to the
     * input <tt>DataSource</tt>s it manages.
     *
     * @throws IOException if input/output error occurred
     */
    void connect()
        throws IOException
    {
        synchronized (inDataSources)
        {
            if (connected == 0)
            {
                for (InDataSourceDesc inDataSourceDesc : inDataSources)
                    try
                    {
                        inDataSourceDesc.connect(this);
                    }
                    catch (IOException ioe)
                    {
                        logger.error(
                                "Failed to connect to inDataSource "
                                    + MediaStreamImpl.toString(
                                            inDataSourceDesc.inDataSource),
                                ioe);
                        throw ioe;
                    }

                /*
                 * Since the media of the input streams is to be mixed, their
                 * bufferLengths have to be equal. After a DataSource is
                 * connected, its BufferControl is available and its
                 * bufferLength may change so make sure that the bufferLengths
                 * of the input streams are equal.
                 */
                if (outStream != null)
                    outStream.equalizeInStreamBufferLength();
            }

            connected++;
        }
    }

    /**
     * Connects to a specific <tt>DataSource</tt> which this <tt>AudioMixer<tt>
     * will read audio from. The specified <tt>DataSource</tt> is known to exist
     * because of a specific <tt>DataSource</tt> added as an input to this
     * instance i.e. it may be an actual input <tt>DataSource</tt> added to this
     * instance or a <tt>DataSource</tt> transcoding an input
     * <tt>DataSource</tt> added to this instance.
     *
     * @param dataSource the <tt>DataSource</tt> to connect to
     * @param inDataSource the <tt>DataSource</tt> which is the cause for
     * <tt>dataSource</tt> to exist in this <tt>AudioMixer</tt>
     * @throws IOException if anything wrong happens while connecting to
     * <tt>dataSource</tt>
     */
    protected void connect(DataSource dataSource, DataSource inDataSource)
        throws IOException
    {
        dataSource.connect();
    }

    /**
     * Notifies this <tt>AudioMixer</tt> that a specific input
     * <tt>DataSource</tt> has finished its connecting procedure. Primarily
     * meant for input <tt>DataSource</tt> which have their connecting executed
     * in a separate thread as are, for example, input <tt>DataSource</tt>s
     * which are being transcoded.
     *
     * @param inDataSource the <tt>InDataSourceDesc</tt> of the input
     * <tt>DataSource</tt> which has finished its connecting procedure
     * @throws IOException if anything wrong happens while including
     * <tt>inDataSource</tt> into the mix
     */
    void connected(InDataSourceDesc inDataSource)
        throws IOException
    {
        synchronized (inDataSources)
        {
            if (inDataSources.contains(inDataSource)
                    && (connected > 0))
            {
                if (started > 0)
                    inDataSource.start();
                if (outStream != null)
                    getOutStream();
            }
        }
    }

    /**
     * Creates a new <tt>InStreamDesc</tt> instance which is to describe a
     * specific input <tt>SourceStream</tt> originating from a specific input
     * <tt>DataSource</tt> given by its <tt>InDataSourceDesc</tt>.
     *
     * @param inStream the input <tt>SourceStream</tt> to be described by the
     * new instance
     * @param inDataSourceDesc the input <tt>DataSource</tt> given by its
     * <tt>InDataSourceDesc</tt> to be described by the new instance
     * @return a new <tt>InStreamDesc</tt> instance which describes the
     * specified input <tt>SourceStream</tt> and <tt>DataSource</tt>
     */
    private InStreamDesc createInStreamDesc(
            SourceStream inStream,
            InDataSourceDesc inDataSourceDesc)
    {
        return new InStreamDesc(inStream, inDataSourceDesc);
    }

    /**
     * Creates a new <tt>AudioMixingPushBufferDataSource</tt> which gives
     * access to a single audio stream representing the mix of the audio streams
     * input into this <tt>AudioMixer</tt> through its input
     * <tt>DataSource</tt>s. The returned
     * <tt>AudioMixingPushBufferDataSource</tt> can also be used to include
     * new input <tt>DataSources</tt> in this <tt>AudioMixer</tt> but
     * have their contributions not included in the mix available through the
     * returned <tt>AudioMixingPushBufferDataSource</tt>.
     *
     * @return a new <tt>AudioMixingPushBufferDataSource</tt> which gives access
     * to a single audio stream representing the mix of the audio streams input
     * into this <tt>AudioMixer</tt> through its input <tt>DataSource</tt>s
     */
    public AudioMixingPushBufferDataSource createOutDataSource()
    {
        return new AudioMixingPushBufferDataSource(this);
    }

    /**
     * Creates a <tt>DataSource</tt> which attempts to transcode the tracks of a
     * specific input <tt>DataSource</tt> into a specific output
     * <tt>Format</tt>.
     *
     * @param inDataSourceDesc the <tt>InDataSourceDesc</tt> describing
     * the input <tt>DataSource</tt> to be transcoded into the specified output
     * <tt>Format</tt> and to receive the transcoding <tt>DataSource</tt>
     * @param outFormat the <tt>Format</tt> in which the tracks of the input
     * <tt>DataSource</tt> are to be transcoded
     * @return <tt>true</tt> if a new transcoding <tt>DataSource</tt> has been
     * created for the input <tt>DataSource</tt> described by
     * <tt>inDataSourceDesc</tt>; otherwise, <tt>false</tt>
     * @throws IOException if an error occurs while creating the transcoding
     * <tt>DataSource</tt>, connecting to it or staring it
     */
    private boolean createTranscodingDataSource(
            InDataSourceDesc inDataSourceDesc,
            Format outFormat)
        throws IOException
    {
        if (inDataSourceDesc.createTranscodingDataSource(outFormat))
        {
            if (connected > 0)
                inDataSourceDesc.connect(this);
            if (started > 0)
                inDataSourceDesc.start();
            return true;
        }
        else
            return false;
    }

    /**
     * Notifies this <tt>AudioMixer</tt> that an output
     * <tt>AudioMixingPushBufferDataSource</tt> reading from it has been
     * disconnected. The last of the many
     * <tt>AudioMixingPushBufferDataSource</tt>s reading from this
     * <tt>AudioMixer</tt> which gets disconnected causes it to disconnect
     * from the input <tt>DataSource</tt>s it manages.
     */
    void disconnect()
    {
        synchronized (inDataSources)
        {
            if (connected <= 0)
                return;

            connected--;

            if (connected == 0)
            {
                for (InDataSourceDesc inDataSourceDesc : inDataSources)
                    inDataSourceDesc.disconnect();

                /*
                 * XXX Make the outStream to release the inStreams.
                 * Otherwise, the PushBufferStream ones which have been wrapped
                 * into CachingPushBufferStream may remain waiting.
                 */
                outStream.setInStreams(null);
                outStream = null;
                startedGeneration = 0;
            }
        }
    }

    /**
     * Gets the <tt>BufferControl</tt> of this instance and, respectively, its
     * <tt>AudioMixingPushBufferDataSource</tt>s.
     *
     * @return the <tt>BufferControl</tt> of this instance and, respectively,
     * its <tt>AudioMixingPushBufferDataSource</tt>s if such a control is
     * available for the <tt>CaptureDevice</tt> of this instance; otherwise,
     * <tt>null</tt>
     */
    BufferControl getBufferControl()
    {
        if ((bufferControl == null) && (captureDevice instanceof Controls))
        {
            BufferControl captureDeviceBufferControl
                = (BufferControl)
                    ((Controls) captureDevice).getControl(
                            BufferControl.class.getName());

            if (captureDeviceBufferControl != null)
                bufferControl
                    = new ReadOnlyBufferControlDelegate(
                            captureDeviceBufferControl);
        }
        return bufferControl;
    }

    /**
     * Gets the <tt>CaptureDeviceInfo</tt> of the <tt>CaptureDevice</tt>
     * this <tt>AudioMixer</tt> provides through its output
     * <tt>AudioMixingPushBufferDataSource</tt>s.
     *
     * @return the <tt>CaptureDeviceInfo</tt> of the <tt>CaptureDevice</tt> this
     * <tt>AudioMixer</tt> provides through its output
     * <tt>AudioMixingPushBufferDataSource</tt>s
     */
    CaptureDeviceInfo getCaptureDeviceInfo()
    {
        return captureDevice.getCaptureDeviceInfo();
    }

    /**
     * Gets the content type of the data output by this <tt>AudioMixer</tt>.
     *
     * @return the content type of the data output by this <tt>AudioMixer</tt>
     */
    String getContentType()
    {
        return ContentDescriptor.RAW;
    }

    /**
     * Gets the duration of each one of the output streams produced by this
     * <tt>AudioMixer</tt>.
     *
     * @return the duration of each one of the output streams produced by this
     * <tt>AudioMixer</tt>
     */
    Time getDuration()
    {
        return ((DataSource) captureDevice).getDuration();
    }

    /**
     * Gets an <tt>InStreamDesc</tt> from a specific existing list of
     * <tt>InStreamDesc</tt>s which describes a specific
     * <tt>SourceStream</tt>. If such an <tt>InStreamDesc</tt> does not
     * exist, returns <tt>null</tt>.
     *
     * @param inStream the <tt>SourceStream</tt> to locate an
     * <tt>InStreamDesc</tt> for in <tt>existingInStreamDescs</tt>
     * @param existingInStreamDescs the list of existing
     * <tt>InStreamDesc</tt>s in which an <tt>InStreamDesc</tt> for
     * <tt>inStream</tt> is to be located
     * @return an <tt>InStreamDesc</tt> from
     * <tt>existingInStreamDescs</tt> which describes <tt>inStream</tt> if
     * such an <tt>InStreamDesc</tt> exists; otherwise, <tt>null</tt>
     */
    private InStreamDesc getExistingInStreamDesc(
            SourceStream inStream,
            InStreamDesc[] existingInStreamDescs)
    {
        if (existingInStreamDescs == null)
            return null;

        for (InStreamDesc existingInStreamDesc
                : existingInStreamDescs)
        {
            SourceStream existingInStream
                = existingInStreamDesc.getInStream();

            if (existingInStream == inStream)
                return existingInStreamDesc;
            if ((existingInStream instanceof BufferStreamAdapter<?>)
                    && (((BufferStreamAdapter<?>) existingInStream).getStream()
                            == inStream))
                return existingInStreamDesc;
            if ((existingInStream instanceof CachingPushBufferStream)
                    && (((CachingPushBufferStream) existingInStream).getStream()
                            == inStream))
                return existingInStreamDesc;
        }
        return null;
    }

    /**
     * Gets an array of <tt>FormatControl</tt>s for the
     * <tt>CaptureDevice</tt> this <tt>AudioMixer</tt> provides through
     * its output <tt>AudioMixingPushBufferDataSource</tt>s.
     *
     * @return an array of <tt>FormatControl</tt>s for the
     *         <tt>CaptureDevice</tt> this <tt>AudioMixer</tt> provides
     *         through its output <tt>AudioMixingPushBufferDataSource</tt>s
     */
    FormatControl[] getFormatControls()
    {
        /*
         * Setting the format of the captureDevice once we've started using it
         * is likely to wreak havoc so disable it.
         */
        FormatControl[] formatControls = captureDevice.getFormatControls();

        if (!OSUtils.IS_ANDROID && (formatControls != null))
        {
            for (int i = 0; i < formatControls.length; i++)
            {
                formatControls[i]
                    = new ReadOnlyFormatControlDelegate(formatControls[i]);
            }
        }
        return formatControls;
    }

    /**
     * Gets the <tt>SourceStream</tt>s (in the form of <tt>InStreamDesc</tt>)
     * of a specific <tt>DataSource</tt> (provided in the form of
     * <tt>InDataSourceDesc</tt>) which produce data in a specific
     * <tt>AudioFormat</tt> (or a matching one).
     *
     * @param inDataSourceDesc the <tt>DataSource</tt> (in the form of
     * <tt>InDataSourceDesc</tt>) which is to be examined for
     * <tt>SourceStreams</tt> producing data in the specified
     * <tt>AudioFormat</tt>
     * @param outFormat the <tt>AudioFormat</tt> in which the collected
     * <tt>SourceStream</tt>s are to produce data
     * @param existingInStreams the <tt>InStreamDesc</tt> instances which
     * already exist and which are used to avoid creating multiple
     * <tt>InStreamDesc</tt>s for input <tt>SourceStream</tt>s which already
     * have ones
     * @param inStreams the <tt>List</tt> of <tt>InStreamDesc</tt> in
     * which the discovered <tt>SourceStream</tt>s are to be returned
     * @return <tt>true</tt> if <tt>SourceStream</tt>s produced by the specified
     * input <tt>DataSource</tt> and outputting data in the specified
     * <tt>AudioFormat</tt> were discovered and reported in
     * <tt>inStreams</tt>; otherwise, <tt>false</tt>
     */
    private boolean getInStreamsFromInDataSource(
            InDataSourceDesc inDataSourceDesc,
            AudioFormat outFormat,
            InStreamDesc[] existingInStreams,
            List<InStreamDesc> inStreams)
    {
        SourceStream[] inDataSourceStreams = inDataSourceDesc.getStreams();

        if (inDataSourceStreams != null)
        {
            boolean added = false;

            for (SourceStream inStream : inDataSourceStreams)
            {
                Format inFormat = getFormat(inStream);

                if ((inFormat != null) && matches(inFormat, outFormat))
                {
                    InStreamDesc inStreamDesc
                        = getExistingInStreamDesc(inStream, existingInStreams);

                    if (inStreamDesc == null)
                        inStreamDesc
                            = createInStreamDesc(inStream, inDataSourceDesc);
                    if (inStreams.add(inStreamDesc))
                        added = true;
                }
            }
            return added;
        }

        DataSource inDataSource = inDataSourceDesc.getEffectiveInDataSource();

        if (inDataSource == null)
            return false;

        Format inFormat = getFormat(inDataSource);

        if ((inFormat != null) && !matches(inFormat, outFormat))
        {
            if (inDataSource instanceof PushDataSource)
            {
                for (PushSourceStream inStream
                        : ((PushDataSource) inDataSource).getStreams())
                {
                    InStreamDesc inStreamDesc
                        = getExistingInStreamDesc(inStream, existingInStreams);

                    if (inStreamDesc == null)
                        inStreamDesc
                            = createInStreamDesc(
                                    new PushBufferStreamAdapter(
                                            inStream,
                                            inFormat),
                                    inDataSourceDesc);
                    inStreams.add(inStreamDesc);
                }
                return true;
            }
            if (inDataSource instanceof PullDataSource)
            {
                for (PullSourceStream inStream
                        : ((PullDataSource) inDataSource).getStreams())
                {
                    InStreamDesc inStreamDesc
                        = getExistingInStreamDesc(inStream, existingInStreams);

                    if (inStreamDesc == null)
                        inStreamDesc
                            = createInStreamDesc(
                                    new PullBufferStreamAdapter(
                                            inStream,
                                            inFormat),
                                    inDataSourceDesc);
                    inStreams.add(inStreamDesc);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the <tt>SourceStream</tt>s (in the form of <tt>InStreamDesc</tt>)
     * of the <tt>DataSource</tt>s from which this <tt>AudioMixer</tt> reads
     * data which produce data in a specific <tt>AudioFormat</tt>. When an input
     * <tt>DataSource</tt> does not have such <tt>SourceStream</tt>s, an attempt
     * is made to transcode its tracks so that such <tt>SourceStream</tt>s can
     * be retrieved from it after transcoding.
     *
     * @param outFormat the <tt>AudioFormat</tt> in which the retrieved
     * <tt>SourceStream</tt>s are to produce data
     * @param existingInStreams the <tt>SourceStream</tt>s which are already
     * known to this <tt>AudioMixer</tt>
     * @return a new collection of <tt>SourceStream</tt>s (in the form of
     * <tt>InStreamDesc</tt>) retrieved from the input <tt>DataSource</tt>s
     * of this <tt>AudioMixer</tt> and producing data in the specified
     * <tt>AudioFormat</tt>
     * @throws IOException if anything wrong goes while retrieving the input
     * <tt>SourceStream</tt>s from the input <tt>DataSource</tt>s
     */
    private Collection<InStreamDesc> getInStreamsFromInDataSources(
            AudioFormat outFormat,
            InStreamDesc[] existingInStreams)
        throws IOException
    {
        List<InStreamDesc> inStreams = new ArrayList<InStreamDesc>();

        synchronized (inDataSources)
        {
            for (InDataSourceDesc inDataSourceDesc : inDataSources)
            {
                boolean got
                    = getInStreamsFromInDataSource(
                            inDataSourceDesc,
                            outFormat,
                            existingInStreams,
                            inStreams);

                if (!got
                        && createTranscodingDataSource(
                                inDataSourceDesc,
                                outFormat))
                    getInStreamsFromInDataSource(
                        inDataSourceDesc,
                        outFormat,
                        existingInStreams,
                        inStreams);
            }
        }
        return inStreams;
    }

    /**
     * Gets the <tt>AudioMixingPushBufferDataSource</tt> containing the mix of
     * all input <tt>DataSource</tt>s excluding the <tt>CaptureDevice</tt> of
     * this <tt>AudioMixer</tt> and is thus meant for playback on the local peer
     * in a call.
     *
     * @return the <tt>AudioMixingPushBufferDataSource</tt> containing the mix
     * of all input <tt>DataSource</tt>s excluding the <tt>CaptureDevice</tt> of
     * this <tt>AudioMixer</tt> and is thus meant for playback on the local peer
     * in a call
     */
    public AudioMixingPushBufferDataSource getLocalOutDataSource()
    {
        return localOutDataSource;
    }

    /**
     * Gets the <tt>AudioFormat</tt> in which the input
     * <tt>DataSource</tt>s of this <tt>AudioMixer</tt> can produce data
     * and which is to be the output <tt>Format</tt> of this
     * <tt>AudioMixer</tt>.
     *
     * @return the <tt>AudioFormat</tt> in which the input
     *         <tt>DataSource</tt>s of this <tt>AudioMixer</tt> can
     *         produce data and which is to be the output <tt>Format</tt> of
     *         this <tt>AudioMixer</tt>
     */
    private AudioFormat getOutFormatFromInDataSources()
    {
        String formatControlType = FormatControl.class.getName();
        AudioFormat outFormat = null;

        synchronized (inDataSources)
        {
            for (InDataSourceDesc inDataSource : inDataSources)
            {
                DataSource effectiveInDataSource
                    = inDataSource.getEffectiveInDataSource();

                if (effectiveInDataSource == null)
                    continue;

                FormatControl formatControl
                    = (FormatControl)
                        effectiveInDataSource.getControl(formatControlType);

                if (formatControl != null)
                {
                    AudioFormat format
                        = (AudioFormat) formatControl.getFormat();

                    if (format != null)
                    {
                        // SIGNED
                        int signed = format.getSigned();

                        if ((AudioFormat.SIGNED == signed)
                                || (Format.NOT_SPECIFIED == signed))
                        {
                            // LITTLE_ENDIAN
                            int endian = format.getEndian();

                            if ((AudioFormat.LITTLE_ENDIAN == endian)
                                    || (Format.NOT_SPECIFIED == endian))
                            {
                                outFormat = format;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (outFormat == null)
            outFormat = DEFAULT_OUTPUT_FORMAT;

        if (logger.isTraceEnabled())
        {
            logger.trace(
                    "Determined outFormat of AudioMixer from inDataSources" +
                        " to be " + outFormat);
        }
        return outFormat;
    }

    /**
     * Gets the <tt>AudioMixerPushBufferStream</tt>, first creating it if it
     * does not exist already, which reads data from the input
     * <tt>DataSource</tt>s of this <tt>AudioMixer</tt> and pushes it to
     * output <tt>AudioMixingPushBufferStream</tt>s for audio mixing.
     *
     * @return the <tt>AudioMixerPushBufferStream</tt> which reads data from
     * the input <tt>DataSource</tt>s of this <tt>AudioMixer</tt> and pushes it
     * to output <tt>AudioMixingPushBufferStream</tt>s for audio mixing
     */
    AudioMixerPushBufferStream getOutStream()
    {
        synchronized (inDataSources)
        {
            AudioFormat outFormat
                = (outStream == null)
                    ? getOutFormatFromInDataSources()
                    : outStream.getFormat();

            setOutFormatToInDataSources(outFormat);

            Collection<InStreamDesc> inStreams;

            try
            {
                inStreams
                    = getInStreamsFromInDataSources(
                        outFormat,
                        (outStream == null) ? null : outStream.getInStreams());
            }
            catch (IOException ioex)
            {
                throw new UndeclaredThrowableException(ioex);
            }

            if (outStream == null)
            {
                outStream = new AudioMixerPushBufferStream(this, outFormat);
                startedGeneration = 0;
            }
            outStream.setInStreams(inStreams);
            return outStream;
        }
    }

    /**
     * Searches this object's <tt>inDataSource</tt>s for one that matches
     * <tt>inDataSource</tt>, and returns it's associated
     * <tt>TranscodingDataSource</tt>. Currently this is only used when
     * the <tt>MediaStream</tt> needs access to the codec chain used to
     * playback one of it's <tt>ReceiveStream</tt>s.
     *
     * @param inDataSource the <tt>DataSource</tt> to search for.
     * @return The <tt>TranscodingDataSource</tt> associated with
     * <tt>inDataSource</tt>, if we can find one, <tt>null</tt> otherwise.
     */
    public TranscodingDataSource getTranscodingDataSource(
            DataSource inDataSource)
    {
        for (InDataSourceDesc inDataSourceDesc : inDataSources)
        {
            DataSource ourDataSource = inDataSourceDesc.getInDataSource();

            if (ourDataSource == inDataSource)
                return inDataSourceDesc.getTranscodingDataSource();
            else if (ourDataSource instanceof ReceiveStreamPushBufferDataSource)
            {
                // Sometimes the inDataSource has come to AudioMixer wrapped in
                // a ReceiveStreamPushBufferDataSource. We consider it to match.
                if (((ReceiveStreamPushBufferDataSource) ourDataSource)
                            .getDataSource()
                        == inDataSource)
                    return inDataSourceDesc.getTranscodingDataSource();
            }
        }
        return null;
    }

    /**
     * Determines whether a specific <tt>Format</tt> matches a specific
     * <tt>Format</tt> in the sense of JMF <tt>Format</tt> matching.
     * Since this <tt>AudioMixer</tt> and the audio mixing functionality
     * related to it can handle varying characteristics of a certain output
     * <tt>Format</tt>, the only requirement for the specified
     * <tt>Format</tt>s to match is for both of them to have one and the
     * same encoding.
     *
     * @param input the <tt>Format</tt> for which it is required to determine
     * whether it matches a specific <tt>Format</tt>
     * @param pattern the <tt>Format</tt> against which the specified
     * <tt>input</tt> is to be matched
     * @return <tt>true</tt> if the specified <tt>input<tt> matches the
     * specified <tt>pattern</tt> in the sense of JMF <tt>Format</tt> matching;
     * otherwise, <tt>false</tt>
     */
    private boolean matches(Format input, AudioFormat pattern)
    {
        return
            ((input instanceof AudioFormat) && input.isSameEncoding(pattern));
    }

    /**
     * Reads media from a specific <tt>PushBufferStream</tt> which belongs to
     * a specific <tt>DataSource</tt> into a specific output <tt>Buffer</tt>.
     * Allows extenders to tap into the reading and monitor and customize it.
     *
     * @param stream the <tt>PushBufferStream</tt> to read media from and known
     * to belong to the specified <tt>DataSOurce</tt>
     * @param buffer the output <tt>Buffer</tt> in which the media read from the
     * specified <tt>stream</tt> is to be written so that it gets returned to
     * the caller
     * @param dataSource the <tt>DataSource</tt> from which <tt>stream</tt>
     * originated
     * @throws IOException if anything wrong happens while reading from the
     * specified <tt>stream</tt>
     */
    protected void read(
            PushBufferStream stream,
            Buffer buffer,
            DataSource dataSource)
        throws IOException
    {
        stream.read(buffer);
    }

    /**
     * Removes <tt>DataSource</tt>s accepted by a specific
     * <tt>DataSourceFilter</tt> from the list of input <tt>DataSource</tt>s of
     * this <tt>AudioMixer</tt> from which it reads audio to be mixed.
     *
     * @param dataSourceFilter the <tt>DataSourceFilter</tt> which selects the
     * <tt>DataSource</tt>s to be removed from the list of input
     * <tt>DataSource</tt>s of this <tt>AudioMixer</tt> from which it reads
     * audio to be mixed
     */
    public void removeInDataSources(DataSourceFilter dataSourceFilter)
    {
        synchronized (inDataSources)
        {
            Iterator<InDataSourceDesc> inDataSourceIter
                = inDataSources.iterator();
            boolean removed = false;

            while (inDataSourceIter.hasNext())
            {
                InDataSourceDesc inDsDesc = inDataSourceIter.next();
                if (dataSourceFilter.accept(inDsDesc.getInDataSource()))
                {
                    inDataSourceIter.remove();
                    removed = true;

                    try
                    {
                        inDsDesc.stop();
                        inDsDesc.disconnect();
                    }
                    catch(IOException ex)
                    {
                        logger.error("Failed to stop DataSource", ex);
                    }
                }
            }
            if (removed && (outStream != null))
                getOutStream();
        }
    }

    /**
     * Sets a specific <tt>AudioFormat</tt>, if possible, as the output
     * format of the input <tt>DataSource</tt>s of this
     * <tt>AudioMixer</tt> in an attempt to not have to perform explicit
     * transcoding of the input <tt>SourceStream</tt>s.
     *
     * @param outFormat the <tt>AudioFormat</tt> in which the input
     * <tt>DataSource</tt>s of this <tt>AudioMixer</tt> are to be instructed to
     * output
     */
    private void setOutFormatToInDataSources(AudioFormat outFormat)
    {
        String formatControlType = FormatControl.class.getName();

        synchronized (inDataSources)
        {
            for (InDataSourceDesc inDataSourceDesc : inDataSources)
            {
                FormatControl formatControl
                    = (FormatControl)
                        inDataSourceDesc.getControl(formatControlType);

                if (formatControl != null)
                {
                    Format inFormat = formatControl.getFormat();

                    if ((inFormat == null) || !matches(inFormat, outFormat))
                    {
                        Format setFormat
                            = formatControl.setFormat(outFormat);

                        if (setFormat == null)
                            logger.error(
                                    "Failed to set format of inDataSource to "
                                        + outFormat);
                        else if (setFormat != outFormat)
                            logger.warn(
                                    "Failed to change format of inDataSource"
                                        + " from " + setFormat + " to "
                                        + outFormat);
                        else if (logger.isTraceEnabled())
                            logger.trace(
                                    "Set format of inDataSource to "
                                        + setFormat);
                    }
                }
            }
        }
    }

    /**
     * Starts the input <tt>DataSource</tt>s of this <tt>AudioMixer</tt>.
     *
     * @param outStream the <tt>AudioMixerPushBufferStream</tt> which requests
     * this <tt>AudioMixer</tt> to start. If <tt>outStream</tt> is the current
     * one and only <tt>AudioMixerPushBufferStream</tt> of this
     * <tt>AudioMixer</tt>, this <tt>AudioMixer</tt> starts if it hasn't started
     * yet. Otherwise, the request is ignored.
     * @param generation a value generated by <tt>outStream</tt> indicating the
     * order of the invocations of the <tt>start</tt> and <tt>stop</tt> methods
     * performed by <tt>outStream</tt> allowing it to execute the said methods
     * outside synchronized blocks for the purposes of reducing deadlock risks
     * @throws IOException if any of the input <tt>DataSource</tt>s of this
     * <tt>AudioMixer</tt> throws such an exception while attempting to start it
     */
    void start(AudioMixerPushBufferStream outStream, long generation)
        throws IOException
    {
        synchronized (inDataSources)
        {
            /*
             * AudioMixer has only one outStream at a time and only its current
             * outStream knows when it has to start (and stop).
             */
            if (this.outStream != outStream)
                return;
            /*
             * The notion of generations was introduced in order to allow
             * outStream to invoke the start and stop methods outside
             * synchronized blocks. The generation value always increases in a
             * synchronized block.
             */
            if (startedGeneration < generation)
                startedGeneration = generation;
            else
                return;

            if (started == 0)
            {
                for (InDataSourceDesc inDataSourceDesc : inDataSources)
                    inDataSourceDesc.start();
            }

            started++;
        }
    }

    /**
     * Stops the input <tt>DataSource</tt>s of this <tt>AudioMixer</tt>.
     *
     * @param outStream the <tt>AudioMixerPushBufferStream</tt> which requests
     * this <tt>AudioMixer</tt> to stop. If <tt>outStream</tt> is the current
     * one and only <tt>AudioMixerPushBufferStream</tt> of this
     * <tt>AudioMixer</tt>, this <tt>AudioMixer</tt> stops. Otherwise, the
     * request is ignored.
     * @param generation a value generated by <tt>outStream</tt> indicating the
     * order of the invocations of the <tt>start</tt> and <tt>stop</tt> methods
     * performed by <tt>outStream</tt> allowing it to execute the said methods
     * outside synchronized blocks for the purposes of reducing deadlock risks
     * @throws IOException if any of the input <tt>DataSource</tt>s of this
     * <tt>AudioMixer</tt> throws such an exception while attempting to stop it
     */
    void stop(AudioMixerPushBufferStream outStream, long generation)
        throws IOException
    {
        synchronized (inDataSources)
        {
            /*
             * AudioMixer has only one outStream at a time and only its current
             * outStream knows when it has to stop (and start).
             */
            if (this.outStream != outStream)
                return;
            /*
             * The notion of generations was introduced in order to allow
             * outStream to invoke the start and stop methods outside
             * synchronized blocks. The generation value always increases in a
             * synchronized block.
             */
            if (startedGeneration < generation)
                startedGeneration = generation;
            else
                return;

            if (started <= 0)
                return;

            started--;

            if (started == 0)
            {
                for (InDataSourceDesc inDataSourceDesc : inDataSources)
                    inDataSourceDesc.stop();
            }
        }
    }
}
