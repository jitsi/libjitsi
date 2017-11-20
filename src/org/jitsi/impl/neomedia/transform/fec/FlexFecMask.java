package org.jitsi.impl.neomedia.transform.fec;

import java.util.*;

/**
 * Models a FlexFec-03 maskWithoutKBits field
 */
public class FlexFecMask
{
    private static int MASK_0_START_BIT = 1;
    private static int MASK_0_END_BIT = 15;
    private static int MASK_1_START_BIT = 17;
    private static int MASK_1_END_BIT = 47;
    private static int MASK_2_START_BIT = 49;
    private static int MASK_2_END_BIT = 111;

    private int sizeBytes;
    private LeftToRightBitSet maskWithoutKBits;
    private int baseSeqNum;

    /**
     * Initialize this maskWithoutKBits with a received buffer.
     * buffer + offset should point to the location of the start
     * of the mask
     * @param buffer the flexfec packet buffer
     * @param offset offset to the location of the start of the mask
     * @param baseSeqNum the base sequence number from the flexfec packet
     */
    FlexFecMask(byte[] buffer, int offset, int baseSeqNum)
    {
        this.sizeBytes = getMaskSizeInBytes(buffer, offset);
        this.maskWithoutKBits = getSeqNumMaskWithoutKBits(buffer, offset, this.sizeBytes);
        this.baseSeqNum = baseSeqNum;
    }

    /**
     * The size of the packet mask in a flexfec packet is dynamic.  This
     * method determines the size (in bytes) of the mask in the given packet
     * buffer and returns it
     * @param buffer the buffer containing the mask
     * @param maskOffset the offset in the buffer to the start of the mask
     * @return the size of the mask, in bytes.
     */
    private static int getMaskSizeInBytes(byte[] buffer, int maskOffset)
    {
        // The smallest mask is 2 bytes
        int maskSizeBytes = 2;
        int kbit0 = (buffer[maskOffset] & 0x80) >> 7;
        if (kbit0 == 0)
        {
            maskSizeBytes += 4;
            int kbit1 =
                (buffer[maskOffset + 2] & 0x80) >> 7;
            if (kbit1 == 0)
            {
                maskSizeBytes += 8;
            }
        }
        return maskSizeBytes;
    }

    /**
     * Get the length of the mask, in bytes
     * @return the length of the mask, in bytes
     */
    public int lengthBytes()
    {
        return sizeBytes;
    }

    /**
     * Get the list of media packet sequence numbers which are marked
     * as protected in this mask
     * @return a list of sequence numbers of the media packets marked as
     * protected by this mask
     */
    public List<Integer> getProtectedSeqNums()
    {
        List<Integer> protectedSeqNums = new ArrayList<>();
        for (int i = 0; i < this.sizeBytes * 8; ++i)
        {
            if (maskWithoutKBits.get(i))
            {
                protectedSeqNums.add(baseSeqNum + i);
            }
        }
        return protectedSeqNums;
    }

    /**
     * Extract the mask containing just the protected sequence number
     * bits (not the k bits) from the given buffer
     * @param buffer the packet buffer
     * @param maskOffset the location of the start of the mask in the given
     * packet buffer
     * @param maskSizeBytes the size of the mask, in bytes
     * @return a {@link LeftToRightBitSet} which contains the bits of
     * the packet mask WITHOUT the k bits (the packet location bits are
     * 'collapsed' so that their bit index correctly represents the delta
     * from baseSeqNum)
     */
    private static LeftToRightBitSet getSeqNumMaskWithoutKBits(byte[] buffer,
                                                               int maskOffset,
                                                               int maskSizeBytes)
    {
        LeftToRightBitSet mask =
            LeftToRightBitSet.valueOf(buffer, maskOffset, maskSizeBytes);
        // We now have a LeftToRightBitset of the entire mask, including the k
        // bits.  Now shift away the k bits

        // Shift away the first k bit
        LeftToRightBitSet.shiftBitsLeft(mask, MASK_0_START_BIT,
            MASK_0_END_BIT, 1);
        if (maskSizeBytes > 2)
        {
            // Shift away the second k bit (this data hasn't shifted at all
            // though, so we need to account for the first k bit in this
            // shift as well, so we shift to the left by 2 bits)
            LeftToRightBitSet.shiftBitsLeft(mask, MASK_1_START_BIT,
                MASK_1_END_BIT, 2);
            if (maskSizeBytes > 6)
            {
                // Shift away the third k bit (this data has to shift by 3
                // bits to take into account the shift of the previous 2
                // k bits)
                LeftToRightBitSet.shiftBitsLeft(mask, MASK_2_START_BIT,
                    MASK_2_END_BIT, 3);
            }
        }
        return mask;
    }
}
