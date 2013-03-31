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
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.util.*;

import java.awt.*;
import java.util.*;

/**
 * Implements an Opus decoder.
 *
 * @author Boris Grozev
 */
public class JNIDecoder
    extends AbstractCodec2
    implements FECDecoderControl
{
    /**
     * The <tt>Logger</tt> used by this <tt>JNIDecoder</tt> instance
     * for logging output.
     */
    private static final Logger logger = Logger.getLogger(JNIDecoder.class);

    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>JNIDecoder</tt> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS
        = new Format[] { new AudioFormat(Constants.OPUS_RTP) };

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
        /*
         * If the Opus class or its supporting JNI library are not functional,
         * it is too late to discover the fact in #doOpen() because a JNIDecoder
         * instance has already been initialized and it has already signaled
         * that the Opus codec is supported. 
         */
        Opus.assertOpusIsFunctional();
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
     * Sequence number of the last packet processed
     */
    private long lastPacketSeq;

    /**
     * Whether at least one packet has already been processed. Use this to
     * prevent FEC data from trying to be decoded from the first packet in a
     * session.
     */
    private boolean firstPacketProcessed = false;

    /**
     * Number of packets decoded with FEC
     */
    private int nbDecodedFec = 0;

    /**
     * Buffer used to store output decoded from FEC.
     */
    private byte[] fecBuffer
            = new byte[2 /* channels */ *
                       2 /* bytes per sample */ *
                       120 /* max ms per opus packet */ *
                       (outputSamplingRate / 1000) /* samples per ms */];

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

        addControl(this);
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

        boolean decodeFec = false;
        long inputSequenceNumber = inputBuffer.getSequenceNumber();

        /* Detect a missing packet, take care of wraps at 2^16 */
        if(firstPacketProcessed &&
              (inputSequenceNumber != lastPacketSeq + 1) &&
              !(inputSequenceNumber == 0 && lastPacketSeq == 65535))
            decodeFec = true;
        if ((inputBuffer.getFlags() & Buffer.FLAG_SKIP_FEC) != 0)
        {
            decodeFec = false;
            if (logger.isTraceEnabled())
                logger.trace("Not decoding FEC for " + inputSequenceNumber +
                                " because SKIP_FEC is set");
        }

        /* We figured out what should be decoded. Now decode it. */
        int fecSamples = 0;
        int outputLength = 0;
        byte[] inputData = (byte[]) inputBuffer.getData();
        int inputOffset = inputBuffer.getOffset();
        int inputLength = inputBuffer.getLength();

        if (decodeFec)
        {
            fecSamples = Opus.decode(decoder,
                                     inputData, inputOffset, inputLength,
                                     fecBuffer, fecBuffer.length,
                                     1 /* decode fec */);
            outputLength += fecSamples * 2;
        }

        outputLength +=  Opus.decoder_get_nb_samples(decoder,
                inputData, inputOffset, inputLength) * 2 /* sizeof(short) */;
        byte[] outputData
            = validateByteArraySize(outputBuffer, outputLength, false);

        int samplesCount = Opus.decode(decoder,
                                       inputData, inputOffset, inputLength,
                                       outputData, outputLength,
                                       0 /* no fec */);

        if (fecSamples > 0)
        {
            /*
             * TODO: add output offset to Opus.decode(), so that we don't have
             * to do this shift
             */
            System.arraycopy(outputData, 0,
                             outputData, fecSamples * 2,
                             samplesCount * 2);
            System.arraycopy(fecBuffer, 0,
                             outputData, 0,
                             fecSamples * 2);
        }

        if (outputLength > 0)
        {
            outputBuffer.setDuration(
                    ((samplesCount + fecSamples) *1000*1000)/outputSamplingRate);
            outputBuffer.setFormat(getOutputFormat());
            outputBuffer.setLength(outputLength);
            outputBuffer.setOffset(0);
            if(fecSamples > 0)
                nbDecodedFec++;
        }
        else
        {
            outputBuffer.setLength(0);
            discardOutputBuffer(outputBuffer);
        }

        firstPacketProcessed = true;

        lastPacketSeq = inputSequenceNumber;
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

    /**
     * Returns the number of packets decoded with FEC
     * @return
     */
    public int fecPacketsDecoded()
    {
        return nbDecodedFec;
    }

    /**
     * Stub. Only added in order to implement the <tt>FECDecoderControl</tt>
     * interface.
     *
     * @return null
     */
    public Component getControlComponent()
    {
        return null;
    }
}
