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
import org.jitsi.util.function.*;

/**
 * An <tt>Iterator</tt> that iterates RED blocks (primary and non-primary).
 *
 * @author George Politis
 */
public class REDBlockIterator
    implements Iterator<REDBlock>
{
    /**
     * The <tt>Logger</tt> used by the <tt>REDBlockIterator</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(REDBlockIterator.class);

    /**
     * The byte buffer that holds the RED payload that this instance is
     * dissecting.
     */
    private final byte[] buffer;

    /**
     * The offset in the buffer where the RED payload begin.
     */
    private final int offset;

    /**
     * The length of the RED payload in the buffer.
     */
    private final int length;

    /**
     * The number of RED blocks inside the RED payload.
     */
    private int cntRemainingBlocks = -1;

    /**
     * The offset of the next RED block header inside the RED payload.
     */
    private int offNextBlockHeader = -1;

    /**
     * The offset of the next RED block payload inside the RED payload.
     */
    private int offNextBlockPayload = -1;

    /**
     * Matches a RED block in the RED payload.
     *
     * @param predicate the predicate that is used to match the RED block.
     * @param buffer the byte buffer that contains the RED payload.
     * @param offset the offset in the buffer where the RED payload begins.
     * @param length the length of the RED payload.
     * @return the first RED block that matches the given predicate, null
     * otherwise.
     */
    public static REDBlock matchFirst(
            Predicate<REDBlock>  predicate,
            byte[] buffer, int offset, int length)
    {
        if (isMultiBlock(buffer, offset, length))
        {
            REDBlockIterator it = new REDBlockIterator(buffer, offset, length);
            while (it.hasNext())
            {
                REDBlock b = it.next();
                if (b != null && predicate.test(b))
                {
                    return b;
                }
            }

            return null;
        }
        else
        {
            REDBlock b = getPrimaryBlock(buffer, offset, length);
            if (b != null && predicate.test(b))
            {
                return b;
            }
            else
            {
                return null;
            }
        }
    }

    /**
     * Gets the first RED block in the RED payload.
     *
     * @param buffer the byte buffer that contains the RED payload.
     * @param offset the offset in the buffer where the RED payload begins.
     * @param length the length of the RED payload.
     * @return the primary RED block if it exists, null otherwise.
     */
    public static REDBlock getPrimaryBlock(
            byte[] buffer, int offset, int length)
    {
        // Chrome is typically sending RED packets with a single block carrying
        // either VP8 or FEC. This is unusual, and probably wrong as it messes
        // up the sequence numbers and packet loss computations but it's just
        // the way it is. Here we detect this situation and avoid looping
        // through the blocks if there is a single block.
        if (isMultiBlock(buffer, offset, length))
        {
            REDBlock block = null;
            REDBlockIterator redBlockIterator
                = new REDBlockIterator(buffer, offset, length);
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
            if (buffer == null || offset < 0 || length < 0
                    || buffer.length < offset + length)
            {
                logger.warn("Prevented an array out of bounds exception: " +
                        "buffer length: " + buffer.length + ", offset: "
                        + offset + ", len: " + length);
                return null;
            }

            byte blockPT = (byte) (buffer[offset] & 0x7f);
            int blockOff = offset + 1; // + 1 for the primary block header.
            int blockLen = length - blockOff;

            if (buffer.length < blockOff + blockLen)
            {
                logger.warn("Primary block doesn't fit in RED packet.");
                return null;
            }

            return new REDBlock(buffer, blockOff, blockLen, blockPT);
        }
    }

    /**
     * Returns {@code true} if a specific RED packet contains multiple blocks;
     * {@code false}, otherwise.
     *
     * @param buffer the byte buffer that contains the RED payload.
     * @param offset the offset in the buffer where the RED payload begins.
     * @param length the length of the RED payload.
     * @return {@code true if {@pkt} contains multiple RED blocks; otherwise,
     * {@code false}
     */
    public static boolean isMultiBlock(byte[] buffer, int offset, int length)
    {
        if (buffer == null || buffer.length == 0)
        {
            logger.warn("The buffer appears to be empty.");
            return false;
        }

        if (offset < 0 || buffer.length <= offset)
        {
            logger.warn("Prevented array out of bounds exception.");
            return false;
        }

        return (buffer[offset] & 0x80) != 0;
    }

    /**
     * Ctor.
     *
     * @param buffer the byte buffer that contains the RED payload.
     * @param offset the offset in the buffer where the RED payload begins.
     * @param length the length of the RED payload.
     */
    public REDBlockIterator(byte[] buffer, int offset, int length)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
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

        if (buffer == null || buffer.length <= offNextBlockHeader)
        {
            logger.warn("Prevented an array out of bounds exception.");
            return null;
        }

        byte blockPT = (byte) (buffer[offNextBlockHeader] & 0x7f);

        int blockLen;
        if (hasNext())
        {
            if (buffer.length < offNextBlockHeader + 4)
            {
                logger.warn("Prevented an array out of bounds exception.");
                return null;
            }

            // 0                   1                   2                   3
            // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            //|F|   block PT  |  timestamp offset         |   block length    |
            //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            blockLen = (buffer[offNextBlockHeader + 2] & 0x03) << 8
                | (buffer[offNextBlockHeader + 3] & 0xFF);
            offNextBlockHeader += 4; // next RED header
            offNextBlockPayload += blockLen;
        }
        else
        {
            // 0 1 2 3 4 5 6 7
            //+-+-+-+-+-+-+-+-+
            //|0|   Block PT  |
            //+-+-+-+-+-+-+-+-+
            blockLen = length - (offNextBlockPayload + 1);
            offNextBlockHeader = -1;
            offNextBlockPayload = -1;
        }

        return new REDBlock(buffer, offNextBlockPayload, blockLen, blockPT);
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
        if (buffer == null || buffer.length == 0)
        {
            return;
        }

        //beginning of RTP payload
        offNextBlockHeader = offset;

        // Number of packets inside RED.
        cntRemainingBlocks = 0;

        // 0                   1                   2                   3
        // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        //|F|   block PT  |  timestamp offset         |   block length    |
        //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        while ((buffer[offNextBlockHeader] & 0x80) != 0)
        {
            cntRemainingBlocks++;
            offNextBlockHeader += 4;
        }

        // 0 1 2 3 4 5 6 7
        //+-+-+-+-+-+-+-+-+
        //|0|   Block PT  |
        //+-+-+-+-+-+-+-+-+
        if (buffer.length >= offNextBlockHeader + 8)
        {
            cntRemainingBlocks++;
        }

        //back to beginning of RTP payload
        offNextBlockHeader = offset;

        if (cntRemainingBlocks > 0)
        {
            offNextBlockPayload
                = offNextBlockHeader + (cntRemainingBlocks - 1) * 4 + 1;
        }
    }
}
