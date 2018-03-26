/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.jitsi.impl.neomedia.rtcp;

import org.jitsi.service.neomedia.*;
import org.junit.*;
import org.jitsi.util.*;

import static org.junit.Assert.*;

/**
 * @author Boris Grozev
 */
public class RTCPTCCPacketTest
{
    /**
     * The FCI of a transport feedback packet.
     */
    private static final byte[] fci = new byte[]{
        // base=4, pkt status count=0x1729=5929
        (byte) 0x00,(byte) 0x04,(byte) 0x17,(byte) 0x29,
        // ref time=0x298710, fbPktCount=1
        (byte) 0x29,(byte) 0x87,(byte) 0x10,(byte) 0x01,

        // Chunks:
        // vector, 1-bit symbols, 1xR + 13xNR, 14 pkts (1 received)
        (byte) 0xa0,(byte) 0x00,
        // vector, 1-bit symbols, 1xR + 13xNR, 14 pkts (1 received)
        (byte) 0xa0,(byte) 0x00,
        // RLE, not received: 5886
        (byte) 0x16,(byte) 0xfe,
        // vector, 2-bit symbols, 1x large delta + 6x small delta, 7 packets
        // (7 received)
        (byte) 0xe5,(byte) 0x55,
        // vector, 1-bit symbols, 3xR + 2NR + 1R + 1NR + 1R [packets over, 6 remaining 0 bits]
        // (5 received)
        (byte) 0xb9,(byte) 0x40,

        // deltas: Sx2, L, Sx11 (15 bytes)
        (byte) 0x2c,(byte) 0x78,
        // the large one
        (byte) 0xff,(byte) 0x64,
        (byte) 0x04,(byte) 0x04,(byte) 0x00,(byte) 0x00,
        (byte) 0x04,(byte) 0x00,(byte) 0x04,(byte) 0x04,
        (byte) 0x00,(byte) 0x1c, (byte) 0x34
    };

    @Test
    public void parse()
        throws Exception
    {
        // Note that this excludes packets reported as not received.
        RTCPTCCPacket.PacketMap packetMap
            = RTCPTCCPacket.getPacketsFromFci(new ByteArrayBufferImpl(fci));

        // Values from the packet defined above
        int received = 14;
        int notReceived = 13 + 13 + 5886 + 3;
        int base = 4;

        assertEquals(received, packetMap.size());
        assertEquals(base, (int) packetMap.firstKey());
        assertEquals(
            base + received + notReceived - 1,
            (int) packetMap.lastKey());

        assertEquals((0x298710L << 8) + 0x2c, // ref time + first delta
                     (long) packetMap.firstEntry().getValue());
    }

    @Test
    public void parseWithNR()
        throws Exception
    {
        // Note that this excludes packets reported as not received.
        RTCPTCCPacket.PacketMap packetMap
            = RTCPTCCPacket.getPacketsFromFci(
                new ByteArrayBufferImpl(fci),
                true /* includeNotReceived */);

        // Values from the packet defined above
        int received = 14;
        int notReceived = 13 + 13 + 5886 + 3;
        int base = 4;

        assertEquals(received + notReceived, packetMap.size());
        assertEquals(base, (int) packetMap.firstKey());
        assertEquals(
            base + received + notReceived - 1,
            (int) packetMap.lastKey());

        assertEquals((0x298710L << 8) + 0x2c, // ref time + first delta
                     (long) packetMap.firstEntry().getValue());
    }

    @Test
    public void createAndParse()
        throws Exception
    {
        long now = 1489968000021L;
        RTCPTCCPacket.PacketMap before = new RTCPTCCPacket.PacketMap();
        before.put(120, now);
        before.put(121, now + 1);
        before.put(122, now + 1);
        before.put(123, now + 20);
        before.put(124, now + 30);
        before.put(125, now + 30);
        before.put(126, now + 30);
        before.put(127, now + 30);
        before.put(128, now + 30);
        before.put(129, now + 30);
        before.put(130, now + 30);
        before.put(138, now + 30);

        int fbPacketCount = 17;
        int first = 120;
        int last = 138;
        int received = 12;

        RTCPTCCPacket packet
            = new RTCPTCCPacket(
                0, 0, before, (byte) fbPacketCount, new DiagnosticContext());

        RTCPTCCPacket.PacketMap after
            = RTCPTCCPacket.getPacketsFromFci(new ByteArrayBufferImpl(packet.fci));

        assertEquals(received, after.size());
        assertEquals(first, (int) after.firstKey());
        assertEquals(last, (int) after.lastKey());
        assertEquals(fbPacketCount, packet.getFbPacketCount());
    }

