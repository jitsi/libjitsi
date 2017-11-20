package org.jitsi.impl.neomedia.transform.fec;

import org.jitsi.util.*;
import org.junit.*;

import java.nio.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by bbaldino on 11/10/17.
 */
public class FlexFecHeaderReaderTest
{
    private static byte[] snBaseToBytes(int seqNumBase)
    {
        return ByteBuffer.allocate(4).putInt(seqNumBase).array();
    }

    /**
     * Given a list of sequence numbers and the base sequence number, return
     * a properly formatted flexfec mask field (including the k bits)
     * NOTE: seqNums must be sorted low -> high
     * @param seqNums
     * @param baseSeqNum
     * @return
     */
    private static byte[] seqNumsToMask(List<Integer> seqNums, int baseSeqNum)
    {
        int maxDelta = seqNums.get(seqNums.size() - 1) - baseSeqNum;
        int maskSizeBytes = 0;
        if (maxDelta <= 15)
        {
            maskSizeBytes = 2;
        }
        else if (maxDelta <= 46)
        {
            maskSizeBytes = 4;
        }
        else
        {
            maskSizeBytes = 12;
        }
        byte[] mask = new byte[maskSizeBytes];

        for (Integer seqNum : seqNums)
        {
            //System.out.println("looking at seq num " + seqNum);
            int bitPos = seqNum - baseSeqNum;
            // Take the k bits into account
            if (bitPos <= 15)
            {
                // Mark this one as the last mask chunk, if we spill
                // into the next chunk we'll change it
                mask[0] |= 0x80;
                bitPos += 1;
            }
            else if (bitPos <= 46)
            {
                // We've spilled over into the next mask area, change the
                // first k bit to 0
                mask[0] &= 0x7f;
                // And mark this one as the last
                mask[2] |= 0x80;
                bitPos += 2;
            }
            else
            {
                mask[2] &= 0x7f;
                mask[4] |= 0x80;
                bitPos += 3;
            }
            int byteNum = bitPos / 8;
            // bitNum is indexed from left to right
            int bitNum = bitPos % 8;
            mask[bitPos / 8] |= (0x80 >> bitNum);
        }

        return mask;
    }

    private static final byte NO_R_BIT = 0 << 7;
    private static final byte NO_F_BIT = 0 << 6;
    private static final byte PT_RECOVERY = 127;
    private static final byte[] LENGTH_RECOVERY = { (byte)0xab, (byte)0xcd };
    private static final byte[] TS_RECOVERY = { 0x01, 0x023, 0x45, 0x67 };
    private static final byte SSRC_COUNT = 1;
    private static final byte RESERVED_BITS = 0x00;
    private static final byte[] PROTECTED_SSRC = { 0x11, 0x22, 0x33, 0x44 };
    //private static final byte[] SN_BASE_BYTES = { (byte)0xaa, (byte)0xbb }; // 43707
    private static final int SN_BASE = 43407;
    private static byte[] SN_BASE_BYTES;
    private static final byte PAYLOAD_BITS = 0x00;

