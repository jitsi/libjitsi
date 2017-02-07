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
package org.jitsi.impl.neomedia.transform.zrtp;

import gnu.java.zrtp.packets.*;
import gnu.java.zrtp.utils.*;

import org.jitsi.service.neomedia.*;

/**
 * ZRTP packet representation.
 *
 * This class extends the RawPacket class and adds some methods
 * required by the ZRTP transformer.
 *
 * @author Werner Dittmann <Werner.Dittmann@t-online.de>
 */
public class ZrtpRawPacket extends RawPacket
{
    /**
     * Each ZRTP packet contains this magic number/cookie.
     */
    public static final byte[] ZRTP_MAGIC
        = new byte[] { 0x5a, 0x52, 0x54, 0x50 };

    /**
     * Construct an input ZrtpRawPacket using a received RTP raw packet.
     *
     * @param pkt a raw RTP packet as received
     */
    public ZrtpRawPacket(RawPacket pkt)
    {
        super(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
    }

    /**
     * Construct an output ZrtpRawPacket using specified value.
     *
     * Initialize this packet and set the ZRTP magic value
     * to mark it as a ZRTP packet.
     *
     * @param buf Byte array holding the content of this Packet
     * @param off Start offset of packet content inside buffer
     * @param len Length of the packet's data
     */
    public ZrtpRawPacket(byte[] buf, int off, int len)
    {
        super (buf, off, len);
        writeByte(0, (byte)0x10);
        writeByte(1, (byte)0);

        int at = 4;
        writeByte(at++, ZRTP_MAGIC[0]);
        writeByte(at++, ZRTP_MAGIC[1]);
        writeByte(at++, ZRTP_MAGIC[2]);
        writeByte(at, ZRTP_MAGIC[3]);
    }

    /**
     * Check if it could be a ZRTP packet.
     *
     * The method checks if the first byte of the received data
     * matches the defined ZRTP pattern 0x10
     *
     * @return true if could be a ZRTP packet, false otherwise.
     */
    protected boolean isZrtpPacket()
    {
        return isZrtpData(this);
    }

    /**
     * Checks whether extension bit is set and if so is the extension header
     * an zrtp one.
     * @param pkt the packet to check.
     * @return <tt>true</tt> if data is zrtp packet.
     */
    static boolean isZrtpData(RawPacket pkt)
    {
        return
            pkt.getExtensionBit() && (pkt.getHeaderExtensionType() == 0x505a);
    }

    /**
     * Check if it is really a ZRTP packet.
     *
     * The method checks if the packet contains the ZRTP magic
     * number.
     *
     * @return true if packet contains the magic number, false otherwise.
     */
    protected boolean hasMagic()
    {
        return
            (readByte(4) == ZRTP_MAGIC[0])
                && (readByte(5) == ZRTP_MAGIC[1])
                && (readByte(6) == ZRTP_MAGIC[2])
                && (readByte(7) == ZRTP_MAGIC[3]);
    }

    /**
     * Set the sequence number in this packet.
     * @param seq sequence number
     */
    protected void setSeqNum(short seq)
    {
        int at = 2;
        writeByte(at++, (byte)(seq>>8));
        writeByte(at, (byte)seq);
    }

    /**
     * Check if the CRC of this packet is ok.
     *
     * @return true if the CRC is valid, false otherwise
     */
    protected boolean checkCrc()
    {
        int crc = readInt(getLength()-ZrtpPacketBase.CRC_SIZE);
        return ZrtpCrc32.zrtpCheckCksum(getBuffer(), getOffset(),
            getLength()-ZrtpPacketBase.CRC_SIZE, crc);
    }

    /**
     * Set ZRTP CRC in this packet
     */
    protected void setCrc()
    {
        int crc = ZrtpCrc32.zrtpGenerateCksum(getBuffer(), getOffset(),
            getLength() - ZrtpPacketBase.CRC_SIZE);
        // convert and store CRC in crc field of ZRTP packet.
        crc = ZrtpCrc32.zrtpEndCksum(crc);
        writeInt(getLength() - ZrtpPacketBase.CRC_SIZE, crc);
    }
}
