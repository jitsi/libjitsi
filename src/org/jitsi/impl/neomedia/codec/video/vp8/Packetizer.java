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
package org.jitsi.impl.neomedia.codec.video.vp8;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.utils.logging.*;

/**
 * Packetizes VP8 encoded frames in accord with
 * {@link "http://tools.ietf.org/html/draft-ietf-payload-vp8-07"}
 *
 * Uses the simplest possible scheme, only splitting large packets. Extended
 * bits are never added, and PartID is always set to 0. The only bit that
 * changes is the Start of Partition bit, which is set only for the first packet
 * encoding a frame.
 *
 * @author Boris Grozev
 */
public class Packetizer
    extends AbstractCodec2
{
    /**
     * The <tt>Logger</tt> used by the <tt>Packetizer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(Packetizer.class);

    /**
     * Maximum size of packets (excluding the payload descriptor and any other
     * headers (RTP, UDP))
     */
    private static final int MAX_SIZE = 1350;

    /**
     * Whether this is the first packet from the frame.
     */
    private boolean firstPacket = true;

    /**
     * Initializes a new <tt>Packetizer</tt> instance.
     */
    public Packetizer()
    {
        super(
                "VP8 Packetizer",
                VideoFormat.class,
                new VideoFormat[] { new VideoFormat(Constants.VP8_RTP) });

        inputFormats = new VideoFormat[] { new VideoFormat(Constants.VP8)};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen()
    {
        if(logger.isTraceEnabled())
            logger.trace("Opened VP8 packetizer");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        int inLen;

        if(inputBuffer.isDiscard() || ((inLen = inputBuffer.getLength()) == 0))
        {
            outputBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        byte[] output;
        int offset;
        int pdMaxLen = DePacketizer.VP8PayloadDescriptor.MAX_LENGTH;

        //The input will fit in a single packet
        int inOff = inputBuffer.getOffset();
        int len = (inLen <= MAX_SIZE) ? inLen : MAX_SIZE;

        offset = pdMaxLen;
        output = validateByteArraySize(outputBuffer, offset + len, true);
        System.arraycopy(
                inputBuffer.getData(), inOff,
                output, offset,
                len);

        //get the payload descriptor and copy it to the output
        byte[] pd = DePacketizer.VP8PayloadDescriptor.create(firstPacket);
        System.arraycopy(
                pd, 0,
                output, offset - pd.length,
                pd.length);
        offset -= pd.length;

        //set up the output buffer
        outputBuffer.setFormat(new VideoFormat(Constants.VP8_RTP));
        outputBuffer.setOffset(offset);
        outputBuffer.setLength(len + pd.length);

        if(inLen <= MAX_SIZE)
        {
            firstPacket = true;
            outputBuffer.setFlags(outputBuffer.getFlags() | Buffer.FLAG_RTP_MARKER);
            return BUFFER_PROCESSED_OK;
        }
        else
        {
            firstPacket = false;
            inputBuffer.setLength(inLen - MAX_SIZE);
            inputBuffer.setOffset(inOff + MAX_SIZE);
            return INPUT_BUFFER_NOT_CONSUMED;
        }
    }
}
