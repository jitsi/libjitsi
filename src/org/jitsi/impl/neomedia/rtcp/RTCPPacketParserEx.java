/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.*;

import java.io.*;

/**
 * Created by gp on 6/25/14.
 */
public class RTCPPacketParserEx
{
    class InternalRTCPPacketParser
            extends net.sf.fmj.media.rtp.RTCPPacketParser
    {
        @Override
        protected RTCPPacket parse(
                RTCPCompoundPacket base,
                int firstbyte /* without version/padding */,
                int type,
                int length /* in actual bytes */,
                DataInputStream in)
                throws BadFormatException, IOException
        {
            if (type ==  RTCPPacketType.RTPFB || type ==  RTCPPacketType.PSFB)
            {
/*
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P|   FMT   |       PT      |          length               |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of packet sender                        |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of media source                         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   :            Feedback Control Information (FCI)                 :
   :                                                               :
*/
                long senderSSRC = in.readInt() & 0xffffffffL;
                long sourceSSRC = in.readInt() & 0xffffffffL;

                if (type == RTCPPacketType.RTPFB)
                {
                    UnknownRTCPFBPacket rtpfb = parseUknownRTCPFBPacket(
                            base, firstbyte, RTCPPacketType.RTPFB, length, in, senderSSRC,
                            sourceSSRC);

                    return rtpfb;
                }
                else
                {
                    switch (firstbyte)
                    {
                        case RTCPPSFBFormat.REMB: // REMB
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
                            RTCPREMBPacket remb = new RTCPREMBPacket(base);

                            remb.senderSSRC = senderSSRC;
                            remb.sourceSSRC = sourceSSRC;

                            // Unique identifier 'R' 'E' 'M' 'B'
                            in.readInt();

                            int destlen = in.readUnsignedByte();

                            byte[] buf = new byte[3];
                            in.read(buf);
                            remb.exp = (buf[0] & 0xFC) >> 2;
                            remb.mantissa = ((buf[0] & 0x3) << 16) & 0xFF0000
                                    | (buf[1] << 8) & 0xFF00
                                    | buf[2] & 0xFF;

                            remb.dest = new long[destlen];
                            for (int i = 0; i < remb.dest.length; i++)
                                remb.dest[i] = in.readInt() & 0xffffffffL;

                            return remb;
                        default:
                            UnknownRTCPFBPacket fb =
                                    parseUknownRTCPFBPacket(base, firstbyte,
                                            RTCPPacketType.PSFB,
                                            length, in,
                                            senderSSRC, sourceSSRC);

                            return fb;
                    }
                }
            }
            else
                return null;
        }

        private UnknownRTCPFBPacket parseUknownRTCPFBPacket(
                RTCPCompoundPacket base,
                int firstbyte,
                int type,
                int length /* in actual bytes */,
                DataInputStream in,
                long senderSSRC,
                long sourceSSRC) throws IOException
        {
            UnknownRTCPFBPacket rtpfb = new UnknownRTCPFBPacket(base);

            rtpfb.fmt = firstbyte;
            rtpfb.type = type;
            rtpfb.senderSSRC = senderSSRC;
            rtpfb.sourceSSRC = sourceSSRC;
            int fcilen = length - 12; // header+ssrc+ssrc = 14
            if (fcilen != 0)
            {
                rtpfb.fci = new byte[fcilen];
                in.read(rtpfb.fci);
            }
            return rtpfb;
        }
    }

    private InternalRTCPPacketParser parser;

    public RTCPPacket parse(byte[] data, int offset, int length)
            throws BadFormatException
    {
        if (parser == null)
            parser = new InternalRTCPPacketParser();

        UDPPacket udpp = new UDPPacket();
        udpp.received = false;
        udpp.data = data;
        udpp.offset = offset;
        udpp.length = length;

        return parser.parse(udpp);
    }
}
