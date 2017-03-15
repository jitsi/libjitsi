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
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.io.*;
import java.util.*;

/**
 * A class which represents an RTCP packet carrying transport-wide congestion
 * control (transport-cc) feedback information. The format is defined here:
 * https://tools.ietf.org/html/draft-holmer-rmcat-transport-wide-cc-extensions-01
 *
 * <pre>{@code
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|  FMT=15 |    PT=205     |           length              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     SSRC of packet sender                     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      SSRC of media source                     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      base sequence number     |      packet status count      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                 reference time                | fb pkt. count |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          packet chunk         |         packet chunk          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * .                                                               .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         packet chunk          |  recv delta   |  recv delta   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * .                                                               .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           recv delta          |  recv delta   | zero padding  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * }</pre>
 *
 * @author Boris Grozev
 */
public class RTCPTCCPacket
    extends RTCPFBPacket
{
    /**
     * Gets a boolean indicating whether or not the RTCP packet specified in the
     * {@link ByteArrayBuffer} that is passed as an argument is a NACK packet or
     * not.
     *
     * @param baf the {@link ByteArrayBuffer}
     * @return true if the byte array buffer holds a NACK packet, otherwise
     * false.
     */
    public static boolean isTCCPacket(ByteArrayBuffer baf)
    {
        int rc = RTCPHeaderUtils.getReportCount(baf);
        return rc == FMT && isRTPFBPacket(baf);
    }

    /**
     * @return the packets represented in an RTCP transport-cc feedback packet.
     * @param baf the buffer which contains the RTCP packet.
     */
    public static PacketMap getPackets(ByteArrayBuffer baf)
    {
        return getPacketsFci(getFCI(baf));
    }

    /**
     * @return the packets represented in the FCI portion of an RTCP
     * transport-cc feedback packet.
     * @param fciBuffer the buffer which contains the FCI portion of the RTCP
     * feedback packet.
     */
    public static PacketMap getPacketsFci(ByteArrayBuffer fciBuffer)
    {
        PacketMap packets = new PacketMap();
        if (fciBuffer == null)
        {
            return packets;
        }

        byte[] buf = fciBuffer.getBuffer();
        int off = fciBuffer.getOffset();
        int len = fciBuffer.getLength();

        int baseSeq = RTPUtils.readUint16AsInt(buf, off);
        int packetStatusCount = RTPUtils.readUint16AsInt(buf, off + 2);

        // reference time with resolution 250µs
        long referenceTime = RTPUtils.readUint24AsInt(buf, off + 4) << 8;

        off += 8;

        int deltaOff = 3;

        return packets;
    }

    /**
     * The value of the "fmt" field for a transport-cc packet.
     */
    public static final int FMT = 15;

    /**
     * The symbol which indicates that a packet was not received.
     */
    private static final int SYMBOL_NOT_RECEIVED = 0;

    /**
     * The symbol which indicates that a packet was received with a small delta
     * (represented in a 1-byte field).
     */
    private static final int SYMBOL_SMALL_DELTA = 1;

    /**
     * The symbol which indicates that a packet was received with a large or
     * negative delta (represented in a 2-byte field).
     */
    private static final int SYMBOL_LARGE_DELTA = 2;

    /**
     * The map which contains the sequence numbers (mapped to the reception
     * timestamp) of the packets described by this RTCP packet.
     */
    private PacketMap packets = null;

    /**
     * Initializes a new <tt>NACKPacket</tt> instance.
     * @param base
     */
    public RTCPTCCPacket(RTCPCompoundPacket base)
    {
        super(base);
    }

    /**
     * Initializes a new {@link RTCPTCCPacket} instance with a specific "packet
     * sender SSRC" and "media source SSRC" values, and which describes a
     * specific set of sequence numbers.
     * @param senderSSRC the value to use for the "packet sender SSRC" field.
     * @param sourceSSRC the value to use for the "media source SSRC" field.
     * @param packets the set of RTP sequence numbers and their reception
     * timestamps which this packet is to describe.
     * @param fbPacketCount the index of this feedback packet, to be used in the
     * "fb pkt count" field.
     *
     * Note that this implementation is not optimized and might not always use
     * the minimal possible number of bytes to describe a given set of packets.
     * Specifically, it does take into account that sequence numbers wrap
     * at 2^16 and fails to pack numbers close to 2^16 with those close to 0.
     */
    public RTCPTCCPacket(long senderSSRC, long sourceSSRC,
                         PacketMap packets,
                         byte fbPacketCount)
    {
        super(FMT, RTPFB, senderSSRC, sourceSSRC);

        TreeSet<Map.Entry<Integer, Long>> sequenceNumbers
            = (TreeSet) packets.entrySet();

        Map.Entry<Integer, Long> first = sequenceNumbers.first();
        int firstSeq = first.getKey();
        Map.Entry<Integer, Long> last = sequenceNumbers.last();
        int packetCount
            = 1 + RTPUtils.sequenceNumberDiff(last.getKey(), firstSeq);

        // Temporary buffer to store the fixed fields (8 bytes) and the list of
        // packet status chunks (see the format above). The buffer may be longer
        // than needed. We pack 7 packets in a chunk, and a chunk is 2 bytes.
        byte[] buf = new byte[(packetCount / 7 + 1) * 2 + 8];
        // Temporary buffer to store the list of deltas (see the format above).
        // We allocated for the worst case (2 bytes per packet), which may
        // be longer than needed.
        byte[] deltas = new byte[packetCount * 2];
        int deltaOff = 0;
        int off = 0;

        long referenceTime = first.getValue();
        referenceTime -= referenceTime % 64;

        // Set the 'base sequence number' field
        off += RTPUtils.writeShort(buf, off, (short) (int) first.getKey());

        // Set the 'packet status count' field
        off += RTPUtils.writeShort(buf, off, (short) packetCount);

        // Set the 'reference time' field
        off +=
            RTPUtils.writeUint24(buf, off,
                                 (int) ((referenceTime >> 6) & 0xffffff));

        // Set the 'fb pkt count' field. TODO increment
        buf[off++] = fbPacketCount;

        // Add the packet status chunks. In this first impl we'll just use
        // status vector chunks (T=1) with two-bit symbols (S=1) as this is
        // most straightforward to implement.
        // TODO: optimize for size
        long nextReferenceTime = referenceTime;
        off--; // we'll take care of this inside the loop.
        for (int seqDelta = 0; seqDelta < packetCount; seqDelta++)
        {
            // A status vector chunk with two-bit symbols contains 7 packet
            // symbols
            if (seqDelta % 7 == 0)
            {
                off++;
                buf[off] = (byte) 0xc0; //T=1, S=1
            }
            else if (seqDelta % 7 == 3)
            {
                off++;
                buf[off] = 0;
            }

            int symbol;

            int seq = (firstSeq + seqDelta) & 0xffff;
            Long ts = packets.get(seq);
            if (ts == null)
            {
                symbol = SYMBOL_NOT_RECEIVED;
            }
            else
            {
                long tsDelta = ts - nextReferenceTime;
                if (tsDelta >= 0 && tsDelta < 63)
                {
                    symbol = SYMBOL_SMALL_DELTA;

                    // The small delta is an 8-bit unsigned with a resolution of
                    // 250µs. Our deltas are all in milliseconds (hence << 2).
                    deltas[deltaOff++] = (byte ) ((tsDelta << 2) & 0xff);
                }
                else if (tsDelta < 8191 && tsDelta > -8192)
                {
                    symbol = SYMBOL_LARGE_DELTA;

                    // The large or negative delta is a 16-bit signed integer
                    // with a resolution of 250µs (hence << 2).
                    short d = (short) (tsDelta << 2);
                    deltas[deltaOff++] = (byte) ((d >> 8) & 0xff);
                    deltas[deltaOff++] = (byte) ((d) & 0xff);
                }
                else
                {
                    // The RTCP packet format does not support deltas bigger
                    // than what we handle above. As per the draft, if we want
                    // send feedback with such deltas, we should split it up
                    // into multiple RTCP packets. We can't do that here in the
                    // constructor.
                    throw new IllegalArgumentException("Delta too big, needs new reference.");
                }

                // If the packet was received, the next delta will be relative
                // to its time. Otherwise, we'll just the previous reference.
                nextReferenceTime = ts;
            }

            // Depending on the index of our packet, we have to offset its
            // symbol (we've already set 'off' to point to the correct byte).
            //  0 1 2 3 4 5 6 7          8 9 0 1 2 3 4 5
            //  S T <0> <1> <2>          <3> <4> <5> <6>
            int symbolOffset;
            switch (seqDelta % 7)
            {
            case 0:
            case 4:
                symbolOffset = 4;
                break;
            case 1:
            case 5:
                symbolOffset = 2;
                break;
            case 2:
            case 6:
                symbolOffset = 0;
                break;
            case 3:
            default:
                symbolOffset = 6;
            }

            symbol <<= symbolOffset;
            buf[off] |= symbol;
        }

        off++;
        if (packetCount % 7 <= 3)
        {
            // the last chunk was not complete
            buf[off++] = 0;
        }


        fci = new byte[off + deltaOff];
        System.arraycopy(buf, 0, fci, 0, off);
        System.arraycopy(deltas, 0, fci, off, deltaOff);
        this.packets = packets;
    }


    /**
     * @return the map of packets represented by this {@link RTCPTCCPacket}.
     */
    synchronized public Map<Integer, Long> getPackets()
    {
        if (packets == null)
        {
            packets = getPacketsFci(new ByteArrayBufferImpl(fci, 0, fci.length));
        }

        return packets;
    }

    @Override
    public void assemble(DataOutputStream dataoutputstream)
        throws IOException
    {
        dataoutputstream.writeByte((byte) (0x80 /* version */ | FMT));
        dataoutputstream.writeByte((byte) RTPFB);
        dataoutputstream.writeShort(2 + (fci.length / 4));
        dataoutputstream.writeInt((int) senderSSRC);
        dataoutputstream.writeInt((int) sourceSSRC);
        dataoutputstream.write(fci);
    }

    @Override
    public String toString()
    {
        return "RTCP transport-cc feedback";
    }

    /**
     * An ordered collection which maps sequence numbers to timestamps, the
     * order is by the sequence number.
     */
    public static class PacketMap extends TreeMap<Integer, Long>
    {
        public PacketMap()
        {
            super(RTPUtils.sequenceNumberComparator);
        }
    }
}

