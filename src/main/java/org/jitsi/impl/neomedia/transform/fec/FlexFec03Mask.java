/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.impl.neomedia.transform.fec;

import org.jitsi.util.*;

import java.util.*;

/**
 * Models a FlexFec-03 mask field
 */
public class FlexFec03Mask
{
    private static final int MASK_0_K_BIT = 0;
    private static final int MASK_1_K_BIT = 16;
    private static final int MASK_2_K_BIT = 48;

    private static final int MASK_SIZE_SMALL = 2;
    private static final int MASK_SIZE_MED = 6;
    private static final int MASK_SIZE_LARGE = 14;

    public static class MalformedMaskException extends Exception
    {

    }

    private static int getNumBitsExcludingKBits(int maskSizeBytes)
    {
        int numBits = maskSizeBytes * 8;
        if (maskSizeBytes > MASK_SIZE_MED)
        {
            return numBits - 3;
        }
        if (maskSizeBytes > MASK_SIZE_SMALL)
        {
            return numBits - 2;
        }
        return numBits - 1;
    }

    private static FlexFec03BitSet createMaskWithKBits(int sizeBytes, int baseSeqNum, List<Integer> protectedSeqNums)
    {
        // The sizeBytes we are given will be the entire size of the mask (including
        // k bits).  We're going to insert the k bits later, so subtract
        // those bits from the size of the mask we'll create now
        int numBits = getNumBitsExcludingKBits(sizeBytes);

        FlexFec03BitSet mask = new FlexFec03BitSet(numBits);
        // First create a mask without the k bits
        for (Integer protectedSeqNum : protectedSeqNums)
        {
            int delta = RTPUtils.getSequenceNumberDelta(protectedSeqNum , baseSeqNum);
            mask.set(delta);
        }

        // Now insert the appropriate k bits
        if (sizeBytes > MASK_SIZE_MED)
        {
            mask.addBit(MASK_0_K_BIT, false);
            mask.addBit(MASK_1_K_BIT, false);
            mask.addBit(MASK_2_K_BIT, true);
        }
        else if (sizeBytes > MASK_SIZE_SMALL)
        {
            mask.addBit(MASK_0_K_BIT, false);
            mask.addBit(MASK_1_K_BIT, true);
        }
        else
        {
            mask.addBit(MASK_0_K_BIT, true);
        }

        return mask;
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
        throws MalformedMaskException
    {
        if ((buffer.length - maskOffset) < MASK_SIZE_SMALL)
        {
            throw new MalformedMaskException();
        }
        // The mask is always at least MASK_SIZE_SMALL bytes
        int maskSizeBytes = MASK_SIZE_SMALL;
        int kbit0 = (buffer[maskOffset] & 0x80) >> 7;
        if (kbit0 == 0)
        {
            maskSizeBytes = MASK_SIZE_MED;
            if ((buffer.length - maskOffset) < MASK_SIZE_MED)
            {
                throw new MalformedMaskException();
            }
            int kbit1 = (buffer[maskOffset + 2] & 0x80) >> 7;
            if (kbit1 == 0)
            {
                if ((buffer.length - maskOffset) < MASK_SIZE_LARGE)
                {
                    throw new MalformedMaskException();
                }
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
        throws MalformedMaskException
    {
        int largestDelta = -1;
        for (Integer protectedSeqNum : sortedProtectedSeqNums)
        {
            int delta = RTPUtils.getSequenceNumberDelta(protectedSeqNum, baseSeqNum);
            if (delta > largestDelta)
            {
                largestDelta = delta;
            }
        }
        if (largestDelta > 108)
        {
            throw new MalformedMaskException();
        }
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
     * Extract the mask containing just the protected sequence number
     * bits (not the k bits) from the given buffer
     * @param maskWithKBits the mask with the k bits included
     * @return a {@link FlexFec03BitSet} which contains the bits of
     * the packet mask WITHOUT the k bits (the packet location bits are
     * 'collapsed' so that their bit index correctly represents the delta
     * from baseSeqNum)
     */
    private static FlexFec03BitSet getMaskWithoutKBits(FlexFec03BitSet maskWithKBits)
    {
        // Copy the given mask
        FlexFec03BitSet maskWithoutKBits =
            FlexFec03BitSet.valueOf(maskWithKBits.toByteArray());
        int maskSizeBytes = maskWithKBits.sizeBytes();
        // We now have a FlexFec03BitSet of the entire mask, including the k
        // bits.  Now shift away the k bits

        // Note that it's important we remove the k bits in this order
        // (otherwise the bit we remove in the k bit position won't be the right
        // one).
        if (maskSizeBytes > MASK_SIZE_MED)
        {
            maskWithoutKBits.removeBit(MASK_2_K_BIT);
        }
        if (maskSizeBytes > MASK_SIZE_SMALL)
        {
            maskWithoutKBits.removeBit(MASK_1_K_BIT);
        }
        maskWithoutKBits.removeBit(MASK_0_K_BIT);

        return maskWithoutKBits;
    }

    private int sizeBytes;
    /**
     * The mask field (including k bits)
     */
    private FlexFec03BitSet maskWithKBits;
    private int baseSeqNum;

    /**
     * Initialize this maskWithoutKBits with a received buffer.
     * buffer + maskOffset should point to the location of the start
     * of the mask
     * @param buffer the flexfec packet buffer
     * @param maskOffset maskOffset to the location of the start of the mask
     * @param baseSeqNum the base sequence number from the flexfec packet
     */
    FlexFec03Mask(byte[] buffer, int maskOffset, int baseSeqNum)
        throws MalformedMaskException
    {
        this.sizeBytes = getMaskSizeInBytes(buffer, maskOffset);
        this.maskWithKBits = FlexFec03BitSet.valueOf(buffer, maskOffset, this.sizeBytes);
        this.baseSeqNum = baseSeqNum;
    }

    /**
     * Create a mask from a base sequence number and a list of protected
     * sequence numbers
     * @param baseSeqNum the base sequence number to use for the mask
     * @param protectedSeqNums the sequence numbers this mask should mark
     * as protected
     */
    public FlexFec03Mask(int baseSeqNum, List<Integer> protectedSeqNums)
        throws MalformedMaskException
    {
        this.sizeBytes = getMaskSizeInBytes(baseSeqNum, protectedSeqNums);
        this.baseSeqNum = baseSeqNum;
        this.maskWithKBits = createMaskWithKBits(this.sizeBytes, this.baseSeqNum, protectedSeqNums);
    }

    public FlexFec03BitSet getMaskWithKBits()
    {
        return maskWithKBits;
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
        FlexFec03BitSet maskWithoutKBits = getMaskWithoutKBits(maskWithKBits);
        List<Integer> protectedSeqNums = new ArrayList<>();
        for (int i = 0; i < maskWithoutKBits.sizeBits(); ++i)
        {
            if (maskWithoutKBits.get(i))
            {
                protectedSeqNums.add(RTPUtils.applySequenceNumberDelta(baseSeqNum, i));
            }
        }
        return protectedSeqNums;
    }
}
