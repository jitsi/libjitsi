package org.jitsi.impl.neomedia.transform.fec;

import org.jitsi.util.*;
import org.junit.*;

import java.nio.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by bbaldino on 11/10/17.
 */
public class FlexFec03HeaderReaderTest
{
    private static final byte R_BIT = (byte)(1 << 7);
    private static final byte NO_R_BIT = 0 << 7;
    private static final byte F_BIT = 1 << 6;
    private static final byte NO_F_BIT = 0 << 6;
    private static final byte PT_RECOVERY = 127;
    private static final byte[] LENGTH_RECOVERY = { (byte)0xab, (byte)0xcd };
    private static final byte[] TS_RECOVERY = { 0x01, 0x023, 0x45, 0x67 };
    private static final byte SSRC_COUNT = 1;
    private static final byte RESERVED_BITS = 0x00;
    private static final byte[] PROTECTED_SSRC = { 0x11, 0x22, 0x33, 0x44 };
    private static final byte K_BIT_1 = (byte)(1 << 7);
    private static final byte K_BIT_0 = (byte)(0 << 7);
    private static final int SN_BASE = 43407;
    private static byte[] SN_BASE_BYTES;
    private static final byte PAYLOAD_BITS = 0x00;

    // The amount of bits dedicated to protecting sequence numbers in the masks
    private static final int NUM_BITS_IN_SMALL_MASK = 15;
    private static final int NUM_BITS_IN_MED_MASK =
        NUM_BITS_IN_SMALL_MASK + 31;
    private static final int NUM_BITS_IN_LARGE_MASK =
        NUM_BITS_IN_MED_MASK + 63;

    private static byte[] snBaseToBytes(int seqNumBase)
    {
        return ByteBuffer.allocate(4).putInt(seqNumBase).array();
    }

    static {
        byte[] snBytes = snBaseToBytes(SN_BASE);
        SN_BASE_BYTES = new byte[]{ snBytes[2], snBytes[3] };
    }

    @Test
    public void testTooSmallPacketIsRejected()
    {
        byte[] buf = new byte[1500];
        FlexFec03Packet packet = FlexFec03Packet.create(buf, 0, buf.length);
        byte[] tooSmallPacket = new byte[12];
        assertFalse(FlexFec03HeaderReader.readFlexFecHeader(packet, tooSmallPacket, 0, tooSmallPacket.length));
    }

    @Test
    public void testRetransmissionIsRejected()
    {
        byte[] mask0 = { 0x00, 0x00 };
        final byte[] flexFecData = {
            R_BIT | NO_F_BIT,       PT_RECOVERY,        LENGTH_RECOVERY[0],         LENGTH_RECOVERY[1],
            TS_RECOVERY[0],         TS_RECOVERY[1],     TS_RECOVERY[2],             TS_RECOVERY[3],
            SSRC_COUNT,             RESERVED_BITS,      RESERVED_BITS,              RESERVED_BITS,
            PROTECTED_SSRC[0],      PROTECTED_SSRC[1],  PROTECTED_SSRC[2],          PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],       SN_BASE_BYTES[1],   (byte)(K_BIT_1 | mask0[0]), mask0[1],
            PAYLOAD_BITS,           PAYLOAD_BITS,       PAYLOAD_BITS,               PAYLOAD_BITS
        };

