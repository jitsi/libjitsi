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

/**
 * The utility class which can be used to clear passwords values from
 * 'sun.java.command' system property.
 *
 * @author Pawel Domas
 */
public class PasswordUtil
{
    /**
     * The method will replace password argument values with 'X' in a string
     * which represents command line arguments(arg=value arg2=value4).
     *
     * @param cmdLine a string which represent command line arguments in a form
     *                where each argument is separated by space and value is
     *                assigned by '=' sign. For example "arg=value -arg2=value4
     *                --arg3=val45".
     * @param passwordArg the name of password argument to be shadowed.
     *
     * @return <tt>cmdLine</tt> string with password argument values shadowed by
     *         'X'
     */
    static public String replacePassword(String cmdLine, String passwordArg)
    {
        int passwordIdx = cmdLine.indexOf(passwordArg+"=");
        if (passwordIdx != -1)
        {
            // Get arg=pass
            int argEndIdx = cmdLine.indexOf(" ", passwordIdx);
            // Check if this is not the last argument
            if (argEndIdx == -1)
                argEndIdx = cmdLine.length();
            String passArg = cmdLine.substring(passwordIdx, argEndIdx);

            // Split to get arg=
            String strippedPassArg = passArg.substring(0, passArg.indexOf("="));

            // Modify to have arg=X
            cmdLine = cmdLine.replace(passArg, strippedPassArg + "=X");
        }
        return cmdLine;
    }

    /**
     * Does {@link #replacePassword(String, String)} for every argument given in
     * <tt>passwordArgs</tt> array.
     *
     * @param string command line argument string, e.g. "arg=3 pass=secret"
     * @param passwordArgs the array which contains the names of password
     *                     argument to be shadowed.
     * @return <tt>cmdLine</tt> string with password arguments values shadowed
     *         by 'X'
     */
    static public String replacePasswords(String string, String[] passwordArgs)
    {
        for (String passArg : passwordArgs)
        {
            if (StringUtils.isNullOrEmpty(passArg))
                continue;

            string = replacePassword(string, passArg);
        }
        return string;
    }
}
