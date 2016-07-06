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
 * Implements a {@link Logger} backed by a {@link java.util.logging.Logger}.
 *
 * @author Boris Grozev
 */
public class LoggerImpl
    extends Logger
{
    /**
     * The java.util.Logger that would actually be doing the logging.
     */
    private final java.util.logging.Logger loggerDelegate;

    /**
     * Base constructor
     *
     * @param logger the implementation specific logger delegate that this
     * Logger instance should be created around.
     */
    protected LoggerImpl(java.util.logging.Logger logger)
    {
        this.loggerDelegate = logger;
    }

    /**
     * Set logging level for all handlers to <tt>level</tt>
     *
     * @param level the level to set for all logger handlers
     */
    @Override
    public void setLevel(java.util.logging.Level level)
    {
        Handler[] handlers = loggerDelegate.getHandlers();
        for (Handler handler : handlers)
            handler.setLevel(level);

        loggerDelegate.setLevel(level);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Level getLevel()
    {
        // OpenJDK's Logger implementation initializes its effective level value
        // with Level.INFO.intValue(), but DOESN'T initialize the Level object.
        // So, if it hasn't been explicitly set, assume INFO.
        Level level = loggerDelegate.getLevel();
        return level != null ? level : Level.INFO;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    boolean isLoggable(Level level)
    {
        return loggerDelegate.isLoggable(level);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(Level level, Object msg)
    {
        loggerDelegate.log(level, msg != null ? msg.toString() : "null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(Level level, Object msg, Throwable thrown)
    {
        loggerDelegate.log(level, msg != null ? msg.toString() : "null", thrown);
    }

    /**
     * Reinitialize the logging properties and reread the logging configuration.
     * <p>
     * The same rules are used for locating the configuration properties
     * as are used at startup. So if the properties containing the log dir
     * locations have changed, we would read the new configuration.
     */
    @Override
    public void reset()
    {
        try
        {
            FileHandler.pattern = null;
            LogManager.getLogManager().reset();
            LogManager.getLogManager().readConfiguration();
        }
        catch (Exception e)
        {
            loggerDelegate.log(Level.INFO, "Failed to reinit logger.", e);
        }
    }
}
