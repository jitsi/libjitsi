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
package org.jitsi.impl.neomedia.device;

import org.jitsi.util.OSUtils;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.*;

/**
 * JNI link to the MacOsX / Windows CoreAudio library.
 *
 * @author Vincent Lucas
 */
public class CoreAudioDevice
{
    /**
     * The <tt>Logger</tt> used by the <tt>CoreAudioDevice</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(CoreAudioDevice.class);

    /**
     * Tells if the CoreAudio library used by this CoreAudioDevice is correctly
     * loaded: if we are under a supported operating system.
     */
    public static boolean isLoaded;

    /**
     * Loads CoreAudioDevice if we are using MacOsX or Windows Vista/7/8.
     */
    static
    {
        boolean isLoaded = false;

        try
        {
            String libname = null;

            if (OSUtils.IS_MAC)
            {
                libname = "jnmaccoreaudio";
            }
            else if (OSUtils.IS_WINDOWS)
            {
                libname = "jnwincoreaudio";
            }
            if (libname != null)
            {
                JNIUtils.loadLibrary(libname, CoreAudioDevice.class);
                isLoaded = true;
            }
        }
        catch (NullPointerException npe)
        {
            /*
             * Swallow whatever exceptions are known to be thrown by
             * System.loadLibrary() because the class has to be loaded in order
             * to not prevent the loading of its users and isLoaded will remain
             * false eventually.
             */
            logger.info("Failed to load CoreAudioDevice library: ", npe);
        }
        catch (SecurityException se)
        {
            logger.info("Failed to load CoreAudioDevice library: ", se);
        }
        catch (UnsatisfiedLinkError ule)
        {
            logger.info("Failed to load CoreAudioDevice library: ", ule);
        }

        CoreAudioDevice.isLoaded = isLoaded;
    }

    public static native void freeDevices();

    public static String getDeviceModelIdentifier(String deviceUID)
    {
        // Prevent an access violation in getDeviceModelIdentifierBytes.
        if (deviceUID == null)
            throw new NullPointerException("deviceUID");

        byte[] deviceModelIdentifierBytes
            = getDeviceModelIdentifierBytes(deviceUID);
        String deviceModelIdentifier
            = StringUtils.newString(deviceModelIdentifierBytes);

        return deviceModelIdentifier;
    }

    public static native byte[] getDeviceModelIdentifierBytes(
            String deviceUID);

    public static String getDeviceName(
            String deviceUID)
    {
        byte[] deviceNameBytes = getDeviceNameBytes(deviceUID);
        String deviceName = StringUtils.newString(deviceNameBytes);

        return deviceName;
    }

    public static native byte[] getDeviceNameBytes(
            String deviceUID);

    public static native float getInputDeviceVolume(
            String deviceUID);

    public static native float getOutputDeviceVolume(
            String deviceUID);

    public static native int initDevices();

    public static native int setInputDeviceVolume(
            String deviceUID,
            float volume);

    public static native int setOutputDeviceVolume(
            String deviceUID,
            float volume);

    private static Runnable devicesChangedCallback;

    /**
     * Implements a callback which gets called by the native coreaudio
     * counterpart to notify the Java counterpart that the list of devices has
     * changed.
     */
    public static void devicesChangedCallback()
    {
        Runnable devicesChangedCallback
            = CoreAudioDevice.devicesChangedCallback;

        if(devicesChangedCallback != null)
            devicesChangedCallback.run();
    }

    public static void setDevicesChangedCallback(
            Runnable devicesChangedCallback)
    {
        CoreAudioDevice.devicesChangedCallback = devicesChangedCallback;
    }

    public static void log(byte[] error)
    {
        String errorString = StringUtils.newString(error);
        logger.info(errorString);
    }
}
