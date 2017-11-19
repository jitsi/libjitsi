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

import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * @author Boris Grozev
 */
public class RTPUtilsTest
{
    /**
     * Keep the seed the same to keep the tests deterministic (although ideally
     * these specific tests should pass on all values, random values are only
     * used to avoid testing the whole space).
     */
    private static final Random random = new Random(602214086);

    @Test
    public void testReadUint16AsInt()
    {
        byte[] buf = new byte[2];
        for (int i = 0; i < 0xffff; i++)
        {
            // We assume that writeShort works correctly.
            RTPUtils.writeShort(buf, 0, (short) i);
            int j = RTPUtils.readUint16AsInt(buf, 0);
            assertEquals(i, j);
        }

    }

    @Test
    public void testReadInt16AsInt()
    {
        byte[] buf = new byte[2];
        for (short i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++)
        {
            // We assume that writeShort works correctly.
            RTPUtils.writeShort(buf, 0, i);
            int j = RTPUtils.readInt16AsInt(buf, 0);
            assertEquals(i, j);
        }
    }

    @Test
    public void testGetSequenceNumberDelta()
    {
        assertEquals(-9, RTPUtils.getSequenceNumberDelta(1, 10));
        assertEquals(9, RTPUtils.getSequenceNumberDelta(10, 1));

        assertEquals(7, RTPUtils.getSequenceNumberDelta(1, 65530));
        assertEquals(-7, RTPUtils.getSequenceNumberDelta(65530, 1));

        assertEquals(0, RTPUtils.getSequenceNumberDelta(1234, 1234));
    }

    @Test
    public void testIsOlderSequenceNumberThan()
    {
        assertTrue(RTPUtils.isOlderSequenceNumberThan(1, 10));
        assertFalse(RTPUtils.isOlderSequenceNumberThan(10, 1));

        assertFalse(RTPUtils.isOlderSequenceNumberThan(1, 65530));
        assertTrue(RTPUtils.isOlderSequenceNumberThan(65530, 1));

        assertFalse(RTPUtils.isOlderSequenceNumberThan(1234, 1234));
    }

    @Test
    public void testUint24()
    {
        byte[] buf = new byte[3];
        for (int x = 0; x < 10000; x++)
        {
            int i = random.nextInt(0xffffff);
            RTPUtils.writeUint24(buf, 0, i);
            int j = RTPUtils.readUint24AsInt(buf, 0);
            assertEquals(i, j);
        }
    }
}
