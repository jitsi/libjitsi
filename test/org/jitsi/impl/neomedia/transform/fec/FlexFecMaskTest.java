package org.jitsi.impl.neomedia.transform.fec;

import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by bbaldino on 11/14/17.
 */
public class FlexFecMaskTest
{
    @Test
    public void testCreateFlexFecMaskShort()
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(0, 1, 3, 5, 14);
        int baseSeqNum = 0;
        FlexFecMask mask = new FlexFecMask(baseSeqNum, expectedProtectedSeqNums);

        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        assertEquals(expectedProtectedSeqNums, protectedSeqNums);
    }

    @Test
    public void testCreateFlexFecMaskMed()
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(0, 1, 3, 5, 14, 15, 16, 20, 24, 45);
        int baseSeqNum = 0;
        FlexFecMask mask = new FlexFecMask(baseSeqNum, expectedProtectedSeqNums);

        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        assertEquals(expectedProtectedSeqNums, protectedSeqNums);
    }
    @Test
    public void testCreateFlexFecMaskLong()
    {
        List<Integer> expectedProtectedSeqNums =
            Arrays.asList(0, 1, 3, 5, 14, 15, 20, 24, 45, 108);
        int baseSeqNum = 0;
        FlexFecMask mask = new FlexFecMask(baseSeqNum, expectedProtectedSeqNums);

        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        assertEquals(expectedProtectedSeqNums, protectedSeqNums);
    }

    /**
     * Since we've already verified that FlexFecMask generates a mask correctly
     * from a given set of sequence numbers, we can use that in the following
     * tests to create the expected mask from a set of sequence numbers via
     * the FlexFecMask methods we tested above
     */
    private LeftToRightBitSet getMask(int baseSeqNum, List<Integer> protectedSeqNums)
    {
        FlexFecMask m = new FlexFecMask(baseSeqNum, protectedSeqNums);
        return m.getMaskWithKBits();
    }

    private void verifyMask(LeftToRightBitSet expected, LeftToRightBitSet actual)
    {
        assertEquals(expected.sizeBits(), actual.sizeBits());
        for (int i = 0; i < expected.sizeBits(); ++i)
        {
            assertEquals(expected.get(i), actual.get(i));
        }
    }

    @Test
    public void testFlexFecMaskShortFromBuffer()
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            0, 2, 5, 9, 10, 12, 14
        );

        LeftToRightBitSet expectedMask = getMask(0, expectedProtectedSeqNums);

        FlexFecMask mask = new FlexFecMask(expectedMask.toByteArray(), 0, 0);
        verifyMask(expectedMask, mask.getMaskWithKBits());
    }

    @Test
    public void testFlexFecMaskMedFromBuffer()
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            0, 2, 5, 9, 10, 12, 14, 15, 22, 32, 45
        );

        LeftToRightBitSet expectedMask = getMask(0, expectedProtectedSeqNums);

        FlexFecMask mask = new FlexFecMask(expectedMask.toByteArray(), 0, 0);
        verifyMask(expectedMask, mask.getMaskWithKBits());
    }

    @Test
    public void testFlexFecMaskLong()
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            0, 2, 5, 9, 10, 12, 14, 15, 22, 32, 45, 46, 56, 66, 76, 90, 108
        );

        LeftToRightBitSet expectedMask = getMask(0, expectedProtectedSeqNums);

        FlexFecMask mask = new FlexFecMask(expectedMask.toByteArray(), 0, 0);
        verifyMask(expectedMask, mask.getMaskWithKBits());
    }
}