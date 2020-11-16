package org.jitsi.impl.neomedia.codec.video.h264;

import org.junit.Before;
import org.junit.Test;

import static org.jitsi.impl.neomedia.codec.video.h264.H264.kNalUSize;
import static org.jitsi.impl.neomedia.codec.video.h264.H264.kStapA;
import static org.jitsi.impl.neomedia.codec.video.h264.H264.verifyStapANaluLengths;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class H264Test
{
    private void populateNalUnits(byte[] data, int[] naluSizes, int offset)
    {
        for (int naluSize : naluSizes)
        {
            data[offset] = (byte) ((naluSize & 0xff00) >> 8);
            data[offset+1] = (byte) (naluSize & 0xff);
            offset += naluSize + kNalUSize;
        }
    }

    private int packetLength(int[] naluSizes)
    {
        int accumulator = 0;
        for (int nalSize : naluSizes)
        {
            accumulator += nalSize + kNalUSize;
        }
        return accumulator;
    }

    private byte[] buildStapA(int[] naluSizes, int offset, int length)
    {
        byte[] data = new byte[offset + length];
        populateNalUnits(data, naluSizes, offset);
        return data;
    }

    private boolean buildAndVerifyStapA(int[] naluSizes, int offset)
    {
        return buildAndVerifyStapA(naluSizes, offset, packetLength(naluSizes));
    }

    private boolean buildAndVerifyStapA(int[] naluSizes, int offset, int length)
    {
        int realPacketLength = packetLength(naluSizes);
        byte[] data = buildStapA(naluSizes, offset, realPacketLength);
        return verifyStapANaluLengths(data, offset, length);
    }

    private static final int kMaxNalSize = 0xffff;

    private DePacketizer dePacketizer;

    @Before
    public void setup()
    {
        dePacketizer = new DePacketizer();
    }

    @Test
    public void getUint16_BuildsUint16()
    {
        assertEquals(0x0, H264.getUint16(new byte[] {0x0, 0x0}, 0));
        assertEquals(0xffff, H264.getUint16(new byte[] {(byte) 0xff, (byte) 0xff}, 0));
        assertEquals(0x1234, H264.getUint16(new byte[] {0x12, 0x34}, 0));
    }

    @Test
    public void getUint16_ObeysOffset()
    {
        assertEquals(0x1234, H264.getUint16(new byte[] {(byte) 0xff, 0x12, 0x34}, 1));
    }

    @Test
    public void getUint16_MostSignificantBitSetInFirstByte()
    {
        assertEquals(0xff00, H264.getUint16(new byte[] {(byte) 0xff, 0}, 0));
    }

    @Test
    public void getUint16_MostSignificantBitSetInSecondByte()
    {
        assertEquals(0xff, H264.getUint16(new byte[] { 0, (byte) 0xff}, 0));
    }

    @Test
    public void verifyStapANaluLengths_EmptyData_ShouldPass()
    {
        assertTrue(verifyStapANaluLengths(new byte[] {kStapA}, 1, 0));
    }

    @Test
    public void verifyStapANaluLengths_LengthLessThanOneNal_ShouldFail()
    {
        assertFalse(verifyStapANaluLengths(new byte[] {kStapA}, 1, 1));
        assertFalse(verifyStapANaluLengths(new byte[] {kStapA}, 1, 2));
    }

    @Test
    public void verifyStapANaluLengths_PacketTooShort_ShouldFail()
    {
        assertFalse(verifyStapANaluLengths(new byte[] {kStapA, 0, 1 }, 1, 3));
    }

    @Test
    public void verifyStapANaluLengths_ValidPacketWithOneNal_ShouldPass()
    {
        assertTrue(verifyStapANaluLengths(new byte[] {kStapA, 0, 1, (byte)0xfa }, 1, 3));
    }

    @Test
    public void verifyStapANaluLengths_OneMinSizedNal_ShouldPass()
    {
        assertTrue(buildAndVerifyStapA(new int[]{ 0 }, 1));
    }

    @Test
    public void verifyStapANaluLengts_OneNal_ShouldPass()
    {
        assertTrue(buildAndVerifyStapA(new int[]{ 10 }, 1));
    }

    @Test
    public void verifyStapANaluLengths_InsufficientLengthForNal_ShouldFail()
    {
        int[] naluSizes = { 10 };
        assertFalse(buildAndVerifyStapA(naluSizes, 1, packetLength(naluSizes) - 1));
    }

    @Test
    public void verifyStapANaluLengths_InsufficentLengthForLargeNal_ShouldFail()
    {
        assertFalse(buildAndVerifyStapA(new int[]{ kMaxNalSize }, 1, 3));
    }

    @Test
    public void verifyStapANaluLengths_MostSignificantBitNalLength_ShouldProperlyHandleSignExtensionWithoutInfiniteLoop()
    {
        assertTrue(buildAndVerifyStapA(new int[]{ kMaxNalSize - 2 }, 1));
    }

    @Test
    public void verifyStapANaluLengths_TwoNal_ShouldPass()
    {
        int[] naluSizes = { 10, 10 };
        assertTrue(buildAndVerifyStapA(naluSizes, 1));
    }

    @Test
    public void verifyStapANaluLengths_TwoNalInsufficientNalHeaderLength_ShouldFail()
    {
        int[] naluSizes = { 10, 10 };
        assertFalse(buildAndVerifyStapA(naluSizes, 1, packetLength(naluSizes) - naluSizes[0] - 1));
    }

    @Test
    public void verifyStapANaluLengths_TwoNalInsufficientLength_ShouldFail()
    {
        int[] naluSizes = { 10, 10 };
        assertFalse(buildAndVerifyStapA(naluSizes, 1, packetLength(naluSizes) - 1));
    }
}
