/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.impl.neomedia.transform.fec;

import java.util.*;

/**
 * A bit-set class which is similar to a standard bitset, but with 2 differences:
 * 1) The size of this set is preserved.  Unlike the standard bitset, which will
 * not include leading 0's in its size (which doesn't work well for a bitmask),
 * this one will always report the size it was allocated for
 * 2) When reading (valueOf) and writing (toByteArray) it inverts the order
 * of the bits.  This is because in FlexFEC-03, the left-most bit of the mask
 * represents a delta value of '0'.
 */
public class FlexFec03BitSet
{
    /**
     * The underlying bitset used to store the bits
     */
    private BitSet bitSet;

    /**
     * The size of the mask this bitset is representing
     */
    private int numBits = 0;

    /**
     * Ctor
     * @param numBits the size, in bits, of this set
     */
    public FlexFec03BitSet(int numBits)
    {
        bitSet = new BitSet(numBits);
        this.numBits = numBits;
    }

    /**
     * Set the bit (set to 1) at the given index
     * @param bitIndex
     */
    public void set(int bitIndex)
    {
        bitSet.set(bitIndex);
    }

    /**
     * Get the bit at the given index
     * @param bitIndex
     * @return
     */
    public boolean get(int bitIndex)
    {
        return bitSet.get(bitIndex);
    }

    /**
     * Clear the bit (set to 0) at the given index
     * @param bitIndex
     */
    public void clear(int bitIndex)
    {
        bitSet.clear(bitIndex);
    }

    /**
     * Add a bit with the given value in the given position.  Existing bits
     * will be moved to the right.  No loss of bits will occur.
     * @param bitIndex the index at which to insert the bit
     * @param bitValue the value to set on the inserted bit
     */
    public void addBit(int bitIndex, boolean bitValue)
    {
        int newNumBits = numBits + 1;
        BitSet newBitSet = new BitSet(newNumBits);
        // copy [0, bitIndex - 1] to the new set in the same position
        // copy [bitIndex, length] to the new set shifted right by 1
        for (int i = 0; i < numBits; ++i)
        {
            if (i < bitIndex)
            {
                newBitSet.set(i, bitSet.get(i));
            }
            else
            {
                newBitSet.set(i + 1, bitSet.get(i));
            }
        }
        newBitSet.set(bitIndex, bitValue);
        bitSet = newBitSet;
        numBits = newNumBits;
    }

    /**
     * Remove a bit from the given position.  Existing bits will be moved
     * to the left.
     * @param bitIndex
     */
    public void removeBit(int bitIndex)
    {
        int newNumBits = numBits - 1;
        BitSet newBitSet = new BitSet(newNumBits);

        for (int i = 0; i < numBits; ++i)
        {
            if (i < bitIndex)
            {
                newBitSet.set(i, bitSet.get(i));
            }
            else if (i > bitIndex)
            {
                newBitSet.set(i - 1, bitSet.get(i));
            }
        }
        bitSet = newBitSet;
        numBits = newNumBits;
    }

    /**
     * Parse the value of the given byte buffer into the bitset
     * @param bytes
     * @return
     */
    public static FlexFec03BitSet valueOf(byte[] bytes)
    {
        return valueOf(bytes, 0, bytes.length);
    }

    /**
     * Parse the given bytes (at the given offset and length) into a
     * FlexFec03BitSet
     * @param bytes the byte buffer to parse
     * @param offset the offset at which to start parsing
     * @param length the length (in bytes) of the chunk to parse
     * @return a FlexFec03BitSet representing the chunk of the given buffer
     * based on the given offset and length
     */
    public static FlexFec03BitSet valueOf(byte[] bytes, int offset, int length)
    {
        int numBits = length * 8;
        FlexFec03BitSet b = new FlexFec03BitSet(numBits);
        for (int currBytePos = 0; currBytePos < length; ++currBytePos)
        {
            byte currByte = bytes[offset + currBytePos];
            for (int currBitPos = 0; currBitPos < 8; ++currBitPos)
            {
                int absoluteBitPos = currBytePos * 8 + currBitPos;
                if ((currByte & (0x80 >> currBitPos)) > 0)
                {
                    b.set(absoluteBitPos);
                }
            }
        }
        return b;
    }

    /**
     * Get the size of this set, in bytes
     * @return the size of this set, in bytes
     */
    public int sizeBytes()
    {
        int numBytes = numBits / 8;
        if (numBits % 8 != 0)
        {
            numBytes++;
        }
        return numBytes;
    }

    /**
     * Get the size of this set, in bits
     * @return the size of this set, in bits
     */
    public int sizeBits()
    {
        return numBits;
    }

    /**
     * Writes this bitset to a byte array, where the rightmost bit is treated
     * as the least significant bit.
     * @return
     */
    public byte[] toByteArray()
    {
        byte[] bytes = new byte[sizeBytes()];
        for (int currBitPos = 0; currBitPos < numBits; ++currBitPos)
        {
            int bytePos = currBitPos / 8;
            // The position of this bit relative to the current byte
            // (left to right)
            int relativeBitPos = currBitPos % 8;

            if (get(currBitPos))
            {
                bytes[bytePos] |= (0x80 >> relativeBitPos);
            }
        }
        return bytes;
    }

    /**
     * Print the bitmask in a manner where the left-most bit is the LSB
     * @return
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        int numSpaces = Integer.toString(numBits).length() + 1;
        for (int i = 0; i < numBits; ++i)
        {
            sb.append(String.format("%-" + numSpaces + "s", i));
        }
        sb.append("\n");
        for (int i = 0; i < numBits; ++i) {
            if (get(i))
            {
                sb.append(String.format("%-" + numSpaces + "s", "1"));
            }
            else
            {
                sb.append(String.format("%-" + numSpaces + "s", "0"));
            }
        }
        return sb.toString();
    }
}
