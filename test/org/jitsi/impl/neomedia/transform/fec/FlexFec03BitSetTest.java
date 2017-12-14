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

import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author bbaldino
 */
public class FlexFec03BitSetTest
{
    @Test
    public void testValueOfOneByte()
    {
        byte[] bytes = new byte[] {
            (byte)0b11110000
        };

        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        // The the bytes should have been completely flipped, such that the
        // overall MSB in the bytes we gave it is now the overall LSB.
        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(1));
        assertTrue(bitSet.get(2));
        assertTrue(bitSet.get(3));
        assertFalse(bitSet.get(4));
        assertFalse(bitSet.get(5));
        assertFalse(bitSet.get(6));
        assertFalse(bitSet.get(7));
    }

    @Test
    public void testValueOfMultiByte()
    {
        byte[] bytes = new byte[] {
            (byte)0b11110000, (byte)0b11110000
        };

        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        // The the bytes should have been completely flipped, such that the
        // overall MSB in the bytes we gave it is now the overall LSB.
        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(1));
        assertTrue(bitSet.get(2));
        assertTrue(bitSet.get(3));
        assertFalse(bitSet.get(4));
        assertFalse(bitSet.get(5));
        assertFalse(bitSet.get(6));
        assertFalse(bitSet.get(7));

        assertTrue(bitSet.get(8));
        assertTrue(bitSet.get(9));
        assertTrue(bitSet.get(10));
        assertTrue(bitSet.get(11));
        assertFalse(bitSet.get(12));
        assertFalse(bitSet.get(13));
        assertFalse(bitSet.get(14));
        assertFalse(bitSet.get(15));
    }

    @Test
    public void testToByteArraySingleByte()
    {
        byte[] bytes = new byte[] {
            (byte)0b11110000
        };

        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        byte[] result = bitSet.toByteArray();

        assertArrayEquals(bytes, result);
    }

    @Test
    public void testToByteArrayMultiBytes()
    {
        byte[] bytes = new byte[] {
            (byte)0b11110000, (byte)0b11110000
        };

        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        byte[] result = bitSet.toByteArray();

        assertArrayEquals(bytes, result);
    }

    @Test
    public void testSetBit()
    {
        byte[] bytes = new byte[] {
            (byte)0b01110000
        };

        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        bitSet.set(6);
        // Make the same change in the expected array
        bytes[0] |= (0x80 >> 6);
        byte[] result = bitSet.toByteArray();

        assertArrayEquals(bytes, result);
    }

    @Test
    public void testSetBitMultiByte()
    {
        byte[] bytes = new byte[] {
            (byte)0b01110000, (byte)0b01110000
        };

        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        bitSet.set(8);
        // Make the same change in the expected array
        bytes[1] |= 0x80;
        byte[] result = bitSet.toByteArray();

        assertArrayEquals(bytes, result);
    }

    @Test
    public void testAddBit()
    {
        byte[] bytes = new byte[] {
            (byte)0b11110000
        };
        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        bitSet.addBit(4, true);
        assertEquals(9, bitSet.sizeBits());
        assertEquals(2, bitSet.sizeBytes());

        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(1));
        assertTrue(bitSet.get(2));
        assertTrue(bitSet.get(3));
        assertTrue(bitSet.get(4));
        assertFalse(bitSet.get(5));
        assertFalse(bitSet.get(6));
        assertFalse(bitSet.get(7));
        assertFalse(bitSet.get(8));
    }

    @Test
    public void testAdd2Bits()
    {
        byte[] bytes = new byte[] {
            (byte)0b11110000
        };
        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        bitSet.addBit(4, true);
        bitSet.addBit(6, false);
        assertEquals(10, bitSet.sizeBits());
        assertEquals(2, bitSet.sizeBytes());

        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(1));
        assertTrue(bitSet.get(2));
        assertTrue(bitSet.get(3));
        assertTrue(bitSet.get(4));
        assertFalse(bitSet.get(5));
        assertFalse(bitSet.get(6));
        assertFalse(bitSet.get(7));
        assertFalse(bitSet.get(8));
        assertFalse(bitSet.get(9));
    }

    @Test
    public void removeBit()
    {
        byte[] bytes = new byte[] {
            (byte)0b11110000
        };
        FlexFec03BitSet bitSet = FlexFec03BitSet.valueOf(bytes);
        bitSet.removeBit(0);
        assertEquals(7, bitSet.sizeBits());
        assertEquals(1, bitSet.sizeBytes());

        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(1));
        assertTrue(bitSet.get(2));
        assertFalse(bitSet.get(3));
        assertFalse(bitSet.get(4));
        assertFalse(bitSet.get(5));
        assertFalse(bitSet.get(6));
        assertFalse(bitSet.get(7));
    }

}