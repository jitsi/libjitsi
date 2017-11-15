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
     * @param buffer
     * @param offset
     */
    FlexFecMask(byte[] buffer, int offset, int baseSeqNum)
    {
        this.sizeBytes = getMaskSizeInBytes(buffer, offset);
        this.maskWithoutKBits = getSeqNumMaskWithoutKBits(buffer, offset, this.sizeBytes);
        this.baseSeqNum = baseSeqNum;
    }

    private static int getMaskSizeInBytes(byte[] buffer, int maskOffset)
    {
        // We know it'll be at least sizeBytes 2
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

    public int lengthBytes()
    {
        return sizeBytes;
    }

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
//        int bitPosIgnoringKBits = 0;
//        for (int currBytePos = 0; currBytePos < this.sizeBytes; ++currBytePos)
//        {
//            for (int currBitPos = 0; currBitPos < 8; ++currBitPos)
//            {
//                int absoluteBitPos = currBytePos * 8 + currBitPos;
//                if (isKbitPosition(absoluteBitPos))
//                {
//                    continue;
//                }
//                if (this.maskWithoutKBits.get(absoluteBitPos))
//                {
//                    protectedSeqNums.add(baseSeqNum + bitPosIgnoringKBits);
//                }
//                bitPosIgnoringKBits++;
//            }
//        }
//        return protectedSeqNums;
    }

    /**
     * Extract the mask containing just the protected sequence number
     * bits (not the k bits) from the given buffer
     * NOTE: this returns a bitset, which treats
     * @param buffer
     * @param maskSizeBytes
     * @return
     */
    private static LeftToRightBitSet getSeqNumMaskWithoutKBits(byte[] buffer, int maskOffset, int maskSizeBytes)
    {
        LeftToRightBitSet mask =
            LeftToRightBitSet.valueOf(buffer, maskOffset, maskSizeBytes);
        System.out.println("mask with k bits:\n" + mask);
        // We now have a LeftToRightBitset of the entire mask, including the k
        // bits.  Now shift away the k bits

        // Shift away the first k bit
        LeftToRightBitSet.shiftBitsLeft(mask, MASK_0_START_BIT, MASK_0_END_BIT, 1);
        if (maskSizeBytes > 2)
        {
            // Shift away the second k bit (this data hasn't shifted at all
            // though, so we need to account for the first k bit in this
            // shift as well
            LeftToRightBitSet.shiftBitsLeft(mask, MASK_1_START_BIT, MASK_1_END_BIT, 2);
            if (maskSizeBytes > 6)
            {
                LeftToRightBitSet.shiftBitsLeft(mask, MASK_2_START_BIT, MASK_2_END_BIT, 3);
            }
        }
        return mask;
    }
}
