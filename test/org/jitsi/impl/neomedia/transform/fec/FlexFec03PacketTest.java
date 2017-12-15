package org.jitsi.impl.neomedia.transform.fec;

import io.pkts.*;
import io.pkts.packet.*;
import io.pkts.protocol.*;
import org.jitsi.service.neomedia.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;

public class FlexFec03PacketTest
{
    private static class FecPacketFinder
        implements PacketHandler
    {
        RawPacket fecPacket = null;
        int fecPt;
        FecPacketFinder(int fecPt)
        {
            this.fecPt = fecPt;
        }

        @Override
        public boolean nextPacket(final Packet pkt)
            throws
            IOException
        {
            if (pkt.hasProtocol(Protocol.UDP))
            {
                UDPPacket udpPacket = (UDPPacket) pkt.getPacket(Protocol.UDP);
                RawPacket packet = new RawPacket(udpPacket.getPayload().getArray(),
                    0, udpPacket.getPayload().getArray().length);

                if (packet.getPayloadType() == fecPt)
                {
                    fecPacket = packet;
                    return false;
                }
            }
            return true;
        }
    }

    // Describes the url path to a capture with a SINGLE flexfec03 packet, and its
    // expected values
    private static class FlexFec03PacketCaptureInfo
    {
        private static final String path = "test/org/jitsi/impl/neomedia/transform/fec/resources/chrome_single_flexfec03_packet.pcap";
        private static final long expectedSsrc = 3324210212L;
        private static final long expectedProtectedSsrc = 3280979721L;
        private static final int expectedFlexFecHeaderOffset = 20;
        private static final int expectedFlexFecHeaderSize = 32;
        private static final int expectedFlexFecPayloadLength = 1137;
        private static final List<Integer> expectedProtectedSequenceNumbers = Arrays.asList(
            33279, 33282, 33286, 33287, 33290, 33293, 33298, 33301,
            33302, 33306, 33311, 33314, 33315, 33318, 33323, 33326
        );

        public static void verify(FlexFec03Packet readPacket, int addedOffset)
        {
            assertEquals(expectedSsrc, readPacket.getSSRCAsLong());
            assertEquals(expectedProtectedSsrc, readPacket.getProtectedSsrc());
            assertEquals(expectedFlexFecHeaderOffset + addedOffset, readPacket.getFlexFecHeaderOffset());
            assertEquals(expectedFlexFecHeaderSize, readPacket.getFlexFecHeaderSize());
            assertEquals(expectedFlexFecPayloadLength, readPacket.getFlexFecPayloadLength());
            assertEquals(expectedProtectedSequenceNumbers.size(), readPacket.getProtectedSequenceNumbers().size());
            for (int i = 0; i < expectedProtectedSequenceNumbers.size(); ++i)
            {
                assertTrue(readPacket.getProtectedSequenceNumbers().contains(expectedProtectedSequenceNumbers.get(i)));
            }
        }
    }

    @Test
    public void testCreate()
        throws
        IOException
    {
        Pcap pcap = Pcap.openStream(FlexFec03PacketCaptureInfo.path);

        final int flexFecPt = 107;

        FecPacketFinder fecPacketFinder = new FecPacketFinder(flexFecPt);

        pcap.loop(fecPacketFinder);

        assertNotNull(fecPacketFinder.fecPacket);
        FlexFec03Packet fecPacket = FlexFec03Packet.create(fecPacketFinder.fecPacket);
        assertNotNull(fecPacket);
        FlexFec03PacketCaptureInfo.verify(fecPacket, 0);
    }

    @Test
    public void testCreateWithOffset()
        throws
        IOException
    {
        Pcap pcap = Pcap.openStream(FlexFec03PacketCaptureInfo.path);
        int addedOffset = 50;

        final int flexFecPt = 107;

        FecPacketFinder fecPacketFinder = new FecPacketFinder(flexFecPt);

        pcap.loop(fecPacketFinder);

        assertNotNull(fecPacketFinder.fecPacket);
        // Re-make the fec packet, but this time using a buffer with an offset
        byte[] offsetBuf = new byte[fecPacketFinder.fecPacket.getLength() + addedOffset];
        System.arraycopy(
            fecPacketFinder.fecPacket.getBuffer(), fecPacketFinder.fecPacket.getOffset(),
            offsetBuf, addedOffset,
            fecPacketFinder.fecPacket.getLength());

        FlexFec03Packet fecPacket = FlexFec03Packet.create(offsetBuf, addedOffset, fecPacketFinder.fecPacket.getLength());
        assertNotNull(fecPacket);
        FlexFec03PacketCaptureInfo.verify(fecPacket, addedOffset);
    }
}