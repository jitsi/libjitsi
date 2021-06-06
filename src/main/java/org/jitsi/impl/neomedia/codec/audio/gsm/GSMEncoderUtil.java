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
package org.jitsi.impl.neomedia.codec.audio.gsm;

import org.rubycoder.gsm.*;

/**
 * GSMEncoderUtil class
 *
 * @author Martin Harvan
 * @author Damian Minkov
 */
public class GSMEncoderUtil {

    private static GSMEncoder encoder = new GSMEncoder();
    /**
     * number of bytes in GSM frame
     */
    private static final int GSM_BYTES = 33;

    /**
     * number of PCM bytes needed to encode
     */
    private static final int PCM_BYTES = 320;

    /**
     * number of PCM ints needed to encode
     */
    private static final int PCM_INTS = 160;

    /**
     * Encode data to GSM.
     *
     * @param bigEndian if the data is in big endian format
     * @param data data to encode
     * @param offset offset
     * @param length length of data
     * @param decoded array of encoded data.
     */
    public static void gsmEncode(
            boolean bigEndian,
            byte[] data,
            int offset,
            int length,
            byte[] decoded)
    {
        for (int i = offset; i < length / PCM_BYTES; i++)
        {
            int[] input = new int[PCM_INTS];
            byte[] output = new byte[GSM_BYTES];

            for (int j = 0; j < PCM_INTS; j++) {
                int index = j << 1;

                input[j] = data[i * PCM_BYTES + index++];

                input[j] <<= 8;
                input[j] |= data[i * PCM_BYTES + index++] & 0xFF;
            }
            encoder.encode(output, input);
            System.arraycopy(output, 0, decoded, i * GSM_BYTES, GSM_BYTES);
        }
    }
}
