package org.jitsi.impl.neomedia.transform.fec;

import java.util.*;

/**
 * Model of the data contained in the FlexFEC -03 header
 *
 * @author bbaldino
 */
public class FlexFec03Header
{
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
    public int size;

    public FlexFec03Header(long protectedSsrc, int seqNumBase,
                           List<Integer> protectedSeqNums, int size)
    {
        this.protectedSsrc = protectedSsrc;
        this.seqNumBase = seqNumBase;
        this.protectedSeqNums = protectedSeqNums;
        this.size = size;
    }
}