        byte[] buf = new byte[1500];
        FlexFec03Packet packet = FlexFec03Packet.create(buf, 0, buf.length);
        assertFalse(FlexFec03HeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length));
    }


    @Test
    public void testNonFlexibleMaskTypeIsRejected()
    {
        byte[] mask0 = { 0x00, 0x00 };
        final byte[] flexFecData = {
            NO_R_BIT | F_BIT,       PT_RECOVERY,        LENGTH_RECOVERY[0],         LENGTH_RECOVERY[1],
            TS_RECOVERY[0],         TS_RECOVERY[1],     TS_RECOVERY[2],             TS_RECOVERY[3],
            SSRC_COUNT,             RESERVED_BITS,      RESERVED_BITS,              RESERVED_BITS,
            PROTECTED_SSRC[0],      PROTECTED_SSRC[1],  PROTECTED_SSRC[2],          PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],       SN_BASE_BYTES[1],   (byte)(K_BIT_1 | mask0[0]), mask0[1],
            PAYLOAD_BITS,           PAYLOAD_BITS,       PAYLOAD_BITS,               PAYLOAD_BITS
        };

        byte[] buf = new byte[1500];
        FlexFec03Packet packet = FlexFec03Packet.create(buf, 0, buf.length);
        assertFalse(FlexFec03HeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length));
    }

    @Test
    public void testMoreThanOneProtectedSsrcIsRejected()
    {
        final byte BAD_SSRC_COUNT = 2;
        byte[] mask0 = { 0x00, 0x00 };
        final byte[] flexFecData = {
            NO_R_BIT | NO_F_BIT,    PT_RECOVERY,        LENGTH_RECOVERY[0],         LENGTH_RECOVERY[1],
            TS_RECOVERY[0],         TS_RECOVERY[1],     TS_RECOVERY[2],             TS_RECOVERY[3],
            BAD_SSRC_COUNT,         RESERVED_BITS,      RESERVED_BITS,              RESERVED_BITS,
            PROTECTED_SSRC[0],      PROTECTED_SSRC[1],  PROTECTED_SSRC[2],          PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],       SN_BASE_BYTES[1],   (byte)(K_BIT_1 | mask0[0]), mask0[1],
            PAYLOAD_BITS,           PAYLOAD_BITS,       PAYLOAD_BITS,               PAYLOAD_BITS
        };

        byte[] buf = new byte[1500];
        FlexFec03Packet packet = FlexFec03Packet.create(buf, 0, buf.length);
        assertFalse(FlexFec03HeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length));
    }

    @Test
    public void testParsesSmallMask()
    {
        byte[] mask0 = { 0x7F, (byte)0xFF }; // All possible packets in the small mask protected
        final byte[] flexFecData = {
            NO_R_BIT | NO_F_BIT,    PT_RECOVERY,        LENGTH_RECOVERY[0],         LENGTH_RECOVERY[1],
            TS_RECOVERY[0],         TS_RECOVERY[1],     TS_RECOVERY[2],             TS_RECOVERY[3],
            SSRC_COUNT,             RESERVED_BITS,      RESERVED_BITS,              RESERVED_BITS,
            PROTECTED_SSRC[0],      PROTECTED_SSRC[1],  PROTECTED_SSRC[2],          PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],       SN_BASE_BYTES[1],   (byte)(K_BIT_1 | mask0[0]), mask0[1],
            PAYLOAD_BITS,           PAYLOAD_BITS,       PAYLOAD_BITS,               PAYLOAD_BITS
        };

        byte[] buf = new byte[1500];
        FlexFec03Packet packet = FlexFec03Packet.create(buf, 0, buf.length);
        assertTrue(FlexFec03HeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length));
        assertEquals(NUM_BITS_IN_SMALL_MASK, packet.getProtectedSequenceNumbers().size());
        for (int i = 0; i < NUM_BITS_IN_SMALL_MASK; ++i)
        {
            assertEquals(SN_BASE + i, (long)packet.getProtectedSequenceNumbers().get(i));
        }
    }

    @Test
    public void testParsesMedMask()
    {
        byte[] mask0 = { 0x7F, (byte)0xFF }; // All possible packets in the small mask protected
        byte[] mask1 = { 0x7F, (byte)0xFF, (byte)0xFF, (byte)0xFF }; // All possible packets in the med mask protected
        final byte[] flexFecData = {
            NO_R_BIT | NO_F_BIT,        PT_RECOVERY,        LENGTH_RECOVERY[0],         LENGTH_RECOVERY[1],
            TS_RECOVERY[0],             TS_RECOVERY[1],     TS_RECOVERY[2],             TS_RECOVERY[3],
            SSRC_COUNT,                 RESERVED_BITS,      RESERVED_BITS,              RESERVED_BITS,
            PROTECTED_SSRC[0],          PROTECTED_SSRC[1],  PROTECTED_SSRC[2],          PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],           SN_BASE_BYTES[1],   (byte)(K_BIT_0 | mask0[0]), mask0[1],
            (byte)(K_BIT_1 | mask1[0]), mask1[1],           mask1[2],                   mask1[3],
            PAYLOAD_BITS,               PAYLOAD_BITS,       PAYLOAD_BITS,               PAYLOAD_BITS
        };

        byte[] buf = new byte[1500];
        FlexFec03Packet packet = FlexFec03Packet.create(buf, 0, buf.length);
        assertTrue(FlexFec03HeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length));
        assertEquals(NUM_BITS_IN_MED_MASK, packet.getProtectedSequenceNumbers().size());
        for (int i = 0; i < NUM_BITS_IN_MED_MASK; ++i)
        {
            assertEquals(SN_BASE + i, (long)packet.getProtectedSequenceNumbers().get(i));
        }
    }

    @Test
    public void testParsesLargeMask()
    {
        byte[] mask0 = { 0x7F, (byte)0xFF }; // All possible packets in the small mask protected
        byte[] mask1 = { 0x7F, (byte)0xFF, (byte)0xFF, (byte)0xFF }; // All possible packets in the med mask protected
        byte[] mask2 = {
            0x7F,       (byte)0xFF, (byte)0xFF, (byte)0xFF,
            (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF
        }; // All possible packets in the med mask protected
        final byte[] flexFecData = {
            NO_R_BIT | NO_F_BIT,        PT_RECOVERY,        LENGTH_RECOVERY[0],         LENGTH_RECOVERY[1],
            TS_RECOVERY[0],             TS_RECOVERY[1],     TS_RECOVERY[2],             TS_RECOVERY[3],
            SSRC_COUNT,                 RESERVED_BITS,      RESERVED_BITS,              RESERVED_BITS,
            PROTECTED_SSRC[0],          PROTECTED_SSRC[1],  PROTECTED_SSRC[2],          PROTECTED_SSRC[3],
            SN_BASE_BYTES[0],           SN_BASE_BYTES[1],   (byte)(K_BIT_0 | mask0[0]), mask0[1],
            (byte)(K_BIT_0 | mask1[0]), mask1[1],           mask1[2],                   mask1[3],
            (byte)(K_BIT_1 | mask2[0]), mask2[1],           mask2[2],                   mask2[3],
            mask2[4],                   mask2[5],           mask2[6],                   mask2[7],
            PAYLOAD_BITS,               PAYLOAD_BITS,       PAYLOAD_BITS,               PAYLOAD_BITS
        };

        byte[] buf = new byte[1500];
        FlexFec03Packet packet = FlexFec03Packet.create(buf, 0, buf.length);
        assertTrue(FlexFec03HeaderReader.readFlexFecHeader(packet, flexFecData, 0, flexFecData.length));
        assertEquals(NUM_BITS_IN_LARGE_MASK, packet.getProtectedSequenceNumbers().size());
        for (int i = 0; i < NUM_BITS_IN_LARGE_MASK; ++i)
        {
            assertEquals(SN_BASE + i, (long)packet.getProtectedSequenceNumbers().get(i));
        }
    }
}