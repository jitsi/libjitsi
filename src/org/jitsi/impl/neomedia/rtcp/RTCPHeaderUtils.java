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
import org.jitsi.impl.neomedia.*;

/**
 * Utility class that contains static methods for RTCP header manipulation.
 *
 * TODO maybe merge into the RTCPHeader class.
 *
 * @author George Politis
 */
public class RTCPHeaderUtils
{
    /**
     * Gets the RTCP packet type.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the unsigned RTCP packet type, or -1 in case of an error.
     */
    public static int getPacketType(byte[] buf, int off, int len)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return buf[off + 1] & 0xff;
    }

    /**
     * Gets the RTCP sender SSRC.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the unsigned RTCP sender SSRC, or -1 in case of an error.
     */
    public static long getSenderSSRC(byte[] buf, int off, int len)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return RawPacket.readInt(buf, off + 4, len) & 0xffffffffl;
    }

    /**
     * Gets the RTCP packet length in bytes.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return  the RTCP packet length in bytes, or -1 in case of an error.
     */
    public static int getLength(byte[] buf, int off, int len)
    {
        // XXX Do not check with isValid.
        if (buf == null || buf.length < off + Math.max(len, 4))
        {
            return -1;
        }

        int lengthInWords
            = ((buf[off + 2] & 0xff) << 8) | (buf[off + 3] & 0xff);

        return (lengthInWords + 1) * 4;
    }

    /**
     * Gets the RTCP packet version.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the RTCP packet version, or -1 in case of an error.
     */
    public static int getVersion(byte[] buf, int off, int len)
    {
        // XXX Do not check with isValid.
        if (buf == null || buf.length < off + Math.max(len, 1))
        {
            return -1;
        }

        return (buf[off] & 0xc0) >>> 6;
    }

    /**
     * Checks whether the RTCP header is valid or not. It does so by checking
     * the RTCP header version and makes sure the buffer is at least 8 bytes
     * long.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return true if the RTCP packet is valid, false otherwise.
     */
    public static boolean isValid(byte[] buf, int off, int len)
    {
        int version = RTCPHeaderUtils.getVersion(buf, off, len);
        if (version != RTCPHeader.VERSION)
        {
            return false;
        }

        int pktLen = RTCPHeaderUtils.getLength(buf, off, len);
        if (pktLen < RTCPHeader.SIZE)
        {
            return false;
        }

        return true;
    }
}
