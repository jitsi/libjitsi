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
import java.util.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.control.*;
import org.jitsi.util.*;
import org.jitsi.utils.logging.*;

/**
 * Represents a <tt>PushBufferStream</tt> containing the result of the audio
 * mixing of <tt>DataSource</tt>s.
 *
 * @author Lyubomir Marinov
 */
public class AudioMixingPushBufferStream
    extends ControlsAdapter
    implements PushBufferStream
{

    /**
     * The <tt>Logger</tt> used by the <tt>AudioMixingPushBufferStream</tt>
     * class and its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AudioMixingPushBufferStream.class);

    /**
     * Gets the maximum possible value for an audio sample of a specific
     * <tt>AudioFormat</tt>.
     *
     * @param outFormat the <tt>AudioFormat</tt> of which to get the maximum
     * possible value for an audio sample
     * @return the maximum possible value for an audio sample of the specified
     * <tt>AudioFormat</tt>
     * @throws UnsupportedFormatException if the specified <tt>outFormat</tt>
     * is not supported by the underlying implementation
     */
    private static int getMaxOutSample(AudioFormat outFormat)
        throws UnsupportedFormatException
    {
        switch(outFormat.getSampleSizeInBits())
        {
        case 8:
            return Byte.MAX_VALUE;
        case 16:
            return Short.MAX_VALUE;
        case 32:
            return Integer.MAX_VALUE;
        case 24:
        default:
            throw new UnsupportedFormatException(
                    "Format.getSampleSizeInBits()",
                    outFormat);
        }
    }

    /**
     * The <tt>AudioMixerPushBufferStream</tt> which reads data from the input
     * <tt>DataSource</tt>s and pushes it to this instance to be mixed.
     */
    private final AudioMixerPushBufferStream audioMixerStream;

    /**
     * The total number of <tt>byte</tt>s read out of this
     * <tt>PushBufferStream</tt> through {@link #read(Buffer)}. Intended for the
     * purposes of debugging at the time of this writing.
     */
    private long bytesRead;

    /**
     * The <tt>AudioMixingPushBufferDataSource</tt> which created and owns this
     * instance and defines the input data which is to not be mixed in the
     * output of this <tt>PushBufferStream</tt>.
     */
    private final AudioMixingPushBufferDataSource dataSource;

    /**
     * The collection of input audio samples still not mixed and read through
     * this <tt>AudioMixingPushBufferStream</tt>.
     */
    private short[][] inSamples;

    /**
     * The maximum number of per-stream audio samples available through
     * <tt>inSamples</tt>.
     */
    private int maxInSampleCount;

    /**
     * The audio samples output by the last invocation of
     * {@link #mix(int[][], AudioFormat, int)}. Cached in order to reduce
     * allocations and garbage collection.
     */
    private short[] outSamples;

    /**
     * The <tt>Object</tt> which synchronizes the access to the data to be read
     * from this <tt>PushBufferStream</tt> i.e. to {@link #inSamples},
     * {@link #maxInSampleCount} and {@link #timeStamp}.
     */
    private final Object readSyncRoot = new Object();

    /**
     * The time stamp of {@link #inSamples} to be reported in the specified
     * <tt>Buffer</tt> when data is read from this instance.
     */
    private long timeStamp = Buffer.TIME_UNKNOWN;

    /**
     * The <tt>BufferTransferHandler</tt> through which this
     * <tt>PushBufferStream</tt> notifies its clients that new data is available
     * for reading.
     */
    private BufferTransferHandler transferHandler;

    /**
     * Initializes a new <tt>AudioMixingPushBufferStream</tt> mixing the
     * input data of a specific <tt>AudioMixerPushBufferStream</tt> and
     * excluding from the mix the audio contributions of a specific
     * <tt>AudioMixingPushBufferDataSource</tt>.
     *
     * @param audioMixerStream the <tt>AudioMixerPushBufferStream</tt> reading
     * data from input <tt>DataSource</tt>s and to push it to the new
     * <tt>AudioMixingPushBufferStream</tt>
     * @param dataSource the <tt>AudioMixingPushBufferDataSource</tt> which has
     * requested the initialization of the new instance and which defines the
     * input data to not be mixed in the output of the new instance
     */
    AudioMixingPushBufferStream(
            AudioMixerPushBufferStream audioMixerStream,
            AudioMixingPushBufferDataSource dataSource)
    {
        this.audioMixerStream = audioMixerStream;
        this.dataSource = dataSource;
    }

    private short[] allocateOutSamples(int minSize)
    {
        short[] outSamples = this.outSamples;

        if ((outSamples == null) || (outSamples.length < minSize))
            this.outSamples = outSamples = new short[minSize];
        return outSamples;
    }

    /**
     * Implements {@link SourceStream#endOfStream()}. Delegates to the wrapped
     * <tt>AudioMixerPushBufferStream</tt> because this instance is just a facet
     * to it.
     *
     * @return <tt>true</tt> if this <tt>SourceStream</tt> has reached the end
     * of the media it makes available; otherwise, <tt>false</tt>
     */
    public boolean endOfStream()
    {
        /*
         * TODO If the inSamples haven't been consumed yet, don't report the
         * end of this stream even if the wrapped stream has reached its end.
         */
        return audioMixerStream.endOfStream();
    }

    /**
     * Implements {@link SourceStream#getContentDescriptor()}. Delegates to the
     * wrapped <tt>AudioMixerPushBufferStream</tt> because this instance is just
     * a facet to it.
     *
     * @return a <tt>ContentDescriptor</tt> which describes the content being
     * made available by this <tt>SourceStream</tt>
     */
    public ContentDescriptor getContentDescriptor()
    {
        return audioMixerStream.getContentDescriptor();
    }

    /**
     * Implements {@link SourceStream#getContentLength()}. Delegates to the
     * wrapped <tt>AudioMixerPushBufferStream</tt> because this instance is just
     * a facet to it.
     *
     * @return the length of the media being made available by this
     * <tt>SourceStream</tt>
     */
    public long getContentLength()
    {
        return audioMixerStream.getContentLength();
    }

    /**
     * Gets the <tt>AudioMixingPushBufferDataSource</tt> which created and owns
     * this instance and defines the input data which is to not be mixed in the
     * output of this <tt>PushBufferStream</tt>.
     *
     * @return the <tt>AudioMixingPushBufferDataSource</tt> which created and
     * owns this instance and defines the input data which is to not be mixed in
     * the output of this <tt>PushBufferStream</tt>
     */
    public AudioMixingPushBufferDataSource getDataSource()
    {
        return dataSource;
    }

    /**
     * Implements {@link PushBufferStream#getFormat()}. Delegates to the wrapped
     * <tt>AudioMixerPushBufferStream</tt> because this instance is just a facet
     * to it.
     *
     * @return the <tt>Format</tt> of the audio being made available by this
     * <tt>PushBufferStream</tt>
     */
    public AudioFormat getFormat()
    {
        return audioMixerStream.getFormat();
    }

    /**
     * Mixes as in audio mixing a specified collection of audio sample sets and
     * returns the resulting mix audio sample set in a specific
     * <tt>AudioFormat</tt>.
     *
     * @param inSamples the collection of audio sample sets to be mixed into
     * one audio sample set in the sense of audio mixing
     * @param outFormat the <tt>AudioFormat</tt> in which the resulting mix
     * audio sample set is to be produced. The <tt>format</tt> property of the
     * specified <tt>outBuffer</tt> is expected to be set to the same value but
     * it is provided as a method argument in order to avoid casting from
     * <tt>Format</tt> to <tt>AudioFormat</tt>.
     * @param outSampleCount the size of the resulting mix audio sample set
     * to be produced
     * @return the resulting audio sample set of the audio mixing of the
     * specified input audio sample sets
     */
    private short[] mix(
            short[][] inSamples,
            AudioFormat outFormat,
            int outSampleCount)
    {
        short[] outSamples;

        /*
         * The trivial case of performing mixing the samples of a single stream.
         * Then there is nothing to mix and the input becomes the output.
         */
        if ((inSamples.length == 1) || (inSamples[1] == null))
        {
            short[] inStreamSamples = inSamples[0];
            int inStreamSampleCount;

            if (inStreamSamples == null)
            {
                inStreamSampleCount = 0;
                outSamples = allocateOutSamples(outSampleCount);
            }
            else if (inStreamSamples.length < outSampleCount)
            {
                inStreamSampleCount = inStreamSamples.length;
                outSamples = allocateOutSamples(outSampleCount);
                System.arraycopy(
                        inStreamSamples, 0,
                        outSamples, 0,
                        inStreamSampleCount);
            }
            else
            {
                inStreamSampleCount = outSampleCount;
                outSamples = inStreamSamples;
            }
            if (inStreamSampleCount != outSampleCount)
            {
                Arrays.fill(
                        outSamples, inStreamSampleCount, outSampleCount,
                        (short) 0);
            }
            return outSamples;
        }

        outSamples = allocateOutSamples(outSampleCount);
        Arrays.fill(outSamples, 0, outSampleCount, (short) 0);

        float maxOutSample;

        try
        {
            maxOutSample = getMaxOutSample(outFormat);
        }
        catch (UnsupportedFormatException ufex)
        {
            throw new UnsupportedOperationException(ufex);
        }

        for (short[] inStreamSamples : inSamples)
        {
            if (inStreamSamples == null)
                continue;

            int inStreamSampleCount
                = Math.min(inStreamSamples.length, outSampleCount);

            if (inStreamSampleCount == 0)
                continue;

            for (int i = 0; i < inStreamSampleCount; i++)
            {
                int inStreamSample = inStreamSamples[i];
                int outSample = outSamples[i];

                outSamples[i]
                    = (short)
                        (inStreamSample
                            + outSample
                            - Math.round(
                                    inStreamSample
                                        * (outSample / maxOutSample)));
            }
        }
        return outSamples;
    }

    /**
     * Implements {@link PushBufferStream#read(Buffer)}. If
     * <tt>inSamples</tt> are available, mixes them and writes the mix to the
     * specified <tt>Buffer</tt> performing the necessary data type conversions.
     *
     * @param buffer the <tt>Buffer</tt> to receive the data read from this
     * instance
     * @throws IOException if anything wrong happens while reading from this
     * instance
     */
    public void read(Buffer buffer)
        throws IOException
    {
        short[][] inSamples;
        int maxInSampleCount;
        long timeStamp;

        synchronized (readSyncRoot)
        {
            inSamples = this.inSamples;
            maxInSampleCount = this.maxInSampleCount;
            timeStamp = this.timeStamp;

            this.inSamples = null;
            this.maxInSampleCount = 0;
            // For the purposes of debugging, we want to have the last known
            // value of the field timeStamp at all times. The reset of the
            // values of the fields inSamples and/or maxInSampleCount should
            // suffice.
            // this.timeStamp = Buffer.TIME_UNKNOWN;
        }

        if ((inSamples == null)
                || (inSamples.length == 0)
                || (maxInSampleCount <= 0))
        {
            buffer.setDiscard(true);
            return;
        }

        AudioFormat outFormat = getFormat();
        short[] outSamples = mix(inSamples, outFormat, maxInSampleCount);
        int outSampleCount = Math.min(maxInSampleCount, outSamples.length);

        if (Format.byteArray.equals(outFormat.getDataType()))
        {
            int outLength;
            Object o = buffer.getData();
            byte[] outData = null;

            if (o instanceof byte[])
                outData = (byte[]) o;

            switch (outFormat.getSampleSizeInBits())
            {
            case 16:
                outLength = outSampleCount * 2;
                if ((outData == null) || (outData.length < outLength))
                    outData = new byte[outLength];
                for (int i = 0; i < outSampleCount; i++)
                    ArrayIOUtils.writeShort(outSamples[i], outData, i * 2);
                break;
            case 8:
            case 24:
            case 32:
            default:
                throw new UnsupportedOperationException(
                        "AudioMixingPushBufferStream.read(Buffer)");
            }

            buffer.setData(outData);
            buffer.setFormat(outFormat);
            buffer.setLength(outLength);
            buffer.setOffset(0);
            buffer.setTimeStamp(timeStamp);

            bytesRead += outLength;
        }
        else
        {
            throw new UnsupportedOperationException(
                    "AudioMixingPushBufferStream.read(Buffer)");
        }
    }

    /**
     * Sets the collection of audio sample sets to be mixed in the sense of
     * audio mixing by this stream when data is read from it. Triggers a push to
     * the clients of this stream.
     *
     * @param inSamples the collection of audio sample sets to be mixed by
     * this stream when data is read from it
     * @param maxInSampleCount the maximum number of per-stream audio samples
     * available through <tt>inSamples</tt>
     * @param timeStamp the time stamp of <tt>inSamples</tt> to be reported
     * in the specified <tt>Buffer</tt> when data is read from this instance
     */
    void setInSamples(short[][] inSamples, int maxInSampleCount, long timeStamp)
    {
        synchronized (readSyncRoot)
        {
            this.inSamples = inSamples;
            this.maxInSampleCount = maxInSampleCount;
            this.timeStamp = timeStamp;
        }

        BufferTransferHandler transferHandler = this.transferHandler;

        if (transferHandler != null)
            transferHandler.transferData(this);
    }

    /**
     * Implements
     * {@link PushBufferStream#setTransferHandler(BufferTransferHandler)}. Sets
     * the <tt>BufferTransferHandler</tt> which is to be notified by this
     * instance when it has media available for reading.
     *
     * @param transferHandler the <tt>BufferTransferHandler</tt> to be notified
     * by this instance when it has media available for reading
     */
    public void setTransferHandler(BufferTransferHandler transferHandler)
    {
        this.transferHandler = transferHandler;
    }

    /**
     * Starts the pushing of data out of this stream.
     *
     * @throws IOException if starting the pushing of data out of this stream
     * fails
     */
    synchronized void start()
        throws IOException
    {
        audioMixerStream.addOutStream(this);
        if (logger.isTraceEnabled())
        {
            logger.trace(
                    "Started " + getClass().getSimpleName() + " with hashCode "
                        + hashCode());
        }
    }

    /**
     * Stops the pushing of data out of this stream.
     *
     * @throws IOException if stopping the pushing of data out of this stream
     * fails
     */
    synchronized void stop()
        throws IOException
    {
        audioMixerStream.removeOutStream(this);
        if (logger.isTraceEnabled())
        {
            logger.trace(
                    "Stopped " + getClass().getSimpleName() + " with hashCode "
                        + hashCode());
        }
    }
}
