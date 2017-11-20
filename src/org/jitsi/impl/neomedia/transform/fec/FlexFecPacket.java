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
     * The list of sequence numbers of packets protected by this fec packet
     */
    public List<Integer> protectedSeqNums;

    /**
     * The size of the FlexFec header (in bytes) in this packet
     */
    public int flexFecHeaderSizeBytes;

    public static FlexFecPacket create(RawPacket p)
    {
        return create(p.getBuffer(), p.getOffset(), p.getLength());
    }

    public static FlexFecPacket create(byte[] buffer, int offset, int length)
    {
        FlexFecPacket flexFecPacket = new FlexFecPacket(buffer, offset, length);
        if (FlexFecHeaderReader.readFlexFecHeader(flexFecPacket,
            flexFecPacket.getBuffer(),
            flexFecPacket.getFlexFecHeaderOffset(),
            flexFecPacket.getLength() - flexFecPacket.getHeaderLength()))
        {
            return flexFecPacket;
        }
        return null;
    }

    /**
     * Ctor
     * @param p a RawPacket representing a flex fec packet
     */
    private FlexFecPacket(RawPacket p)
    {
        this(p.getBuffer(), p.getOffset(), p.getLength());
    }

    /**
     * Ctor
     * @param buffer rtp packet buffer
     * @param offset offset at which the rtp packet starts in the given
     * buffer
     * @param length length of the packet
     */
    private FlexFecPacket(byte[] buffer, int offset, int length)
    {
        super(buffer, offset, length);
    }

    /**
     * Get the list of media packet sequence numbers protected by this
     * FlexFecPacket
     * @return the list of media packet sequence numbers protected by this
     * FlexFecPacket
     */
    public List<Integer> getProtectedSequenceNumbers()
    {
        return this.protectedSeqNums;
    }

    /**
     * Returns the size of the FlexFEC payload, in bytes
     * @return the size of the FlexFEC packet payload, in bytes
     */
    public int getPayloadLength()
    {
        return this.getLength() - this.getHeaderLength() - this.flexFecHeaderSizeBytes;
    }

    /**
     * Get the offset at which the FlexFEC header starts
     * @return the offset at which the FlexFEC header starts
     */
    private int getFlexFecHeaderOffset()
    {
        return this.getHeaderLength();
    }
}