    @Test
    public void createAndParseWithNR()
        throws Exception
    {
        long now = 1489968000021L;
        RTCPTCCPacket.PacketMap before = new RTCPTCCPacket.PacketMap();
        before.put(120, now);
        before.put(121, now + 1);
        before.put(122, now + 1);
        before.put(123, now + 20);
        before.put(124, now + 30);
        before.put(125, now + 30);
        before.put(126, now + 30);
        before.put(127, now + 30);
        before.put(128, now + 30);
        before.put(129, now + 30);
        before.put(130, now + 30);
        before.put(138, now + 30);

        int fbPacketCount = 17;
        int first = 120;
        int last = 138;

        RTCPTCCPacket packet
            = new RTCPTCCPacket(
                0, 0, before, (byte) fbPacketCount, new DiagnosticContext());

        RTCPTCCPacket.PacketMap after
            = RTCPTCCPacket.getPacketsFromFci(
                new ByteArrayBufferImpl(packet.fci),
                true /* includeNotReceived */);

        assertEquals(last - first + 1, after.size());
        assertEquals(first, (int) after.firstKey());
        assertEquals(last, (int) after.lastKey());
        assertEquals(fbPacketCount, packet.getFbPacketCount());
    }

    @Test
    public void createAndParse2()
        throws Exception
    {
        long nowMs = 1515520570364L;
        RTCPTCCPacket.PacketMap before = new RTCPTCCPacket.PacketMap();
        before.put(1268, nowMs);
        before.put(1269, nowMs + 7);
        before.put(1270, nowMs + 7);
        before.put(1271, nowMs + 12);
        before.put(1272, nowMs + 18);
        before.put(1273, nowMs + 18);
        before.put(1274, nowMs + 35);

        int fbPacketCount = 28;
        RTCPTCCPacket packet
            = new RTCPTCCPacket(
                0, 0, before, (byte) fbPacketCount, new DiagnosticContext());

        ByteArrayBufferImpl afterBaf = new ByteArrayBufferImpl(packet.fci);
        RTCPTCCPacket.PacketMap after = RTCPTCCPacket.getPacketsFromFci(afterBaf);

        assertEquals(7, after.size());
        assertEquals(1268, (int) after.firstKey());
        assertEquals(1274, (int) after.lastKey());
        assertEquals(fbPacketCount, packet.getFbPacketCount());

        int referenceTime64ms = (int) ((nowMs >> 6) & 0xffffff);
        long referenceTime250us = referenceTime64ms << 8;
        assertEquals(referenceTime250us,
                RTCPTCCPacket.getReferenceTime250us(afterBaf));

        long nextReferenceTime250us = referenceTime250us + (60 << 2);
        assertEquals(nextReferenceTime250us, (long) after.get(1268));

        nextReferenceTime250us = nextReferenceTime250us + (7 << 2);
        assertEquals(nextReferenceTime250us, (long) after.get(1269));
        assertEquals(nextReferenceTime250us, (long) after.get(1270));

        nextReferenceTime250us = nextReferenceTime250us + (5 << 2);
        assertEquals(nextReferenceTime250us, (long) after.get(1271));

        nextReferenceTime250us = nextReferenceTime250us + (6 << 2);
        assertEquals(nextReferenceTime250us, (long) after.get(1272));
        assertEquals(nextReferenceTime250us, (long) after.get(1273));

        nextReferenceTime250us = nextReferenceTime250us + (17 << 2);
        assertEquals(nextReferenceTime250us, (long) after.get(1274));
    }
}
