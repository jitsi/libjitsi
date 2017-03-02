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
package org.jitsi.util;

import java.util.*;

/**
 * RTP-related static utility methods.
 * @author Boris Grozev
 */
public class RTPUtils
{
    /**
     * Returns the difference between two RTP sequence numbers (modulo 2^16).
     * @return the difference between two RTP sequence numbers (modulo 2^16).
     */
    public static int sequenceNumberDiff(int a, int b)
    {
        int diff = a - b;

        if (diff < -(1<<15))
            diff += 1<<16;
        else if (diff > 1<<15)
            diff -= 1<<16;

        return diff;
    }

    /**
     * Returns result of the subtraction of one RTP sequence number from another
     * (modulo 2^16).
     * @return result of the subtraction of one RTP sequence number from another
     * (modulo 2^16).
     */
    public static int subtractNumber(int a, int b)
    {
        return (a - b) & 0xFFFF;
    }

    /**
     * Set an integer at specified offset in network order.
     *
     * @param off Offset into the buffer
     * @param data The integer to store in the packet
     */
    public static int writeInt(byte[] buf, int off, int data)
    {
        if (buf == null || buf.length < off + 4)
        {
            return -1;
        }

        buf[off++] = (byte)(data>>24);
        buf[off++] = (byte)(data>>16);
        buf[off++] = (byte)(data>>8);
        buf[off] = (byte)data;
        return 4;
    }

    /**
     * Set an integer at specified offset in network order.
     *
     * @param off Offset into the buffer
     * @param data The integer to store in the packet
     */
    public static int writeShort(byte[] buf, int off, short data)
    {
        buf[off++] = (byte)(data>>8);
        buf[off] = (byte)data;
        return 2;
    }

    /**
     * Read a integer from a buffer at a specified offset.
     *
     * @param buffer the buffer.
     * @param offset start offset of the integer to be read.
     */
    public static int readInt(byte[] buffer, int offset)
    {
        return
            ((buffer[offset++] & 0xFF) << 24)
                | ((buffer[offset++] & 0xFF) << 16)
                | ((buffer[offset++] & 0xFF) << 8)
                | (buffer[offset] & 0xFF);
    }

    /**
     * Reads a 32-bit unsigned integer from the given buffer at the given
     * offset and returns its {@link long} representation.
     * @param buffer the buffer.
     * @param offset start offset of the integer to be read.
     */
    public static long readUint32AsLong(byte[] buffer, int offset)
    {
        return readInt(buffer, offset) & 0xFFFFFFFFL;
    }

    /**
     * Read an unsigned short at specified offset as a int
     *
     * @param buffer
     * @param offset start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    public static int readUint16AsInt(byte[] buffer, int offset)
    {
        int b1 = (0xFF & (buffer[offset + 0]));
        int b2 = (0xFF & (buffer[offset + 1]));
        int val = b1 << 8 | b2;
        return val;
    }

    /**
     * A {@link Comparator} implementation for unsigned 16-bit {@link Integer}s.
     * Compares {@code a} and {@code b} inside the [0, 2^16] ring;
     * {@code a} is considered smaller than {@code b} if it takes a smaller
     * number to reach from {@code a} to {@code b} than the other way round.
     *
     * IMPORTANT: This is a valid {@link Comparator} implementation only when
     * used for subsets of [0, 2^16) which don't span more than 2^15 elements.
     *
     * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000])
     * Doesn't work for: [0, 2^15] and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
     */
    public static final Comparator<? super Integer> sequenceNumberComparator
        = new Comparator<Integer>() {
        @Override
        public int compare(Integer a, Integer b)
        {
            if (a == b)
            {
                return 0;
            }
            else if (a > b)
            {
                if (a - b < 0x10000)
                    return 1;
                else
                    return -1;
            }
            else //a < b
            {
                if (b - a < 0x10000)
                    return -1;
                else
                    return 1;
            }
        }
    };

}
