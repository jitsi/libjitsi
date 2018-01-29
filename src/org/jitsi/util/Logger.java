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

import java.util.*;
import java.util.logging.*;

/**
 * Standard logging methods.
 *
 * @author Emil Ivov
 * @author Boris Grozev
 */
public abstract class Logger
{
    /**
     * Create a logger for the specified class.
     * <p>
     * @param clazz The class for which to create a logger.
     * <p>
     * @return a suitable Logger
     * @throws NullPointerException if the class is null.
     */
    public static Logger getLogger(Class<?> clazz)
        throws NullPointerException
    {
        return getLogger(clazz.getName());
    }

    /**
     * Create a logger for the specified name.
     * <p>
     * @return a suitable Logger
     * @throws NullPointerException if the name is null.
     */
    public static Logger getLogger(String name)
        throws NullPointerException
    {
        return new LoggerImpl(java.util.logging.Logger.getLogger(name));
    }

    /**
     * Creates a new {@link Logger} instance which performs logging through
     * {@code loggingDelegate} and uses {@code levelDelegate} to configure its
     * level.
     * @param loggingDelegate the {@link Logger} used for logging.
     * @param levelDelegate the {@link Logger} used for configuring the log
     * level.
     */
    public static Logger getLogger(Logger loggingDelegate, Logger levelDelegate)
    {
        return new InstanceLogger(loggingDelegate, levelDelegate);
    }

    /**
     * Logs an entry in the calling method.
     */
    public void logEntry()
    {
        if (isLoggable(Level.FINEST))
        {
            StackTraceElement caller = new Throwable().getStackTrace()[1];
            log(Level.FINEST, "[entry] " + caller.getMethodName());
        }
    }

    /**
     * Logs exiting the calling method
     */
    public void logExit()
    {
        if (isLoggable(Level.FINEST))
        {
            StackTraceElement caller = new Throwable().getStackTrace()[1];
            log(Level.FINEST, "[exit] " + caller.getMethodName());
        }
    }

    /**
     * Check if a message with a TRACE level would actually be logged by this
     * logger.
     * <p>
     * @return true if the TRACE level is currently being logged
     */
    public boolean isTraceEnabled()
    {
        return isLoggable(Level.FINER);
    }

    /**
     * Log a TRACE message.
     * <p>
     * If the logger is currently enabled for the TRACE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param msg The message to log
     */
    public void trace(Object msg)
    {
        log(Level.FINER, msg);
    }

    /**
     * Log a message, with associated Throwable information.
     * <p>
     * @param msg   The message to log
     * @param   t   Throwable associated with log message.
     */
    public void trace(Object msg, Throwable t)
    {
        log(Level.FINER, msg, t);
    }

    /**
     * Check if a message with a DEBUG level would actually be logged by this
     * logger.
     * <p>
     * @return true if the DEBUG level is currently being logged
     */
    public boolean isDebugEnabled()
    {
        return isLoggable(Level.FINE);
    }

    /**
     * Log a DEBUG message.
     * <p>
     * If the logger is currently enabled for the DEBUG message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param msg The message to log
     */
    public void debug(Object msg)
    {
        log(Level.FINE, msg);
    }

    /**
     * Log a message, with associated Throwable information.
     * <p>
     * @param msg    The message to log
     * @param t  Throwable associated with log message.
     */
    public void debug(Object msg, Throwable t)
    {
        log(Level.FINE, msg, t);
    }

    /**
     * Check if a message with an INFO level would actually be logged by this
     * logger.
     *
     * @return true if the INFO level is currently being logged
     */
    public boolean isInfoEnabled()
    {
        return isLoggable(Level.INFO);
    }

    /**
     * Log a INFO message.
     * <p>
     * If the logger is currently enabled for the INFO message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param msg The message to log
     */
    public void info(Object msg)
    {
        log(Level.INFO, msg);
    }

    /**
     * Log a message, with associated Throwable information.
     * <p>
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void info(Object msg, Throwable t)
    {
        log(Level.INFO, msg, t);
    }

    /**
     * Check if a message with a WARN level would actually be logged by this
     * logger.
     * <p>
     * @return true if the WARN level is currently being logged
     */
    public boolean isWarnEnabled()
    {
        return isLoggable(Level.WARNING);
    }

    /**
     * Log a WARN message.
     * <p>
     * If the logger is currently enabled for the WARN message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param msg The message to log
     */
    public void warn(Object msg)
    {
        log(Level.WARNING, msg);
    }

    /**
     * Log a message, with associated Throwable information.
     * <p>
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void warn(Object msg, Throwable t)
    {
        log(Level.WARNING, msg, t);
    }

    /**
     * Log a ERROR message.
     * <p>
     * If the logger is currently enabled for the ERROR message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param msg The message to log
     */
    public void error(Object msg)
    {
        log(Level.SEVERE, msg);
    }

    /**
     * Log a message, with associated Throwable information.
     * <p>
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void error(Object msg, Throwable t)
    {
        log(Level.SEVERE, msg, t);
    }

    /**
     * Log a FATAL message.
     * <p>
     * If the logger is currently enabled for the FATAL message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     * @param msg The message to log
     */
    public void fatal(Object msg)
    {
        error(msg);
    }

    /**
     * Log a message, with associated Throwable information.
     * <p>
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void fatal(Object msg, Throwable t)
    {
        error(msg, t);
    }

    /**
     * Set logging level for all handlers to FATAL
     */
    public void setLevelFatal()
    {
        setLevel(Level.SEVERE);
    }

