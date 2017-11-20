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
    extends BitSet
{
    private int numBits = 0;

    public LeftToRightBitSet(int numBits)
    {
        super(numBits);
        this.numBits = numBits;
    }

    /**
     * Translate a left-side least significant bit index to
     * a right-side least significant bit index.
     * @param leftLSBBitIndex
     * @return
     */
    private int translate(int leftLSBBitIndex)
    {
        if (leftLSBBitIndex < 0)
        {
            return leftLSBBitIndex;
        }
        // Subtract 1 since we're 0-based
        return this.numBits - leftLSBBitIndex - 1;
    }

    @Override
    public void clear(int bitIndex)
    {
        super.clear(translate(bitIndex));
    }

    @Override
    public void clear(int fromIndex, int toIndex)
    {
        // The inverting of the toIndex and fromIndex arguments
        // here is intentional.  We need to do this for the range
        // to be properly formed
        super.clear(translate(toIndex), translate(fromIndex));
    }

    @Override
    public void flip(int bitIndex)
    {
        super.flip(translate(bitIndex));
    }

    @Override
    public void flip(int fromIndex, int toIndex)
    {
        super.flip(translate(toIndex), translate(fromIndex));
    }

    @Override
    public boolean get(int bitIndex)
    {
        return super.get(translate(bitIndex));
    }

    @Override
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

    @Override
    public int nextClearBit(int bitIndex)
    {
        // Implement this by completely inverting the call (translate the
        // index, call the inverse next/previous, then translate
        // the result back)
        return translate(super.previousClearBit(translate(bitIndex)));
    }

    @Override
    public int nextSetBit(int bitIndex)
    {
        return translate(super.previousSetBit(translate(bitIndex)));
    }

    @Override
    public int previousClearBit(int bitIndex)
    {
        return translate(super.nextClearBit(translate(bitIndex)));
    }

    @Override
    public int previousSetBit(int bitIndex)
    {
        return translate(super.nextSetBit(translate(bitIndex)));
    }

    @Override
    public void set(int bitIndex)
    {
        super.set(translate(bitIndex));
    }

    @Override
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

    @Override
    public void set(int fromIndex, int toIndex)
    {
        for (int i = fromIndex; i < toIndex; ++i)
        {
            set(i);
        }
    }

    @Override
    public void set(int fromIndex, int toIndex, boolean value)
    {
        for (int i = fromIndex; i < toIndex; ++i)
        {
            set(i, value);
        }
    }

    @Override
    public IntStream stream()
    {
        throw new NotImplementedException();
    }

    @Override
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

    @Override
    public long[] toLongArray()
    {
        throw new NotImplementedException();
    }

    @Override
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
     * to the left by shiftAmount bits
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
            if (i == 0)
            {
                // This isn't a rotate: this bit will just get dropped
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
}
