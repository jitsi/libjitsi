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

import java.util.*;

import static org.junit.Assert.*;

/**
 * @author bbaldino
 */
public class FlexFec03MaskTest
{
    @Test
    public void testTooShortBuffer()
    {
        final int K_BIT_0 = 0 << 7;
        final byte[] maskData = {
            K_BIT_0 | 0x00
        };

        try
        {
            FlexFec03Mask mask = new FlexFec03Mask(maskData, 0, 0);
            fail("Expected MalformedMaskException");
        }
        catch (FlexFec03Mask.MalformedMaskException e)
        {

        }
    }

    @Test
    public void testCreateFlexFecMaskShort()
        throws Exception
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(0, 1, 3, 5, 14);
        int baseSeqNum = 0;
        FlexFec03Mask mask = new FlexFec03Mask(baseSeqNum, expectedProtectedSeqNums);

        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        assertEquals(expectedProtectedSeqNums, protectedSeqNums);
    }

    @Test
    public void testSeqNumRollover()
        throws Exception
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(65530, 65531, 65533, 65535, 5, 6);
        int baseSeqNum = 65530;

        FlexFec03Mask mask = new FlexFec03Mask(baseSeqNum, expectedProtectedSeqNums);

        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        assertEquals(expectedProtectedSeqNums, protectedSeqNums);
    }

    @Test
    public void testCreateFlexFecMaskMed()
        throws Exception
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(0, 1, 3, 5, 14, 15, 16, 20, 24, 45);
        int baseSeqNum = 0;
        FlexFec03Mask mask = new FlexFec03Mask(baseSeqNum, expectedProtectedSeqNums);

        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        assertEquals(expectedProtectedSeqNums, protectedSeqNums);
    }

    @Test
    public void testCreateFlexFecMaskLong()
        throws Exception
    {
        List<Integer> expectedProtectedSeqNums =
            Arrays.asList(0, 1, 3, 5, 14, 15, 20, 24, 45, 108);
        int baseSeqNum = 0;
        FlexFec03Mask mask = new FlexFec03Mask(baseSeqNum, expectedProtectedSeqNums);

        List<Integer> protectedSeqNums = mask.getProtectedSeqNums();
        assertEquals(expectedProtectedSeqNums, protectedSeqNums);
    }

    /**
     * Since we've already verified that FlexFec03Mask generates a mask correctly
     * from a given set of sequence numbers, we can use that in the following
     * tests to create the expected mask from a set of sequence numbers via
     * the FlexFec03Mask methods we tested above
     */
    private FlexFec03BitSet getMask(int baseSeqNum, List<Integer> protectedSeqNums)
        throws Exception
    {
        FlexFec03Mask m = new FlexFec03Mask(baseSeqNum, protectedSeqNums);
        return m.getMaskWithKBits();
    }

    private void verifyMask(FlexFec03BitSet expected, FlexFec03BitSet actual)
    {
        assertEquals(expected.sizeBits(), actual.sizeBits());
        for (int i = 0; i < expected.sizeBits(); ++i)
        {
            assertEquals(expected.get(i), actual.get(i));
        }
    }

    @Test
    public void testFlexFecMaskShortFromBuffer()
        throws Exception
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            0, 2, 5, 9, 10, 12, 14
        );

        FlexFec03BitSet expectedMask = getMask(0, expectedProtectedSeqNums);

        FlexFec03Mask mask = new FlexFec03Mask(expectedMask.toByteArray(), 0, 0);
        verifyMask(expectedMask, mask.getMaskWithKBits());
    }

    @Test
    public void testFlexFecMaskMedFromBuffer()
        throws Exception
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            0, 2, 5, 9, 10, 12, 14, 15, 22, 32, 45
        );

        FlexFec03BitSet expectedMask = getMask(0, expectedProtectedSeqNums);

        FlexFec03Mask mask = new FlexFec03Mask(expectedMask.toByteArray(), 0, 0);
        verifyMask(expectedMask, mask.getMaskWithKBits());
    }

    @Test
    public void testFlexFecMaskLong()
        throws Exception
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            0, 2, 5, 9, 10, 12, 14, 15, 22, 32, 45, 46, 56, 66, 76, 90, 108
        );

        FlexFec03BitSet expectedMask = getMask(0, expectedProtectedSeqNums);

        FlexFec03Mask mask = new FlexFec03Mask(expectedMask.toByteArray(), 0, 0);
        verifyMask(expectedMask, mask.getMaskWithKBits());
    }

    @Test
    public void testFlexFecTooBigDelta()
        throws Exception
    {
        List<Integer> expectedProtectedSeqNums = Arrays.asList(
            109
        );

        try
        {
            FlexFec03BitSet expectedMask = getMask(0, expectedProtectedSeqNums);
            fail("Should have thrown MalformedMaskException");
        }
        catch (FlexFec03Mask.MalformedMaskException e)
        {

        }
    }
}