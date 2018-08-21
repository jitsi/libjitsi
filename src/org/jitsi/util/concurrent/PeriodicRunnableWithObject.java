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
 * Implements a {@link PeriodicRunnable} associated with an {@code Object}.
 *
 * @param <T> the type of the {@code Object} associated with the
 * {@code PeriodicRunnable}
 *
 * @author Lyubomir Marinov
 * @author George Politis
 * @author Boris Grozev
 */
public abstract class PeriodicRunnableWithObject<T>
    extends PeriodicRunnable
{
    /**
     * The {@code Object} associated with this {@link PeriodicRunnable}.
     */
    public final T o;

    /**
     * Initializes a new {@code PeriodicRunnableWithObject} instance
     * associated with a specific {@code Object}.
     *
     * @param o the {@code Object} associated with the new instance
     * @param period the interval/period in milliseconds at which
     * {@link #run()} is to be invoked
     * @param invokeImmediately whether to invoke the runnable immediately or
     * wait for one {@code period} before the first invocation.
     */
    protected PeriodicRunnableWithObject(
        T o, long period, boolean invokeImmediately)
    {
        super(period, invokeImmediately);

        if (o == null)
            throw new NullPointerException("o");

        this.o = o;
    }

    /**
     * Initializes a new {@code PeriodicRunnableWithObject} instance
     * associated with a specific {@code Object}.
     *
     * @param o the {@code Object} associated with the new instance
     * @param period the interval/period in milliseconds at which
     * {@link #run()} is to be invoked
     */
    protected PeriodicRunnableWithObject(T o, long period)
    {
        this(o, period, false);
    }

    /**
     * Invoked by {@link #run()} (1) before
     * {@link PeriodicRunnable#_lastProcessTime} is updated and (2) removing
     * the requirement of {@link RecurringRunnable#run()} to return a
     * {@code long} value with unknown/undocumented (at the time of this
     * writing) meaning.
     */
    protected abstract void doRun();

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
        try
        {
            doRun();
        }
        finally
        {
            super.run();
        }
    }
}
