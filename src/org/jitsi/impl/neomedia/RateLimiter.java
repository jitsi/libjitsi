package org.jitsi.impl.neomedia;

/**
 * Simple backoff timer to limit the rate of operations.
 *
 * A simple usage may be to limit the frequency of error logging.
 */
public class RateLimiter
{
    private static final int DEFAULT_MIN_BACKOFF_MS = 10*1000;

    private final TimeProvider timeProvider;

    private int minBackoffMs;
    private int lastUsedInstantMs;

    interface TimeProvider
    {
        long getTimeMs();
    }

    /**
     * Constructor which uses the wall clock as the reference time.
     */
    public RateLimiter()
    {
        this(new TimeProvider()
        {
            @Override
            public long getTimeMs()
            {
                return System.currentTimeMillis();
            }
        });
    }

    /**
     * Constructor which injects a custom TimeProvider for unit testing.
     */
    RateLimiter(TimeProvider timeProvider)
    {
        this.timeProvider = timeProvider;
        minBackoffMs = DEFAULT_MIN_BACKOFF_MS;
        //noinspection NumericOverflow
        lastUsedInstantMs = -(minBackoffMs + 1);
    }

    /**
     * Controls if the rate limited operation should be run.
     *
     * Example:
     * if (logNonFatalError.shouldRun()) {
     *    logger.error("This is a non-fatal error message for which " +
     *        "we want to print at least once and rate limit the frequency of printing."
     * }
     *
     * @returns true if we should allow the rate limited operation to continue.
     */
    boolean shouldRun()
    {
        int nowMs = (int) (timeProvider.getTimeMs());
        if (((nowMs - lastUsedInstantMs) & 0xffff_ffffL) > minBackoffMs)
        {
            lastUsedInstantMs = nowMs;
            return true;
        }
        return false;
    }

    /**
     * @return the minimum backoff time in milliseconds.
     */
    int getMinBackoffMs()
    {
        return minBackoffMs;
    }
}
