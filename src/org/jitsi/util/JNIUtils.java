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

import com.sun.jna.*;
import java.io.*;
import java.util.regex.*;

/**
 * Implements Java Native Interface (JNI)-related facilities such as loading a
 * JNI library from a jar.
 *
 * @author Lyubomir Marinov
 */
public final class JNIUtils
{
    /**
     * The regular expression pattern which matches the file extension
     * &quot;dylib&quot; that is commonly used on Mac OS X for dynamic
     * libraries/shared objects.
     */
    private static final Pattern DYLIB_PATTERN = Pattern.compile("\\.dylib$");

    public static void loadLibrary(String libname, ClassLoader classLoader)
    {
        try
        {
            System.loadLibrary(libname);
        }
        catch (UnsatisfiedLinkError ulerr)
        {
            // Attempt to extract the library from the resources and load it that
            // way.
            libname = System.mapLibraryName(libname);
            if (Platform.isMac())
                libname = DYLIB_PATTERN.matcher(libname).replaceFirst(".jnilib");

            File embedded;

            try
            {
                embedded
                    = Native.extractFromResourcePath(
                            "/" + Platform.RESOURCE_PREFIX + "/" + libname,
                            classLoader);
            }
            catch (IOException ioex)
            {
                throw ulerr;
            }
            try
            {
                System.load(embedded.getAbsolutePath());
            }
            finally
            {
                // Native.isUnpacked(String) is (package) internal.
                if (embedded.getName().startsWith("jna"))
                {
                    // Native.deleteLibrary(String) is (package) internal.
                    if (!embedded.delete())
                        embedded.deleteOnExit();
                }
            }
        }
    }

    /**
     * Prevents the initialization of new <tt>JNIUtils</tt> instances.
     */
    private JNIUtils()
    {
    }
}
