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
 * Implements a {@link PeriodicProcessible} associated with an {@code Object}.
 *
 * @param <T> the type of the {@code Object} associated with the
 * {@code PeriodicProcessible}
 */
public abstract class PeriodicProcessibleWithObject<T>
    extends PeriodicProcessible
{
    /**
     * The {@code Object} associated with this {@link PeriodicProcessible}.
     */
    public final T o;

    /**
     * Initializes a new {@code PeriodicProcessibleWithObject} instance
     * associated with a specific {@code Object}.
     *
     * @param o the {@code Object} associated with the new instance
     * @param period the interval/period in milliseconds at which
     * {@link #process()} is to be invoked
     */
    protected PeriodicProcessibleWithObject(T o, long period)
    {
        super(period);

        if (o == null)
            throw new NullPointerException("o");

        this.o = o;
    }

    /**
     * Invoked by {@link #process()} (1) before
     * {@link PeriodicProcessible#_lastProcessTime} is updated and (2) removing
     * the requirement of {@link RecurringProcessible#process()} to return a
     * {@code long} value with unknown/undocumented (at the time of this
     * writing) meaning.
     */
    protected abstract void doProcess();

    /**
     * {@inheritDoc}
     */
    @Override
    public long process()
    {
        long ret;

        try
        {
            doProcess();
        }
        finally
        {
            ret = super.process();
        }
        return ret;
    }
}
