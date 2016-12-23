package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import org.junit.*;

import static org.junit.Assert.*;

public class InterArrivalTest {

    private final static long kBoundary = 0x80000000L;

    @Test
    public void testTimestampIsNewer()
        throws Exception
    {
        assertEquals(true, InterArrival.isNewerTimestamp(1L, 0L));
        assertEquals(true, InterArrival.isNewerTimestamp(0L, kBoundary + 1L));
        assertEquals(true, InterArrival.isNewerTimestamp(kBoundary, 0L));
    }

    @Test
    public void testTimestampIsOlder()
        throws Exception
    {
        assertEquals(false, InterArrival.isNewerTimestamp(0L, 1L));
        assertEquals(false, InterArrival.isNewerTimestamp(0L, kBoundary));
    }

    @Test
    public void returnLatestTimestamp()
        throws Exception
    {
        assertEquals(1L, InterArrival.latestTimestamp(1L, 0L));
        assertEquals(0L, InterArrival.latestTimestamp(0L, kBoundary + 1));
        assertEquals(kBoundary, InterArrival.latestTimestamp(1L, kBoundary));
    }

}