    static {
        byte[] snBytes = snBaseToBytes(SN_BASE);
        SN_BASE_BYTES = new byte[]{ snBytes[2], snBytes[3] };
    }

//    @Test
//    public void testGetSeqNumMaskShort()
//    {
//        final byte K_BIT_1 = (byte)(1 << 7);
//        final byte[] maskBytes = {
//            K_BIT_1 | 0x12, 0x34
//        };
//        final byte[] expected = {
//            0x12 << 1, 0x34 << 1
//        };
//
//        LeftToRightBitSet mask = FlexFecHeaderReader.getSeqNumMaskWithoutKBits(maskBytes, 0, 2);
//        assertEquals(expected[0], mask.toByteArray()[0]);
//        assertEquals(expected[1], mask.toByteArray()[1]);
//    }
//
//    @Test
//    public void testGetSeqNumMaskMed()
//    {
//        final byte K_BIT_0 = (byte)(0 << 7);
//        final byte K_BIT_1 = (byte)(1 << 7);
//        final byte[] maskBytes = {
//            K_BIT_0 | 0x12, 0x34,
//            K_BIT_1 | 0x42, 0x34, 0x56, 0x78
//        };
//        final byte[] expected = {
//            0x24, 0x69,
//            (byte)0x08, (byte)0xd1, 0x59, (byte)0xe0
//        };
//
//        LeftToRightBitSet mask = FlexFecHeaderReader.getSeqNumMaskWithoutKBits(maskBytes, 0, 6);
//        assertEquals(expected[0], mask.toByteArray()[0]);
//        assertEquals(expected[1], mask.toByteArray()[1]);
//        assertEquals(expected[2], mask.toByteArray()[2]);
//        assertEquals(expected[3], mask.toByteArray()[3]);
//    }
//
//    @Test
//    public void testGetSeqNumMaskLong()
//    {
//        final byte K_BIT_0 = (byte)(0 << 7);
//        final byte K_BIT_1 = (byte)(1 << 7);
//        final byte[] maskBytes = {
//            K_BIT_0 | 0x12, 0x34,
//            K_BIT_0 | 0x42, 0x34,       0x56,       0x78,
//            K_BIT_1 | 0x42, 0x34,       0x56,       0x78,
//            (byte)0x9a,     (byte)0xbc, (byte)0xcd, (byte)0xef
//        };
//        final byte[] expected = {
//            0x24,           0x69,
//            (byte)0x08,     (byte)0xd1, 0x59,       (byte)0xe2,
//            0x11,           (byte)0xa2, (byte)0xb3, (byte)0xc4,
//            (byte)0xd5,     (byte)0xe6,  0x6f,       0x78
//        };
//
//        LeftToRightBitSet mask = FlexFecHeaderReader.getSeqNumMaskWithoutKBits(maskBytes, 0, 14);
//        assertEquals(expected[0], mask.toByteArray()[0]);
//        assertEquals(expected[1], mask.toByteArray()[1]);
//        assertEquals(expected[2], mask.toByteArray()[2]);
//        assertEquals(expected[3], mask.toByteArray()[3]);
//        assertEquals(expected[4], mask.toByteArray()[4]);
//        assertEquals(expected[5], mask.toByteArray()[5]);
//        assertEquals(expected[6], mask.toByteArray()[6]);
//        assertEquals(expected[7], mask.toByteArray()[7]);
//        assertEquals(expected[8], mask.toByteArray()[8]);
//        assertEquals(expected[9], mask.toByteArray()[9]);
//        assertEquals(expected[10], mask.toByteArray()[10]);
//        assertEquals(expected[11], mask.toByteArray()[11]);
//        assertEquals(expected[12], mask.toByteArray()[12]);
//        assertEquals(expected[13], mask.toByteArray()[13]);
//    }
//
//    /**
//     * This helper function is meaty enough that i think it warrants its own
//     * test...
//     */
//    @Test
//    public void testSeqNumsToMaskShort()
//    {
//        int baseSeqNum = 0;
//        List<Integer> expectedSeqNums = Arrays.asList(10, 12, 14);
//
//        BitSet mask = BitSet.valueOf(seqNumsToMask(expectedSeqNums, baseSeqNum));
//        List<Integer> seqNums = FlexFecHeaderReader.getProtectedSeqNums(mask, baseSeqNum);
//        assertEquals(expectedSeqNums, seqNums);
//    }
//
//    @Test
//    public void testSeqNumsToMaskMed()
//    {
//        int baseSeqNum = 0;
//        List<Integer> expectedSeqNums = Arrays.asList(16, 18, 20);
//
//        BitSet mask = BitSet.valueOf(seqNumsToMask(expectedSeqNums, baseSeqNum));
//        List<Integer> seqNums = FlexFecHeaderReader.getProtectedSeqNums(mask, baseSeqNum);
//        assertEquals(expectedSeqNums, seqNums);
//    }
//
//    @Test
//    public void testSeqNumsToMaskLong()
//    {
//        int baseSeqNum = 0;
//        List<Integer> expectedSeqNums = Arrays.asList(48, 50, 52);
//
//        BitSet mask = BitSet.valueOf(seqNumsToMask(expectedSeqNums, baseSeqNum));
//        List<Integer> seqNums = FlexFecHeaderReader.getProtectedSeqNums(mask, baseSeqNum);
//        assertEquals(expectedSeqNums, seqNums);
//    }

