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
