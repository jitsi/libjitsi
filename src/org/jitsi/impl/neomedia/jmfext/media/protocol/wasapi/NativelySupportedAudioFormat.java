/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import javax.media.format.*;

/**
 * Represents an <tt>AudioFormat</tt> which is natively supported by the entity
 * which supports it. The <tt>NativelySupportedAudioFormat</tt> class is used
 * purely as a flag/indicator/marker. In the context of the Windows Audio
 * Session API (WASAPI) integration, it signals that the endpoint device
 * represented by an associated <tt>CaptureDeviceInfo2</tt> supports the format
 * either directly or with built-in conversion between mono and stereo.
 *
 * @author Lyubomir Marinov
 */
public class NativelySupportedAudioFormat
    extends AudioFormat
{
    public NativelySupportedAudioFormat(
            String encoding,
            double sampleRate,
            int sampleSizeInBits,
            int channels,
            int endian,
            int signed,
            int frameSizeInBits,
            double frameRate,
            Class<?> dataType)
    {
        super(
                encoding,
                sampleRate,
                sampleSizeInBits,
                channels,
                endian,
                signed,
                frameSizeInBits,
                frameRate,
                dataType);

        /*
         * The NativelySupportedAudioFormat class is used purely as a
         * flag/indicator/marker and, consequently, needs to not affect value
         * equality.
         */
        clz = AudioFormat.class;
    }
}
