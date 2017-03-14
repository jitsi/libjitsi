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
 * Utility class that contains static methods for RTCP sender info manipulation.
 *
 * TODO maybe merge into the RTCPSenderInfo class.
 *
 * @author George Politis
 */
public class RTCPSenderInfoUtils
{
    /**
     * Gets the RTP timestamp from an SR.
     *
     * @param buf the byte buffer that contains the RTCP sender report.
     * @param off the offset in the byte buffer where the RTCP sender report
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

        return RTPUtils.readUint32AsLong(buf, off + 16);
    }

    /**
     * Sets the RTP timestamp in an SR.
     *
     * @param buf the byte buffer that contains the RTCP sender report.
     * @param off the offset in the byte buffer where the RTCP sender report
     * starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @param ts the new timestamp to be set.
     *
     * @return the number of bytes written.
     */
    public static int setTimestamp(byte[] buf, int off, int len, int ts)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return RTPUtils.writeInt(buf, off + 16, ts);
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

        return RTPUtils.readUint32AsLong(buf, off);
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

        return RTPUtils.readUint32AsLong(buf, off + 4);
    }

    /**
     * Sets the RTP timestamp in an SR.
     *
     * @param baf the {@link ByteArrayBuffer} that holds the SR.
     * @param ts the new timestamp to be set.
     *
     * @return the number of bytes written.
     */
    public static int setTimestamp(ByteArrayBuffer baf, int ts)
    {
        if (baf == null)
        {
            return -1;
        }

        return setTimestamp(
            baf.getBuffer(), baf.getOffset(), baf.getLength(), ts);
    }

    /**
     * Gets the RTP timestamp from an SR.
     *
     * @param baf the {@link ByteArrayBuffer} that holds the SR.
     * @return the RTP timestamp, or -1 in case of an error.
     */
    public static long getTimestamp(ByteArrayBuffer baf)
    {
        if (baf == null)
        {
            return -1;
        }

        return getTimestamp(baf.getBuffer(), baf.getOffset(), baf.getLength());
    }

    /**
     * Sets the octet count in the SR that is specified in the arguments.
     *
     * @param baf the {@link ByteArrayBuffer} that holds the SR.
     * @return the number of bytes that were written, otherwise -1.
     * @param octetCount the octet count ot set.
     * @return the number of bytes that were written, otherwise -1.
     */
    public static int setOctetCount(ByteArrayBuffer baf, int octetCount)
    {
        if (baf == null)
        {
            return -1;
        }

        return setOctetCount(
            baf.getBuffer(), baf.getOffset(), baf.getLength(), octetCount);
    }

    /**
     * Sets the packet count in the SR that is specified in the arguments.
     * @param packetCount the packet count to set.
     * @param baf the {@link ByteArrayBuffer} that holds the SR.
     * @return the number of bytes that were written, otherwise -1.
     */
    public static int setPacketCount(ByteArrayBuffer baf, int packetCount)
    {
        if (baf == null)
        {
            return -1;
        }

        return setPacketCount(
            baf.getBuffer(), baf.getOffset(), baf.getLength(), packetCount);
    }

    /**
     * Sets the packet count in the SR that is specified in the arguments.
     *
     * @param packetCount the packet count to set.
     * @param buf the byte buffer that holds the SR.
     * @param off the offset where the data starts
     * @param len the length of the data.
     * @return the number of bytes that were written, otherwise -1.
     */
    private static int setPacketCount(
        byte[] buf, int off, int len, int packetCount)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return RTPUtils.writeInt(buf, off + 20, packetCount);
    }

    /**
     * Sets the octet count in the SR that is specified in the arguments.
     *
     * @param buf the byte buffer that holds the SR.
     * @param off the offset where the data starts
     * @param len the length of the data.
     * @param octetCount the octet count ot set.
     * @return the number of bytes that were written, otherwise -1.
     */
    private static int setOctetCount(
        byte[] buf, int off, int len, int octetCount)
    {
        if (!isValid(buf, off, len))
        {
            return -1;
        }

        return RTPUtils.writeInt(buf, off + 24, octetCount);
    }
}
