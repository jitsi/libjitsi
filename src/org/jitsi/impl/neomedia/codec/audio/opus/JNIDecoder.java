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
package org.jitsi.impl.neomedia.codec.audio.opus;

import java.awt.*;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.utils.logging.*;

/**
 * Implements an Opus decoder.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
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
                            AbstractAudioRenderer.NATIVE_AUDIO_FORMAT_ENDIAN,
                            AudioFormat.SIGNED,
                            /* frameSizeInBits */ Format.NOT_SPECIFIED,
                            /* frameRate */ Format.NOT_SPECIFIED,
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
     * Number of channels to decode into.
     */
    private int channels = 1;

    /**
     * Pointer to the native OpusDecoder structure
     */
    private long decoder = 0;

    /**
     * The size in samples per channel of the last decoded frame in the terms of
     * the Opus library.
     */
    private int lastFrameSizeInSamplesPerChannel;

    /**
     * The sequence number of the last processed <tt>Buffer</tt>.
     */
    private long lastSeqNo = Buffer.SEQUENCE_UNKNOWN;

    /**
     * Number of packets decoded with FEC
     */
    private int nbDecodedFec = 0;

    /**
     * The size in bytes of an audio frame in the terms of the output
     * <tt>AudioFormat</tt> of this instance i.e. based on the values of the
     * <tt>sampleSizeInBits</tt> and <tt>channels</tt> properties of the
     * <tt>outputFormat</tt> of this instance.
     */
    private int outputFrameSize;

    /**
     * The sample rate of the audio data output by this instance.
     */
    private int outputSampleRate;

    /**
     * Initializes a new <tt>JNIDecoder</tt> instance.
     */
    public JNIDecoder()
    {
        super("Opus JNI Decoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);

        features = BUFFER_FLAG_FEC | BUFFER_FLAG_PLC;
        inputFormats = SUPPORTED_INPUT_FORMATS;

        addControl(this);
    }

    /**
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected void doClose()
    {
        if (decoder != 0)
        {
            Opus.decoder_destroy(decoder);
            decoder = 0;
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
        if (decoder == 0)
        {
            decoder = Opus.decoder_create(outputSampleRate, channels);
            if (decoder == 0)
                throw new ResourceUnavailableException("opus_decoder_create");

            lastFrameSizeInSamplesPerChannel = 0;
            lastSeqNo = Buffer.SEQUENCE_UNKNOWN;
        }
    }

    /**
     * {@inheritDoc}
     *
     * Decodes an Opus packet.
     */
    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf)
    {
        Format inFormat = inBuf.getFormat();

        if ((inFormat != null)
                && (inFormat != this.inputFormat)
                && !inFormat.equals(this.inputFormat)
                && (null == setInputFormat(inFormat)))
        {
            return BUFFER_PROCESSED_FAILED;
        }

        long seqNo = inBuf.getSequenceNumber();

        /*
         * Buffer.FLAG_SILENCE is set only when the intention is to drop the
         * specified input Buffer but to note that it has not been lost.
         */
        if ((Buffer.FLAG_SILENCE & inBuf.getFlags()) != 0)
        {
            lastSeqNo = seqNo;
            return OUTPUT_BUFFER_NOT_FILLED;
        }

        int lostSeqNoCount = calculateLostSeqNoCount(lastSeqNo, seqNo);
        /*
         * Detect the lost Buffers/packets and decode FEC/PLC. When no in-band
         * forward error correction data is available, the Opus decoder will
         * operate as if PLC has been specified.
         */
        boolean decodeFEC
            = ((lostSeqNoCount > 0)
                    && (lostSeqNoCount <= MAX_AUDIO_SEQUENCE_NUMBERS_TO_PLC)
                    && (lastFrameSizeInSamplesPerChannel != 0));

        if (decodeFEC && ((inBuf.getFlags() & Buffer.FLAG_SKIP_FEC) != 0))
        {
            decodeFEC = false;
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "Not decoding FEC/PLC for " + seqNo
                            + " because of Buffer.FLAG_SKIP_FEC.");
            }
        }

        // After we have determined what is to be decoded, do decode it.
        byte[] in = (byte[]) inBuf.getData();
        int inOffset = inBuf.getOffset();
        int inLength = inBuf.getLength();
        int outOffset = 0;
        int outLength = 0;
        int totalFrameSizeInSamplesPerChannel = 0;

        if (decodeFEC)
        {
            inLength = (lostSeqNoCount == 1) ? inLength /* FEC */ : 0 /* PLC */;

            byte[] out
                = validateByteArraySize(
                        outBuf,
                        outOffset
                            + lastFrameSizeInSamplesPerChannel
                                * outputFrameSize,
                        outOffset != 0);
            int frameSizeInSamplesPerChannel
                = Opus.decode(
                        decoder,
                        in, inOffset, inLength,
                        out, outOffset, lastFrameSizeInSamplesPerChannel,
                        /* decodeFEC */ 1);

            if (frameSizeInSamplesPerChannel > 0)
            {
                int frameSizeInBytes
                    = frameSizeInSamplesPerChannel * outputFrameSize;

                outLength += frameSizeInBytes;
                outOffset += frameSizeInBytes;
                totalFrameSizeInSamplesPerChannel
                    += frameSizeInSamplesPerChannel;

                outBuf.setFlags(
                        outBuf.getFlags()
                            | (((in == null) || (inLength == 0))
                                    ? BUFFER_FLAG_PLC
                                    : BUFFER_FLAG_FEC));

                long ts = inBuf.getRtpTimeStamp();
                ts -= lostSeqNoCount * lastFrameSizeInSamplesPerChannel;
                if (ts < 0)
                    ts += 1L<<32;
                outBuf.setRtpTimeStamp(ts);

                nbDecodedFec++;
            }

            lastSeqNo = incrementSeqNo(lastSeqNo);
        }
        else
        {
            int frameSizeInSamplesPerChannel
                = Opus.decoder_get_nb_samples(decoder, in, inOffset, inLength);
            byte[] out
                = validateByteArraySize(
                        outBuf,
                        outOffset
                            + frameSizeInSamplesPerChannel * outputFrameSize,
                        outOffset != 0);

            frameSizeInSamplesPerChannel
                = Opus.decode(
                        decoder,
                        in, inOffset, inLength,
                        out, outOffset, frameSizeInSamplesPerChannel,
                        /* decodeFEC */ 0);
            if (frameSizeInSamplesPerChannel > 0)
            {
                int frameSizeInBytes
                    = frameSizeInSamplesPerChannel * outputFrameSize;

                outLength += frameSizeInBytes;
                outOffset += frameSizeInBytes;
                totalFrameSizeInSamplesPerChannel
                    += frameSizeInSamplesPerChannel;

                outBuf.setFlags(
                        outBuf.getFlags()
                            & ~(BUFFER_FLAG_FEC | BUFFER_FLAG_PLC));

                /*
                 * When we encounter a lost frame, we will presume that it was
                 * of the same duration as the last received frame.
                 */
                lastFrameSizeInSamplesPerChannel = frameSizeInSamplesPerChannel;
            }

            lastSeqNo = seqNo;
        }

        int ret
            = (lastSeqNo == seqNo)
                ? BUFFER_PROCESSED_OK
                : INPUT_BUFFER_NOT_CONSUMED;

        if (outLength > 0)
        {
            outBuf.setDuration(
                    totalFrameSizeInSamplesPerChannel * 1000L * 1000L * 1000L
                        / outputSampleRate);
            outBuf.setFormat(getOutputFormat());
            outBuf.setLength(outLength);
            outBuf.setOffset(0);
            /*
             * The sequence number is not likely to be important after the
             * depacketization and the decoding but BasicFilterModule will copy
             * them from the input Buffer into the output Buffer anyway so it
             * makes sense to keep the sequence number straight for the sake of
             * completeness.
             */
            outBuf.setSequenceNumber(lastSeqNo);
        }
        else
        {
            ret |= OUTPUT_BUFFER_NOT_FILLED;
        }

        return ret;
    }

    /**
     * Returns the number of packets decoded with FEC.
     *
     * @return the number of packets decoded with FEC
     */
    @Override
    public int fecPacketsDecoded()
    {
        return nbDecodedFec;
    }

    /**
     * Implements {@link Control#getControlComponent()}. <tt>JNIDecoder</tt>
     * does not provide user interface of its own.
     *
     * @return <tt>null</tt> to signify that <tt>JNIDecoder</tt> does not
     * provide user interface of its own
     */
    @Override
    public Component getControlComponent()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        AudioFormat af = (AudioFormat) inputFormat;

        return
            new Format[]
                    {
                        new AudioFormat(
                                AudioFormat.LINEAR,
                                af.getSampleRate(),
                                16,
                                1,
                                AbstractAudioRenderer
                                    .NATIVE_AUDIO_FORMAT_ENDIAN,
                                AudioFormat.SIGNED,
                                /* frameSizeInBits */ Format.NOT_SPECIFIED,
                                /* frameRate */ Format.NOT_SPECIFIED,
                                Format.byteArray)
                    };
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that the <tt>outputFormat</tt> of this instance is in accord
     * with the <tt>inputFormat</tt> of this instance.
     */
    @Override
    public Format setInputFormat(Format format)
    {
        Format inFormat = super.setInputFormat(format);

        if ((inFormat != null) && (outputFormat == null))
            setOutputFormat(SUPPORTED_OUTPUT_FORMATS[0]);

        return inFormat;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Format setOutputFormat(Format format)
    {
        Format setOutputFormat = super.setOutputFormat(format);

        if (setOutputFormat != null)
        {
            AudioFormat af = (AudioFormat) setOutputFormat;

            outputFrameSize = (af.getSampleSizeInBits() / 8) * af.getChannels();
            outputSampleRate = (int) af.getSampleRate();
        }
        return setOutputFormat;
    }
}
