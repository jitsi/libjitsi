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
    public void clear()
        throws
        Exception
    {


    }

    @Test
    public void clear1()
        throws
        Exception
    {

    }

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
    public void flip1()
        throws
        Exception
    {

    }

    @Test
    public void get()
        throws
        Exception
    {

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
        System.out.println(bs.get(0));
        assertFalse(bs.get(0)); // corresponds to position 2
        assertTrue(bs.get(1)); // corresponds to position 3

        System.out.println("3-8 range");
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

        System.out.println(bitSet);

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
    public void testOr()
    {
        BitSet left = new LeftToRightBitSet(8);
        left.flip(0);
        left.flip(4);

        BitSet right = new LeftToRightBitSet(8);
        right.flip(3);
        right.flip(6);

        left.or(right);
        assertTrue(left.get(0));
        assertTrue(left.get(3));
        assertTrue(left.get(4));
        assertTrue(left.get(6));
    }

    @Test
    public void testSet()
    {
        BitSet b = new LeftToRightBitSet(16);
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
        BitSet b = new LeftToRightBitSet(16);
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
        BitSet b = LeftToRightBitSet.valueOf(bytes);
        System.out.println(b);
        assertTrue(b.get(1));
        assertTrue(b.get(2));
        assertTrue(b.get(4));
        assertTrue(b.get(5));
    }
}