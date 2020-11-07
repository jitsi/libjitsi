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
package org.jitsi.impl.neomedia.codec.audio.silk;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.utils.logging.*;

import java.awt.*;

/**
 * Implements the SILK encoder as an FMJ/JMF <tt>Codec</tt>.
 *
 * @author Dingxin Xu
 * @author Boris Grozev
 */
public class JavaEncoder
    extends AbstractCodec2
    implements PacketLossAwareEncoder
{
    /**
     * The <tt>Logger</tt> used by this <tt>JavaEncoder</tt> instance
     * for logging output.
     */
    private final Logger logger
            = Logger.getLogger(JavaEncoder.class);

    private static final int BITRATE = 40000;

    private static final int COMPLEXITY = 2;

    /**
     * The maximum number of output payload bytes per input frame. Equals peak
     * bitrate of 100 kbps.
     */
    static final int MAX_BYTES_PER_FRAME = 250;

    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>JavaEncoder</tt> instances.
     */
    static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>JavaEncoder</tt> instances.
     */
    static final Format[] SUPPORTED_OUTPUT_FORMATS;

    /**
     * The list of sample rates of audio data supported as input and output by
     * <tt>JavaEncoder</tt> instances.
     */
    private static final double[] SUPPORTED_SAMPLE_RATES
        = new double[] { 8000, 12000, 16000, 24000 };

    /**
     * Default value for the use DTX setting
     */
    private static final boolean USE_DTX = false;

    /**
     * If <tt>alwaysExpectPacketLoss</tt> is <tt>true</tt> the expected
     * packet loss will always be set at or above this threshold.
     */
    private static final int MIN_PACKET_LOSS_PERCENTAGE = 3;

    /**
     * Whether to use FEC or not.
     */
    private boolean useFec;

    /**
     * Whether to always assume packet loss and set the encoder's expected
     * packet loss over <tt>MIN_PACKET_LOSS_PERCENTAGE</tt>.
     */
    private boolean alwaysAssumePacketLoss = true;

    /**
     * The duration an output <tt>Buffer</tt> produced by this <tt>Codec</tt>
     * in nanosecond.
     */
    private int duration = JavaDecoder.FRAME_DURATION * 1000000;

    static
    {
        int supportedCount = SUPPORTED_SAMPLE_RATES.length;

        SUPPORTED_INPUT_FORMATS = new Format[supportedCount];
        SUPPORTED_OUTPUT_FORMATS = new Format[supportedCount];
        for (int i = 0; i < supportedCount; i++)
        {
            double supportedSampleRate = SUPPORTED_SAMPLE_RATES[i];

            SUPPORTED_INPUT_FORMATS[i]
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        supportedSampleRate,
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.shortArray);
            SUPPORTED_OUTPUT_FORMATS[i]
                = new AudioFormat(
                        Constants.SILK_RTP,
                        supportedSampleRate,
                        Format.NOT_SPECIFIED /* sampleSizeInBits */,
                        1,
                        Format.NOT_SPECIFIED /* endian */,
                        Format.NOT_SPECIFIED /* signed */,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.byteArray);
        }
    }

    /**
     * The SILK encoder control (structure).
     */
    private SKP_SILK_SDK_EncControlStruct encControl;

    /**
     * The SILK encoder state.
     */
    private SKP_Silk_encoder_state_FLP encState;

    /**
     * The length of an output payload as reported by
     * {@link Silk_enc_API#SKP_Silk_SDK_Encode(Object, SKP_SILK_SDK_EncControlStruct, short[], int, int, byte[], int, short[])}.
     */
    private final short[] outputLength = new short[1];

    /**
     * Initializes a new <code>JavaEncoder</code> instance.
     */
    public JavaEncoder()
    {
        super("SILK Encoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;

        ConfigurationService cfg = LibJitsi.getConfigurationService();
        //TODO: we should have a default value dependent on the SDP parameters
        //here.
        useFec = cfg.getBoolean(Constants.PROP_SILK_FEC, true);
        alwaysAssumePacketLoss
                = cfg.getBoolean(Constants.PROP_SILK_ASSUME_PL, true);

        //Update the statically defined value for "speech activity threshold"
        //according to our configuration
        String satStr = cfg.getString(Constants.PROP_SILK_FEC_SAT, "0.5");
        float sat = DefineFLP.LBRR_SPEECH_ACTIVITY_THRES;
        if ((satStr != null) && (satStr.length() != 0))
        {
            try
            {
                sat = Float.parseFloat(satStr);
            }
            catch (NumberFormatException nfe) {}
        }
        DefineFLP.LBRR_SPEECH_ACTIVITY_THRES = sat;

        addControl(this);
    }

    @Override
    protected void doClose()
    {
        encState = null;
        encControl = null;
    }

    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
        encState = new SKP_Silk_encoder_state_FLP();
        encControl = new SKP_SILK_SDK_EncControlStruct();
        if (EncAPI.SKP_Silk_SDK_InitEncoder(encState, encControl) != 0)
        {
            throw new ResourceUnavailableException(
                    "EncAPI.SKP_Silk_SDK_InitEncoder");
        }

        AudioFormat inputFormat = (AudioFormat) getInputFormat();
        double sampleRate = inputFormat.getSampleRate();
        int channels = inputFormat.getChannels();


        encControl.API_sampleRate = (int) sampleRate;
        encControl.bitRate = BITRATE;
        encControl.complexity = COMPLEXITY;
        encControl.maxInternalSampleRate = encControl.API_sampleRate;
        setExpectedPacketLoss(0);
        encControl.packetSize
            = (int)
                ((JavaDecoder.FRAME_DURATION * sampleRate * channels) / 1000);
        encControl.useDTX = USE_DTX ? 1 : 0;
        encControl.useInBandFEC = useFec ? 1 : 0;
    }

    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        short[] inputData = (short[]) inputBuffer.getData();
        int inputLength = inputBuffer.getLength();
        int inputOffset = inputBuffer.getOffset();

        if (inputLength > encControl.packetSize)
            inputLength = encControl.packetSize;

        byte[] outputData
            = validateByteArraySize(outputBuffer, MAX_BYTES_PER_FRAME, false);
        int outputOffset = 0;

        int processed;

        outputLength[0] = MAX_BYTES_PER_FRAME;
        if (EncAPI.SKP_Silk_SDK_Encode(
                    encState, encControl,
                    inputData, inputOffset, inputLength,
                    outputData, outputOffset, outputLength)
                == 0)
        {
            outputBuffer.setLength(outputLength[0]);
            outputBuffer.setOffset(outputOffset);
            processed = BUFFER_PROCESSED_OK;
        }
        else
            processed = BUFFER_PROCESSED_FAILED;

        inputBuffer.setLength(inputBuffer.getLength() - inputLength);
        inputBuffer.setOffset(inputBuffer.getOffset() + inputLength);

        if (processed != BUFFER_PROCESSED_FAILED)
        {
            if(processed == BUFFER_PROCESSED_OK)
            {
                updateOutput(
                    outputBuffer,
                    getOutputFormat(), outputBuffer.getLength(),
                    outputBuffer.getOffset());
                outputBuffer.setDuration(duration);
            }

            if (inputBuffer.getLength() > 0)
                processed |= INPUT_BUFFER_NOT_CONSUMED;
        }

        return processed;
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
        return
            getMatchingOutputFormats(
                    inputFormat,
                    SUPPORTED_INPUT_FORMATS,
                    SUPPORTED_OUTPUT_FORMATS);
    }

    static Format[] getMatchingOutputFormats(
            Format inputFormat,
            Format[] supportedInputFormats,
            Format[] supportedOutputFormats)
    {
        if (inputFormat == null)
            return supportedOutputFormats;
        else
        {
            Format matchingInputFormat
                = matches(inputFormat, supportedInputFormats);

            if (matchingInputFormat == null)
                return new Format[0];
            else
            {
                AudioFormat matchingInputAudioFormat
                    = (AudioFormat) matchingInputFormat.intersects(inputFormat);
                Format outputFormat
                    = new AudioFormat(
                            null /* encoding */,
                            matchingInputAudioFormat.getSampleRate(),
                            Format.NOT_SPECIFIED /* sampleSizeInBits */,
                            Format.NOT_SPECIFIED /* channels */,
                            Format.NOT_SPECIFIED /* endian */,
                            Format.NOT_SPECIFIED /* signed */,
                            Format.NOT_SPECIFIED /* frameSizeInBits */,
                            Format.NOT_SPECIFIED /* frameRate */,
                            null /* dataType */);
                Format matchingOutputFormat
                    = matches(outputFormat, supportedOutputFormats);

                if (matchingOutputFormat == null)
                    return new Format[0];
                else
                    return
                        new Format[]
                        {
                            matchingOutputFormat.intersects(outputFormat)
                        };
            }
        }
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
                                return JavaEncoder.this.duration;
                            }
                        });
        }
        return outputFormat;
    }

    /**
     * Updates the encoder's packet loss percentage. Takes into account
     * <tt>this.alwaysAssumePacketLoss</tt>.
     *
     * @param percentage the expected packet loss percentage to set.
     */
    public void setExpectedPacketLoss(int percentage)
    {
        if(opened)
        {
            if(alwaysAssumePacketLoss &&
                    MIN_PACKET_LOSS_PERCENTAGE >= percentage)
                percentage = MIN_PACKET_LOSS_PERCENTAGE;
            encControl.packetLossPercentage = percentage;
            if(logger.isTraceEnabled())
                logger.trace("Setting expected packet loss to: "
                        + percentage);
        }
    }

    /**
     * Stub. Only added in order to implement the
     * <tt>PacketLossAwareEncoder</tt> interface.
     *
     * @return null
     */
    public Component getControlComponent()
    {
        return null;
    }
}
