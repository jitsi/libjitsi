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
 * Implements an Opus encoder.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 */
public class JNIEncoder
    extends AbstractCodec2
    implements FormatParametersAwareCodec,
               PacketLossAwareEncoder
{
    /**
     * The <tt>Logger</tt> used by this <tt>JNIEncoder</tt> instance
     * for logging output.
     */
    private static final Logger logger = Logger.getLogger(JNIEncoder.class);

    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>JNIEncoder</tt> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of sample rates of audio data supported as input by
     * <tt>JNIEncoder</tt> instances.
     * <p>
     * The implementation does support 8, 12, 16, 24 and 48kHz but the lower
     * sample rates are not listed to prevent FMJ from defaulting to them.
     * </p>
     */
    static final double[] SUPPORTED_INPUT_SAMPLE_RATES
        = new double[] { 48000 };

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>JNIEncoder</tt> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS
        = new Format[]
                {
                    new AudioFormat(
                            Constants.OPUS_RTP,
                            48000,
                            /* sampleSizeInBits */ Format.NOT_SPECIFIED,
                            /* channels */ 2,
                            /* endian */ Format.NOT_SPECIFIED,
                            /* signed */ Format.NOT_SPECIFIED,
                            /* frameSizeInBits */ Format.NOT_SPECIFIED,
                            /* frameRate */ Format.NOT_SPECIFIED,
                            Format.byteArray)
                };

    /**
     * Sets the supported input formats.
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

        SUPPORTED_INPUT_FORMATS = new Format[supportedInputCount];
        //SUPPORTED_INPUT_FORMATS = new Format[supportedInputCount*2];
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
                        /* frameSizeInBits */ Format.NOT_SPECIFIED,
                        /* frameRate */ Format.NOT_SPECIFIED,
                        Format.byteArray);
        }
        /*
         * Using stereo input formats leads to problems (at least when used with
         * pulse audio). It is unclear whether they are rooted in this encoder
         * or somewhere else in the code. So stereo input formats are disabled
         * until we make sure that they work properly.
         */
        //for (int i = 0; i < supportedInputCount; i++)
        //{
        //    SUPPORTED_INPUT_FORMATS[i+supportedInputCount]
        //        = new AudioFormat(
        //                AudioFormat.LINEAR,
        //                SUPPORTED_INPUT_SAMPLE_RATES[i],
        //                16,
        //                2,
        //                AudioFormat.LITTLE_ENDIAN,
        //                AudioFormat.SIGNED,
        //                /* frameSizeInBits */ Format.NOT_SPECIFIED,
        //                /* frameRate */ Format.NOT_SPECIFIED,
        //                Format.byteArray);
        //}
    }

    /**
     * Codec audio bandwidth, obtained from configuration.
     */
    private int bandwidthConfig;

    /**
     * The bitrate in bits per second obtained from the configuration and set on
     * {@link #encoder}.
     */
    private int bitrate;

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
     * The size in bytes of an audio frame input by this instance. Automatically
     * calculated, based on {@link #frameSizeInMillis} and the
     * <tt>inputFormat</tt> of this instance.
     */
    private int frameSizeInBytes;

    /**
     * The size/duration in milliseconds of an audio frame output by this
     * instance. The possible values are: 2.5, 5, 10, 20, 40 and 60. The default
     * value is 20.
     */
    private final int frameSizeInMillis = 20;

    /**
     * The size in samples per channel of an audio frame input by this instance.
     * Automatically calculated, based on {@link #frameSizeInMillis} and the
     * <tt>inputFormat</tt> of this instance.
     */
    private int frameSizeInSamplesPerChannel;

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
    private byte[] prevIn = null;

    /**
     * The length of the audio data in {@link #prevIn}.
     */
    private int prevInLength = 0;

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
        super("Opus JNI Encoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;

        addControl(this);
    }

    /**
     * {@inheritDoc}
     *
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected void doClose()
    {
        if (encoder != 0)
        {
           Opus.encoder_destroy(encoder);
           encoder = 0;
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
     * @see AbstractCodec2#doOpen()
     */
    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
        AudioFormat inputFormat = (AudioFormat) getInputFormat();
        int sampleRate = (int) inputFormat.getSampleRate();

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

        bitrate = 1000 * //configuration is in kilobits per second
                cfg.getInt(Constants.PROP_OPUS_BITRATE, 32);
        if(bitrate < 500)
            bitrate = 500;
        if(bitrate > 512000)
            bitrate = 512000;
        Opus.encoder_set_bitrate(encoder, bitrate);

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
                    : "nb")
                + ", bitrate " + Opus.encoder_get_bitrate(encoder)
                + ", DTX " + Opus.encoder_get_dtx(encoder)
                + ", FEC " + Opus.encoder_get_inband_fec(encoder));
        }
    }

    /**
     * Processes (i.e. encodes) a specific input <tt>Buffer</tt>.
     *
     * @param inBuffer the <tt>Buffer</tt> from which the media to be encoded is
     * to be read
     * @param outBuffer the <tt>Buffer</tt> into which the encoded media is to
     * be written
     * @return <tt>BUFFER_PROCESSED_OK</tt> if the specified <tt>inBuffer</tt>
     * has been processed successfully
     * @see AbstractCodec2#doProcess(Buffer, Buffer)
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        Format inFormat = inBuffer.getFormat();

        if ((inFormat != null)
                && (inFormat != this.inputFormat)
                && !inFormat.equals(this.inputFormat)
                && (null == setInputFormat(inFormat)))
        {
            return BUFFER_PROCESSED_FAILED;
        }

        byte[] in = (byte[]) inBuffer.getData();
        int inLength = inBuffer.getLength();
        int inOffset = inBuffer.getOffset();

        if ((prevIn != null) && (prevInLength > 0))
        {
            if (prevInLength < frameSizeInBytes)
            {
                if (prevIn.length < frameSizeInBytes)
                {
                    byte[] newPrevInput = new byte[frameSizeInBytes];

                    System.arraycopy(prevIn, 0, newPrevInput, 0, prevIn.length);
                    prevIn = newPrevInput;
                }

                int bytesToCopyFromInToPrevInput
                    = Math.min(frameSizeInBytes - prevInLength, inLength);

                if (bytesToCopyFromInToPrevInput > 0)
                {
                    System.arraycopy(
                            in, inOffset,
                            prevIn, prevInLength,
                            bytesToCopyFromInToPrevInput);
                    prevInLength += bytesToCopyFromInToPrevInput;
                    inLength -= bytesToCopyFromInToPrevInput;
                    inBuffer.setLength(inLength);
                    inBuffer.setOffset(inOffset + bytesToCopyFromInToPrevInput);
                }
            }

            if (prevInLength == frameSizeInBytes)
            {
                in = prevIn;
                inOffset = 0;
                prevInLength = 0;
            }
            else
            {
                outBuffer.setLength(0);
                discardOutputBuffer(outBuffer);
                if (inLength < 1)
                    return BUFFER_PROCESSED_OK;
                else
                    return BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED;
            }
        }
        else if (inLength < 1)
        {
            outBuffer.setLength(0);
            discardOutputBuffer(outBuffer);
            return BUFFER_PROCESSED_OK;
        }
        else if (inLength < frameSizeInBytes)
        {
            if ((prevIn == null) || (prevIn.length < inLength))
                prevIn = new byte[frameSizeInBytes];
            System.arraycopy(in, inOffset, prevIn, 0, inLength);
            prevInLength = inLength;
            outBuffer.setLength(0);
            discardOutputBuffer(outBuffer);
            return BUFFER_PROCESSED_OK;
        }
        else
        {
            inLength -= frameSizeInBytes;
            inBuffer.setLength(inLength);
            inBuffer.setOffset(inOffset + frameSizeInBytes);
        }

        // At long last, do the actual encoding.
        byte[] out = validateByteArraySize(outBuffer, Opus.MAX_PACKET, false);
        int outLength
            = Opus.encode(
                    encoder,
                    in, inOffset, frameSizeInSamplesPerChannel,
                    out, 0, out.length);

        if (outLength < 0)  // error from opus_encode
            return BUFFER_PROCESSED_FAILED;

        if (outLength > 0)
        {
            outBuffer.setDuration(((long) frameSizeInMillis) * 1000 * 1000);
            outBuffer.setFormat(getOutputFormat());
            outBuffer.setLength(outLength);
            outBuffer.setOffset(0);
        }

        if (inLength < 1)
            return BUFFER_PROCESSED_OK;
        else
            return BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED;
    }

    /**
     * Implements {@link Control#getControlComponent()}. <tt>JNIEncoder</tt>
     * does not provide user interface of its own.
     *
     * @return <tt>null</tt> to signify that <tt>JNIEncoder</tt> does not
     * provide user interface of its own
     */
    public Component getControlComponent()
    {
        return null;
    }

    /**
     * Gets the <tt>Format</tt> of the media output by this <tt>Codec</tt>.
     *
     * @return the <tt>Format</tt> of the media output by this <tt>Codec</tt>
     * @see net.sf.fmj.media.AbstractCodec#getOutputFormat()
     */
    @Override
    public Format getOutputFormat()
    {
        Format f = super.getOutputFormat();

        if ((f != null) && (f.getClass() == AudioFormat.class))
        {
            AudioFormat af = (AudioFormat) f;

            f
                = setOutputFormat(
                        new AudioFormat(
                                    af.getEncoding(),
                                    af.getSampleRate(),
                                    af.getSampleSizeInBits(),
                                    af.getChannels(),
                                    af.getEndian(),
                                    af.getSigned(),
                                    af.getFrameSizeInBits(),
                                    af.getFrameRate(),
                                    af.getDataType())
                                {
                                    private static final long serialVersionUID
                                        = 0L;

                                    @Override
                                    public long computeDuration(long length)
                                    {
                                        return
                                            ((long) frameSizeInMillis)
                                                * 1000 * 1000;
                                    }
                                });
        }
        return f;
    }

    /**
     * Updates the encoder's expected packet loss percentage to the bigger of
     * <tt>percentage</tt> and <tt>this.minPacketLoss</tt>.
     *
     * @param percentage the expected packet loss percentage to set
     */
    public void setExpectedPacketLoss(int percentage)
    {
        if (opened)
        {
            Opus.encoder_set_packet_loss_perc(
                    encoder,
                    (percentage > minPacketLoss) ? percentage : minPacketLoss);
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "Updating expected packet loss: " + percentage
                            + " (minimum " + minPacketLoss + ")");
            }
        }
    }

    /**
     * Sets the format parameters.
     *
     * @param fmtps the format parameters to set
     */
    public void setFormatParameters(Map<String, String> fmtps)
    {
        if (logger.isDebugEnabled())
            logger.debug("Setting format parameters: " + fmtps);

        /*
         * TODO Use the default value for maxaveragebitrate as defined at
         * http://tools.ietf.org/html/draft-spittka-payload-rtp-opus-02#section-6.1
         */
        int maxaveragebitrate = 40000;

        try
        {
            String s = fmtps.get("maxaveragebitrate");

            if ((s != null) && (s.length() != 0))
                maxaveragebitrate = Integer.parseInt(s);
        }
        catch (Exception e)
        {
            // Ignore and fall back to the default value.
        }
        Opus.encoder_set_bitrate(
                encoder,
                (maxaveragebitrate < bitrate) ? maxaveragebitrate : bitrate);

        // DTX is off unless specified.
        boolean useDtx = "1".equals(fmtps.get("usedtx"));
        Opus.encoder_set_dtx(encoder, (useDtx && useDtxConfig) ? 1 : 0);

        // FEC is on unless specified.
        String s = fmtps.get("useinbandfec");
        boolean useFec = (s == null) || s.equals("1");
        Opus.encoder_set_inband_fec(encoder, (useFec && useFecConfig) ? 1 : 0);
    }

    /**
     * {@inheritDoc}
     *
     * Automatically tracks and calculates the size in bytes of an audio frame
     * (to be) output by this instance.
     */
    @Override
    public Format setInputFormat(Format format)
    {
        Format oldValue = getInputFormat();
        Format setInputFormat = super.setInputFormat(format);
        Format newValue = getInputFormat();

        if (oldValue != newValue)
        {
            AudioFormat af = (AudioFormat) newValue;
            int sampleRate = (int) af.getSampleRate();

            frameSizeInSamplesPerChannel
                = (sampleRate * frameSizeInMillis) / 1000;
            frameSizeInBytes
                = 2 /* sizeof(opus_int16) */
                    * channels
                    * frameSizeInSamplesPerChannel;
        }
        return setInputFormat;
    }
}
