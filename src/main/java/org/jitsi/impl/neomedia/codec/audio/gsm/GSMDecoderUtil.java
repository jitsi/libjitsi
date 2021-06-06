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
 * GSMDecoderUtil class
 *
 * @author Martin Harvan
 * @author Damian Minkov
 */
public class GSMDecoderUtil
{
    private static GSMDecoder decoder = new GSMDecoder();
    private static final int GSM_BYTES = 33;
    private static final int PCM_INTS = 160;
    private static final int PCM_BYTES = 320;

    /**
     * Decode GSM data.
     *
     * @param bigEndian if the data are in big endian format
     * @param data the GSM data
     * @param offset offset
     * @param length length of the data
     * @param decoded decoded data array
     */
    public static void gsmDecode(boolean bigEndian,
                                 byte[] data,
                                 int offset,
                                 int length,
                                 byte[] decoded)
    {
        for (int i = 0; i < length / GSM_BYTES; i++)
        {
            int[] output = new int[PCM_INTS];
            byte[] input = new byte[GSM_BYTES];
            System.arraycopy(data, i * GSM_BYTES, input, 0, GSM_BYTES);
            try
            {
                decoder.decode(input, output);
            } catch (InvalidGSMFrameException e)
            {
                e.printStackTrace();
            }
            for (int j = 0; j < PCM_INTS; j++)
            {
                int index = j << 1;
                if (bigEndian)
                {
                    decoded[index + i * PCM_BYTES]
                            = (byte) ((output[j] & 0xff00) >> 8);
                    decoded[++index + (i * PCM_BYTES)]
                            = (byte) ((output[j] & 0x00ff));
                } else
                {
                    decoded[index + i * PCM_BYTES]
                            = (byte) ((output[j] & 0x00ff));
                    decoded[++index + (i * PCM_BYTES)]
                            = (byte) ((output[j] & 0xff00) >> 8);
                }
            }
        }
    }
}
