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
package org.jitsi.util;

import org.jitsi.service.neomedia.*;

/**
 * Utility class that contains static methods for RTCP header manipulation.
 *
 * @author George Politis
 */
public class RTCPUtils
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
        if (!RawPacket.isRtpRtcp(buf, off, len))
        {
            return -1;
        }

        return buf[off + 1] & 0xff;
    }

    /**
     * Gets the RTCP packet type.
     *
     * @param baf the {@link ByteArrayBuffer} that contains the RTCP header.
     * @return the unsigned RTCP packet type, or -1 in case of an error.
     */
    public static int getPacketType(ByteArrayBuffer baf)
    {
        if (baf == null)
        {
            return -1;
        }

        return getPacketType(baf.getBuffer(), baf.getOffset(), baf.getLength());
    }

    /**
     * Gets the RTCP packet length in bytes as specified by the length field
     * of the RTCP packet (does not verify that the buffer is actually large
     * enough).
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return  the RTCP packet length in bytes, or -1 in case of an error.
     */
    public static int getLength(byte[] buf, int off, int len)
    {
        // XXX Do not check with isRtpRtcp.
        if (buf == null || buf.length < off + len || len < 4)
        {
            return -1;
        }

        int lengthInWords
            = ((buf[off + 2] & 0xff) << 8) | (buf[off + 3] & 0xff);

        return (lengthInWords + 1) * 4;
    }

    /**
     * Gets the report count field of the RTCP packet specified in the
     * {@link ByteArrayBuffer} that is passed in as a parameter.
     *
     * @param baf the {@link ByteArrayBuffer} that contains the RTCP header.
     * @return the report count field of the RTCP packet specified in the
     * {@link ByteArrayBuffer} that is passed in as a parameter, or -1 in case
     * of an error.
     */
    public static int getReportCount(ByteArrayBuffer baf)
    {
        if (baf == null)
        {
            return -1;
        }

        return getReportCount(
            baf.getBuffer(), baf.getOffset(), baf.getLength());
    }

    /**
     * Gets the report count field of the RTCP packet specified in the
     * {@link ByteArrayBuffer} that is passed in as a parameter.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the report count field of the RTCP packet specified in the
     * byte buffer that is passed in as a parameter, or -1 in case
     * of an error.
     */
    private static int getReportCount(byte[] buf, int off, int len)
    {
        if (buf == null || buf.length < off + len || len < 1)
        {
            return -1;
        }

        return buf[off] & 0x1F;
    }

    /**
     * Gets the RTCP packet length in bytes.
     *
     * @param baf the {@link ByteArrayBuffer} that contains the RTCP header.
     * @return  the RTCP packet length in bytes, or -1 in case of an error.
     */
    public static int getLength(ByteArrayBuffer baf)
    {
        if (baf == null)
        {
            return -1;
        }

        return getLength(baf.getBuffer(), baf.getOffset(), baf.getLength());
    }

    /**
     * Checks whether the buffer described by the parameters looks like an
     * RTCP packet. It only checks the Version and Packet Type fields, as
     * well as a minimum length.
     * This method returning {@code true} does not necessarily mean that the
     * given packet is a valid RTCP packet, but it should be parsed as RTCP
     * (as opposed to as e.g. RTP or STUN).
     *
     * @param buf
     * @param off
     * @param len
     * @return {@code true} if the described packet looks like RTCP.
     */
    public static boolean isRtcp(byte[] buf, int off, int len)
    {
        if (!RawPacket.isRtpRtcp(buf, off, len))
        {
            return false;
        }

        int pt = getPacketType(buf, off, len);

        // Other packet types are used for RTP.
        return 200 <= pt && pt <= 211;
    }

}
