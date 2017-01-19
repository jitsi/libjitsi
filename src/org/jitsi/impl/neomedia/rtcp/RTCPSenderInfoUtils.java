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
     * @param buf the byte buffer that contains the RTCP sender info.
     * @param off the offset in the byte buffer where the RTCP sender info
     * starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the RTP timestamp, or -1 in case of an error.
     */
    public static long getTimestamp(byte[] buf, int off, int len)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return RawPacket.readInt(buf, off + 8, len) & 0xffffffffl;
    }

    /**
     * Sets the RTP timestamp.
     *
     * @param buf the byte buffer that contains the RTCP sender info.
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
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        buf[off + 8] = (byte)(ts>>24);
        buf[off + 9] = (byte)(ts>>16);
        buf[off + 10] = (byte)(ts>>8);
        buf[off + 11] = (byte)ts;

        return 4;
    }

    /**
     *
     * @param buf the byte buffer that contains the RTCP sender info.
     * @param off the offset in the byte buffer where the RTCP sender info
     * starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     *
     * @return true if the RTCP sender info is valid, false otherwise.
     */
    public static boolean isValid(byte[] buf, int off, int len)
    {
        if (buf == null || buf.length < off + len || len < RTCPSenderInfo.SIZE)
        {
            return false;
        }

        return true;
    }

    /**
     * Gets the NTP timestamp MSW.
     *
     * @param buf the byte buffer that contains the RTCP sender info.
     * @param off the offset in the byte buffer where the RTCP sender info
     * starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the RTP timestamp, or -1 in case of an error.
     */
    public static long getNtpTimestampMSW(byte[] buf, int off, int len)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return RawPacket.readInt(buf, off, len) & 0xffffffffl;
    }

    /**
     * Gets the NTP timestamp LSW.
     *
     * @param buf the byte buffer that contains the RTCP sender info.
     * @param off the offset in the byte buffer where the RTCP sender info
     * starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the RTP timestamp, or -1 in case of an error.
     */
    public static long getNtpTimestampLSW(byte[] buf, int off, int len)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return RawPacket.readInt(buf, off + 4, len) & 0xffffffffl;
    }

    public static int setTimestamp(ByteArrayBuffer baf, long ts)
    {
        if (baf == null)
        {
            return -1;
        }

        return setTimestamp(baf.getBuffer(), baf.getOffset(), baf.getLength(), ts);
    }

    public static long getTimestamp(ByteArrayBuffer baf)
    {
        if (baf == null)
        {
            return -1;
        }

        return getTimestamp(baf.getBuffer(), baf.getOffset(), baf.getLength());
    }

    /**
     *
     * @param val
     */
    public static int setOctetCount(ByteArrayBuffer baf, long val)
    {
        if (baf == null)
        {
            return -1;
        }

        return setOctetCount(
            baf.getBuffer(), baf.getOffset(), baf.getLength(), val);
    }

    /**
     *
     * @param val
     */
    public static int setPacketCount(ByteArrayBuffer baf, long val)
    {
        if (baf == null)
        {
            return -1;
        }

        return setPacketCount(
            baf.getBuffer(), baf.getOffset(), baf.getLength(), val);
    }

    /**
     *
     * @param buf
     * @param off
     * @param len
     * @param val
     * @return
     */
    private static int setPacketCount(byte[] buf, int off, int len, long val)
    {
        // TODO error handling.
        buf[off + 12] = (byte)(val>>24);
        buf[off + 13] = (byte)(val>>16);
        buf[off + 14] = (byte)(val>>8);
        buf[off + 15] = (byte)val;

        return 4;
    }

    /**
     *
     * @param buf
     * @param off
     * @param len
     * @param val
     * @return
     */
    private static int setOctetCount(byte[] buf, int off, int len, long val)
    {
        // TODO error handling.
        buf[off + 16] = (byte)(val>>24);
        buf[off + 17] = (byte)(val>>16);
        buf[off + 18] = (byte)(val>>8);
        buf[off + 19] = (byte)val;

        return 4;
    }
}
