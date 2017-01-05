package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import org.junit.*;

import static org.junit.Assert.*;

public class TimestampUtilsTest
{
    private final static long kBoundary = 0x80000000L;

    @Test
    public void testTimestampIsNewer()
        throws Exception
    {
        assertEquals(true, TimestampUtils.isNewerTimestamp(1L, 0L));
        assertEquals(true, TimestampUtils.isNewerTimestamp(0L, kBoundary + 1L));
        assertEquals(true, TimestampUtils.isNewerTimestamp(kBoundary, 0L));
    }

    @Test
    public void testTimestampIsOlder()
        throws Exception
    {
        assertEquals(false, TimestampUtils.isNewerTimestamp(0L, 1L));
        assertEquals(false, TimestampUtils.isNewerTimestamp(0L, kBoundary));
    }

    @Test
    public void returnLatestTimestamp()
        throws Exception
    {
        assertEquals(1L, TimestampUtils.latestTimestamp(1L, 0L));
        assertEquals(0L, TimestampUtils.latestTimestamp(0L, kBoundary + 1));
        assertEquals(kBoundary, TimestampUtils.latestTimestamp(1L, kBoundary));
    }

}

