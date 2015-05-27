/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.g722;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class JNIDecoderImpl
    extends AbstractCodec2
{
    static final Format[] SUPPORTED_INPUT_FORMATS
        = new Format[]
                {
                    new AudioFormat(
                            Constants.G722_RTP,
                            8000,
                            Format.NOT_SPECIFIED /* sampleSizeInBits */,
                            1)
                };

    static final Format[] SUPPORTED_OUTPUT_FORMATS
        = new Format[]
                {
                    new AudioFormat(
                            AudioFormat.LINEAR,
                            16000,
                            16,
                            1,
                            AudioFormat.LITTLE_ENDIAN,
                            AudioFormat.SIGNED,
                            Format.NOT_SPECIFIED /* frameSizeInBits */,
                            Format.NOT_SPECIFIED /* frameRate */,
                            Format.byteArray)
                };

    private long decoder;

    /**
     * Initializes a new {@code JNIDecoderImpl} instance.
     */
    public JNIDecoderImpl()
    {
        super("G.722 JNI Decoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    /**
     *
     * @see AbstractCodecExt#doClose()
     */
    @Override
    protected void doClose()
    {
        JNIDecoder.g722_decoder_close(decoder);
    }

    /**
     *
     * @throws ResourceUnavailableException
     * @see AbstractCodecExt#doOpen()
     */
    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
        decoder = JNIDecoder.g722_decoder_open();
        if (decoder == 0)
            throw new ResourceUnavailableException("g722_decoder_open");
    }

    /**
     *
     * @param inputBuffer
     * @param outputBuffer
     * @return
     * @see AbstractCodecExt#doProcess(Buffer, Buffer)
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        byte[] input = (byte[]) inputBuffer.getData();

        int outputOffset = outputBuffer.getOffset();
        int outputLength = inputBuffer.getLength() * 4;
        byte[] output
            = validateByteArraySize(
                    outputBuffer,
                    outputOffset + outputLength,
                    true);

        JNIDecoder.g722_decoder_process(
                decoder,
                input, inputBuffer.getOffset(),
                output, outputOffset, outputLength);

        outputBuffer.setDuration(
                (outputLength * 1000000L)
                    / (16L /* kHz */ * 2L /* sampleSizeInBits / 8 */));
        outputBuffer.setFormat(getOutputFormat());
        outputBuffer.setLength(outputLength);
        return BUFFER_PROCESSED_OK;
    }
}
