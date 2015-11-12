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

package org.jitsi.impl.neomedia.codec;

import java.util.*;
import org.jitsi.util.*;
import org.jitsi.impl.neomedia.*;

/**
 * An <tt>Iterator</tt> that iterates RED blocks (primary and non-primary).
 *
 * @author George Politis
 */
public class REDBlockIterator
    implements Iterator<REDBlockIterator.REDBlock>
{
    /**
     * The <tt>Logger</tt> used by the <tt>SsrcRewritingEngine</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(REDBlockIterator.class);

    /**
     * The <tt>RawPacket</tt> that this instance is dissecting.
     */
    private final RawPacket rawPacket;

    /**
     * The number of RED blocks inside the {#rawPacket}.
     */
    private int cntRemainingBlocks = -1;

    /**
     * The offset of the next RED block header inside the {#rawPacket} buffer.
     */
    private int offNextBlockHeader = -1;

    /**
     * The offset of the next RED block payload inside the {#rawPacket} buffer.
     */
    private int offNextBlockPayload = -1;

    /**
     * Gets the primary block of a RED packet.
     */
    public static REDBlock getPrimaryBlock(RawPacket pkt)
    {
        if (pkt == null)
        {
            return null;
        }

        // Chrome is typically sending RED packets with a single block carrying
        // either VP8 or FEC. This is unusual, and probably wrong as it messes
        // up the sequence numbers and packet loss computations but it's just
        // the way it is. Here we detect this situation and avoid looping
        // through the blocks if there is a single block.
        if (isMultiBlock(pkt))
        {
            logger.debug("Dissecting multiblock RED.");

            REDBlock block = null;
            REDBlockIterator redBlockIterator = new REDBlockIterator(pkt);
            while (redBlockIterator.hasNext())
            {
                block = redBlockIterator.next();
            }

            if (block == null)
            {
                logger.warn("No primary block found.");
            }

            return block;
        }
        else
        {
            logger.debug("Dissecting uniblock RED.");

            byte[] buff = pkt.getBuffer();
            int off = pkt.getPayloadOffset();
            int len = pkt.getPayloadLength();

            if (buff == null || off < 0 || len < 0 || buff.length < off + len)
            {
                logger.warn("Prevented an array out of bounds exception: " +
                        "buffer length: " + buff.length + ", offset: " + off +
                        ", len: " + len);
                return null;
            }

            byte blockPT = (byte) (buff[off] & 0x7f);
            int blockOff = off + 1; // + 1 for the primary block header.
            int blockLen = len - blockOff;

            if (buff.length < blockOff + blockLen)
            {
                logger.warn("Primary block doesn't fit in RED packet.");
                return null;
            }

            return new REDBlock(blockOff, blockLen, blockPT);
        }
    }

    /**
     * Returns true if this RED packet contains multiple blocks, false
     * otherwise.
     */
    public static boolean isMultiBlock(RawPacket pkt)
    {
        if (pkt == null)
        {
            return false;
        }

        byte[] buff = pkt.getBuffer();
        if (buff == null || buff.length == 0)
        {
            logger.warn("The buffer appears to be empty.");
            return false;
        }

        int off = pkt.getPayloadOffset();
        if (off < 0 || buff.length <= off)
        {
            logger.warn("Prevented array out of bounds exception.");
            return false;
        }

        return (buff[off] & 0x80) != 0;
    }

    /**
     * Ctor.
     *
     * @param rawPacket The <tt>RawPacket</tt> that represents the RED packet.
     */
    public REDBlockIterator(RawPacket rawPacket)
    {
        this.rawPacket = rawPacket;
        this.initialize();
    }

    @Override
    public boolean hasNext()
    {
        return cntRemainingBlocks > 0;
    }

    @Override
    public REDBlock next()
    {
        if (!hasNext())
        {
            throw new NoSuchElementException();
        }

        cntRemainingBlocks--;

        byte[] buff = rawPacket.getBuffer();
        if (buff == null || buff.length <= offNextBlockHeader)
        {
            logger.warn("Prevented an array out of bounds exception.");
            return null;
        }

        byte blockPT = (byte) (buff[offNextBlockHeader] & 0x7f);

        int blockLen;
        if (hasNext())
        {
            if (buff.length < offNextBlockHeader + 4)
            {
                logger.warn("Prevented an array out of bounds exception.");
                return null;
            }

            // 0                   1                   2                   3
            // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            //|F|   block PT  |  timestamp offset         |   block length    |
            //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            blockLen = (buff[offNextBlockHeader + 2] & 0x03) << 8
                | (buff[offNextBlockHeader + 3]);
            offNextBlockHeader += 4; // next RED header
            offNextBlockPayload += blockLen;
        }
        else
        {
            // 0 1 2 3 4 5 6 7
            //+-+-+-+-+-+-+-+-+
            //|0|   Block PT  |
            //+-+-+-+-+-+-+-+-+
            blockLen = rawPacket.getLength() - (offNextBlockPayload + 1);
            offNextBlockHeader = -1;
            offNextBlockPayload = -1;
        }

        return new REDBlock(offNextBlockPayload, blockLen, blockPT);
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Initializes this instance.
     */
    private void initialize()
    {
        byte[] buff = rawPacket.getBuffer();
        if (buff == null || buff.length == 0)
        {
            return;
        }

        //beginning of RTP payload
        offNextBlockHeader = rawPacket.getPayloadOffset();

        // Number of packets inside RED.
        cntRemainingBlocks = 0;

        // 0                   1                   2                   3
        // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        //|F|   block PT  |  timestamp offset         |   block length    |
        //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        while ((buff[offNextBlockHeader] & 0x80) != 0)
        {
            cntRemainingBlocks++;
            offNextBlockHeader += 4;
        }

        // 0 1 2 3 4 5 6 7
        //+-+-+-+-+-+-+-+-+
        //|0|   Block PT  |
        //+-+-+-+-+-+-+-+-+
        if (buff.length >= offNextBlockHeader + 8)
        {
            cntRemainingBlocks++;
        }

        offNextBlockHeader
            = rawPacket.getPayloadOffset(); //back to beginning of RTP payload

        if (cntRemainingBlocks > 0)
        {
            offNextBlockPayload
                = offNextBlockHeader + (cntRemainingBlocks - 1) * 4 + 1;
        }
    }

    /**
     * A class that represents a RED block.
     */
    public static class REDBlock
    {
        /**
         * Ctor.
         */
        public REDBlock(int offset, int len, byte pt)
        {
            this.pt = pt;
            this.offset = offset;
            this.len = len;
        }

        /**
         * The payload type of this RED block.
         */
        private byte pt;

        /**
         * The offset in the buffer where this RED block start.
         */
        private int offset;

        /**
         * The length in the buffer of this RED block.
         */
        private int len;

        /**
         * Gets the payload type of this RED block.
         */
        public byte getPayloadType()
        {
            return this.pt;
        }

        /**
         * Gets the length of this RED block.
         */
        public int getBlockLength()
        {
            return this.len;
        }


        /**
         * Gets the offset in the buffer where this RED block starts.
         */
        public int getBlockOffset()
        {
            return this.offset;
        }
    }
}
