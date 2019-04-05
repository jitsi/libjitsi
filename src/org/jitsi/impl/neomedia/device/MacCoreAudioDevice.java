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

import org.jitsi.utils.*;

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
