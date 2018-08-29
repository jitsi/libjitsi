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
package org.jitsi.util.concurrent;

/**
 * Implements a {@link RecurringRunnable} which has its
 * {@link RecurringRunnable#run()} invoked at a specific interval/period.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public abstract class PeriodicRunnable
    implements RecurringRunnable
{
    /**
     * The last time in milliseconds at which {@link #run} was invoked.
     */
    private long _lastProcessTime;

    /**
     * The interval/period in milliseconds at which {@link #run} is to be
     * invoked.
     */
    private long _period;

    /**
     * Initializes a new {@code PeriodicRunnable} instance which is to have
     * its {@link #run()} invoked at a specific interval/period.
     *
     * @param period the interval/period in milliseconds at which
     * {@link #run()} is to be invoked
     */
    public PeriodicRunnable(long period)
    {
        this(period, false);
    }

    /**
     * Initializes a new {@code PeriodicRunnable} instance which is to have
     * its {@link #run()} invoked at a specific interval/period.
     *
     * @param period the interval/period in milliseconds at which
     * {@link #run()} is to be invoked
     * @param invokeImmediately whether to invoke the runnable immediately or
     * wait for one {@code period} before the first invocation.
     */
    public PeriodicRunnable(long period, boolean invokeImmediately)
    {
        if (period < 1)
            throw new IllegalArgumentException("period " + period);

        _period = period;
        _lastProcessTime = invokeImmediately ? -1 : System.currentTimeMillis();
    }

    /**
     * Gets the last time in milliseconds at which {@link #run} was invoked.
     *
     * @return the last time in milliseconds at which {@link #run} was
     * invoked
     */
    public final long getLastProcessTime()
    {
        return _lastProcessTime;
    }

    /**
     * Gets the interval/period in milliseconds at which {@link #run} is to
     * be invoked.
     *
     * @return the interval/period in milliseconds at which {@link #run} is
     * to be invoked
     */
    public final long getPeriod()
    {
        return _period;
    }

    /**
     * Sets the period in milliseconds at which {@link #run} is to be invoked.
     * Note that the change may not take effect immediately.
     * @param period the period to set.
     */
    public void setPeriod(long period)
    {
        _period = period;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimeUntilNextRun()
    {
        long timeSinceLastProcess
            = Math.max(System.currentTimeMillis() - _lastProcessTime, 0);

        return Math.max(getPeriod() - timeSinceLastProcess, 0);
    }

    /**
     * {@inheritDoc}
     *
     * Updates {@link #_lastProcessTime}.
     */
    @Override
    public void run()
    {
        _lastProcessTime = System.currentTimeMillis();
    }
}
