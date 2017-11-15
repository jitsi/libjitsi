package org.jitsi.impl.neomedia.transform.fec;

import org.jitsi.service.neomedia.*;
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

public class FlexFecPacket
    extends RawPacket
{
    private static final Logger logger
        = Logger.getLogger(FlexFecPacket.class);

    /**
     * The SSRC of the media stream protected by this FEC packet
     */
    public long protectedSsrc;

    /**
     * The base sequence number from which the mask defines the sequence numbers
     * of the media packets protected by this packet
     */
    public int seqNumBase;

    /**
     * The mask denoting which sequence numbers (relative to seqNumBase)
     * of the media stream are protected by this fec packet
     */
    public BitSet packetMask;

    /**
     * The list of sequence numbers of packets protected by this fec packet
     */
    public List<Integer> protectedSeqNums;

    /**
     * The size of the FlexFec header (in bytes) in this packet
     */
    public int flexFecHeaderSizeBytes;

    public FlexFecPacket(RawPacket p)
    {
        this(p.getBuffer(), p.getOffset(), p.getLength());
        FlexFecHeaderReader.readFlexFecHeader(
            this,
            this.getBuffer(),
            this.getFlexFecBufOffset(),
            this.getLength() - this.getHeaderLength());
    }

    public FlexFecPacket(byte[] buffer, int offset, int length)
    {
        super(buffer, offset, length);
    }

    public List<Integer> getProtectedSequenceNumbers()
    {
        return this.protectedSeqNums;
    }

    public int getPayloadLength()
    {
        return this.getLength() - this.getHeaderLength() - this.flexFecHeaderSizeBytes;
    }

    /**
     * Get the offset at which the flexfec header starts
     * @return
     */
    private int getFlexFecBufOffset()
    {
        return this.getHeaderLength();
    }
}
