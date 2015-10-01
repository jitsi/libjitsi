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

import org.jitsi.service.configuration.*;

import java.io.*;

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
}
