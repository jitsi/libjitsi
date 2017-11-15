package org.jitsi.impl.neomedia.transform.fec;

import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by bbaldino on 11/14/17.
 */
//FIXME: manually writing out the list of expected protected seq nums is a pain
//and is error-prone (and will require re-reading the spec/re-learning if we
//need to add another test).  unfortunately, writing code that does the logic
//for us would be complicated and worth testing on its own, however, we'll
//have that logic once the send-side logic is in, so i'm hoping we can test
//the send-side code, then use it to test this receive code and that will be
//more straightforward
public class FlexFecMaskTest
{
    private static final byte K_BIT_0 = 0x0 << 7;
    private static final byte K_BIT_1 = (byte)(0x1 << 7);

    @Test
    public void testFlexFecMaskShort()
    {
        byte[] maskBytes = {
            K_BIT_1 | 0x12, 0x34
        };

        FlexFecMask mask = new FlexFecMask(maskBytes, 0, 0);
        assertEquals(2, mask.lengthBytes());
        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            2, 5, 9, 10, 12
        );
        assertEquals(expectedProtectedSeqNums.size(), protectedSeqNums.size());
        for (Integer expectedProtectedSeqNum : expectedProtectedSeqNums)
        {
            assertTrue(protectedSeqNums.contains(expectedProtectedSeqNum));
        }
    }

    @Test
    public void testFlexFecMaskMed()
    {
        byte[] maskBytes = {
            K_BIT_0 | 0x12, 0x34,
            K_BIT_1 | 0x12, 0x34, 0x45, 0x67
        };

        FlexFecMask mask = new FlexFecMask(maskBytes, 0, 0);
        assertEquals(6, mask.lengthBytes());
        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            2, 5, 9, 10, 12,
            17, 20, 24, 25, 27, 31, 35, 37, 39, 40, 43, 44, 45
        );
        assertEquals(expectedProtectedSeqNums.size(), protectedSeqNums.size());
        for (Integer expectedProtectedSeqNum : expectedProtectedSeqNums)
        {
            assertTrue(protectedSeqNums.contains(expectedProtectedSeqNum));
        }
    }

    @Test
    public void testFlexFecMaskLong()
    {
        byte[] maskBytes = {
            K_BIT_0 | 0x12, 0x34,
            K_BIT_0 | 0x12, 0x34, 0x45, 0x67,
            K_BIT_1 | 0x12, 0x34, 0x45, 0x67, 0x12, 0x34, 0x45, 0x67

        };

        FlexFecMask mask = new FlexFecMask(maskBytes, 0, 0);
        assertEquals(14, mask.lengthBytes());
        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            2, 5, 9, 10, 12,
            17, 20, 24, 25, 27, 31, 35, 37, 39, 40, 43, 44, 45,
            48, 51, 55, 56, 58, 62, 66, 68, 70, 71, 74, 75, 76, 80, 83, 87, 88, 90, 94, 98, 100, 102, 103, 106, 107
        );
        assertEquals(expectedProtectedSeqNums.size(), protectedSeqNums.size());
        for (Integer expectedProtectedSeqNum : expectedProtectedSeqNums)
        {
            assertTrue(protectedSeqNums.contains(expectedProtectedSeqNum));
        }
    }
}