    @Test
    public void testReadHeaderShortMask()
    {
        final byte K_BIT_1 = (byte)(1 << 7);
        final int expectedPacketMaskSize = 2;
        final int expectedFecHeaderSize = 20;
        //final byte[] mask = { K_BIT_1 | 0x08, (byte)0x81 };
        List<Integer> protectedSeqNums = Arrays.asList(SN_BASE + 1, SN_BASE + 4, SN_BASE + 5);
        final byte[] mask = seqNumsToMask(protectedSeqNums, SN_BASE);
        // Just double-check that it's the right size
        assertTrue(mask.length == 2);

        final byte[] flexFecData = {
            NO_R_BIT | NO_F_BIT,    PT_RECOVERY,        LENGTH_RECOVERY[0],     LENGTH_RECOVERY[1],
            TS_RECOVERY[0],         TS_RECOVERY[1],     TS_RECOVERY[2],         TS_RECOVERY[3],
            SSRC_COUNT,             RESERVED_BITS,      RESERVED_BITS,          RESERVED_BITS,
            PROTECTED_SSRC[0],      PROTECTED_SSRC[1],  PROTECTED_SSRC[2],      PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],       SN_BASE_BYTES[1],   mask[0],                mask[1],
            PAYLOAD_BITS,           PAYLOAD_BITS,       PAYLOAD_BITS,           PAYLOAD_BITS
        };
        FlexFecPacket packet = FlexFecPacket.create(new byte[12], 0, 12);
        assertNotNull(packet);
        FlexFecHeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length);

        assertEquals(RTPUtils.readUint32AsLong(PROTECTED_SSRC, 0), packet.protectedSsrc);
        assertEquals(SN_BASE, packet.seqNumBase);
        //assertTrue(mask.equals(packet.packetMask.toByteArray()));
        assertEquals(packet.flexFecHeaderSizeBytes, expectedFecHeaderSize);
    }

    @Test
    public void testReadHeaderMedMask()
    {
        final byte K_BIT_0 = (byte)(0 << 7);
        final byte K_BIT_1 = (byte)(1 << 7);
        final int expectedPacketMaskSize = 6;
        final int expectedFecHeaderSize = 24;
        final byte[] mask = {
            K_BIT_0 | 0x08, (byte)0x81,
            K_BIT_1 | 0x01, 0x02, 0x03, 0x04
        };


        final byte[] flexFecData = {
            NO_R_BIT | NO_F_BIT,    PT_RECOVERY,        LENGTH_RECOVERY[0],     LENGTH_RECOVERY[1],
            TS_RECOVERY[0],         TS_RECOVERY[1],     TS_RECOVERY[2],         TS_RECOVERY[3],
            SSRC_COUNT,             RESERVED_BITS,      RESERVED_BITS,          RESERVED_BITS,
            PROTECTED_SSRC[0],      PROTECTED_SSRC[1],  PROTECTED_SSRC[2],      PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],       SN_BASE_BYTES[1],   mask[0],                mask[1],
            mask[2],                mask[3],            mask[4],                mask[5],
            PAYLOAD_BITS,           PAYLOAD_BITS,       PAYLOAD_BITS,           PAYLOAD_BITS
        };
        FlexFecPacket packet = FlexFecPacket.create(new byte[12], 0, 12);
        assertNotNull(packet);
        FlexFecHeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length);

        assertEquals(RTPUtils.readUint32AsLong(PROTECTED_SSRC, 0), packet.protectedSsrc);
        assertEquals(SN_BASE, packet.seqNumBase);
        //assertTrue(mask.equals(packet.packetMask.toByteArray()));
        assertEquals(packet.flexFecHeaderSizeBytes, expectedFecHeaderSize);
    }

    @Test
    public void testReadHeaderLongMask()
    {
        final byte K_BIT_0 = (byte)(0 << 7);
        final byte K_BIT_1 = (byte)(1 << 7);
        final int expectedPacketMaskSize = 14;
        final int expectedFecHeaderSize = 32;
        final byte[] mask = {
            K_BIT_0 | 0x08, (byte)0x81,
            K_BIT_0 | 0x01, 0x02, 0x03, 0x04,
            K_BIT_1 | 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c
        };


        final byte[] flexFecData = {
            NO_R_BIT | NO_F_BIT,    PT_RECOVERY,        LENGTH_RECOVERY[0],     LENGTH_RECOVERY[1],
            TS_RECOVERY[0],         TS_RECOVERY[1],     TS_RECOVERY[2],         TS_RECOVERY[3],
            SSRC_COUNT,             RESERVED_BITS,      RESERVED_BITS,          RESERVED_BITS,
            PROTECTED_SSRC[0],      PROTECTED_SSRC[1],  PROTECTED_SSRC[2],      PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],       SN_BASE_BYTES[1],   mask[0],                mask[1],
            mask[2],                mask[3],            mask[4],                mask[5],
            mask[6],                mask[7],            mask[8],                mask[9],
            mask[10],               mask[11],           mask[12],                mask[13],
            PAYLOAD_BITS,           PAYLOAD_BITS,       PAYLOAD_BITS,           PAYLOAD_BITS
        };
        FlexFecPacket packet = FlexFecPacket.create(new byte[12], 0, 12);
        assertNotNull(packet);
        FlexFecHeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length);

        assertEquals(RTPUtils.readUint32AsLong(PROTECTED_SSRC, 0), packet.protectedSsrc);
        assertEquals(SN_BASE, packet.seqNumBase);
        //assertTrue(mask.equals(packet.packetMask.toByteArray()));
        assertEquals(packet.flexFecHeaderSizeBytes, expectedFecHeaderSize);
    }
}