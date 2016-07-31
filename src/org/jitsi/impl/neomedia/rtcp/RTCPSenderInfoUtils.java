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
package org.jitsi.impl.neomedia.rtcp;

import net.sf.fmj.media.rtp.*;

/**
 * Utility class that contains static methods for RTCP sender info manipulation.
 *
 * TODO maybe merge into the RTCPSenderInfo class.
 *
 * @author George Politis
 */
public class RTCPSenderInfoUtils
{
    /**
     * Gets the RTP timestamp.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP sender info
     * starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the RTP timestamp, or -1 in case of an error.
     */
    public static long getTimestamp(byte[] buf, int off, int len)
    {
        if (buf == null ||  Math.min(buf.length, len) < off + 12)
        {
            return -1;
        }

        return (((buf[off + 8] & 0xff) << 24)
            | ((buf[off + 9] & 0xff) << 16)
            | ((buf[off + 10] & 0xff) << 8)
            | (buf[off + 11] & 0xff)) & 0xffffffffl;
    }

    /**
     * Sets the RTP timestamp.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP sender info
     * starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @param ts the new timestamp to be set.
     *
     * @return the number of bytes written.
     */
    public static int setTimestamp(
        byte[] buf, int off, int len, long ts)
    {
        if (buf == null ||  Math.min(buf.length, len) < off + 12)
        {
            return -1;
        }

        buf[off + 8] = (byte)(ts>>24);
        buf[off + 9] = (byte)(ts>>16);
        buf[off + 10] = (byte)(ts>>8);
        buf[off + 11] = (byte)ts;

        return 12;
    }

    /**
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP sender info
     * starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     *
     * @return true if the RTCP sender info is valid, false otherwise.
     */
    public static boolean isValid(byte[] buf, int off, int len)
    {
        if (buf == null || Math.min(buf.length, len) < RTCPSenderInfo.SIZE)
        {
            return false;
        }

        return true;
    }
}
