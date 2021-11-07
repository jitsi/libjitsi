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
 * Utility fields for OS detection.
 *
 * @author Sebastien Vincent
 * @author Lubomir Marinov
 */
public class OSUtils
{

    /** <tt>true</tt> if architecture is 32 bit. */
    public static final boolean IS_32_BIT;

    /** <tt>true</tt> if architecture is 64 bit. */
    public static final boolean IS_64_BIT;

    /** <tt>true</tt> if OS is Android */
    public static final boolean IS_ANDROID;

    /** <tt>true</tt> if OS is Linux. */
    public static final boolean IS_LINUX;

    /** <tt>true</tt> if OS is Linux 32-bit. */
    public static final boolean IS_LINUX32;

    /** <tt>true</tt> if OS is Linux 64-bit. */
    public static final boolean IS_LINUX64;

    /** <tt>true</tt> if OS is MacOSX. */
    public static final boolean IS_MAC;

    /** <tt>true</tt> if OS is MacOSX 32-bit. */
    public static final boolean IS_MAC32;

    /** <tt>true</tt> if OS is MacOSX 64-bit. */
    public static final boolean IS_MAC64;

    /** <tt>true</tt> if OS is Windows. */
    public static final boolean IS_WINDOWS;

    /** <tt>true</tt> if OS is Windows 32-bit. */
    public static final boolean IS_WINDOWS32;

    /** <tt>true</tt> if OS is Windows 64-bit. */
    public static final boolean IS_WINDOWS64;

    /** <tt>true</tt> if OS is Windows 7. */
    public static final boolean IS_WINDOWS_7;

    /** <tt>true</tt> if OS is Windows 8. */
    public static final boolean IS_WINDOWS_8;

    /** <tt>true</tt> if OS is Windows 10. */
    public static final boolean IS_WINDOWS_10;

    /** <tt>true</tt> if OS is FreeBSD. */
    public static final boolean IS_FREEBSD;

    static
    {
        // OS
        String osName = System.getProperty("os.name");

        if (osName == null)
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_WINDOWS_7 = false;
            IS_WINDOWS_8 = false;
            IS_WINDOWS_10 = false;
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
            IS_WINDOWS_7 = false;
            IS_WINDOWS_8 = false;
            IS_WINDOWS_10 = false;
            IS_FREEBSD = false;
        }
        else if (osName.startsWith("Mac"))
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = true;
            IS_WINDOWS = false;
            IS_WINDOWS_7 = false;
            IS_WINDOWS_8 = false;
            IS_WINDOWS_10 = false;
            IS_FREEBSD = false;
        }
        else if (osName.startsWith("Windows"))
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = false;
            IS_WINDOWS = true;
            IS_WINDOWS_7 = (osName.indexOf("7") != -1);
            IS_WINDOWS_8 = (osName.indexOf("8") != -1);
            IS_WINDOWS_10 = (osName.indexOf("10") != -1);
            IS_FREEBSD = false;
        }
        else if (osName.startsWith("FreeBSD"))
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_WINDOWS_7 = false;
            IS_WINDOWS_8 = false;
            IS_WINDOWS_10 = false;
            IS_FREEBSD = true;
        }
        else
        {
            IS_ANDROID = false;
            IS_LINUX = false;
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_WINDOWS_7 = false;
            IS_WINDOWS_8 = false;
            IS_WINDOWS_10 = false;
            IS_FREEBSD = false;
        }

        // arch i.e. x86, amd64
        String osArch = System.getProperty("sun.arch.data.model");

        if(osArch == null)
        {
            IS_32_BIT = true;
            IS_64_BIT = false;
        }
        else if (osArch.indexOf("32") != -1)
        {
            IS_32_BIT = true;
            IS_64_BIT = false;
        }
        else if (osArch.indexOf("64") != -1)
        {
            IS_32_BIT = false;
            IS_64_BIT = true;
        }
        else
        {
            IS_32_BIT = false;
            IS_64_BIT = false;
        }

        // OS && arch
        IS_LINUX32 = IS_LINUX && IS_32_BIT;
        IS_LINUX64 = IS_LINUX && IS_64_BIT;
        IS_MAC32 = IS_MAC && IS_32_BIT;
        IS_MAC64 = IS_MAC && IS_64_BIT;
        IS_WINDOWS32 = IS_WINDOWS && IS_32_BIT;
        IS_WINDOWS64 = IS_WINDOWS && IS_64_BIT;
    }

    /**
     * Allows the extending of the <tt>OSUtils</tt> class but disallows
     * initializing non-extended <tt>OSUtils</tt> instances.
     */
    protected OSUtils()
    {
    }
}
