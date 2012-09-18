/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.opus;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;
import org.jitsi.impl.neomedia.codec.*;

/**
 * Implements an Opus decoder
 *
 * @author Boris Grozec
 */
public class JNIDecoder
    extends AbstractCodecExt
{
    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>JNIDecoder</tt> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>JNIDecoder</tt> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS
        = new Format[]
                {
                    new AudioFormat(
                            AudioFormat.LINEAR,
                            48000,
                            16,
                            1,
                            AudioFormat.LITTLE_ENDIAN,
                            AudioFormat.SIGNED,
                            Format.NOT_SPECIFIED,
                            Format.NOT_SPECIFIED,
                            Format.byteArray)
                };

    static
    {
        SUPPORTED_INPUT_FORMATS
	    = new Format[] {new AudioFormat(Constants.OPUS_RTP)};
    }

    /**
     * Pointer to the native OpusDecoder structure
     */
    private long decoder = 0;

    /**
     * Number of channels
     */
    private int channels = 1;

    /**
     * Output sampling rate
     */
    private int outputSamplingRate = 48000;

    /**
     * Initializes a new <tt>JNIDecoder</tt> instance.
     */
    public JNIDecoder()
    {
        super(
            "Opus JNI Decoder",
            AudioFormat.class,
            SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    /**
     * @see AbstractCodecExt#doClose()
     */
    protected void doClose()
    {
        if (decoder != 0)
        {
            Opus.decoder_destroy(decoder);
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
    protected void doOpen()
        throws ResourceUnavailableException
    {
        decoder = Opus.decoder_create(outputSamplingRate, channels);
        if (decoder == 0)
            throw new ResourceUnavailableException("opus_decoder_create");
    }

    /**
     * Decodes an Opus packet
     *
     * @param inputBuffer input <tt>Buffer</tt>
     * @param outputBuffer output <tt>Buffer</tt>
     * @return <tt>BUFFER_PROCESSED_OK</tt> if <tt>inBuffer</tt> has been
     * successfully processed
     * @see AbstractCodecExt#doProcess(Buffer, Buffer)
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        Format inputFormat = inputBuffer.getFormat();

        if ((inputFormat != null)
                && (inputFormat != this.inputFormat)
                && !inputFormat.equals(this.inputFormat))
        {
            if (null == setInputFormat(inputFormat))
                return BUFFER_PROCESSED_FAILED;
        }
        inputFormat = this.inputFormat;

        byte[] input = (byte[]) inputBuffer.getData();
        int inputOffset = inputBuffer.getOffset();
        int inputLength = inputBuffer.getLength();

        int outputLength =  Opus.decoder_get_nb_samples(decoder,
                input, inputOffset, inputLength) * 2 /* sizeof(short) */;
        byte[] output = validateByteArraySize(outputBuffer, outputLength);

        int samplesCount = Opus.decode(decoder, input, inputOffset, inputLength,
                                                output, outputLength);

        if (samplesCount > 0)
        {
            outputBuffer.setDuration(
                    (samplesCount*1000*1000)/outputSamplingRate);
            outputBuffer.setFormat(getOutputFormat());
            outputBuffer.setLength(2*samplesCount); //16bit pcm
            outputBuffer.setOffset(0);
        }
        else
        {
            outputBuffer.setLength(0);
            discardOutputBuffer(outputBuffer);
        }

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Get all supported output <tt>Format</tt>s.
     *
     * @param inputFormat input <tt>Format</tt> to determine corresponding output
     * <tt>Format/tt>s
     * @return array of supported <tt>Format</tt>
     * @see AbstractCodecExt#getMatchingOutputFormats(Format)
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        AudioFormat inputAudioFormat = (AudioFormat) inputFormat;

        return
            new Format[]
                    {
                        new AudioFormat(
                                AudioFormat.LINEAR,
                                inputAudioFormat.getSampleRate(),
                                16,
                                1,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                Format.byteArray)
                    };
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
        Format inputFormat = super.setInputFormat(format);

        if (inputFormat != null)
        {
            double outputSampleRate;
            int outputChannels;

            if (outputFormat == null)
            {
                outputSampleRate = Format.NOT_SPECIFIED;
                outputChannels = Format.NOT_SPECIFIED;
            }
            else
            {
                AudioFormat outputAudioFormat = (AudioFormat) outputFormat;

                outputSampleRate = outputAudioFormat.getSampleRate();
                outputChannels = outputAudioFormat.getChannels();
            }

            AudioFormat inputAudioFormat = (AudioFormat) inputFormat;
            double inputSampleRate = inputAudioFormat.getSampleRate();
            int inputChannels = inputAudioFormat.getChannels();

            if ((outputSampleRate != inputSampleRate)
                    || (outputChannels != inputChannels))
            {
                setOutputFormat(
                    new AudioFormat(
                            AudioFormat.LINEAR,
                            inputSampleRate,
                            16,
                            inputChannels,
                            AudioFormat.LITTLE_ENDIAN,
                            AudioFormat.SIGNED,
                            Format.NOT_SPECIFIED,
                            Format.NOT_SPECIFIED,
                            Format.byteArray));
            }
        }
        return inputFormat;
    }
}
