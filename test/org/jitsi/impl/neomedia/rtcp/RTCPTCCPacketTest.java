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
        // vector, 1-bit symbols, 1xR + 13xNR, 14 pkts
        (byte) 0xa0,(byte) 0x00,
        // vector, 1-bit symbols, 1xR + 13xNR, 14 pkts
        (byte) 0xa0,(byte) 0x00,
        // RLE, not received: 5886
        (byte) 0x16,(byte) 0xfe,
        // vector, 2-bit symbols, 1x large delta + 6x small delta, 7 packets
        (byte) 0xe5,(byte) 0x55,
        // vector, 1-bit symbols, 3xR + 2NR + 1R + 1NR + 1R [packets over, 6 remaining 0 bits]
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
        RTCPTCCPacket tccPacket = new RTCPTCCPacket();
        tccPacket.parseFci(tccPacket, new ByteArrayBufferImpl(fci));
        RTCPTCCPacket.PacketMap packetMap = tccPacket.getPackets();

        assertEquals(5929, packetMap.size());
        assertEquals(4, (int) packetMap.firstKey());
        assertEquals(4 + 5929 - 1, (int) packetMap.lastKey());
        assertEquals((0x298710L << 8) + 0x2c, (long) packetMap.firstEntry().getValue());
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

        RTCPTCCPacket packet = new RTCPTCCPacket(0, 0, before, (byte) 13);

        RTCPTCCPacket tccPacket = new RTCPTCCPacket();
        tccPacket.parseFci(tccPacket, new ByteArrayBufferImpl(packet.fci));
        RTCPTCCPacket.PacketMap after = tccPacket.getPackets();

        assertEquals(138 - 120 + 1, after.size());
        assertEquals(120, (int) after.firstKey());
        assertEquals(138, (int) after.lastKey());
        assertEquals(13, packet.getFbPacketCount());
    }
}