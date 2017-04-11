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

import java.io.*;
import org.jitsi.service.configuration.*;

/**
 * @author George Politis
 */
public class ConfigUtils
{
    /**
     * Gets an absolute path in the form of <tt>File</tt> from an absolute or
     * relative <tt>path</tt> specified in the form of a <tt>String</tt>. If
     * <tt>path</tt> is relative, it is resolved against
     * <tt>ConfigurationService.PNAME_SC_HOME_DIR_LOCATION</tt> and
     * <tt>ConfigurationService.PNAME_SC_HOME_DIR_NAME</tt>, <tt>user.home</tt>,
     * or the current working directory.
     *
     * @param path the absolute or relative path in the form of <tt>String</tt>
     * for/from which an absolute path in the form of <tt>File</tt> is to be
     * returned
     * @param cfg the <tt>ConfigurationService</tt> to be employed by the method
     * (invocation) if necessary
     * @return an absolute path in the form of <tt>File</tt> for/from the
     * specified <tt>path</tt>
     */
    public static File getAbsoluteFile(String path, ConfigurationService cfg)
    {
        File file = new File(path);

        if (!file.isAbsolute())
        {
            String scHomeDirLocation, scHomeDirName;

            if (cfg == null)
            {
                scHomeDirLocation
                    = System.getProperty(
                        ConfigurationService.PNAME_SC_HOME_DIR_LOCATION);
                scHomeDirName
                    = System.getProperty(
                        ConfigurationService.PNAME_SC_HOME_DIR_NAME);
            }
            else
            {
                scHomeDirLocation = cfg.getScHomeDirLocation();
                scHomeDirName = cfg.getScHomeDirName();
            }
            if (scHomeDirLocation == null)
            {
                scHomeDirLocation = System.getProperty("user.home");
                if (scHomeDirLocation == null)
                    scHomeDirLocation = ".";
            }
            if (scHomeDirName == null)
                scHomeDirName = ".";
            file
                = new File(new File(scHomeDirLocation, scHomeDirName), path)
                    .getAbsoluteFile();
        }
        return file;
    }

    /**
     * Gets the value as a {@code boolean} of a property from either a specific
     * {@code ConfigurationService} or {@code System}.
     *
     * @param cfg the {@code ConfigurationService} to get the value from or
     * {@code null} if the property is to be retrieved from {@code System}
     * @param property the name of the property to get
     * @param defaultValue the value to be returned if {@code property} is not
     * associated with a value
     * @return the value as a {@code boolean} of {@code property} retrieved from
     * either {@code cfg} or {@code System}
     */
    public static boolean getBoolean(
            ConfigurationService cfg,
            String property,
            boolean defaultValue)
    {
        boolean b;

        if (cfg == null)
        {
            String s = System.getProperty(property);

            b
                = (s == null || s.length() == 0)
                    ? defaultValue
                    : Boolean.parseBoolean(s);
        }
        else
        {
            b = cfg.getBoolean(property, defaultValue);
        }
        return b;
    }

    /**
     * Gets the value as an {@code int} of a property from either a specific
     * {@code ConfigurationService} or {@code System}.
     *
     * @param cfg the {@code ConfigurationService} to get the value from or
     * {@code null} if the property is to be retrieved from {@code System}
     * @param property the name of the property to get
     * @param defaultValue the value to be returned if {@code property} is not
     * associated with a value
     * @return the value as an {@code int} of {@code property} retrieved from
     * either {@code cfg} or {@code System}
     */
    public static int getInt(
            ConfigurationService cfg,
            String property,
            int defaultValue)
    {
        int i;

        if (cfg == null)
        {
            String s = System.getProperty(property);

            if (s == null || s.length() == 0)
            {
                i = defaultValue;
            }
            else
            {
                try
                {
                    i = Integer.parseInt(s);
                }
                catch (NumberFormatException nfe)
                {
                    i = defaultValue;
                }
            }
        }
        else
        {
            i = cfg.getInt(property, defaultValue);
        }
        return i;
    }

    /**
     * Gets the value as an {@code long} of a property from either a specific
     * {@code ConfigurationService} or {@code System}.
     *
     * @param cfg the {@code ConfigurationService} to get the value from or
     * {@code null} if the property is to be retrieved from {@code System}
     * @param property the name of the property to get
     * @param defaultValue the value to be returned if {@code property} is not
     * associated with a value
     * @return the value as an {@code long} of {@code property} retrieved from
     * either {@code cfg} or {@code System}
     */
    public static long getLong(
            ConfigurationService cfg,
            String property,
            long defaultValue)
    {
        long i;

        if (cfg == null)
        {
            String s = System.getProperty(property);

            if (s == null || s.length() == 0)
            {
                i = defaultValue;
            }
            else
            {
                try
                {
                    i = Long.parseLong(s);
                }
                catch (NumberFormatException nfe)
                {
                    i = defaultValue;
                }
            }
        }
        else
        {
            i = cfg.getLong(property, defaultValue);
        }
        return i;
    }

    /**
     * Gets the value as a {@code String} of a property from either a specific
     * {@code ConfigurationService} or {@code System}.
     *
     * @param cfg the {@code ConfigurationService} to get the value from or
     * {@code null} if the property is to be retrieved from {@code System}
     * @param property the name of the property to get
     * @param defaultValue the value to be returned if {@code property} is not
     * associated with a value
     * @return the value as a {@code String} of {@code property} retrieved from
     * either {@code cfg} or {@code System}
     */
    public static String getString(
            ConfigurationService cfg,
            String property,
            String defaultValue)
    {
        String s;

        if (cfg == null)
            s = System.getProperty(property, defaultValue);
        else
            s = cfg.getString(property, defaultValue);
        return s;
    }

    /**
     * Gets the value as a {@code String} of a property from either a specific
     * {@code ConfigurationService} or {@code System}.
     *
     * @param cfg the {@code ConfigurationService} to get the value from or
     * {@code null} if the property is to be retrieved from {@code System}
     * @param property the name of the property to get
     * @param propertyAlternative an alternative name of the property
     * @param defaultValue the value to be returned if {@code property} is not
     * associated with a value
     * @return the value as a {@code String} of {@code property} retrieved from
     * either {@code cfg} or {@code System}
     */
    public static String getString(ConfigurationService cfg,
                            String property,
                            String propertyAlternative,
                            String defaultValue)
    {
        String ret = getString(cfg, property, null);
        if (ret == null)
        {
            ret = getString(cfg, propertyAlternative, defaultValue);
        }
        return ret;
    }
}
