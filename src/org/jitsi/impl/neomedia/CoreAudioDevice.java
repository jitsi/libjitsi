/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import org.jitsi.util.*;

/**
 * JNI link to the MacOsX / Windows CoreAudio library.
 *
 * @author Vincent Lucqs
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
            if (OSUtils.IS_MAC)
            {
                System.loadLibrary("jnmaccoreaudio");
                isLoaded = true;
            }
            else if (OSUtils.IS_WINDOWS_VISTA
                    || OSUtils.IS_WINDOWS_7
                    || OSUtils.IS_WINDOWS_8)
            {
                System.loadLibrary("jnwincoreaudio");
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
        }
        catch (SecurityException se)
        {
        }
        catch (UnsatisfiedLinkError ule)
        {
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
        {
            devicesChangedCallback.run();
        }
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
