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
    static
    {
        if(OSUtils.IS_MAC)
        {
            System.loadLibrary("jnmaccoreaudio");
        }
        else if(OSUtils.IS_WINDOWS_VISTA
                || OSUtils.IS_WINDOWS_7
                || OSUtils.IS_WINDOWS_8)
        {
            System.loadLibrary("jnwincoreaudio");
        }
    }

    public static native int initDevices();

    public static native void freeDevices();

    public static String getDeviceName(
            String deviceUID)
    {
        byte[] deviceNameBytes = getDeviceNameBytes(deviceUID);
        String deviceName = StringUtils.newString(deviceNameBytes);

        return deviceName;
    }

    public static native byte[] getDeviceNameBytes(
            String deviceUID);

    public static String getDeviceModelIdentifier(
            String deviceUID)
    {
        byte[] deviceModelIdentifierBytes
            = getDeviceModelIdentifierBytes(deviceUID);
        String deviceModelIdentifier
            = StringUtils.newString(deviceModelIdentifierBytes);

        return deviceModelIdentifier;
    }

    public static native byte[] getDeviceModelIdentifierBytes(
            String deviceUID);

    public static native int setInputDeviceVolume(
            String deviceUID,
            float volume);

    public static native int setOutputDeviceVolume(
            String deviceUID,
            float volume);

    public static native float getInputDeviceVolume(
            String deviceUID);

    public static native float getOutputDeviceVolume(
            String deviceUID);
}
