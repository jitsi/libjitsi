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

import org.jitsi.service.neomedia.*;

import java.util.*;

/**
 * @author bbaldino
 * Based on FlexFec draft -03
 * https://tools.ietf.org/html/draft-ietf-payload-flexible-fec-scheme-03
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |R|F| P|X|  CC   |M| PT recovery |         length recovery      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          TS recovery                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   SSRCCount   |                    reserved                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             SSRC_i                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           SN base_i           |k|          Mask [0-14]        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |k|                   Mask [15-45] (optional)                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |k|                                                             |
 * +-+                   Mask [46-108] (optional)                  |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     ... next in SSRC_i ...                    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

public class FlexFec03Packet
    extends RawPacket
{
    /**
     * Create a {@link FlexFec03Packet}
     * @param p the RawPacket to attempt parsing as a FlexFEC packet
     * @return a {@link FlexFec03Packet} if 'p' is successfully parsed
     * as a {@link FlexFec03Packet}, null otherwise
     */
    public static FlexFec03Packet create(RawPacket p)
    {
        return create(p.getBuffer(), p.getOffset(), p.getLength());
    }

    /**
     * Create a {@link FlexFec03Packet}
     * @param buffer
     * @param offset
     * @param length
     * @return a {@link FlexFec03Packet} if 'p' is successfully parsed
     * as a {@link FlexFec03Packet}, null otherwise
     */
    public static FlexFec03Packet create(byte[] buffer, int offset, int length)
    {
        FlexFec03Packet flexFecPacket = new FlexFec03Packet(buffer, offset, length);
        FlexFec03Header header = FlexFec03HeaderReader.readFlexFecHeader(
            flexFecPacket.getBuffer(),
            flexFecPacket.getFlexFecHeaderOffset(),
            flexFecPacket.getLength() - flexFecPacket.getHeaderLength());
        if (header == null)
        {
            return null;
        }
        flexFecPacket.header = header;
        return flexFecPacket;
    }

    /**
     * The FlexFEC03 header
     */
    protected FlexFec03Header header;

    /**
     * Ctor
     * @param buffer rtp packet buffer
     * @param offset offset at which the rtp packet starts in the given
     * buffer
     * @param length length of the packet
     */
    private FlexFec03Packet(byte[] buffer, int offset, int length)
    {
        super(buffer, offset, length);
    }

    /**
     * Get the list of media packet sequence numbers protected by this
     * FlexFec03Packet
     * @return the list of media packet sequence numbers protected by this
     * FlexFec03Packet
     */
    public List<Integer> getProtectedSequenceNumbers()
    {
        return this.header.protectedSeqNums;
    }

    /**
     * Get the size of the flexfec header for this packet
     * @return the size of the flexfec header for this packet
     */
    public int getFlexFecHeaderSize()
    {
        return this.header.size;
    }

    /**
     * Get the media ssrc protected by this flexfec packet
     * @return the media ssrc protected by this flexfec packet
     */
    public long getProtectedSsrc()
    {
        return this.header.protectedSsrc;
    }

    /**
     * Returns the size of the FlexFEC payload, in bytes
     * @return the size of the FlexFEC packet payload, in bytes
     */
    public int getFlexFecPayloadLength()
    {
        return this.getLength() - this.getHeaderLength() - this.header.size;
    }

    /**
     * Get the offset at which the FlexFEC header starts
     * @return the offset at which the FlexFEC header starts
     */
    public int getFlexFecHeaderOffset()
    {
        return getOffset() + this.getHeaderLength();
    }
}
