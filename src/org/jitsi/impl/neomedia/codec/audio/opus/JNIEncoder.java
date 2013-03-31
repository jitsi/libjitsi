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
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.util.*;

import java.awt.*;
import java.util.*;

/**
 * Implements an opus encoder.
 *
 * @author Boris Grozev
 */
public class JNIEncoder
    extends AbstractCodec2
    implements PacketLossAwareEncoder, FormatParametersAwareCodec
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

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>JNIEncoder</tt> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS
        = new Format[] { new AudioFormat(
                Constants.OPUS_RTP,
                48000,
                Format.NOT_SPECIFIED,
                1,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED,
                Format.byteArray) };

    /**
     * Set the supported input formats.
     */
    static
    {
        /*
         * If the Opus class or its supporting JNI library are not functional,
         * it is too late to discover the fact in #doOpen() because a JNIEncoder
         * instance has already been initialized and it has already signaled
         * that the Opus codec is supported. 
         */
        Opus.assertOpusIsFunctional();

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
     * Codec audio bandwidth, obtained from configuration.
     */
    private int bandwidthConfig;

    /**
     *  Bitrate in bits per second setting, obtained from the configuration.
     */
    private int bitrateConfig;

    /**
     * Number of channels to use, default to 1.
     */
    private int channels = 1;

    /**
     * Complexity setting, obtained from configuration.
     */
    private int complexityConfig;

    /**
     * The pointer to the native OpusEncoder structure
     */
    private long encoder = 0;

    /**
     * Frame size in ms (2.5, 5, 10, 20, 40 or 60). Default to 20
     */
    private double frameSize = 20;

    /**
     * The <tt>Logger</tt> used by this <tt>JNIEncoder</tt> instance
     * for logging output.
     */
    private static final Logger logger = Logger.getLogger(JNIEncoder.class);

    /**
     * The minimum expected packet loss percentage to set to the encoder.
     */
    private int minPacketLoss = 0;

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
     * Whether to use DTX, obtained from configuration.
     */
    private boolean useDtxConfig;

    /**
     * Whether to use FEC, obtained from configuration.
     */
    private boolean useFecConfig;


    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public JNIEncoder()
    {
        super("Opus JNI Encoder",
               AudioFormat.class,
               SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;

        addControl(this);
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
        AudioFormat inputFormat = (AudioFormat) getInputFormat();
        int sampleRate = (int)inputFormat.getSampleRate();
        channels = inputFormat.getChannels();

        encoder = Opus.encoder_create(sampleRate, channels);
        if (encoder == 0)
            throw new ResourceUnavailableException("opus_encoder_create()");

        //Set encoder options according to user configuration
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        String str;

        str = cfg.getString(Constants.PROP_OPUS_BANDWIDTH, "auto");
        bandwidthConfig = Opus.OPUS_AUTO;
        if("fb".equals(str))
            bandwidthConfig = Opus.BANDWIDTH_FULLBAND;
        else if("swb".equals(str))
            bandwidthConfig = Opus.BANDWIDTH_SUPERWIDEBAND;
        else if("wb".equals(str))
            bandwidthConfig = Opus.BANDWIDTH_WIDEBAND;
        else if("mb".equals(str))
            bandwidthConfig = Opus.BANDWIDTH_MEDIUMBAND;
        else if("nb".equals(str))
            bandwidthConfig = Opus.BANDWIDTH_NARROWBAND;

        Opus.encoder_set_bandwidth(encoder, bandwidthConfig);

        bitrateConfig = 1000 * //configuration is in kilobits per second
                cfg.getInt(Constants.PROP_OPUS_BITRATE, 32);
        if(bitrateConfig < 500)
            bitrateConfig = 500;
        if(bitrateConfig > 512000)
            bitrateConfig = 512000;
        Opus.encoder_set_bitrate(encoder, bitrateConfig);

        complexityConfig = cfg.getInt(Constants.PROP_OPUS_COMPLEXITY, 10);
        Opus.encoder_set_complexity(encoder, complexityConfig);

        useFecConfig = cfg.getBoolean(Constants.PROP_OPUS_FEC, true);
        Opus.encoder_set_inband_fec(encoder, useFecConfig ? 1 : 0);

        minPacketLoss = cfg.getInt(
                Constants.PROP_OPUS_MIN_EXPECTED_PACKET_LOSS, 1);
        Opus.encoder_set_packet_loss_perc(encoder, minPacketLoss);

        useDtxConfig = cfg.getBoolean(Constants.PROP_OPUS_DTX, true);
        Opus.encoder_set_dtx(encoder, useDtxConfig ? 1 : 0);

        if(logger.isDebugEnabled())
        {
            int b = Opus.encoder_get_bandwidth(encoder);
            logger.debug("Encoder settings: audio bandwidth " +
                   (b == Opus.BANDWIDTH_FULLBAND ? "fb"
                    : b == Opus.BANDWIDTH_SUPERWIDEBAND ? "swb"
                    : b == Opus.BANDWIDTH_WIDEBAND ? "wb"
                    : b == Opus.BANDWIDTH_MEDIUMBAND ? "mb"
                    : "nb") +
                ", bitrate " + Opus.encoder_get_bitrate(encoder) + ", DTX " +
                Opus.encoder_get_dtx(encoder) + ", FEC " + useFecConfig);
                //TODO: add Opus.encoder_get_inband_fec()
        }
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

        byte[] output
            = validateByteArraySize(outputBuffer, Opus.MAX_PACKET, false);

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
     * Stub. Only added in order to implement the
     * <tt>Control</tt> interface.
     *
     * @return null
     */
    public Component getControlComponent()
    {
        return null;
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
     * Updates the encoder's expected packet loss percentage to the bigger of
     * <tt>percentage</tt> and <tt>this.minPacketLoss</tt>.
     *
     * @param percentage the expected packet loss percentage to set
     */
    public void setExpectedPacketLoss(int percentage)
    {
        if(opened)
            Opus.encoder_set_packet_loss_perc(encoder,
                    (percentage > minPacketLoss) ? percentage : minPacketLoss);

        if(logger.isTraceEnabled())
            logger.trace("Updating expected packet loss: " + percentage
                    + " (minimum " + minPacketLoss + ")");
    }

    /**
     * Sets the format parameters.
     *
     * @param fmtps the format parameters to set
     */
    public void setFormatParameters(Map<String, String> fmtps)
    {
        if(logger.isDebugEnabled())
            logger.debug("Setting format parameters: " + fmtps);

        String str = fmtps.get("maxaveragebitrate");
        /* TODO: Use the default value for the 'maxaveragebitrate' as defined here:
         * http://tools.ietf.org/html/draft-spittka-payload-rtp-opus-02#section-6.1
         */
        int maxBitrate = 40000;
        try
        {
            maxBitrate = Integer.parseInt(str);
        }
        catch(Exception e)
        {} //ignore parsing errors

        Opus.encoder_set_bitrate(
                encoder,
                (maxBitrate<bitrateConfig) ? maxBitrate : bitrateConfig);

        //DTX is off if not specified
        boolean useDtx = "1".equals(fmtps.get("usedtx"));
        Opus.encoder_set_dtx(encoder, (useDtx && useDtxConfig) ? 1 : 0);

        //FEC is on if not specified
        str = fmtps.get("useinbandfec");
        boolean useFec = (str == null) || str.equals("1");
        Opus.encoder_set_inband_fec(encoder, (useFec && useFecConfig) ? 1 : 0);
    }
}
