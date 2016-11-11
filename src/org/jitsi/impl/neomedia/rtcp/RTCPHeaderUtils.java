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
        if (getLength(buf, off, len) < RTCPHeader.SIZE)
        {
            return -1;
        }

        return buf[off + 1] & 0xff;
    }

    /**
     * Gets the RTCP packet type.
     *
     * @param pkt the <tt>SimpleBUffer</tt> that contains the RTCP header.
     * @return the unsigned RTCP packet type, or -1 in case of an error.
     */
    public static int getPacketType(ByteArrayBuffer pkt)
    {
        if (pkt == null)
        {
            return -1;
        }

        return getPacketType(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
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
        if (getLength(buf, off, len) < RTCPHeader.SIZE)
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
        if (buf == null
            || buf.length < off + Math.max(len, RTCPHeader.SIZE)
            || len < RTCPHeader.SIZE)
        {
            return -1;
        }

        if (RTCPHeader.VERSION != (buf[off] & 0xc0) >>> 6)
        {
            return -1;
        }

        int lengthInWords
            = ((buf[off + 2] & 0xff) << 8) | (buf[off + 3] & 0xff);

        return (lengthInWords + 1) * 4;
    }

    /**
     * Gets the RTCP packet length in bytes.
     *
     * @param pkt the byte buffer that contains the RTCP header.
     * @return the RTCP packet length in bytes, or -1 in case of an error.
     */
    public static int getLength(ByteArrayBuffer pkt)
    {
        if (pkt == null)
        {
            return -1;
        }

        return getLength(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
    }

    /**
     * Gets the RTCP packet report count.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the RTCP packet report count, or -1 in case of an error.
     */
    public static int getReportCount(byte[] buf, int off, int len)
    {
        if (getLength(buf, off, len) < RTCPHeader.SIZE)
        {
            return -1;
        }

        return (buf[off] & 0x1F);
    }

    /**
     * Gets the RTCP packet report count.
     *
     * @param pkt the <tt>RawPacket</tt> that contains the RTCP header.
     * @return the RTCP packet report count, or -1 in case of an error.
     */
    public static int getReportCount(ByteArrayBuffer pkt)
    {
        if (pkt == null)
        {
            return -1;
        }

        return getReportCount(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
    }

    /**
     *
     * @param baf
     * @return
     */
    public static long getSenderSSRC(ByteArrayBuffer baf)
    {
        if (baf == null)
        {
            return -1;

        }
        return getSenderSSRC(baf.getBuffer(), baf.getOffset(), baf.getLength());
    }
}
