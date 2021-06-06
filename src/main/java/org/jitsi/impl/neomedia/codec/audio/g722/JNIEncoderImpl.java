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
package org.jitsi.impl.neomedia.codec.audio.g722;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class JNIEncoderImpl
    extends AbstractCodec2
{
    private long encoder;

    /**
     * Initializes a new {@code JNIEncoderImpl} instance.
     */
    public JNIEncoderImpl()
    {
        super(
            "G.722 JNI Encoder",
            AudioFormat.class,
            JNIDecoderImpl.SUPPORTED_INPUT_FORMATS);

        inputFormats = JNIDecoderImpl.SUPPORTED_OUTPUT_FORMATS;
    }

    private long computeDuration(long length)
    {
        return (length * 1000000L) / 8L;
    }

    /**
     *
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected void doClose()
    {
        JNIEncoder.g722_encoder_close(encoder);
    }

    /**
     * @see AbstractCodec2#doOpen()
     */
    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
        encoder = JNIEncoder.g722_encoder_open();
        if (encoder == 0)
            throw new ResourceUnavailableException("g722_encoder_open");
    }

    /**
     * @see AbstractCodec2#doProcess(Buffer, Buffer)
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        int inputOffset = inputBuffer.getOffset();
        int inputLength = inputBuffer.getLength();
        byte[] input = (byte[]) inputBuffer.getData();

        int outputOffset = outputBuffer.getOffset();
        int outputLength = inputLength / 4;
        byte[] output
            = validateByteArraySize(
                    outputBuffer,
                    outputOffset + outputLength,
                    true);

        JNIEncoder.g722_encoder_process(
                encoder,
                input, inputOffset,
                output, outputOffset, outputLength);
        outputBuffer.setDuration(computeDuration(outputLength));
        outputBuffer.setFormat(getOutputFormat());
        outputBuffer.setLength(outputLength);
        return BUFFER_PROCESSED_OK;
    }

    /**
     * Get the output <tt>Format</tt>.
     *
     * @return output <tt>Format</tt> configured for this <tt>Codec</tt>
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
                                return
                                    JNIEncoderImpl.this.computeDuration(length);
                            }
                        });
        }
        return outputFormat;
    }
}
