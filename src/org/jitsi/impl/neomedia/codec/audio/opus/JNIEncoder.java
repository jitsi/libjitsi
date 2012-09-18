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
 * Implements an opus encoder
 *
 * @author Boris Grozev
 */
public class JNIEncoder
    extends AbstractCodecExt
{
    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>JNIEncoder</tt> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of sample rates of audio data supported as input by
     * <tt>JNIEncoder</tt> instances.
     * This codec does support 8, 12, 16, 24 and 48kHz input. Just enable them
     * here if needed. The reason lower rates are disabled is that enabling for
     * example 8kHz causes FMJ to always use it.
     */
    static final double[] SUPPORTED_INPUT_SAMPLE_RATES
        = new double[] { 48000 };
        //= new double[] { 8000, 12000, 16000, 24000, 48000 };

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>JNIEncoder</tt> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS
        = new Format[] { new AudioFormat(Constants.OPUS_RTP) };

    /**
     * Set the supported input formats.
     */
    static
    {
        int supportedInputCount = SUPPORTED_INPUT_SAMPLE_RATES.length;

        SUPPORTED_INPUT_FORMATS = new Format[supportedInputCount*2];
        for (int i = 0; i < supportedInputCount; i++)
        {
            SUPPORTED_INPUT_FORMATS[i]
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_INPUT_SAMPLE_RATES[i],
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED,
                        Format.byteArray);
        }
        for (int i = 0; i < supportedInputCount; i++)
        {
            SUPPORTED_INPUT_FORMATS[i+supportedInputCount]
                    = new AudioFormat(
                    AudioFormat.LINEAR,
                    SUPPORTED_INPUT_SAMPLE_RATES[i],
                    16,
                    2,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED,
                    Format.NOT_SPECIFIED,
                    Format.byteArray);
        }
    }

    /**
     * The bytes from an input <tt>Buffer</tt> from a previous call to
     * {@link #process(Buffer, Buffer)} that this <tt>Codec</tt> didn't process
     * because the total number of bytes was less than {@link #inputFrameSize()}
     * need to be prepended to a subsequent input <tt>Buffer</tt> in order to
     * process a total of {@link #inputFrameSize()} bytes.
     */
    private byte[] previousInput = null;

    /**
     * The length of the audio data in {@link #previousInput}.
     */
    private int previousInputLength = 0;

    /**
     * The pointer to the native OpusEncoder structure
     */
    private long encoder = 0;

    /**
     * Number of channels to use, default to 1.
     */
    private int channels = 1;

    /**
     * Frame size in ms (2.5, 5, 10, 20, 40 or 60). Default to 20
     */
    private double frameSize = 20;

    /**
     * Returns the number of bytes that we need to read from the input buffer
     * in order ot fill a frame of <tt>frameSize</tt>. Depends on the input
     * sample frequency, the number of channels and <tt>frameSize</tt>
     *
     * @return the number of bytes that we need to read from the input buffer
     * in order ot fill a frame of <tt>frameSize</tt>. Depends on the input
     * sample frequency, the number of channels and <tt>frameSize</tt>
     */
    private int inputFrameSize()
    {
        int fs =
	    (int) (
           2 /* sizeof(short) */
           * channels
           *  ((AudioFormat)getInputFormat()).getSampleRate() /* samples in 1s */
           *   frameSize /* milliseconds */
           ) / 1000;

	    return fs;
    }

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public JNIEncoder()
    {
        super("Opus JNI Encoder",
               AudioFormat.class,
               SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    /**
     * @see AbstractCodecExt#doClose()
     */
    protected void doClose()
    {
        if (encoder != 0)
        {
           Opus.encoder_destroy(encoder);
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

    //TODO: make a ParametrizedAudioFormat (or something) class, and use it here
    //to set the encoder parameters
	AudioFormat inputFormat = (AudioFormat) getInputFormat();
	int sampleRate = (int)inputFormat.getSampleRate();
    channels = inputFormat.getChannels();

    encoder = Opus.encoder_create(sampleRate, channels);
    if (encoder == 0)
        throw new ResourceUnavailableException("opus_encoder_create()");

    //set default encoder settings
    Opus.encoder_set_bitrate(encoder, 60000);
	Opus.encoder_set_bandwidth(encoder, Opus.BANDWIDTH_FULLBAND);
	Opus.encoder_set_vbr(encoder, 1);
	Opus.encoder_set_complexity(encoder, 10);
	Opus.encoder_set_inband_fec(encoder, 0);
	Opus.encoder_set_dtx(encoder, 1);
	Opus.encoder_set_force_channels(encoder, 1);
    }

    /**
     * Processes (encode) a specific input <tt>Buffer</tt>.
     *
     * @param inputBuffer input buffer
     * @param outputBuffer output buffer
     * @return <tt>BUFFER_PROCESSED_OK</tt> if buffer has been successfully
     * processed
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
        int inputLength = inputBuffer.getLength();
        int inputOffset = inputBuffer.getOffset();


	    int inputBytesNeeded = inputFrameSize();

        if ((previousInput != null) && (previousInputLength > 0))
        {
            if (previousInputLength < inputBytesNeeded)
            {
                if (previousInput.length < inputBytesNeeded)
                {
                    byte[] newPreviousInput = new byte[inputBytesNeeded];

                    System.arraycopy(
                            previousInput, 0,
                            newPreviousInput, 0,
                            previousInput.length);
                    previousInput = newPreviousInput;
                }

                int bytesToCopyFromInputToPreviousInput
                    = Math.min(
                            inputBytesNeeded - previousInputLength,
                            inputLength);

                if (bytesToCopyFromInputToPreviousInput > 0)
                {
                    System.arraycopy(
                            input, inputOffset,
                            previousInput, previousInputLength,
                            bytesToCopyFromInputToPreviousInput);
                    previousInputLength += bytesToCopyFromInputToPreviousInput;
                    inputLength -= bytesToCopyFromInputToPreviousInput;
                    inputBuffer.setLength(inputLength);
                    inputBuffer.setOffset(
                            inputOffset + bytesToCopyFromInputToPreviousInput);
                }
            }

            if (previousInputLength == inputBytesNeeded)
            {
                input = previousInput;
                inputOffset = 0;
                previousInputLength = 0;
            }
            else
            {
                outputBuffer.setLength(0);
                discardOutputBuffer(outputBuffer);
                if (inputLength < 1)
                    return BUFFER_PROCESSED_OK;
                else
                    return BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED;
            }
        }
        else if (inputLength < 1)
        {
            outputBuffer.setLength(0);
            discardOutputBuffer(outputBuffer);
            return BUFFER_PROCESSED_OK;
        }
        else if (inputLength < inputBytesNeeded)
        {
            if ((previousInput == null) || (previousInput.length < inputLength))
                previousInput = new byte[inputBytesNeeded];
            System.arraycopy(input, inputOffset, previousInput, 0, inputLength);
            previousInputLength = inputLength;
            outputBuffer.setLength(0);
            discardOutputBuffer(outputBuffer);
            return BUFFER_PROCESSED_OK;
        }
        else
        {
            inputLength -= inputBytesNeeded;
            inputBuffer.setLength(inputLength);
            inputBuffer.setOffset(inputOffset + inputBytesNeeded);
        }



        /* At long last, do the actual encoding. */

        byte[] output = validateByteArraySize(outputBuffer, Opus.MAX_PACKET);

	    int outputLength = Opus.encode(encoder, input, inputOffset,
                inputBytesNeeded / 2, output, Opus.MAX_PACKET);


        if (outputLength < 0)  //error from opus_encode
            return BUFFER_PROCESSED_FAILED;

        if (outputLength > 0)
        {
            outputBuffer.setDuration((long) this.frameSize * 1000 * 1000);
            outputBuffer.setFormat(getOutputFormat());
            outputBuffer.setLength(outputLength);
            outputBuffer.setOffset(0);
        }

        if (inputLength < 1)
            return BUFFER_PROCESSED_OK;
        else
            return BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED;
    }

    /**
     * Get the output format.
     *
     * @return output format
     * @see net.sf.fmj.media.AbstractCodec#getOutputFormat()
     */
    @Override
    public Format getOutputFormat()
    {
        Format outputFormat = super.getOutputFormat();

        if ((outputFormat != null)
                && (outputFormat.getClass() == AudioFormat.class))
        {
            AudioFormat outputAudioFormat = (AudioFormat) outputFormat;

            outputFormat = setOutputFormat(
                new AudioFormat(
                            outputAudioFormat.getEncoding(),
                            outputAudioFormat.getSampleRate(),
                            outputAudioFormat.getSampleSizeInBits(),
                            outputAudioFormat.getChannels(),
                            outputAudioFormat.getEndian(),
                            outputAudioFormat.getSigned(),
                            outputAudioFormat.getFrameSizeInBits(),
                            outputAudioFormat.getFrameRate(),
                            outputAudioFormat.getDataType())
                        {
                            private static final long serialVersionUID = 0L;

                            @Override
                            public long computeDuration(long length)
                            {
                                return ((long) JNIEncoder.this.frameSize)*1000*1000;
                            }
                        });
        }
        return outputFormat;
    }

    /**
     * Sets the input format.
     *
     * @param format format to set
     * @return format
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
                            Constants.SPEEX_RTP,
                            inputSampleRate,
                            Format.NOT_SPECIFIED,
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
