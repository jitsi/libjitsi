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

import java.util.logging.*;

/**
 * Implements a {@link Logger}, which delegates logging to another
 * {@link Logger}, and allows it's level to be lowered independent of the
 * delegate (by another {@link Logger} instance or by a level configuration).
 *
 * @author Boris Grozev
 */
public class InstanceLogger
    extends Logger
{
    /**
     * The {@link Logger} used for logging.
     */
    private Logger loggingDelegate;

    /**
     * The {@link Logger} used for configuring the level. Messages are logged
     * iff:
     * 1. {@link #levelDelegate} is not set or allows it.
     * 2. {@link #loggingDelegate} allows it.
     * 3. {@link #level}, is not set or allows it.
     */
    private Logger levelDelegate;

    /**
     * The level configured for this instance.
     */
    private Level level = null;

    /**
     * Initializes an {@link InstanceLogger} instance with the given delegate
     * for logging and for getting the level.
     */
    protected InstanceLogger(Logger loggingDelegate,
                             Logger levelDelegate)
    {
        this.loggingDelegate = loggingDelegate;
        this.levelDelegate = levelDelegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLevel(Level level)
    {
        this.level = level;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Level getLevel()
    {
        return higher(
                higher(level != null ? level : Level.ALL,
                       loggingDelegate.getLevel()),
                levelDelegate != null ? levelDelegate.getLevel() : Level.ALL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    boolean isLoggable(Level level)
    {
        Level loggerLevel = getLevel();
        if (level == null || loggerLevel == Level.OFF)
        {
            return false;
        }

        return level.intValue() >= loggerLevel.intValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(Level level, Object msg)
    {
        if (isLoggable(level))
        {
            loggingDelegate.log(level, msg != null ? msg.toString() : "null");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(Level level, Object msg, Throwable thrown)
    {
        if (isLoggable(level))
        {
            loggingDelegate
                .log(level, msg != null ? msg.toString() : "null", thrown);
        }
    }

    /**
     * @return the higher of two logging levels.
     * e.g.: higher(Level.FINE, Level.WARNING) -> Level.WARNING
     */
    private Level higher(Level a, Level b)
    {
        if (a.intValue() >= b.intValue())
            return a;
        return b;
    }
}
