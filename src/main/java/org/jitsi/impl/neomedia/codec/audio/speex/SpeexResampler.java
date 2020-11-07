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
package org.jitsi.impl.neomedia.codec.audio.speex;

import java.util.*;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;

import org.jitsi.impl.neomedia.codec.*;

/**
 * Implements an audio resampler using Speex.
 *
 * @author Lyubomir Marinov
 */
public class SpeexResampler
    extends AbstractCodec2
{
    /**
     * The list of <tt>Format</tt>s of audio data supported as input and output
     * by <tt>SpeexResampler</tt> instances.
     */
    private static final Format[] SUPPORTED_FORMATS;

    /**
     * The list of sample rates of audio data supported as input and output by
     * <tt>SpeexResampler</tt> instances.
     */
    private static final double[] SUPPORTED_SAMPLE_RATES
        = new double[]
                {
                    8000,
                    11025,
                    12000,
                    16000,
                    22050,
                    24000,
                    32000,
                    44100,
                    48000,
                    Format.NOT_SPECIFIED
                };

    static
    {
        Speex.assertSpeexIsFunctional();

        int supportedCount = SUPPORTED_SAMPLE_RATES.length;

        SUPPORTED_FORMATS = new Format[4 * supportedCount];
        for (int i = 0; i < supportedCount; i++)
        {
            int j = 4 * i;

            SUPPORTED_FORMATS[j]
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        1 /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.byteArray);
            SUPPORTED_FORMATS[j + 1]
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        1 /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.shortArray);
            SUPPORTED_FORMATS[j + 2]
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        2 /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.byteArray);
            SUPPORTED_FORMATS[j + 3]
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        2 /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.shortArray);
        }
    }

    /**
     * The number of channels with which {@link #resampler} has been
     * initialized.
     */
    private int channels;

    /**
     * The input sample rate configured in {@link #resampler}.
     */
    private int inputSampleRate;

    /**
     * The output sample rate configured in {@link #resampler}.
     */
    private int outputSampleRate;

    /**
     * The pointer to the native <tt>SpeexResamplerState</tt> which is
     * represented by this instance.
     */
    private long resampler;

    /**
     * Initializes a new <tt>SpeexResampler</tt> instance.
     */
    public SpeexResampler()
    {
        super("Speex Resampler", AudioFormat.class, SUPPORTED_FORMATS);

        inputFormats = SUPPORTED_FORMATS;
    }

    /**
     * @see AbstractCodecExt#doClose()
     */
    @Override
    protected void doClose()
    {
        if (resampler != 0)
        {
            Speex.speex_resampler_destroy(resampler);
            resampler = 0;
        }
    }

    /**
     * Opens this <tt>Codec</tt> and acquires the resources that it needs to
     * operate. A call to {@link PlugIn#open()} on this instance will result in
     * a call to <tt>doOpen</tt> only if {@link AbstractCodec#opened} is
     * <tt>false</tt>. All required input and/or output formats are assumed to
     * have been set on this <tt>Codec</tt> before <tt>doOpen</tt> is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this
     * <tt>Codec</tt> needs to operate cannot be acquired
     * @see AbstractCodecExt#doOpen()
     */
    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
    }

    /**
     * Resamples audio from a specific input <tt>Buffer</tt> into a specific
     * output <tt>Buffer</tt>.
     *
     * @param inBuffer input <tt>Buffer</tt>
     * @param outBuffer output <tt>Buffer</tt>
     * @return <tt>BUFFER_PROCESSED_OK</tt> if <tt>inBuffer</tt> has been
     * successfully processed
     * @see AbstractCodecExt#doProcess(Buffer, Buffer)
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        Format inFormat = inBuffer.getFormat();

        if ((inFormat != null)
                && (inFormat != this.inputFormat)
                && !inFormat.equals(this.inputFormat))
        {
            if (null == setInputFormat(inFormat))
                return BUFFER_PROCESSED_FAILED;
        }
        inFormat = this.inputFormat;

        AudioFormat inAudioFormat = (AudioFormat) inFormat;
        int inSampleRate = (int) inAudioFormat.getSampleRate();
        AudioFormat outAudioFormat = (AudioFormat) getOutputFormat();
        int outSampleRate = (int) outAudioFormat.getSampleRate();

        if (inSampleRate == outSampleRate)
        {
            // passthrough
            Class<?> inDataType = inAudioFormat.getDataType();
            Class<?> outDataType = outAudioFormat.getDataType();

            if (Format.byteArray.equals(inDataType))
            {
                byte[] input = (byte[]) inBuffer.getData();

                if (Format.byteArray.equals(outDataType))
                {
                    int length = (input == null) ? 0 : input.length;
                    byte[] output
                        = validateByteArraySize(outBuffer, length, false);

                    if ((input != null) && (output != null))
                        System.arraycopy(input, 0, output, 0, length);
                    outBuffer.setFormat(inBuffer.getFormat());
                    outBuffer.setLength(inBuffer.getLength());
                    outBuffer.setOffset(inBuffer.getOffset());
                }
                else
                {
                    int inLength = inBuffer.getLength();
                    int outOffset = 0;
                    int outLength = inLength / 2;
                    short[] output
                        = validateShortArraySize(outBuffer, outLength);

                    for (int i = inBuffer.getOffset(), o = outOffset;
                            o < outLength;
                            o++)
                    {
                        output[o]
                            = (short)
                                (((input[i++] & 0xFF)
                                        | (input[i++] & 0xFF) << 8));
                    }
                    outBuffer.setFormat(outAudioFormat);
                    outBuffer.setLength(outLength);
                    outBuffer.setOffset(outOffset);
                }
            }
            else
            {
                short[] input = (short[]) inBuffer.getData();

                if (Format.byteArray.equals(outDataType))
                {
                    int inLength = inBuffer.getLength();
                    int outOffset = 0;
                    int outLength = inLength * 2;
                    byte[] output
                        = validateByteArraySize(outBuffer, outLength, false);

                    for (int i = inBuffer.getOffset(), o = outOffset;
                            o < outLength;
                            i++)
                    {
                        short s = input[i];

                        output[o++] = (byte) (s & 0x00FF);
                        output[o++] = (byte) ((s & 0xFF00) >>> 8);
                    }
                    outBuffer.setFormat(outAudioFormat);
                    outBuffer.setLength(outLength);
                    outBuffer.setOffset(outOffset);
                }
                else
                {
                    int length = (input == null) ? 0 : input.length;
                    short[] output
                        = validateShortArraySize(outBuffer, length);

                    if ((input != null) && (output != null))
                        System.arraycopy(input, 0, output, 0, length);
                    outBuffer.setFormat(inBuffer.getFormat());
                    outBuffer.setLength(inBuffer.getLength());
                    outBuffer.setOffset(inBuffer.getOffset());
                }
            }
        }
        else
        {
            int channels = inAudioFormat.getChannels();

            if (outAudioFormat.getChannels() != channels)
                return BUFFER_PROCESSED_FAILED;

            boolean channelsHaveChanged = (this.channels != channels);

            if (channelsHaveChanged
                    || (this.inputSampleRate != inSampleRate)
                    || (this.outputSampleRate != outSampleRate))
            {
                if (channelsHaveChanged && (resampler != 0))
                {
                    Speex.speex_resampler_destroy(resampler);
                    resampler = 0;
                }
                if (resampler == 0)
                {
                    resampler
                        = Speex.speex_resampler_init(
                                channels,
                                inSampleRate,
                                outSampleRate,
                                Speex.SPEEX_RESAMPLER_QUALITY_VOIP,
                                0);
                }
                else
                {
                    Speex.speex_resampler_set_rate(
                            resampler,
                            inSampleRate,
                            outSampleRate);
                }
                if (resampler != 0)
                {
                    this.inputSampleRate = inSampleRate;
                    this.outputSampleRate = outSampleRate;
                    this.channels = channels;
                }
            }
            if (resampler == 0)
                return BUFFER_PROCESSED_FAILED;

            byte[] in = (byte[]) inBuffer.getData();
            int inLength = inBuffer.getLength();
            int frameSize
                = channels * (inAudioFormat.getSampleSizeInBits() / 8);
            /*
             * XXX The numbers of input and output samples which are to be
             * specified to the function speex_resampler_process_interleaved_int
             * are per-channel.
             */
            int inSampleCount = inLength / frameSize;
            int outSampleCount = (inSampleCount * outSampleRate) / inSampleRate;
            int outOffset = outBuffer.getOffset();
            byte[] out
                = validateByteArraySize(
                        outBuffer,
                        outSampleCount * frameSize + outOffset,
                        outOffset != 0);

            /*
             * XXX The method Speex.speex_resampler_process_interleaved_int will
             * crash if in is null.
             */
            if (inSampleCount == 0)
            {
                outSampleCount = 0;
            }
            else
            {
                int inOffset = inBuffer.getOffset();

                outSampleCount
                    = Speex.speex_resampler_process_interleaved_int(
                            resampler,
                            in, inOffset, inSampleCount,
                            out, outOffset, outSampleCount);

                /*
                 * Report how many bytes of inBuffer have been consumed in the
                 * sample rate conversion.
                 */
                int resampled = inSampleCount * frameSize;

                inLength -= resampled;
                if (inLength < 0)
                    inLength = 0;
                inBuffer.setLength(inLength);
                inBuffer.setOffset(inOffset + resampled);
            }
            outBuffer.setFormat(outAudioFormat);
            outBuffer.setLength(outSampleCount * frameSize);
            outBuffer.setOffset(outOffset);
        }

        outBuffer.setDuration(inBuffer.getDuration());
        outBuffer.setEOM(inBuffer.isEOM());
        outBuffer.setFlags(inBuffer.getFlags());
        outBuffer.setHeader(inBuffer.getHeader());
        outBuffer.setSequenceNumber(inBuffer.getSequenceNumber());
        outBuffer.setTimeStamp(inBuffer.getTimeStamp());

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Get the output formats matching a specific input format.
     *
     * @param inputFormat the input format to get the matching output formats of
     * @return the output formats matching the specified input format
     * @see AbstractCodecExt#getMatchingOutputFormats(Format)
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        Class<?> inDataType = inputFormat.getDataType();
        List<Format> matchingOutputFormats = new ArrayList<Format>();

        if (inputFormat instanceof AudioFormat)
        {
            AudioFormat inAudioFormat = (AudioFormat) inputFormat;
            int inChannels = inAudioFormat.getChannels();
            double inSampleRate = inAudioFormat.getSampleRate();

            for (Format supportedFormat : SUPPORTED_FORMATS)
            {
                AudioFormat supportedAudioFormat
                    = (AudioFormat) supportedFormat;

                if (supportedAudioFormat.getChannels() != inChannels)
                    continue;

                if ((Format.byteArray.equals(supportedFormat.getDataType())
                            && Format.byteArray.equals(inDataType))
                        || (supportedAudioFormat.getSampleRate()
                                == inSampleRate))
                {
                    matchingOutputFormats.add(supportedFormat);
                }
            }
        }
        return
            matchingOutputFormats.toArray(
                    new Format[matchingOutputFormats.size()]);
    }

    /**
     * Sets the <tt>Format</tt> of the media data to be input for processing in
     * this <tt>Codec</tt>.
     *
     * @param format the <tt>Format</tt> of the media data to be input for
     * processing in this <tt>Codec</tt>
     * @return the <tt>Format</tt> of the media data to be input for processing
     * in this <tt>Codec</tt> if <tt>format</tt> is compatible with this
     * <tt>Codec</tt>; otherwise, <tt>null</tt>
     * @see AbstractCodecExt#setInputFormat(Format)
     */
    @Override
    public Format setInputFormat(Format format)
    {
        AudioFormat inFormat = (AudioFormat) super.setInputFormat(format);

        if (inFormat != null)
        {
            double outSampleRate;
            Class<?> outDataType;

            if (outputFormat == null)
            {
                outSampleRate = inFormat.getSampleRate();
                outDataType = inFormat.getDataType();
            }
            else
            {
                AudioFormat outAudioFormat = (AudioFormat) outputFormat;

                outSampleRate = outAudioFormat.getSampleRate();
                outDataType = outAudioFormat.getDataType();
                /*
                 * Conversion between data types is only supported when not
                 * resampling but rather passing through.
                 */
                if (outSampleRate != inFormat.getSampleRate())
                    outDataType = inFormat.getDataType();
            }

            setOutputFormat(
                    new AudioFormat(
                            inFormat.getEncoding(),
                            outSampleRate,
                            inFormat.getSampleSizeInBits(),
                            inFormat.getChannels(),
                            inFormat.getEndian(),
                            inFormat.getSigned(),
                            Format.NOT_SPECIFIED,
                            Format.NOT_SPECIFIED,
                            outDataType));
        }
        return inFormat;
    }
}
