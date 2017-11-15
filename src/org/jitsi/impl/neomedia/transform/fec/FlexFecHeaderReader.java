package org.jitsi.impl.neomedia.transform.fec;

import org.jitsi.util.*;

import java.util.*;

/**
 * Created by bbaldino on 11/9/17.
 * Based on FlexFec draft -03
 * https://tools.ietf.org/html/draft-ietf-payload-flexible-fec-scheme-03
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |R|F| P|X|  CC   |M| PT recovery |         length recovery      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          TS recovery                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   SSRCCount   |                    reserved                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             SSRC_i                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           SN base_i           |k|          Mask [0-14]        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |k|                   Mask [15-45] (optional)                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |k|                                                             |
 * +-+                   Mask [46-108] (optional)                  |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     ... next in SSRC_i ...                    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
public class FlexFecHeaderReader
{
    /**
     * The minimum size of the FlexFec header, in bytes.
     * We include here one instance of SSRC_i and SN base_i, as well as
     * the smallest possible packet mask.
     */
    private static int HEADER_MIN_SIZE_BYTES = 20;

    /**
     * The offset of the mask in the flexfec header, relative to the start
     * of the flexfec header
     */
    private static int MASK_START_OFFSET_BYTES = 18;

    /**
     * Parse a buffer pointing to FlexFec data.
     * @param flexFecPacket the (empty) FlexFecPacket which the parsed
     * values will be written into
     * @param buffer buffer which contains the flexfec data
     * @param flexFecOffset the flexFecOffset in buffer at which the flexfec header starts
     * @param length length of the buffer
     * @return true if parsing succeeded, false otherwise
     */
    public static boolean readFlexFecHeader(FlexFecPacket flexFecPacket,
                                            byte[] buffer, int flexFecOffset,
                                            int length)
    {
        if (length < HEADER_MIN_SIZE_BYTES)
        {
            return false;
        }
        boolean retransmissionBit = ((buffer[flexFecOffset] & 0x80) >> 7) == 1;
        if (retransmissionBit)
        {
            // We don't support flexfec retransmissions
            return false;
        }
        int maskType = (buffer[flexFecOffset] & 0x40) >> 6;
        if (maskType != 0)
        {
            // We only support flexible (f == 0) mask type
            return false;
        }

        int ssrcCount = buffer[flexFecOffset + 8];
        if (ssrcCount > 1)
        {
            // We only support a single protected ssrc
            return false;
        }

        long protectedSsrc =
            RTPUtils.readUint32AsLong(buffer, flexFecOffset + 12);
        int seqNumBase =
            RTPUtils.readUint16AsInt(buffer,flexFecOffset + 16);

        FlexFecMask mask =
            new FlexFecMask(buffer,
                flexFecOffset + MASK_START_OFFSET_BYTES, seqNumBase);

        // HEADER_MIN_SIZES_BYTES already includes the the size of the smallest
        // possible packet mask, but maskSizeBytes will include that as well,
        // so subtract the size of the smallest possible packet mask (2)
        // here when we want to calculate the total.
        int flexFecHeaderSize = HEADER_MIN_SIZE_BYTES - 2 + mask.lengthBytes();

        flexFecPacket.protectedSsrc = protectedSsrc;
        flexFecPacket.seqNumBase = seqNumBase;
        flexFecPacket.protectedSeqNums = mask.getProtectedSeqNums();
        flexFecPacket.flexFecHeaderSizeBytes = flexFecHeaderSize;

        return true;
    }
}
