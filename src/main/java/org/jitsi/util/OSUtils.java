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

import java.util.function.*;
import org.jitsi.utils.*;

/**
 * Utility fields for OS detection.
 *
 * @author Sebastien Vincent
 * @author Lubomir Marinov
 */
public class OSUtils
{
    private static final BiConsumer<String, Class<?>> loadLibrary;

    /** <tt>true</tt> if OS is Android */
    public static final boolean IS_ANDROID;

    /** <tt>true</tt> if OS is Linux. */
    public static final boolean IS_LINUX;

    /** <tt>true</tt> if OS is MacOSX. */
    public static final boolean IS_MAC;

    /** <tt>true</tt> if OS is Windows. */
    public static final boolean IS_WINDOWS;

    /** <tt>true</tt> if OS is FreeBSD. */
    public static final boolean IS_FREEBSD;

    static
    {
        if (OSUtils.class.getClassLoader().getClass().getName().contains("Bundle"))
        {
            loadLibrary = (libname, clazz) -> System.loadLibrary(libname);
        }
        else
        {
            loadLibrary = JNIUtils::loadLibrary;
        }

        // OS
        String osName = System.getProperty("os.name");

        if (osName == null)
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_FREEBSD = false;
        }
        else if (osName.startsWith("Linux"))
        {
            String javaVmName = System.getProperty("java.vm.name");

            if ((javaVmName != null) && javaVmName.equalsIgnoreCase("Dalvik"))
            {
                IS_ANDROID = true;
                IS_LINUX = false;
            }
            else
            {
                IS_ANDROID = false;
                IS_LINUX = true;
            }
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_FREEBSD = false;
        }
        else if (osName.startsWith("Mac"))
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = true;
            IS_WINDOWS = false;
            IS_FREEBSD = false;
        }
        else if (osName.startsWith("Windows"))
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = false;
            IS_WINDOWS = true;
            IS_FREEBSD = false;
        }
        else if (osName.startsWith("FreeBSD"))
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_FREEBSD = true;
        }
        else
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_FREEBSD = false;
        }
    }

    public static void loadLibrary(String libname, Class<?> clazz)
    {
        loadLibrary.accept(libname, clazz);
    }

    private OSUtils()
    {
    }
}
