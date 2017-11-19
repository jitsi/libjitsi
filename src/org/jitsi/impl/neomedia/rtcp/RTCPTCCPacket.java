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
     * Parses the FCI portion of an RTCP transport-cc feedback packet in the
     * specified buffer.
     *
     * Warning: the timestamps are represented in the 250µs format used by the
     * on-the-wire format, and don't represent local time. This is different
     * than the timestamps expected as input when constructing a packet with
     * {@link RTCPTCCPacket#RTCPTCCPacket(long, long, PacketMap, byte)}.
     *
     * @param tccPacket the {@link RTCPTCCPacket} that will hold the parsed FCI.
     * @param fciBuffer the buffer which contains the FCI portion of the RTCP
     * feedback packet.
     * @return true if parsing succeeded, false otherwise.
     */
    public static boolean parseFci(
        RTCPTCCPacket tccPacket, ByteArrayBuffer fciBuffer)
    {
        if (fciBuffer == null)
        {
            return false;
        }

        byte[] buf = fciBuffer.getBuffer();
        int off = fciBuffer.getOffset();
        int len = fciBuffer.getLength();

        if (len < MIN_FCI_LENGTH)
        {
            logger.warn(PARSE_ERROR + "length too small: " + len);
            return false;
        }

        // The fixed fields
        int baseSeq = RTPUtils.readUint16AsInt(buf, off);
        int packetStatusCount = RTPUtils.readUint16AsInt(buf, off + 2);

        // reference time. The 24 bit field uses increments of 2^6ms, and we
        // shift by 8 to change the resolution to 250µs.
        tccPacket.referenceTime = RTPUtils.readUint24AsInt(buf, off + 4) << 8;

        long offsetUs = 0;

        // The offset at which the packet status chunk list starts.
        int pscOff = off + 8;

        // First find where the delta list begins.
        int packetsRemaining = packetStatusCount;
        while (packetsRemaining > 0)
        {
            if (pscOff + 2 > off + len)
            {
                logger.warn(PARSE_ERROR + "reached the end while reading chunks");
                return false;
            }

            int packetsInChunk = getPacketCount(buf, pscOff);
            packetsRemaining -= packetsInChunk;

            pscOff += 2; // all chunks are 16bit
        }

        // At this point we have the the beginning of the delta list. Start
        // reading from the chunk and delta lists together.
        int deltaStart = pscOff;
        int deltaOff = pscOff;

        // Reset to the start of the chunks list.
        pscOff = off + 8;
        packetsRemaining = packetStatusCount;
        PacketMap packets = new PacketMap();
        while (packetsRemaining > 0 && pscOff < deltaStart)
        {
            // packetsRemaining is based on the "packet status count" field,
            // which helps us find the correct number of packets described in
            // the last chunk. E.g. if the last chunk is a vector chunk, we
            // don't really know by the chunk alone how many packets are
            // described.
            int packetsInChunk
                = Math.min(getPacketCount(buf, pscOff), packetsRemaining);

            // TODO: do not loop for RLE NR chunks.
            // Read deltas for all packets in the chunk.
            for (int i = 0; i < packetsInChunk; i++)
            {
                int symbol = readSymbol(buf, pscOff, i);
                // -1 or delta in 250µs increments
                int delta = -1;
                switch (symbol)
                {
                case SYMBOL_SMALL_DELTA:
                    // The delta is an 8-bit unsigned integer.
                    if (deltaOff >= off + len)
                    {
                        logger.warn(PARSE_ERROR
                                + "reached the end while reading delta.");
                        return false;
                    }
                    delta = buf[deltaOff++] & 0xff;
                    break;
                case SYMBOL_LARGE_DELTA:
                    // The delta is a 6-bit signed integer.
                    if (deltaOff + 1 >= off + len) // we're about to read 2 bytes
                    {
                        logger.warn(PARSE_ERROR
                                + "reached the end while reading long delta.");
                        return false;
                    }
                    delta = RTPUtils.readInt16AsInt(buf, deltaOff);
                    deltaOff += 2;
                    break;
                case SYMBOL_NOT_RECEIVED:
                default:
                    delta = -1;
                    break;
                }

                if (delta == -1)
                {
                    // Packet not received. We don't update the reference time,
                    // but we push the packet in the map to indicate that it was
                    // marked as not received.
                    packets.put(baseSeq, NEGATIVE_ONE);
                }
                else
                {
                    offsetUs += delta;
                    packets.put(baseSeq, offsetUs);
                }

                baseSeq = (baseSeq + 1) & 0xffff;
            }

            // next packet status chunk
            pscOff += 2;
            packetsRemaining -= packetsInChunk;
        }

        tccPacket.packets = packets;

        return true;
    }

    /**
     * Reads the {@code i}-th (zero-based) symbol from the Packet Status Chunk
     * contained in {@code buf} at offset {@code off}. Returns -1 if the index
     * is found to be invalid (although the validity check is not performed
     * for RLE chunks).
     *
     * @param buf the buffer which contains the Packet Status Chunk.
     * @param off the offset in {@code buf} at which the Packet Status Chunk
     * begins.
     * @param i the zero-based index of the symbol to return.
     * @return the {@code i}-th symbol from the given Packet Status Chunk.
     */
    private static int readSymbol(byte[] buf, int off, int i)
    {
        int chunkType = (buf[off] & 0x80) >> 7;
        if (chunkType == CHUNK_TYPE_VECTOR)
        {
            int symbolType = (buf[off] & 0x40) >> 6;
            switch (symbolType)
            {
            case SYMBOL_TYPE_LONG:
                //  0                   1
                //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
                // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                // |T|S| s0| s1| s2| s3| s4| s5| s6|
                // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                if (0 <= i && i <= 2)
                {
                    return (buf[off] >> (4-2*i)) & 0x03;
                }
                else if (3 <= i && i <= 6)
                {
                    return (buf[off + 1] >> (6-2*(i-3))) & 0x03;
                }

                return -1;

            // Note that draft defines the short symbols as 0 for received and
            // 1 for not received, but but the webrtc.org code uses the opposite.
            // We do what webrtc.org does for interop (also, it is easier,
            // because the short and long symbols match, so we don't have to
            // convert.
            case SYMBOL_TYPE_SHORT:
                // The format is similar to above, except with 14 one-bit
                // symbols.
                if (0 <= i && i <= 5)
                {
                    return (buf[off] >> (5-i)) & 0x01;
                }
                else if (6 <= i && i <= 13)
                {
                    return (buf[off + 1] >> (13-i)) & 0x01;
                }
                return -1;
            default:
                    return -1;
            }
        }
        else if (chunkType == CHUNK_TYPE_RLE)
        {

            // A RLE chunk looks like this:
            //  0                   1
            //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
            // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            // |T| S |       Run Length        |
            // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

            // We assume the caller knows what they are doing and they have
            // given us a valid i, so we just return the symbol (S). Otherwise
            // we'd have to read the Run Length field every time.
            return (buf[off] >> 5) & 0x03;
        }

        return -1;
    }

    /**
     * Returns the number of packets described in the Packet Status Chunk
     * contained in the buffer {@code buf} at offset {@code off}.
     * Note that this may not necessarily match with the number of packets
     * that we want to read from the chunk. E.g. if a feedback packet describes
     * 3 packets (indicated by the value "3" in the "packet status count" field),
     * and it contains a Vector Status Chunk which can describe 7 packets (long
     * symbols), then we want to read only 3 packets (but this method will
     * return 7).
     *
     * @param buf the buffer which contains the Packet Status Chunk
     * @param off the offset at which the Packet Status Chunk starts.
     *
     * @return the number of packets described by the Packet Status Chunk.
     */
    private static int getPacketCount(byte[] buf, int off)
    {
        int chunkType = (buf[off] & 0x80) >> 7;
        if (chunkType == CHUNK_TYPE_VECTOR)
        {
            // A vector chunk looks like this:
            //  0                   1
            //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
            // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            // |1|S|       symbol list         |
            // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            // The 14-bit long symbol list consists of either 14 single-bit
            // symbols, or 7 two-bit symbols, according to the S bit.
            int symbolType = (buf[off] & 0x40) >> 6;
            return symbolType == SYMBOL_TYPE_SHORT ? 14 : 7;
        }
        else if (chunkType == CHUNK_TYPE_RLE)
        {
            // A RLE chunk looks like this:
            //  0                   1
            //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
            // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            // |T| S |       Run Length        |
            // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            return
                ((buf[off] & 0x1f) << 8)
                | (buf[off + 1] & 0xff);
        }

        // This should never happen.
        throw new IllegalStateException(
            "The one-bit chunk type is neither 0 nor 1. A superposition is "+
                " not a valid chunk type.");
    }

    /**
     * The {@link Logger} used by the {@link RTCPTCCPacket} class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(RTCPTCCPacket.class);

    /**
     * The value of the "fmt" field for a transport-cc RTCP feedback packet.
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
     * The value of the {@code T} bit of a Packet Status Chunk, which
     * identifies it as a Vector chunk.
     */
    private static final int CHUNK_TYPE_VECTOR = 1;

    /**
     * The value of the {@code T} bit of a Packet Status Chunk, which
     * identifies it as a Run Length Encoding chunk.
     */
    private static final int CHUNK_TYPE_RLE = 0;

    /**
     * The value of the {@code S} bit og a Status Vector Chunk, which
     * indicates 1-bit (short) symbols.
     */
    private static final int SYMBOL_TYPE_SHORT = 0;

    /**
     * The value of the {@code S} bit of a Status Vector Chunk, which
     * indicates 2-bit (long) symbols.
     */
    private static final int SYMBOL_TYPE_LONG = 1;

    /**
     * A static object defined here in the hope that it will reduce boxing.
     */
    private static final Long NEGATIVE_ONE = -1L;

    /**
     * The minimum length of the FCI field of a valid transport-cc RTCP
     * feedback message. 8 bytes for the fixed fields + 2 bytes for one
     * packet status chunk.
     */
    private static final int MIN_FCI_LENGTH = 10;

    /**
     * An error message to use when parsing failed.
     */
    private static final String PARSE_ERROR
        = "Failed to parse an RTCP transport-cc feedback packet: ";

    /**
     * The map which contains the sequence numbers (mapped to the reception
     * timestamp) of the packets described by this RTCP packet.
     */
    private PacketMap packets = null;

    /**
     * The reference time of this TCC packet.
     */
    private long referenceTime = -1;

    /**
     * Ctor.
     */
    public RTCPTCCPacket()
    {

    }

    /**
     * Initializes a new <tt>RTCPTCCPacket</tt> instance.
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
     * timestamps which this packet is to describe. Note that missing sequence
     * numbers, as well as those mapped to a negative will be interpreted as
     * missing (not received) packets.
     * @param fbPacketCount the index of this feedback packet, to be used in the
     * "fb pkt count" field.
     *
     * Warning: The timestamps for the packets are expected to be in
     * millisecond increments, which is different than the output map produced
     * after parsing a packet!
     *
     * Note: this implementation is not optimized and might not always use
     * the minimal possible number of bytes to describe a given set of packets.
     */
    public RTCPTCCPacket(long senderSSRC, long sourceSSRC,
                         PacketMap packets,
                         byte fbPacketCount)
    {
        super(FMT, RTPFB, senderSSRC, sourceSSRC);

        Map.Entry<Integer, Long> first = packets.firstEntry();
        int firstSeq = first.getKey();
        Map.Entry<Integer, Long> last = packets.lastEntry();
        int packetCount
            = 1 + RTPUtils.getSequenceNumberDelta(last.getKey(), firstSeq);

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
                // Clear previous contents.
                buf[off] = 0;
            }

            int symbol;

            int seq = (firstSeq + seqDelta) & 0xffff;
            Long ts = packets.get(seq);
            if (ts == null || ts < 0)
            {
                symbol = SYMBOL_NOT_RECEIVED;
            }
            else
            {
                long tsDelta = ts - nextReferenceTime;
                if (tsDelta >= 0 && tsDelta <= 63)
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
    }


    /**
     * @return the map of packets represented by this {@link RTCPTCCPacket}.
     *
     * Warning: the timestamps are represented in the 250µs format used by the
     * on-the-wire format, and don't represent local time. This is different
     * than the timestamps expected as input when constructing a packet with
     * {@link RTCPTCCPacket#RTCPTCCPacket(long, long, PacketMap, byte)}.
     */
    synchronized public PacketMap getPackets()
    {
        if (packets == null)
        {
            parseFci(this, new ByteArrayBufferImpl(fci, 0, fci.length));
        }

        return packets;
    }

    /**
     * Gets the reference time of this TCC packet.
     *
     * @return the reference time of this TCC packet.
     */
    public long getReferenceTime()
    {
        return referenceTime;
    }

    /**
     * @return the value of the "fb packet count" field of this packet, or -1.
     */
    public int getFbPacketCount()
    {
        return
            (fci == null || fci.length < MIN_FCI_LENGTH) ? -1 : fci[7] & 0xff;
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