    /**
     * Set logging level for all handlers to ERROR
     */
    public void setLevelError()
    {
        setLevel(Level.SEVERE);
    }

    /**
     * Set logging level for all handlers to WARNING
     */
    public void setLevelWarn()
    {
        setLevel(Level.WARNING);
    }

    /**
     * Set logging level for all handlers to INFO
     */
    public void setLevelInfo()
    {
        setLevel(Level.INFO);
    }

    /**
     * Set logging level for all handlers to DEBUG
     */
    public void setLevelDebug()
    {
        setLevel(Level.FINE);
    }

    /**
     * Set logging level for all handlers to TRACE
     */
    public void setLevelTrace()
    {
        setLevel(Level.FINER);
    }

    /**
     * Set logging level for all handlers to ALL (allow all log messages)
     */
    public void setLevelAll()
    {
        setLevel(Level.ALL);
    }

    /**
     * Set logging level for all handlers to OFF (allow no log messages)
     */
    public void setLevelOff()
    {
        setLevel(Level.OFF);
    }

    /**
     * Reinitialize the logging properties and reread the logging configuration.
     * <p>
     * The same rules are used for locating the configuration properties
     * as are used at startup. So if the properties containing the log dir
     * locations have changed, we would read the new configuration.
     */
    public void reset()
    {
    }

    /**
     * Set logging level for all handlers to <tt>level</tt>
     *
     * @param level the level to set for all logger handlers
     */
    public abstract void setLevel(java.util.logging.Level level);

    /**
     * @return the {@link Level} configured for this {@link Logger}.
     */
    public abstract Level getLevel();

    /**
     * Checks whether messages with a particular level should be logged
     * according to the log level configured for this {@link Logger}.
     * @param level the log level.
     */
    abstract boolean isLoggable(Level level);

    /**
     * Logs a message at a given level, if that level is loggable according to
     * the log level configured by this instance.
     * @param level the level at which to log the message.
     * @param msg the message to log.
     */
    public abstract void log(Level level, Object msg);

    /**
     * Logs a message at a given level, if that level is loggable according to
     * the log level configured by this instance.
     * @param level the level at which to log the message.
     * @param msg the message to log.
     * @param thrown a {@link Throwable} associated with log message.
     */
    public abstract void log(Level level, Object msg, Throwable thrown);

    /**
     * Logs a given message with and given category at a given level, if that
     * level is loggable according to the log level configured by this instance.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * @param level the level at which to log the message.
     * @param category the category.
     * @param msg the message to log.
     */
    public void log(Level level, Category category, String msg)
    {
        Objects.requireNonNull(category, "category");
        log(level, category.prepend + msg);
    }

    /**
     * Logs a given message with and given category at a given level, if that
     * level is loggable according to the log level configured by this instance.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * @param level the level at which to log the message.
     * @param category the category.
     * @param msg the message to log.
     * @param thrown a {@link Throwable} associated with log message.
     */
    public void log(
            Level level, Category category,
            String msg, Throwable thrown)
    {
        Objects.requireNonNull(category, "category");
        log(level, category.prepend + msg, thrown);
    }

    /**
     * Log a message with debug level.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * <p>
     * @param msg The message to log
     * @param category the category.
     */
    public void debug(Category category, String msg)
    {
        log(Level.FINE, category, msg);
    }

    /**
     * Log a message with debug level, with associated Throwable information.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * <p>
     * @param msg The message to log
     * @param category the category.
     * @param t  Throwable associated with log message.
     */
    public void debug(Category category, String msg, Throwable t)
    {
        log(Level.FINE, category, msg, t);
    }

    /**
     * Log a message with error level.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * <p>
     * @param msg The message to log
     * @param category the category.
     */
    public void error(Category category, String msg)
    {
        log(Level.SEVERE, category, msg);
    }

    /**
     * Log a message with error level, with associated Throwable information.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * <p>
     * @param msg The message to log
     * @param category the category.
     * @param t Throwable associated with log message.
     */
    public void error(Category category, String msg, Throwable t)
    {
        log(Level.SEVERE, category, msg, t);
    }

    /**
     * Log a message with info level, with associated Throwable information.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * <p>
     * @param category the category.
     * @param msg The message to log
     * @param t Throwable associated with log message.
     */
    public void info(Category category, String msg, Throwable t)
    {
        log(Level.INFO, category, msg, t);
    }

    /**
     * Log a message with info level.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * <p>
     * @param category the category.
     * @param msg The message to log
     */
    public void info(Category category, String msg)
    {
        log(Level.INFO, category, msg);
    }

    /**
     * An enumeration of different categories for log messages.
     */
    public enum Category
    {
        /**
         * A category for log messages containing statistics.
         */
        STATISTICS("stat"),

        /**
         * A category for messages which needn't be stored.
         */
        VOLATILE("vol");

        /**
         * The short string which identifies the category and is added to
         * messages logged with this category.
         */
        private String name;

        /**
         * The string to prepend to messages with this category.
         */
        private String prepend;


        /**
         * Initializes a {@link Category} instance with the given name.
         * @param name The short string which identifies the category.
         */
        Category(String name)
        {
            this.name = name;
            this.prepend = "CAT=" + name + " ";
        }

        /**
         * {@inheritDoc}
         * @return
         */
        @Override
        public String toString()
        {
            return name;
        }
    }
}
