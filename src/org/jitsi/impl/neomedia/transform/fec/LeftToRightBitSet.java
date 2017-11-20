package org.jitsi.impl.neomedia.transform.fec;

import sun.reflect.generics.reflectiveObjects.*;

import java.util.*;
import java.util.stream.*;

/**
 * Created by bbaldino on 11/14/17.
 *
 * A BitSet, but treats the leftmost bit as the least significant
 * bit (index 0).
 */
public class LeftToRightBitSet
{
    private BitSet bitSet;
    private int numBits = 0;

    public LeftToRightBitSet(int numBits)
    {
        bitSet = new BitSet(numBits);
        this.numBits = numBits;
    }

    /**
     * Translate a left-side least significant bit index to
     * a right-side least significant bit index.
     * Translate an index between a left-side least-significant-bit index
     * and a right-side least-significant-bit index
     * @param bitIndex
     * @return
     */
    private int translate(int bitIndex)
    {
        if (bitIndex < 0)
        {
            return bitIndex;
        }
        // Subtract 1 since we're 0-based
        return this.numBits - bitIndex - 1;
    }

    public void clear(int bitIndex)
    {
        bitSet.clear(translate(bitIndex));
    }

    public void clear(int fromIndex, int toIndex)
    {
        // The inverting of the toIndex and fromIndex arguments
        // here is intentional.  We need to do this for the range
        // to be properly formed
        bitSet.clear(translate(toIndex), translate(fromIndex));
    }

    public void flip(int bitIndex)
    {
        bitSet.flip(translate(bitIndex));
    }

    public void flip(int fromIndex, int toIndex)
    {
        bitSet.flip(translate(toIndex), translate(fromIndex));
    }

    public boolean get(int bitIndex)
    {
        return bitSet.get(translate(bitIndex));
    }

    public LeftToRightBitSet get(int fromIndex, int toIndex)
    {
        LeftToRightBitSet b =
            new LeftToRightBitSet(toIndex - fromIndex);

        int newBitPos = 0;
        for (int i = fromIndex; i < toIndex; ++i)
        {
            if (get(i))
            {
                b.set(newBitPos);
            }
            newBitPos++;
        }
        return b;
    }

    public int nextClearBit(int bitIndex)
    {
        // Implement this by completely inverting the call (translate the
        // index, call the inverse next/previous, then translate
        // the result back)
        return translate(bitSet.previousClearBit(translate(bitIndex)));
    }

    public int nextSetBit(int bitIndex)
    {
        return translate(bitSet.previousSetBit(translate(bitIndex)));
    }

    public int previousClearBit(int bitIndex)
    {
        return translate(bitSet.nextClearBit(translate(bitIndex)));
    }


    public int previousSetBit(int bitIndex)
    {
        return translate(bitSet.nextSetBit(translate(bitIndex)));
    }


    public void set(int bitIndex)
    {
        bitSet.set(translate(bitIndex));
    }

    public void set(int bitIndex, boolean value)
    {
        if (value)
        {
            set(bitIndex);
        }
        else
        {
            clear(bitIndex);
        }
    }

    public void set(int fromIndex, int toIndex)
    {
        for (int i = fromIndex; i < toIndex; ++i)
        {
            set(i);
        }
    }

    public void set(int fromIndex, int toIndex, boolean value)
    {
        for (int i = fromIndex; i < toIndex; ++i)
        {
            set(i, value);
        }
    }

    public byte[] toByteArray()
    {
        byte[] bytes = new byte[numBits / 8];

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

    public int sizeBytes()
    {
        int numBytes = numBits / 8;
        if (numBits % 8 != 0)
        {
            numBytes++;
        }
        return numBytes;
    }

    public int sizeBits()
    {
        return numBits;
    }

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

    public static LeftToRightBitSet valueOf(byte[] bytes)
    {
        return valueOf(bytes, 0, bytes.length);
    }

    /**
     * Parse the given bytes (at the given offset and length) into a
     * LeftToRightBitSet
     * @param bytes the byte buffer to parse
     * @param offset the offset at which to start parsing
     * @param length the length (in bytes) of the chunk to parse
     * @return a LeftToRightBitSet representing the chunk of the given buffer
     * based on the given offset and length
     */
    public static LeftToRightBitSet valueOf(byte[] bytes, int offset, int length)
    {
        int numBits = bytes.length * 8;
        LeftToRightBitSet b = new LeftToRightBitSet(numBits);
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
     * Shift the bits (in place) from fromIndex (inclusive) to toIndex (inclusive)
     * to the left by shiftAmount bits.
     * @param bitSet the set to shift (in place)
     * @param fromIndex the starting bit position (inclusive) of the chunk to shift
     * @param toIndex the end bit position (inclusive) of the chunk to shift
     * @param shiftAmount the amount of bits to shift
     */
    public static void shiftBitsLeft(LeftToRightBitSet bitSet, int fromIndex,
                                     int toIndex, int shiftAmount)
    {
        for (int i = fromIndex; i <= toIndex; ++i)
        {
            if ((i - shiftAmount) < 0)
            {
                // Make sure we stay within bounds
                continue;
            }
            bitSet.set(i - shiftAmount, bitSet.get(i));
        }
        // Set the positions we shifted out of to false
        for (int i = toIndex; i > toIndex - shiftAmount; --i)
        {
            bitSet.set(i, false);
        }
    }

    public static void shiftBitsRight(LeftToRightBitSet bitSet, int fromIndex,
                                      int toIndex, int shiftAmount)
    {
        for (int i = toIndex; i >= fromIndex; --i)
        {
            if ((i + shiftAmount) > (bitSet.numBits - 1))
            {
                // Make sure we stay within bounds
                continue;
            }
            bitSet.set(i + shiftAmount, bitSet.get(i));
        }
        // Set the positions we shifted out of to false
        for (int i = fromIndex; i < fromIndex + shiftAmount; ++i)
        {
            bitSet.set(i, false);
        }
    }
}
