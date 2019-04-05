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
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.util.*;
import org.jitsi.utils.logging.*;

/**
 * Represents a <tt>PushBufferStream</tt> which reads data from the
 * <tt>SourceStream</tt>s of the input <tt>DataSource</tt>s of the associated
 * <tt>AudioMixer</tt> and pushes it to <tt>AudioMixingPushBufferStream</tt>s
 * for audio mixing.
 * <p>
 * Pretty much private to <tt>AudioMixer</tt> but extracted into its own file
 * for the sake of clarity.
 * </p>
 *
 * @author Lyubomir Marinov
 */
class AudioMixerPushBufferStream
    extends ControlsAdapter
    implements PushBufferStream
{
    /**
     * The <tt>Logger</tt> used by the <tt>AudioMixerPushBufferStream</tt> class
     * and its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AudioMixerPushBufferStream.class);

    /**
     * The <tt>AudioMixer</tt> which created this
     * <tt>AudioMixerPushBufferStream</tt>.
     */
    private final AudioMixer audioMixer;

    /**
     * The <tt>SourceStream</tt>s (in the form of <tt>InStreamDesc</tt> so
     * that this instance can track back the
     * <tt>AudioMixingPushBufferDataSource</tt> which outputs the mixed audio
     * stream and determine whether the associated <tt>SourceStream</tt> is to
     * be included into the mix) from which this instance reads its data.
     */
    private InStreamDesc[] inStreams;

    /**
     * The <tt>Object</tt> which synchronizes the access to
     * {@link #inStreams}-related members.
     */
    private final Object inStreamsSyncRoot = new Object();

    /**
     * The cache of <tt>short</tt> arrays utilized by this instance for the
     * purposes of reducing garbage collection.
     */
    private final ShortArrayCache shortArrayCache = new ShortArrayCache();

    /**
     * The <tt>AudioFormat</tt> of the <tt>Buffer</tt> read during the last read
     * from one of the {@link #inStreams}. Only used for debugging purposes.
     */
    private AudioFormat lastReadInFormat;

    /**
     * The <tt>AudioFormat</tt> of the data this instance outputs.
     */
    private final AudioFormat outFormat;

    /**
     * The <tt>AudioMixingPushBufferStream</tt>s to which this instance pushes
     * data for audio mixing.
     */
    private final List<AudioMixingPushBufferStream> outStreams
        = new ArrayList<AudioMixingPushBufferStream>();

    /**
     * The number of times that {@link #outStreams} has been modified via
     * {@link #addOutStream(AudioMixingPushBufferStream)} and
     * {@link #removeOutStream(AudioMixingPushBufferStream)} in order to allow
     * {@link AudioMixer#start(AudioMixerPushBufferStream)} and
     * {@link AudioMixer#stop(AudioMixerPushBufferStream)} to be invoked outside
     * blocks synchronized on <tt>outStreams</tt>.
     */
    private long outStreamsGeneration;

    /**
     * The <tt>BufferTransferHandler</tt> through which this instance gets
     * notifications from its input <tt>SourceStream</tt>s that new data is
     * available for audio mixing.
     */
    private final BufferTransferHandler transferHandler
        = new BufferTransferHandler()
        {
            /**
             * The cached <tt>Buffer</tt> instance to be used during the
             * execution of {@link #transferData(PushBufferStream)} in order to
             * reduce garbage collection.
             */
            private final Buffer buffer = new Buffer();

            @Override
            public void transferData(PushBufferStream stream)
            {
                buffer.setDiscard(false);
                buffer.setFlags(0);
                buffer.setLength(0);
                buffer.setOffset(0);

                AudioMixerPushBufferStream.this.transferData(buffer);
            }
        };

    /**
     * A copy of {@link #outStreams} which will cause no
     * <tt>ConcurrentModificationException</tt> and which has been introduced to
     * reduce allocations and garbage collection.
     */
    private AudioMixingPushBufferStream[] unmodifiableOutStreams;

    /**
     * Initializes a new <tt>AudioMixerPushBufferStream</tt> instance to output
     * data in a specific <tt>AudioFormat</tt> for a specific
     * <tt>AudioMixer</tt>.
     *
     * @param audioMixer the <tt>AudioMixer</tt> which creates this instance and
     * for which it is to output data
     * @param outFormat the <tt>AudioFormat</tt> in which the new instance is to
     * output data
     */
    public AudioMixerPushBufferStream(
            AudioMixer audioMixer,
            AudioFormat outFormat)
    {
        this.audioMixer = audioMixer;
        this.outFormat = outFormat;
    }

    /**
     * Adds a specific <tt>AudioMixingPushBufferStream</tt> to the collection of
     * such streams to which this instance is to push the data for audio mixing
     * it reads from its input <tt>SourceStream</tt>s.
     *
     * @param outStream the <tt>AudioMixingPushBufferStream</tt> to add to the
     * collection of such streams to which this instance is to push the data for
     * audio mixing it reads from its input <tt>SourceStream</tt>s
     * @throws IOException if <tt>outStream</tt> was the first
     * <tt>AudioMixingPushBufferStream</tt> and the <tt>AudioMixer</tt> failed
     * to start
     */
    void addOutStream(AudioMixingPushBufferStream outStream)
        throws IOException
    {
        if (outStream == null)
            throw new IllegalArgumentException("outStream");

        boolean start = false;
        long generation = 0;

        synchronized (outStreams)
        {
            if (!outStreams.contains(outStream) && outStreams.add(outStream))
            {
                unmodifiableOutStreams = null;
                if (outStreams.size() == 1)
                {
                    start = true;
                    generation = ++outStreamsGeneration;
                }
            }
        }
        if (start)
        {
            /*
             * The start method of AudioMixer is potentially blocking so it has
             * been moved out of synchronized blocks in order to reduce the
             * risks of deadlocks.
             */
            audioMixer.start(this, generation);
        }
    }

    /**
     * Implements {@link SourceStream#endOfStream()}. Delegates to the input
     * <tt>SourceStreams</tt> of this instance.
     *
     * @return <tt>true</tt> if all input <tt>SourceStream</tt>s of this
     * instance have reached the end of their content; <tt>false</tt>, otherwise
     */
    @Override
    public boolean endOfStream()
    {
        synchronized (inStreamsSyncRoot)
        {
            if (inStreams != null)
            {
                for (InStreamDesc inStreamDesc : inStreams)
                {
                    if (!inStreamDesc.getInStream().endOfStream())
                        return false;
                }
            }
        }
        return true;
    }

    /**
     * Attempts to equalize the length in milliseconds of the buffering
     * performed by the <tt>inStreams</tt> in order to always read and mix
     * one and the same length in milliseconds.
     */
    void equalizeInStreamBufferLength()
    {
        synchronized (inStreamsSyncRoot)
        {
            if ((inStreams == null) || (inStreams.length < 1))
                return;

            /*
             * The first inStream is expected to be from the CaptureDevice
             * and no custom BufferControl is provided for it so the
             * bufferLength is whatever it says.
             */
            BufferControl bufferControl = getBufferControl(inStreams[0]);
            long bufferLength
                = (bufferControl == null)
                    ? CachingPushBufferStream.DEFAULT_BUFFER_LENGTH
                    : bufferControl.getBufferLength();

            for (int i = 1; i < inStreams.length; i++)
            {
                BufferControl inStreamBufferControl
                    = getBufferControl(inStreams[i]);

                if (inStreamBufferControl != null)
                    inStreamBufferControl.setBufferLength(bufferLength);
            }
        }
    }

    /**
     * Gets the <tt>BufferControl<tt> of a specific input stream. The returned
     * <tt>BufferControl</tt> may be available through its input
     * <tt>DataSource</tt>, its transcoding <tt>DataSource</tt> if any or the
     * very input stream.
     *
     * @param inStreamDesc an <tt>InStreamDesc</tt> which describes the
     * input stream and its originating <tt>DataSource</tt>s from which the
     * <tt>BufferControl</tt> is to be retrieved
     * @return the <tt>BufferControl</tt> of the specified input stream found in
     * its input <tt>DataSource</tt>, its transcoding <tt>DataSource</tt> if any
     * or the very input stream if such a control exists; otherwise,
     * <tt>null</tt>
     */
    private BufferControl getBufferControl(InStreamDesc inStreamDesc)
    {
        InDataSourceDesc inDataSourceDesc
            = inStreamDesc.inDataSourceDesc;

        // Try the DataSource which directly provides the specified inStream.
        DataSource effectiveInDataSource
            = inDataSourceDesc.getEffectiveInDataSource();
        String bufferControlType = BufferControl.class.getName();

        if (effectiveInDataSource != null)
        {
            BufferControl bufferControl
                = (BufferControl)
                    effectiveInDataSource.getControl(bufferControlType);

            if (bufferControl != null)
                return bufferControl;
        }

        /*
         * If transcoding is taking place and the transcodingDataSource does not
         * have a BufferControl, try the inDataSource which is being
         * transcoded.
         */
        DataSource inDataSource = inDataSourceDesc.inDataSource;

        if ((inDataSource != null)
                && (inDataSource != effectiveInDataSource))
        {
            BufferControl bufferControl
                = (BufferControl) inDataSource.getControl(bufferControlType);

            if (bufferControl != null)
                return bufferControl;
        }

        // If everything else has failed, try the very inStream.
        return
            (BufferControl)
                inStreamDesc.getInStream().getControl(bufferControlType);
    }

    /**
     * Implements {@link SourceStream#getContentDescriptor()}. Returns a
     * <tt>ContentDescriptor</tt> which describes the content type of this
     * instance.
     *
     * @return a <tt>ContentDescriptor</tt> which describes the content type of
     * this instance
     */
    @Override
    public ContentDescriptor getContentDescriptor()
    {
        return new ContentDescriptor(audioMixer.getContentType());
    }

    /**
     * Implements {@link SourceStream#getContentLength()}. Delegates to the
     * input <tt>SourceStreams</tt> of this instance.
     *
     * @return the length of the content made available by this instance which
     * is the maximum length of the contents made available by its input
     * <tt>StreamSource</tt>s
     */
    @Override
    public long getContentLength()
    {
        long contentLength = 0;

        synchronized (inStreamsSyncRoot)
        {
            if (inStreams != null)
                for (InStreamDesc inStreamDesc : inStreams)
                {
                    long inContentLength
                        = inStreamDesc.getInStream().getContentLength();

                    if (LENGTH_UNKNOWN == inContentLength)
                        return LENGTH_UNKNOWN;
                    if (contentLength < inContentLength)
                        contentLength = inContentLength;
                }
        }
        return contentLength;
    }

    /**
     * Implements {@link PushBufferStream#getFormat()}. Returns the
     * <tt>AudioFormat</tt> in which this instance was configured to output its
     * data.
     *
     * @return the <tt>AudioFormat</tt> in which this instance was configured to
     * output its data
     */
    @Override
    public AudioFormat getFormat()
    {
        return outFormat;
    }

    /**
     * Gets the <tt>SourceStream</tt>s (in the form of
     * <tt>InStreamDesc</tt>s) from which this instance reads audio samples.
     *
     * @return an array of <tt>InStreamDesc</tt>s which describe the input
     * <tt>SourceStream</tt>s from which this instance reads audio samples
     */
    InStreamDesc[] getInStreams()
    {
        synchronized (inStreamsSyncRoot)
        {
            return (inStreams == null) ? null : inStreams.clone();
        }
    }

    /**
     * Implements {@link PushBufferStream#read(Buffer)}. Reads audio samples
     * from the input <tt>SourceStreams</tt> of this instance in various
     * formats, converts the read audio samples to one and the same format and
     * pushes them to the output <tt>AudioMixingPushBufferStream</tt>s for the
     * very audio mixing.
     *
     * @param buffer the <tt>Buffer</tt> in which the audio samples read from
     * the input <tt>SourceStream</tt>s are to be returned to the caller
     * @throws IOException if any of the input <tt>SourceStream</tt>s throws
     * such an exception while reading from them or anything else goes wrong
     */
    @Override
    public void read(Buffer buffer)
        throws IOException
    {
        InSampleDesc inSampleDesc;
        int inStreamCount;
        AudioFormat format = getFormat();

        synchronized (inStreamsSyncRoot)
        {
            InStreamDesc[] inStreams = this.inStreams;

            if ((inStreams == null) || (inStreams.length == 0))
            {
                return;
            }
            else
            {
                inSampleDesc = (InSampleDesc) buffer.getData();
                // format
                if ((inSampleDesc != null) && inSampleDesc.format != format)
                    inSampleDesc = null;
                // inStreams
                inStreamCount = inStreams.length;
                if (inSampleDesc != null)
                {
                    InStreamDesc[] inSampleDescInStreams
                        = inSampleDesc.inStreams;

                    if (inSampleDescInStreams.length == inStreamCount)
                    {
                        for (int i = 0; i < inStreamCount; i++)
                        {
                            if (inSampleDescInStreams[i] != inStreams[i])
                            {
                                inSampleDesc = null;
                                break;
                            }
                        }
                    }
                    else
                    {
                        inSampleDesc = null;
                    }
                }
                if (inSampleDesc == null)
                {
                    inSampleDesc
                        = new InSampleDesc(
                                new short[inStreamCount][],
                                inStreams.clone(),
                                format);
                }
            }
        }

        int maxInSampleCount;

        try
        {
            maxInSampleCount = readInPushBufferStreams(format, inSampleDesc);
        }
        catch (UnsupportedFormatException ufex)
        {
            IOException ioex = new IOException();

            ioex.initCause(ufex);
            throw ioex;
        }

        maxInSampleCount
            = Math.max(
                    maxInSampleCount,
                    readInPullBufferStreams(
                            format,
                            maxInSampleCount,
                            inSampleDesc));

        buffer.setData(inSampleDesc);
        buffer.setLength(maxInSampleCount);

        /*
         * Convey the timeStamp so that it can be reported by the Buffers of
         * the AudioMixingPushBufferStreams when mixes are read from them.
         */
        long timeStamp = inSampleDesc.getTimeStamp();

        if (timeStamp != Buffer.TIME_UNKNOWN)
            buffer.setTimeStamp(timeStamp);
    }

    /**
     * Reads audio samples from the input <tt>PullBufferStream</tt>s of this
     * instance and converts them to a specific output <tt>AudioFormat</tt>. An
     * attempt is made to read a specific maximum number of samples from each of
     * the <tt>PullBufferStream</tt>s but the very <tt>PullBufferStream</tt> may
     * not honor the request.
     *
     * @param outFormat the <tt>AudioFormat</tt> in which the audio samples
     * read from the <tt>PullBufferStream</tt>s are to be converted before being
     * returned
     * @param outSampleCount the maximum number of audio samples to be read
     * from each of the <tt>PullBufferStream</tt>s but the very
     * <tt>PullBufferStream</tt>s may not honor the request
     * @param inSampleDesc an <tt>InStreamDesc</tt> which specifies the
     * input streams to be read and the collection of audio samples in which the
     * read audio samples are to be returned
     * @return the maximum number of audio samples actually read from the input
     * <tt>PullBufferStream</tt>s of this instance
     * @throws IOException if anything goes wrong while reading the specified
     * input streams
     */
    private int readInPullBufferStreams(
            AudioFormat outFormat,
            int outSampleCount,
            InSampleDesc inSampleDesc)
        throws IOException
    {
        InStreamDesc[] inStreams = inSampleDesc.inStreams;
        int maxInSampleCount = 0;

        for (InStreamDesc inStream : inStreams)
            if (inStream.getInStream() instanceof PullBufferStream)
                throw new UnsupportedOperationException(
                        AudioMixerPushBufferStream.class.getSimpleName()
                            + ".readInPullBufferStreams"
                            + "(AudioFormat,int,InSampleDesc)");
        return maxInSampleCount;
    }

    /**
     * Reads audio samples from a specific <tt>PushBufferStream</tt> and
     * converts them to a specific output <tt>AudioFormat</tt>. An attempt is
     * made to read a specific maximum number of samples from the specified
     * <tt>PushBufferStream</tt> but the very <tt>PushBufferStream</tt> may not
     * honor the request.
     *
     * @param inStreamDesc an <tt>InStreamDesc</tt> which specifies the
     * input <tt>PushBufferStream</tt> to read from
     * @param outFormat the <tt>AudioFormat</tt> to which the samples read
     * from <tt>inStream</tt> are to be converted before being returned
     * @param sampleCount the maximum number of samples which the read operation
     * should attempt to read from <tt>inStream</tt> but the very
     * <tt>inStream</tt> may not honor the request
     * @param outBuffer the <tt>Buffer</tt> into which the array of
     * <tt>int</tt> audio samples read from the specified <tt>inStream</tt>
     * is to be written
     * @throws IOException if anything wrong happens while reading
     * <tt>inStream</tt>
     * @throws UnsupportedFormatException if converting the samples read from
     * <tt>inStream</tt> to <tt>outFormat</tt> fails
     */
    private void readInPushBufferStream(
            InStreamDesc inStreamDesc,
            AudioFormat outFormat,
            int sampleCount,
            Buffer outBuffer)
        throws IOException,
               UnsupportedFormatException
    {
        PushBufferStream inStream
            = (PushBufferStream) inStreamDesc.getInStream();
        AudioFormat inStreamFormat = (AudioFormat) inStream.getFormat();
        Buffer inBuffer = inStreamDesc.getBuffer(true);

        if (sampleCount != 0)
        {
            if (Format.byteArray.equals(inStreamFormat.getDataType()))
            {
                Object data = inBuffer.getData();
                int length
                    = sampleCount * (inStreamFormat.getSampleSizeInBits() / 8);

                if (!(data instanceof byte[])
                        || (((byte[]) data).length != length))
                    inBuffer.setData(new byte[length]);
            }
            else
            {
                throw new UnsupportedFormatException(
                        "!Format.getDataType().equals(byte[].class)",
                        inStreamFormat);
            }
        }
        inBuffer.setDiscard(false);
        inBuffer.setFlags(0);
        inBuffer.setLength(0);
        inBuffer.setOffset(0);

        audioMixer.read(
                inStream,
                inBuffer,
                inStreamDesc.inDataSourceDesc.inDataSource);

        /*
         * If the media is to be discarded, don't even bother with the checks
         * and the conversion.
         */
        if (inBuffer.isDiscard())
        {
            outBuffer.setDiscard(true);
            return;
        }

        int inLength = inBuffer.getLength();

        if (inLength <= 0)
        {
            outBuffer.setDiscard(true);
            return;
        }

        AudioFormat inFormat = (AudioFormat) inBuffer.getFormat();

        if (inFormat == null)
            inFormat = inStreamFormat;

        if (logger.isTraceEnabled())
        {
            if (lastReadInFormat == null)
                lastReadInFormat = inFormat;
            else if (!lastReadInFormat.matches(inFormat))
            {
                lastReadInFormat = inFormat;
                logger.trace(
                        "Read inSamples in different format "
                            + lastReadInFormat);
            }
        }

        int inFormatSigned = inFormat.getSigned();

        if ((inFormatSigned != AudioFormat.SIGNED)
                && (inFormatSigned != Format.NOT_SPECIFIED))
        {
            throw new UnsupportedFormatException(
                    "AudioFormat.getSigned()",
                    inFormat);
        }

        int inChannels = inFormat.getChannels();
        int outChannels = outFormat.getChannels();

        if ((inChannels != outChannels)
                && (inChannels != Format.NOT_SPECIFIED)
                && (outChannels != Format.NOT_SPECIFIED))
        {
            logger.error(
                    "Read inFormat with channels " + inChannels
                        + " while expected outFormat channels is "
                        + outChannels);
            throw new UnsupportedFormatException(
                    "AudioFormat.getChannels()",
                    inFormat);
        }

        // Warn about different sampleRates.
        double inSampleRate = inFormat.getSampleRate();
        double outSampleRate = outFormat.getSampleRate();

        if (inSampleRate != outSampleRate)
        {
            logger.warn(
                    "Read inFormat with sampleRate " + inSampleRate
                        + " while expected outFormat sampleRate is "
                        + outSampleRate);
        }

        Object inData = inBuffer.getData();

        if (inData == null)
        {
            outBuffer.setDiscard(true);
        }
        else if (inData instanceof byte[])
        {
            int inSampleSizeInBits = inFormat.getSampleSizeInBits();
            int outSampleSizeInBits = outFormat.getSampleSizeInBits();

            if (logger.isTraceEnabled()
                    && (inSampleSizeInBits != outSampleSizeInBits))
            {
                logger.trace(
                        "Read inFormat with sampleSizeInBits "
                            + inSampleSizeInBits
                            + ". Will convert to sampleSizeInBits "
                            + outSampleSizeInBits);
            }

            byte[] inSamples = (byte[]) inData;
            int outLength;
            short[] outSamples;

            switch (inSampleSizeInBits)
            {
            case 16:
                outLength = inLength / 2;
                outSamples
                    = shortArrayCache.validateShortArraySize(
                            outBuffer,
                            outLength);
                switch (outSampleSizeInBits)
                {
                case 16:
                    for (int i = 0; i < outLength; i++)
                    {
                        outSamples[i]
                            = ArrayIOUtils.readShort(inSamples, i * 2);
                    }
                    break;
                case 8:
                case 24:
                case 32:
                default:
                    throw new UnsupportedFormatException(
                            "AudioFormat.getSampleSizeInBits()",
                            outFormat);
                }
                break;
            case 8:
            case 24:
            case 32:
            default:
                throw new UnsupportedFormatException(
                        "AudioFormat.getSampleSizeInBits()",
                        inFormat);
            }

            outBuffer.setFlags(inBuffer.getFlags());
            outBuffer.setFormat(outFormat);
            outBuffer.setLength(outLength);
            outBuffer.setOffset(0);
            outBuffer.setTimeStamp(inBuffer.getTimeStamp());
        }
        else
        {
            throw new UnsupportedFormatException(
                    "Format.getDataType().equals(" + inData.getClass() + ")",
                    inFormat);
        }
    }

    /**
     * Reads audio samples from the input <tt>PushBufferStream</tt>s of this
     * instance and converts them to a specific output <tt>AudioFormat</tt>.
     *
     * @param outFormat the <tt>AudioFormat</tt> in which the audio samples
     * read from the <tt>PushBufferStream</tt>s are to be converted before being
     * returned
     * @param inSampleDesc an <tt>InSampleDesc</tt> which specifies the
     * input streams to be read and  the collection of audio samples in which
     * the read audio samples are to be returned
     * @return the maximum number of audio samples actually read from the input
     * <tt>PushBufferStream</tt>s of this instance
     * @throws IOException if anything wrong happens while reading the specified
     * input streams
     * @throws UnsupportedFormatException if any of the input streams provides
     * media in a format different than <tt>outFormat</tt>
     */
    private int readInPushBufferStreams(
            AudioFormat outFormat,
            InSampleDesc inSampleDesc)
        throws IOException,
               UnsupportedFormatException
    {
        InStreamDesc[] inStreams = inSampleDesc.inStreams;
        Buffer buffer = inSampleDesc.getBuffer();
        int maxInSampleCount = 0;
        short[][] inSamples = inSampleDesc.inSamples;

        for (int i = 0; i < inStreams.length; i++)
        {
            InStreamDesc inStreamDesc = inStreams[i];
            SourceStream inStream = inStreamDesc.getInStream();

            if (inStream instanceof PushBufferStream)
            {
                buffer.setDiscard(false);
                buffer.setFlags(0);
                buffer.setLength(0);
                buffer.setOffset(0);

                readInPushBufferStream(
                        inStreamDesc, outFormat, maxInSampleCount,
                        buffer);

                int sampleCount;
                short[] samples;

                if (buffer.isDiscard())
                {
                    sampleCount = 0;
                    samples = null;
                }
                else
                {
                    sampleCount = buffer.getLength();
                    if (sampleCount <= 0)
                    {
                        sampleCount = 0;
                        samples = null;
                    }
                    else
                    {
                        samples = (short[]) buffer.getData();
                    }
                }

                if (sampleCount != 0)
                {
                    /*
                     * The short array with the samples will be used via
                     * inputSamples so the buffer cannot use it anymore.
                     */
                    buffer.setData(null);

                    /*
                     * If the samples array has more elements than sampleCount,
                     * the elements in question may contain stale samples.
                     */
                    if (samples.length > sampleCount)
                    {
                        Arrays.fill(
                                samples, sampleCount, samples.length,
                                (short) 0);
                    }

                    inSamples[i]
                        = ((buffer.getFlags() & Buffer.FLAG_SILENCE) == 0)
                            ? samples
                            : null;

                    if (maxInSampleCount < samples.length)
                        maxInSampleCount = samples.length;

                    /*
                     * Convey the timeStamp so that it can be set to the Buffers
                     * of the AudioMixingPushBufferStreams when mixes are read
                     * from them. Since the inputStreams will report different
                     * timeStamps, only use the first meaningful timestamp for
                     * now.
                     */
                    if (inSampleDesc.getTimeStamp() == Buffer.TIME_UNKNOWN)
                        inSampleDesc.setTimeStamp(buffer.getTimeStamp());

                    continue;
                }
            }

            inSamples[i] = null;
        }
        return maxInSampleCount;
    }

    /**
     * Removes a specific <tt>AudioMixingPushBufferStream</tt> from the
     * collection of such streams to which this instance pushes the data for
     * audio mixing it reads from its input <tt>SourceStream</tt>s.
     *
     * @param outStream the <tt>AudioMixingPushBufferStream</tt> to remove from
     * the collection of such streams to which this instance pushes the data for
     * audio mixing it reads from its input <tt>SourceStream</tt>s
     * @throws IOException if <tt>outStream</tt> was the last
     * <tt>AudioMixingPushBufferStream</tt> and the <tt>AudioMixer</tt> failed
     * to stop
     */
    void removeOutStream(AudioMixingPushBufferStream outStream)
        throws IOException
    {
        boolean stop = false;
        long generation = 0;

        synchronized (outStreams)
        {
            if ((outStream != null) && outStreams.remove(outStream))
            {
                unmodifiableOutStreams = null;
                if (outStreams.isEmpty())
                {
                    stop = true;
                    generation = ++outStreamsGeneration;
                }
            }
        }
        if (stop)
        {
            /*
             * The stop method of AudioMixer is potentially blocking so it has
             * been moved out of synchronized blocks in order to reduce the
             * risks of deadlocks.
             */
            audioMixer.stop(this, generation);
        }
    }

    /**
     * Pushes a copy of a specific set of input audio samples to a specific
     * <tt>AudioMixingPushBufferStream</tt> for audio mixing. Audio samples read
     * from input <tt>DataSource</tt>s which the
     * <tt>AudioMixingPushBufferDataSource</tt> owner of the specified
     * <tt>AudioMixingPushBufferStream</tt> has specified to not be included in
     * the output mix are not pushed to the
     * <tt>AudioMixingPushBufferStream</tt>.
     *
     * @param outStream the <tt>AudioMixingPushBufferStream</tt> to push the
     * specified set of audio samples to
     * @param inSampleDesc the set of audio samples to be pushed to
     * <tt>outStream</tt> for audio mixing
     * @param maxInSampleCount the maximum number of audio samples available
     * in <tt>inSamples</tt>
     */
    private void setInSamples(
            AudioMixingPushBufferStream outStream,
            InSampleDesc inSampleDesc,
            int maxInSampleCount)
    {
        short[][] inSamples = inSampleDesc.inSamples;
        InStreamDesc[] inStreams = inSampleDesc.inStreams;

        inSamples = inSamples.clone();

        CaptureDevice captureDevice = audioMixer.captureDevice;
        AudioMixingPushBufferDataSource outDataSource
            = outStream.getDataSource();
        boolean outDataSourceIsSendingDTMF
            = (captureDevice instanceof AudioMixingPushBufferDataSource)
                ? outDataSource.isSendingDTMF()
                : false;
        boolean outDataSourceIsMute = outDataSource.isMute();

        for (int i = 0, o = 0; i < inSamples.length; i++)
        {
            InStreamDesc inStreamDesc = inStreams[i];
            DataSource inDataSource
                = inStreamDesc.inDataSourceDesc.inDataSource;

            if (outDataSourceIsSendingDTMF && (inDataSource == captureDevice))
            {
                PushBufferStream inStream
                    = (PushBufferStream) inStreamDesc.getInStream();
                AudioFormat inStreamFormat = (AudioFormat) inStream.getFormat();
                // Generate the inband DTMF signal.
                short[] nextToneSignal
                    = outDataSource.getNextToneSignal(
                            inStreamFormat.getSampleRate(),
                            inStreamFormat.getSampleSizeInBits());

                inSamples[i] = nextToneSignal;
                if (maxInSampleCount < nextToneSignal.length)
                    maxInSampleCount = nextToneSignal.length;
            }
            else if (outDataSource.equals(inStreamDesc.getOutDataSource())
                    || (outDataSourceIsMute && (inDataSource == captureDevice)))
            {
                inSamples[i] = null;
            }

            /*
             * Have the samples of the contributing streams at the head of the
             * sample set (and the non-contributing at the tail) in order to
             * optimize determining the number of contributing streams later on
             * and, consequently, the mixing.
             */
            short[] inStreamSamples = inSamples[i];

            if (inStreamSamples != null)
            {
                if (i != o)
                {
                    inSamples[o] = inStreamSamples;
                    inSamples[i] = null;
                }
                o++;
            }
        }

        outStream.setInSamples(
                inSamples,
                maxInSampleCount,
                inSampleDesc.getTimeStamp());
    }

    /**
     * Sets the <tt>SourceStream</tt>s (in the form of <tt>InStreamDesc</tt>)
     * from which this instance is to read audio samples and push them to the
     * <tt>AudioMixingPushBufferStream</tt>s for audio mixing.
     *
     * @param inStreams the <tt>SourceStream</tt>s (in the form of
     * <tt>InStreamDesc</tt>) from which this instance is to read audio
     * samples and push them to the <tt>AudioMixingPushBufferStream</tt>s for
     * audio mixing
     */
    void setInStreams(Collection<InStreamDesc> inStreams)
    {
        InStreamDesc[] oldValue;
        InStreamDesc[] newValue
            = (null == inStreams)
                ? null
                : inStreams.toArray(new InStreamDesc[inStreams.size()]);

        synchronized (inStreamsSyncRoot)
        {
            oldValue = this.inStreams;

            this.inStreams = newValue;
        }

        boolean valueIsChanged = !Arrays.equals(oldValue, newValue);

        if (valueIsChanged)
        {
            if (oldValue != null)
                setTransferHandler(oldValue, null);

            if (newValue == null)
                return;

            boolean skippedForTransferHandler = false;

            for (InStreamDesc inStreamDesc : newValue)
            {
                SourceStream inStream = inStreamDesc.getInStream();

                if (!(inStream instanceof PushBufferStream))
                    continue;
                if (!skippedForTransferHandler)
                {
                    skippedForTransferHandler = true;
                    continue;
                }
                if (!(inStream instanceof CachingPushBufferStream))
                {
                    PushBufferStream cachingInStream
                        = new CachingPushBufferStream(
                                (PushBufferStream) inStream);

                    inStreamDesc.setInStream(cachingInStream);
                    if (logger.isTraceEnabled())
                        logger.trace(
                                "Created CachingPushBufferStream"
                                    + " with hashCode "
                                    + cachingInStream.hashCode()
                                    + " for inStream with hashCode "
                                    + inStream.hashCode());
                }
            }

            setTransferHandler(newValue, transferHandler);
            equalizeInStreamBufferLength();

            if (logger.isTraceEnabled())
            {
                int oldValueLength = (oldValue == null) ? 0 : oldValue.length;
                int difference = newValue.length - oldValueLength;

                if (difference > 0)
                    logger.trace(
                            "Added " + difference
                                + " inStream(s) and the total is "
                                + newValue.length);
                else if (difference < 0)
                    logger.trace(
                            "Removed " + difference
                                + " inStream(s) and the total is "
                                + newValue.length);
            }
        }
    }

    /**
     * Implements
     * {@link PushBufferStream#setTransferHandler(BufferTransferHandler)}.
     * Because this instance pushes data to multiple output
     * <tt>AudioMixingPushBufferStreams</tt>, a single
     * <tt>BufferTransferHandler</tt> is not sufficient and thus this method is
     * unsupported and throws <tt>UnsupportedOperationException</tt>.
     *
     * @param transferHandler the <tt>BufferTransferHandler</tt> to be notified
     * by this <tt>PushBufferStream</tt> when media is available for reading
     */
    @Override
    public void setTransferHandler(BufferTransferHandler transferHandler)
    {
        throw new UnsupportedOperationException(
                AudioMixerPushBufferStream.class.getSimpleName()
                    + ".setTransferHandler(BufferTransferHandler)");
    }

    /**
     * Sets a specific <tt>BufferTransferHandler</tt> to a specific collection
     * of <tt>SourceStream</tt>s (in the form of <tt>InStreamDesc</tt>)
     * abstracting the differences among the various types of
     * <tt>SourceStream</tt>s.
     *
     * @param inStreams the input <tt>SourceStream</tt>s to which the
     * specified <tt>BufferTransferHandler</tt> is to be set
     * @param transferHandler the <tt>BufferTransferHandler</tt> to be set to
     * the specified <tt>inStreams</tt>
     */
    private void setTransferHandler(
            InStreamDesc[] inStreams,
            BufferTransferHandler transferHandler)
    {
        if ((inStreams == null) || (inStreams.length <= 0))
            return;

        boolean transferHandlerIsSet = false;

        for (InStreamDesc inStreamDesc : inStreams)
        {
            SourceStream inStream = inStreamDesc.getInStream();

            if (inStream instanceof PushBufferStream)
            {
                BufferTransferHandler inStreamTransferHandler;
                PushBufferStream inPushBufferStream
                    = (PushBufferStream) inStream;

                if (transferHandler == null)
                    inStreamTransferHandler = null;
                else if (transferHandlerIsSet)
                {
                    inStreamTransferHandler
                        = new BufferTransferHandler()
                        {
                            @Override
                            public void transferData(PushBufferStream stream)
                            {
                                /*
                                 * Do nothing because we don't want the
                                 * associated PushBufferStream to cause the
                                 * transfer of data from this
                                 * AudioMixerPushBufferStream.
                                 */
                            }
                        };
                }
                else
                {
                    inStreamTransferHandler
                        = new StreamSubstituteBufferTransferHandler(
                                transferHandler,
                                inPushBufferStream,
                                this);
                }

                inPushBufferStream.setTransferHandler(
                        inStreamTransferHandler);

                transferHandlerIsSet = true;
            }
        }
    }

    /**
     * Reads audio samples from the input <tt>SourceStream</tt>s of this
     * instance and pushes them to its output
     * <tt>AudioMixingPushBufferStream</tt>s for audio mixing.
     *
     * @param buffer the cached <tt>Buffer</tt> instance to be used during the
     * execution of the method in order to reduce garbage collection. The
     * <tt>length</tt> of the <tt>buffer</tt> will be reset to <tt>0</tt> before
     * and after the execution of the method.
     */
    protected void transferData(Buffer buffer)
    {
        try
        {
            read(buffer);
        }
        catch (IOException ex)
        {
            throw new UndeclaredThrowableException(ex);
        }

        InSampleDesc inSampleDesc = (InSampleDesc) buffer.getData();
        short[][] inSamples = inSampleDesc.inSamples;
        int maxInSampleCount = buffer.getLength();

        if ((inSamples == null)
                || (inSamples.length == 0)
                || (maxInSampleCount <= 0))
            return;

        AudioMixingPushBufferStream[] outStreams;

        synchronized (this.outStreams)
        {
            outStreams = this.unmodifiableOutStreams;
            if (outStreams == null)
            {
                this.unmodifiableOutStreams
                    = outStreams
                        = this.outStreams.toArray(
                                new AudioMixingPushBufferStream[
                                        this.outStreams.size()]);
            }
        }
        for (AudioMixingPushBufferStream outStream : outStreams)
            setInSamples(outStream, inSampleDesc, maxInSampleCount);

        /*
         * The input samples have already been delivered to the output streams
         * and are no longer necessary.
         */
        for (int i = 0; i < inSamples.length; i++)
        {
            shortArrayCache.deallocateShortArray(inSamples[i]);
            inSamples[i] = null;
        }
    }
}
