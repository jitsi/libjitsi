/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import org.jitsi.util.*;

/**
 * Extension for the JNI link to the MacOsX CoreAudio library.
 *
 * @author Vincent Lucas
 */
public class MacCoreAudioDevice
    extends CoreAudioDevice
{
    /**
     * The number of milliseconds to be read from or written to a native
     * CoreAudio stream in a single transfer of data.
     */
    public static final int DEFAULT_MILLIS_PER_BUFFER = 20;

    /**
     * The default value for the sample rate of the input and the output
     * MacCoreaudio streams with which they are to be opened if no other
     * specific sample rate is specified to the MacCoreaudio <tt>DataSource</tt>
     * or <tt>MacCoreaudioRenderer</tt> that they represent.
     */
    public static final double DEFAULT_SAMPLE_RATE = 44100.0;

    public static native String[] getDeviceUIDList();

    public static native boolean isInputDevice(String deviceUID);

    public static native boolean isOutputDevice(String deviceUID);

    public static String getTransportType(String deviceUID)
    {
        // Prevent an access violation.
        if (deviceUID == null)
            throw new NullPointerException("deviceUID");

        byte[] transportTypeBytes = getTransportTypeBytes(deviceUID);
        String transportType = StringUtils.newString(transportTypeBytes);

        return transportType;
    }

    public static native byte[] getTransportTypeBytes(String deviceUID);

    public static native float getNominalSampleRate(
            String deviceUID,
            boolean isOutputStream,
            boolean isEchoCancel);

    public static native float getMinimalNominalSampleRate(
            String deviceUID,
            boolean isOutputStream,
            boolean isEchoCancel);

    public static native float getMaximalNominalSampleRate(
            String deviceUID,
            boolean isOutputStream,
            boolean isEchoCancel);

    public static String getDefaultInputDeviceUID()
    {
        byte[] defaultInputDeviceUIDBytes = getDefaultInputDeviceUIDBytes();
        String defaultInputDeviceUID
            = StringUtils.newString(defaultInputDeviceUIDBytes);

        return defaultInputDeviceUID;
    }

    public static native byte[] getDefaultInputDeviceUIDBytes();

    public static String getDefaultOutputDeviceUID()
    {
        byte[] defaultOutputDeviceUIDBytes = getDefaultOutputDeviceUIDBytes();
        String defaultOutputDeviceUID
            = StringUtils.newString(defaultOutputDeviceUIDBytes);

        return defaultOutputDeviceUID;
    }

    public static native byte[] getDefaultOutputDeviceUIDBytes();

    public static native long startStream(
            String deviceUID,
            Object callback,
            float sampleRate,
            int nbChannels,
            int bitsPerChannel,
            boolean isFloat,
            boolean isBigEndian,
            boolean isNonInterleaved,
            boolean isInput,
            boolean isEchoCancel);

    public static native void stopStream(String deviceUID, long stream);

    public static native int countInputChannels(String deviceUID);

    public static native int countOutputChannels(String deviceUID);
}
