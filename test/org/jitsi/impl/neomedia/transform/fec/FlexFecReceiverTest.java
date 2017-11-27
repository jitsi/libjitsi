package org.jitsi.impl.neomedia.transform.fec;

import io.pkts.packet.rtp.*;
import io.pkts.protocol.*;
import org.jitsi.service.neomedia.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by bbaldino on 11/15/17.
 */
public class FlexFecReceiverTest
{
    public class FecCaptureReadResult
    {
        Set<FlexFecPacket> flexFecPackets = new HashSet<>();
        Map<Integer, RawPacket> mediaPackets;
    }

    /**
     * Returns the RawPacket whose sequence number matches recoveredPacketSeqNum
     * if it exists in the given RawPacket array
     * @param packets the packets to search
     * @param recoveredPacketSeqNum the sequence number of the desired packet
     * @return the RawPacket in packets whose sequence number matches
     * recoveredPacketSeqNum or null if it isn't present
     */
    private RawPacket getRecoveredPacket(RawPacket[] packets, int recoveredPacketSeqNum)
    {
        for (RawPacket packet : packets)
        {
            if (packet.getSequenceNumber() == recoveredPacketSeqNum)
            {
                return packet;
            }
        }
        return null;
    }

    private void verifyFlexFec(FlexFecPacket flexFecPacket, Map<Integer, RawPacket> mediaPackets)
    {
        List<Integer> protectedSeqNums = flexFecPacket.getProtectedSequenceNumbers();
        List<RawPacket> protectedMediaPackets = new ArrayList<>();
        for (Integer protectedSeqNum : protectedSeqNums)
        {
            protectedMediaPackets.add(mediaPackets.get(protectedSeqNum));
        }
        // We'll treat the first one as the 'lost' packet we'll expect the
        // FlexFecReceiver to recover
        RawPacket lostMediaPacket = protectedMediaPackets.get(0);
        System.out.println("Withholding media packet " + lostMediaPacket.getSequenceNumber());

        FlexFecReceiver receiver =
            new FlexFecReceiver(lostMediaPacket.getSSRCAsLong(),
                (byte)107);

        receiver.reverseTransform(new RawPacket[] {flexFecPacket});
        RawPacket[] packets = null;
        for (int i = 1; i < protectedMediaPackets.size(); ++i)
        {
            packets = receiver.reverseTransform(new RawPacket[] { protectedMediaPackets.get(i) });
        }
        // The last time we called reverseTransform, we should've gotten 2
        // packets back (the one we put in and the one that was recovered)
        RawPacket recoveredPacket = getRecoveredPacket(packets, lostMediaPacket.getSequenceNumber());
        assertNotNull(recoveredPacket);
        assertEquals(lostMediaPacket.getLength(), recoveredPacket.getLength());
        for (int i = 0; i < lostMediaPacket.getLength(); ++i)
        {
            if (lostMediaPacket.getBuffer()[i] != recoveredPacket.getBuffer()[i])
            {
                System.err.println("Expected recoveredPacket[" + i + "]" +
                    "(" + recoveredPacket.getBuffer()[i] + " to equal " +
                    "lostMediaPacket[" + i + "](" +
                    lostMediaPacket.getBuffer()[i] + ")");
                assertTrue(false);
            }
        }
    }

    /**
     * This test will read from a pcap file that contains both video and
     * flexfec packets.  the capture MUST contain at least ONE fec packet for
     * which EVERY media packet it covers is also in the capture for this
     * test to do anything interesting.
     *
     * The test will read until it has found 1) a fec packet and 2) all the
     * media packets which it protects.  It will then feed the fec packets
     * and all the media packets (except for 1) into the FlexFecReceiver
     * to verify it can correctly recover the withheld packet.
     * @throws Exception
     */
    @Test
    public void testReverseTransform()
        throws
        Exception
    {
        //FecPacketReader reader = new FecPacketReader("/Users/bbaldino/Desktop/chrome_single_flexfec_packet.pcap");
        FecPacketReader reader = new FecPacketReader("/Users/bbaldino/Desktop/chrome_flexfec_and_video_capture.pcap");
        int videoPt = 98;
        int flexFecPt = 107;

        FecCaptureReadResult fecCaptureReadResult = new FecCaptureReadResult();

        reader.run(videoPt, flexFecPt, fecCaptureReadResult);

        if (fecCaptureReadResult.flexFecPackets.isEmpty())
        {
            System.out.println("Unable to find a fec packet with all of its corresponding media packets");
            assertTrue(false);
        }

        for (FlexFecPacket flexFecPacket : fecCaptureReadResult.flexFecPackets)
        {
            verifyFlexFec(flexFecPacket, fecCaptureReadResult.mediaPackets);
        }

    }
}