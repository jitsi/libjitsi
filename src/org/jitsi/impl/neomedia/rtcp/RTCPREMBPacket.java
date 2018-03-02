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

import java.io.*;
import java.util.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Created by gp on 6/24/14.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P| FMT=15  |   PT=206      |             length            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of packet sender                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of media source                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Unique identifier 'R' 'E' 'M' 'B'                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Num SSRC     | BR Exp    |  BR Mantissa                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   SSRC feedback                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ...                                                          |
 */
public class RTCPREMBPacket extends RTCPFBPacket
{
    public static final int FMT = 15;

    /**
     * The exponential scaling of the mantissa for the maximum total media
     * bit rate value, ignoring all packet overhead.
     */
    public int exp;

    /**
     * The mantissa of the maximum total media bit rate (ignoring all packet
     * overhead) that the sender of the REMB estimates.  The BR is the estimate
     * of the traveled path for the SSRCs reported in this message.
     */
    public int mantissa;

    /**
     * one or more SSRC entries which this feedback message applies to.
     */
    public long[] dest;

    public RTCPREMBPacket(
            long senderSSRC,
            long mediaSSRC,
            int exp,
            int mantissa,
            long[] dest)
    {
        super(FMT, PSFB, senderSSRC, mediaSSRC);

        this.exp = exp;
        this.mantissa = mantissa;
        this.dest = dest;
    }

    public RTCPREMBPacket(
            long senderSSRC,
            long mediaSSRC,
            long bitrate,
            long[] dest)
    {
        super(FMT, PSFB, senderSSRC, mediaSSRC);

        // 6 bit Exp
        // 18 bit mantissa
        this.exp = 0;
        for(int i=0; i<64; i++)
        {
            if(bitrate <= (0x3ffff << i))
            {
                this.exp = i;
                break;
            }
        }

        /* type of bitrate is an unsigned int (32 bits) */
        this.mantissa = (((int)bitrate) >> this.exp);
        this.dest = dest;
    }

    public RTCPREMBPacket(RTCPCompoundPacket base)
    {
        super(base);
        super.fmt = FMT;
        super.type = PSFB;
    }

    public long[] getDest()
    {
        return this.dest;
    }

    public void setDest(long[] dest)
    {
        this.dest = dest;
    }

/*
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P| FMT=15  |   PT=206      |             length            |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of packet sender                        |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of media source                         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |  Unique identifier 'R' 'E' 'M' 'B'                            |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |  Num SSRC     | BR Exp    |  BR Mantissa                      |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |   SSRC feedback                                               |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |  ...                                                          |

 */

    @Override
    public void assemble(DataOutputStream dataoutputstream) throws IOException
    {
        int len = this.calcLength();
        byte[] buf = new byte[len];
        int off = 0;

        /*
         * version (V): (2 bits):   This field identifies the RTP version.  The
         *     current version is 2.
         * padding (P) (1 bit):   If set, the padding bit indicates that the
         *     packet contains additional padding octets at the end that
         *     are not part of the control information but are included
         *     in the length field.  Always 0.
         * Feedback message type (FMT) (5 bits):  This field identifies the type
         *     of the FB message and is interpreted relative to the type
         *     (transport layer, payload- specific, or application layer
         *     feedback).  Always 15, application layer feedback
         *     message.
         */
        buf[off++] = (byte) (0x8F);

        /*
         * Payload type (PT) (8 bits):   This is the RTCP packet type that
         *     identifies the packet as being an RTCP FB message.
         *     Always PSFB (206), Payload-specific FB message.
         */
        buf[off++] = (byte) (0xCE);

        // Length (16 bits):  The length of this packet in 32-bit words minus
        // one, including the header and any padding. This is in
        // line with the definition of the length field used in RTCP
        // sender and receiver reports
        int rtcpPacketLength = len / 4 - 1;
        buf[off++] = (byte) ((rtcpPacketLength & 0xFF00) >> 8);
        buf[off++] = (byte) (rtcpPacketLength & 0x00FF);

        // SSRC of packet sender: 32 bits
        RTCPFeedbackMessagePacket.writeSSRC(senderSSRC, buf, off);
        off += 4;

        //  SSRC of media source (32 bits):  Always 0;
        RTCPFeedbackMessagePacket.writeSSRC(0L, buf, off);
        off += 4;

        // Unique identifier (32 bits):  Always 'R' 'E' 'M' 'B' (4 ASCII
        // characters).
        buf[off++] = (byte) 'R';
        buf[off++] = (byte) 'E';
        buf[off++] = (byte) 'M';
        buf[off++] = (byte) 'B';

        // Num SSRC (8 bits):  Number of SSRCs in this message.
        buf[off++] =
                (byte) ((dest != null && dest.length != 0)
                        ? dest.length
                        : 0);

        // BR Exp (6 bits):   The exponential scaling of the mantissa for the
        // maximum total media bit rate value, ignoring all packet
        // overhead.

        // BR Mantissa (18 bits):   The mantissa of the maximum total media bit
        // rate (ignoring all packet overhead) that the sender of
        // the REMB estimates.
        buf[off++] = (byte) (((exp & 0x3f) << 2) | (mantissa & 0x30000) >> 16);
        buf[off++] = (byte) ((mantissa & 0xff00) >> 8);
        buf[off++] = (byte) (mantissa & 0xff);

        // SSRC feedback (32 bits)  Consists of one or more SSRC entries which
        // this feedback message applies to.
        if (dest != null && dest.length != 0)
        {
            for (long d : dest)
            {
                RTCPFeedbackMessagePacket.writeSSRC(d, buf, off);
                off += 4;
            }
        }

        dataoutputstream.write(buf, 0, len);
    }

    @Override
    public int calcLength()
    {
        int len = 20; // 20 bytes header + standard data

        if (dest != null)
            len += dest.length * 4;

        return len;
    }

    @Override
    public String toString()
    {
        return "\tRTCP REMB packet from sync source " + senderSSRC
                + "\n\t\tfor sync sources: " + Arrays.toString(dest)
                + "\n\t\tBR Exp: " + exp
                + "\n\t\tBR Mantissa: " + mantissa;
    }

    /**
     * Gets the bitrate described in this packet in bits per second.
     * @return the bitrate described in this packet in bits per second.
     */
    public long getBitrate()
    {
        return (long) (mantissa * Math.pow(2, exp));
    }

    /**
     * Gets a boolean that indicates whether or not the packet specified in the
     * {@link ByteArrayBuffer} that is passed in the first argument is an RTCP
     * REMB packet.
     *
     * @param baf the {@link ByteArrayBuffer} that holds the RTCP packet.
     * @return true if the packet specified in the {@link ByteArrayBuffer} that
     * is passed in the first argument is an RTCP REMB packet, otherwise false.
     */
    public static boolean isREMBPacket(ByteArrayBuffer baf)
    {
        int rc = RTCPUtils.getReportCount(baf);
        return isPSFBPacket(baf) && rc == FMT;
    }
}
