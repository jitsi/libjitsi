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
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

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
     * Sets the RTCP sender SSRC.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @param senderSSRC the sender SSRC to set.
     * @return the number of bytes that were written to the byte buffer, or -1
     * in case of an error.
     */
    private static int setSenderSSRC(
        byte[] buf, int off, int len, int senderSSRC)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return RTPUtils.writeInt(buf, off + 4, senderSSRC);
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
        if (buf == null || buf.length < off + len || len < 4)
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
        if (buf == null || buf.length < off + len || len < 1)
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

    /**
     * Sets the RTCP sender SSRC.
     *
     * @param baf the {@link ByteArrayBuffer} that contains the RTCP header.
     * @param senderSSRC the sender SSRC to set.
     * @return the number of bytes that were written to the byte buffer, or -1
     * in case of an error.
     */
    public static int setSenderSSRC(ByteArrayBuffer baf, int senderSSRC)
    {
        if (baf == null)
        {
            return -1;
        }

        return setSenderSSRC(
            baf.getBuffer(), baf.getOffset(), baf.getLength(), senderSSRC);
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
}
