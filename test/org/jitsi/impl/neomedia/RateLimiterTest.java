package org.jitsi.impl.neomedia;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RateLimiterTest
{
    abstract class SettableTimeProvider implements RateLimiter.TimeProvider
    {
        abstract public void setTimeMs(long timeMs);
    }

    private SettableTimeProvider settableTime = new SettableTimeProvider()
    {
        long timeMs;

        public void setTimeMs(long timeMs)
        {
            this.timeMs = timeMs;
        }

        @Override
        public long getTimeMs()
        {
            return timeMs;
        }
    };

    private RateLimiter rateLimiter;

    @Before
    public void setup()
    {
        rateLimiter = new RateLimiter(settableTime);
    }


    @Test
    public void testInitialState()
    {
        settableTime.setTimeMs(0);
        assertTrue(rateLimiter.shouldRun());
    }

    @Test
    public void testDenyRun()
    {
        settableTime.setTimeMs(0);
        assertTrue(rateLimiter.shouldRun());
        assertFalse(rateLimiter.shouldRun());
        settableTime.setTimeMs(rateLimiter.getMinBackoffMs() - 1);
        assertFalse(rateLimiter.shouldRun());
    }

    @Test
    public void testAllowRunAfterBackoffPeriod()
    {
        settableTime.setTimeMs(0);
        assertTrue(rateLimiter.shouldRun());
        settableTime.setTimeMs(rateLimiter.getMinBackoffMs());
        assertFalse(rateLimiter.shouldRun());
    }

    @Test
    public void testRollover()
    {
        settableTime.setTimeMs(Integer.MIN_VALUE);
        assertTrue(rateLimiter.shouldRun());
    }
}
