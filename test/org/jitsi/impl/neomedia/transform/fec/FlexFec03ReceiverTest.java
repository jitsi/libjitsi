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

import org.easymock.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.junit.*;
import org.junit.runner.*;
import org.powermock.api.easymock.*;
import org.powermock.core.classloader.annotations.*;
import org.powermock.modules.junit4.*;

import java.util.*;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;
import static org.powermock.api.easymock.PowerMock.*;

/**
 * @author bbaldino
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(LibJitsi.class)
public class FlexFec03ReceiverTest
{
    private ConfigurationService mockConfigurationService;

    @Before
    public void setUp()
    {
        PowerMock.mockStatic(LibJitsi.class);
        mockConfigurationService = PowerMock.createMock(ConfigurationService.class);
        expect(mockConfigurationService.getInt(EasyMock.anyString(), EasyMock.anyInt())).andAnswer(new IAnswer<Integer>() {
            public Integer answer()
            {
                return (Integer)EasyMock.getCurrentArguments()[1];
            }

        }).anyTimes();
        expect(LibJitsi.getConfigurationService()).andReturn(mockConfigurationService).anyTimes();
    }

    @After
    public void tearDown()
    {
        verifyAll();
    }

    public class FecCaptureReadResult
    {
        Set<FlexFec03Packet> flexFecPackets = new HashSet<>();
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

    /**
     * Given a flexfec packet, a COMPLETE set of its protected media packets
     * and the sequence number of the protected packet to 'drop', make sure the
     * dropped packet can be recreated correctly
     * @param flexFecPacket
     * @param missingSeqNum
     * @param protectedMediaPackets
     */
    private void verifyFlexFec(FlexFec03Packet flexFecPacket, int missingSeqNum, List<RawPacket> protectedMediaPackets)
    {
        FlexFec03Receiver receiver =
            new FlexFec03Receiver(flexFecPacket.getProtectedSsrc(),
                (byte)107);

        RawPacket lostMediaPacket = null;
        for (RawPacket proectedMediaPacket : protectedMediaPackets)
        {
            if (proectedMediaPacket.getSequenceNumber() == missingSeqNum)
            {
                lostMediaPacket = proectedMediaPacket;
                continue;
            }
            receiver.reverseTransform(new RawPacket[] { proectedMediaPacket });
        }
        RawPacket[] packets = receiver.reverseTransform(new RawPacket[] {flexFecPacket});

        // The last time we called reverseTransform, we should've gotten 2
        // packets back (the one we put in and the one that was recovered)
        RawPacket recoveredPacket = getRecoveredPacket(packets, lostMediaPacket.getSequenceNumber());
        assertNotNull(recoveredPacket);
        assertEquals(lostMediaPacket.getLength(), recoveredPacket.getLength());
        for (int i = 0; i < lostMediaPacket.getLength(); ++i)
        {
            if (lostMediaPacket.getBuffer()[lostMediaPacket.getOffset() + i] != recoveredPacket.getBuffer()[recoveredPacket.getOffset() + i])
            {
                fail("Expected recoveredPacket[" + i + "]" +
                    "(" + recoveredPacket.getBuffer()[i] + " to equal " +
                    "lostMediaPacket[" + i + "](" +
                    lostMediaPacket.getBuffer()[i] + ")");
            }
        }
    }

    /**
     * For a given flexFecPacket and a map of media packets, verify each one of
     * the protected media packets (one by one) can be recovered if dropped
     * @param flexFecPacket
     * @param mediaPackets
     */
    private void verifyFlexFec(FlexFec03Packet flexFecPacket, Map<Integer, RawPacket> mediaPackets)
    {
        List<Integer> protectedSeqNums = flexFecPacket.getProtectedSequenceNumbers();
        List<RawPacket> protectedMediaPackets = new ArrayList<>();
        for (Integer protectedSeqNum : protectedSeqNums)
        {
            protectedMediaPackets.add(mediaPackets.get(protectedSeqNum));
        }
        // We'll test multiple times, each time 'dropping' a different protected
        // packet
        for (RawPacket p : protectedMediaPackets)
        {
            verifyFlexFec(flexFecPacket, p.getSequenceNumber(), protectedMediaPackets);
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
     * and all the media packets (except for 1) into the FlexFec03Receiver
     * to verify it can correctly recover the withheld packet.
     * @throws Exception
     */
    @Test
    public void testReverseTransform()
        throws
        Exception
    {
        replayAll();
        FecPacketReader reader = new FecPacketReader("test/org/jitsi/impl/neomedia/transform/fec/resources/chrome_flexfec_and_video_capture.pcap");

        int videoPt = 98;
        int flexFecPt = 107;

        FecCaptureReadResult fecCaptureReadResult = new FecCaptureReadResult();

        reader.run(videoPt, flexFecPt, fecCaptureReadResult);

        if (fecCaptureReadResult.flexFecPackets.isEmpty())
        {
            fail("Unable to find a fec packet with all of its corresponding media packets");
        }

        for (FlexFec03Packet flexFecPacket : fecCaptureReadResult.flexFecPackets)
        {
            verifyFlexFec(flexFecPacket, fecCaptureReadResult.mediaPackets);
        }
    }

    private static void addOffsetToPacket(int offset, RawPacket packet)
    {
        byte[] newBuf = new byte[packet.getLength() + offset];
        System.arraycopy(
            packet.getBuffer(), packet.getOffset(),
            newBuf, offset,
            packet.getLength());
        packet.setBuffer(newBuf);
        packet.setOffset(offset);
    }

    private static void addOffsetToPackets(int offset, FecCaptureReadResult fecCaptureReadResult)
    {
        for (FlexFec03Packet fecPacket : fecCaptureReadResult.flexFecPackets)
        {
            addOffsetToPacket(offset, fecPacket);
        }

        for (RawPacket packet : fecCaptureReadResult.mediaPackets.values())
        {
            addOffsetToPacket(offset, packet);
        }
    }

    /**
     * Same as the above test, but re-create packets so that the
     * data in the buffer starts at a non-zero offset
     * @throws Exception
     */
    @Test
    public void testReverseTransformWithOffset()
        throws
        Exception
    {
        replayAll();
        FecPacketReader reader = new FecPacketReader("test/org/jitsi/impl/neomedia/transform/fec/resources/chrome_flexfec_and_video_capture.pcap");

        int videoPt = 98;
        int flexFecPt = 107;

        FecCaptureReadResult fecCaptureReadResult = new FecCaptureReadResult();

        reader.run(videoPt, flexFecPt, fecCaptureReadResult);

        if (fecCaptureReadResult.flexFecPackets.isEmpty())
        {
            fail("Unable to find a fec packet with all of its corresponding media packets");
        }

        addOffsetToPackets(50, fecCaptureReadResult);

        for (FlexFec03Packet flexFecPacket : fecCaptureReadResult.flexFecPackets)
        {
            verifyFlexFec(flexFecPacket, fecCaptureReadResult.mediaPackets);
        }
    }
}