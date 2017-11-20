package org.jitsi.impl.neomedia.transform.fec;

import java.util.*;

/**
 * Models a FlexFec-03 maskWithoutKBits field
 */
public class FlexFecMask
{
    private static final int MASK_0_START_BIT = 1;
    private static final int MASK_0_END_BIT = 15;
    private static final int MASK_1_START_BIT = 17;
    private static final int MASK_1_END_BIT = 47;
    private static final int MASK_2_START_BIT = 49;
    private static final int MASK_2_END_BIT = 111;

    private static final int MASK_SIZE_SMALL = 2;
    private static final int MASK_SIZE_MED = 6;
    private static final int MASK_SIZE_LARGE = 14;

    private int sizeBytes;
    //private LeftToRightBitSet maskWithoutKBits;
    /**
     * The mask field (including k bits)
     */
    private LeftToRightBitSet mask;
    private int baseSeqNum;

    /**
     * Initialize this maskWithoutKBits with a received buffer.
     * buffer + maskOffset should point to the location of the start
     * of the mask
     * @param buffer the flexfec packet buffer
     * @param maskOffset maskOffset to the location of the start of the mask
     * @param baseSeqNum the base sequence number from the flexfec packet
     */
    FlexFecMask(byte[] buffer, int maskOffset, int baseSeqNum)
    {
        this.sizeBytes = getMaskSizeInBytes(buffer, maskOffset);
        this.mask = LeftToRightBitSet.valueOf(buffer, maskOffset, this.sizeBytes);
        //this.maskWithoutKBits = getSeqNumMaskWithoutKBits(buffer, maskOffset, this.sizeBytes);
        this.baseSeqNum = baseSeqNum;
    }

    private FlexFecMask()
    {

    }

    /**
     * Create a mask from a base sequence number and a list of protected
     * sequence numbers
     * @param baseSeqNum the base sequence number to use for the mask
     * @param protectedSeqNums the sequence numbers this mask should mark
     * as protected
     * @return the created FlexFecMask
     */
    public static FlexFecMask create(int baseSeqNum, List<Integer> protectedSeqNums)
    {
        FlexFecMask mask = new FlexFecMask();
        mask.sizeBytes = getMaskSizeInBytes(baseSeqNum, protectedSeqNums);

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
        int maskSizeBytes = MASK_SIZE_SMALL;
        int kbit0 = (buffer[maskOffset] & 0x80) >> 7;
        if (kbit0 == 0)
        {
            maskSizeBytes = MASK_SIZE_MED;
            int kbit1 =
                (buffer[maskOffset + 2] & 0x80) >> 7;
            if (kbit1 == 0)
            {
                maskSizeBytes = MASK_SIZE_LARGE;
            }
        }
        return maskSizeBytes;
    }

    /**
     * Determine how big the mask needs to be based on the given base sequence
     * number and the list of protected sequence numbers
     * @param baseSeqNum the base sequence number to use for the mask
     * @param sortedProtectedSeqNums the sequence numbers this mask should mark
     * as protected. NOTE: this list MUST be in sorted order
     * @return the size, in bytes, of the mask that is needed to convey
     * the given protected sequence numbers
     */
    private static int getMaskSizeInBytes(int baseSeqNum, List<Integer> sortedProtectedSeqNums)
    {
        int largestSeqNum = sortedProtectedSeqNums.get(sortedProtectedSeqNums.size() - 1);
        int largestDelta = largestSeqNum - baseSeqNum;
        if (largestDelta <= 14)
        {
            return MASK_SIZE_SMALL;
        }
        else if (largestDelta <= 45)
        {
            return MASK_SIZE_MED;
        }
        return MASK_SIZE_LARGE;
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
    private static LeftToRightBitSet getMaskWithoutKBits(LeftToRightBitSet maskWithKBits)
    {
        // Copy the given mask
        LeftToRightBitSet maskWithoutKBits =
            LeftToRightBitSet.valueOf(maskWithKBits.toByteArray());
        int maskSizeBytes = maskWithKBits.
        //LeftToRightBitSet mask =
        //    LeftToRightBitSet.valueOf(buffer, maskOffset, maskSizeBytes);
        // We now have a LeftToRightBitset of the entire mask, including the k
        // bits.  Now shift away the k bits

        // Shift away the first k bit
        LeftToRightBitSet.shiftBitsLeft(maskWithoutKBits, MASK_0_START_BIT,
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
