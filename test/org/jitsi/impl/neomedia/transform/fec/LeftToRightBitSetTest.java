package org.jitsi.impl.neomedia.transform.fec;

import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by bbaldino on 11/14/17.
 */
public class LeftToRightBitSetTest
{
    LeftToRightBitSet bitSet;

    @Test
    public void flip()
        throws
        Exception
    {
        bitSet = new LeftToRightBitSet(16);
        bitSet.flip(0);
        bitSet.flip(1);
        bitSet.flip(3);
        bitSet.flip(7);
        byte expectedByte0 = (byte)0b11010001;

        bitSet.flip(9);
        bitSet.flip(10);
        bitSet.flip(15);
        byte expectedByte1 = 0b01100001;

        byte[] bytes = bitSet.toByteArray();
        assertEquals(expectedByte0, bytes[0]);
        assertEquals(expectedByte1, bytes[1]);
    }

    @Test
    public void getRange()
        throws
        Exception
    {
        bitSet = new LeftToRightBitSet(16);
        bitSet.flip(0);
        bitSet.flip(1);
        bitSet.flip(3);
        bitSet.flip(7);

        bitSet.flip(9);
        bitSet.flip(10);
        bitSet.flip(15);

        LeftToRightBitSet bs = bitSet.get(2, 4);
        assertFalse(bs.get(0)); // corresponds to position 2
        assertTrue(bs.get(1)); // corresponds to position 3

        bs = bitSet.get(3, 8);
        assertTrue(bs.get(0)); // corresponds to position 3
        assertFalse(bs.get(1)); // corresponds to position 4
        assertFalse(bs.get(2)); // corresponds to position 5
        assertFalse(bs.get(3)); // corresponds to position 6
        assertTrue(bs.get(4)); // corresponds to position 7
    }

    @Test
    public void nextClearBit()
        throws
        Exception
    {
        bitSet = new LeftToRightBitSet(16);
        bitSet.flip(0);
        bitSet.flip(1);
        bitSet.flip(3);
        bitSet.flip(7);

        bitSet.flip(9);
        bitSet.flip(10);
        bitSet.flip(15);

        assertEquals(11, bitSet.nextClearBit(9));
        assertEquals(2, bitSet.nextClearBit(2));
        assertEquals(-1, bitSet.nextClearBit(15));
    }

    @Test
    public void nextSetBit()
        throws
        Exception
    {
        bitSet = new LeftToRightBitSet(16);
        bitSet.flip(0);
        bitSet.flip(1);
        bitSet.flip(3);
        bitSet.flip(7);

        bitSet.flip(9);
        bitSet.flip(10);
        bitSet.flip(15);

        assertEquals(9, bitSet.nextSetBit(9));
        assertEquals(3, bitSet.nextSetBit(2));
    }

    @Test
    public void testSet()
    {
        LeftToRightBitSet b = new LeftToRightBitSet(16);
        List<Integer> positions = Arrays.asList(0, 8, 11, 14);
        for (Integer p : positions)
        {
            b.set(p);
        }
        for (int i = 0; i < 16; ++i)
        {
            if (positions.contains(i))
            {
                assertTrue(b.get(i));
            }
            else
            {
                assertFalse(b.get(i));
            }
        }
    }

    @Test
    public void testSetRange()
    {
        LeftToRightBitSet b = new LeftToRightBitSet(16);
        int rangeStart = 5;
        int rangeEnd = 9;
        b.set(rangeStart, rangeEnd);

        for (int i = 0; i < 16; ++i)
        {
            if (i >= rangeStart && i < rangeEnd)
            {
                assertTrue(b.get(i));
            }
            else
            {
                assertFalse(b.get(i));
            }
        }
    }

    @Test
    public void testValueOf()
    {
        byte[] bytes = { 0x6c }; // 0b01101100
        LeftToRightBitSet b = LeftToRightBitSet.valueOf(bytes);
        assertTrue(b.get(1));
        assertTrue(b.get(2));
        assertTrue(b.get(4));
        assertTrue(b.get(5));
    }

    @Test
    public void testLeftShift1()
    {
        byte[] bytes = {
            (byte)0b11111111
        };
        LeftToRightBitSet bitSet = LeftToRightBitSet.valueOf(bytes);
        LeftToRightBitSet.shiftBitsLeft(bitSet, 0, 7, 1);
        assertEquals((byte)0b11111110, bitSet.toByteArray()[0]);
    }

    @Test
    public void testLeftShift2()
    {
        byte[] bytes = {
            (byte)0b10000000
        };
        LeftToRightBitSet bitSet = LeftToRightBitSet.valueOf(bytes);
        LeftToRightBitSet.shiftBitsLeft(bitSet, 0, 7, 1);
        assertEquals(0, bitSet.toByteArray()[0]);
    }

    @Test
    public void testLeftShiftRange()
    {
        byte[] bytes = {
            (byte)0b11111111
        };
        LeftToRightBitSet bitSet = LeftToRightBitSet.valueOf(bytes);
        LeftToRightBitSet.shiftBitsLeft(bitSet, 1, 3, 2);
        assertEquals((byte)0b11001111, bitSet.toByteArray()[0]);
    }

    @Test
    public void testLeftShiftRange2()
    {
        byte[] bytes = {
            0b01110000
        };
        LeftToRightBitSet bitSet = LeftToRightBitSet.valueOf(bytes);
        LeftToRightBitSet.shiftBitsLeft(bitSet, 1, 3, 2);
        assertEquals((byte)0b11000000, bitSet.toByteArray()[0]);
    }

    @Test
    public void testRightShift1()
    {
        byte[] bytes = {
            (byte)0b11111111
        };
        LeftToRightBitSet bitSet = LeftToRightBitSet.valueOf(bytes);
        LeftToRightBitSet.shiftBitsRight(bitSet, 0, 7, 1);
        assertEquals((byte)0b01111111, bitSet.toByteArray()[0]);
    }

    @Test
    public void testRightShift2()
    {
        byte[] bytes = {
            (byte)0b00000001
        };
        LeftToRightBitSet bitSet = LeftToRightBitSet.valueOf(bytes);
        LeftToRightBitSet.shiftBitsRight(bitSet, 0, 7, 1);
        assertEquals(0, bitSet.toByteArray()[0]);
    }

    @Test
    public void testRightShiftRange()
    {
        byte[] bytes = {
            (byte)0b11111111
        };
        LeftToRightBitSet bitSet = LeftToRightBitSet.valueOf(bytes);
        LeftToRightBitSet.shiftBitsRight(bitSet, 1, 3, 2);
        assertEquals(bitSet.toByteArray()[0], (byte)0b10011111);
    }

    @Test
    public void testRightShiftRange2()
    {
        byte[] bytes = {
            0b01110000
        };
        LeftToRightBitSet bitSet = LeftToRightBitSet.valueOf(bytes);
        LeftToRightBitSet.shiftBitsRight(bitSet, 1, 3, 2);
        assertEquals((byte)0b00011100, bitSet.toByteArray()[0]);
    }
}