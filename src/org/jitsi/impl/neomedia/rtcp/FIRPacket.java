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

import net.sf.fmj.media.rtp.*;

import java.io.*;

/**
 * The Feedback Control Information (FCI) for the Full Intra Request
 * consists of one or more FCI entries, the content of which is depicted
 * in the figure below.  The length of the FIR feedback message MUST be set to
 * 2+2*N, where N is the number of FCI entries.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                              SSRC                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Seq nr.       |    Reserved                                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * SSRC (32 bits): The SSRC value of the media sender that is
 * requested to send a decoder refresh point.
 *
 * Seq nr. (8 bits): Command sequence number.  The sequence number
 * space is unique for each pairing of the SSRC of command
 * source and the SSRC of the command target.  The sequence
 * number SHALL be increased by 1 modulo 256 for each new
 * command.  A repetition SHALL NOT increase the sequence
 * number.  The initial value is arbitrary.
 *
 * Reserved (24 bits): All bits SHALL be set to 0 by the sender and
 * SHALL be ignored on reception.
 *
 * @author George Politis
 */
public class FIRPacket extends RTCPFBPacket
{
    /**
     * The FIR message is identified by RTCP packet type value PT=PSFB and
     * FMT=4.
     */
    public static final int FMT = 4;

    /**
     * The "SSRC of media source" is not used and SHALL be set to 0.
     */
    private static final int MEDIA_SOURCE_SSRC = 0;

    /**
     * The FCI entries in this FIR.
     */
    private FIRPacketEntry[] fciEntries;

    /**
     * Ctor to use when creating an FIR from scratch.
     *
     * @param fciEntries
     * @param senderSSRC
     */
    public FIRPacket(FIRPacketEntry[] fciEntries, long senderSSRC)
    {
        super(FMT, PSFB, senderSSRC, MEDIA_SOURCE_SSRC);
        this.fciEntries = fciEntries;
    }

    /**
     * Ctor to use when reading an FIR.
     *
     * @param base
     */
    public FIRPacket(RTCPCompoundPacket base)
    {
        super(base);
        super.fmt = FMT;
        super.type = PSFB;
    }

    /**
     * Parses the FCI field of a packet that appears to be an FIR.
     *
     * @param base
     * @param firstbyte
     * @param rtpfb
     * @param length
     * @param in
     * @param senderSSRC
     * @param sourceSSRC
     * @return
     */
    public static RTCPPacket parse(
        RTCPCompoundPacket base,
        int firstbyte, int rtpfb, int length, DataInputStream in,
        long senderSSRC, long sourceSSRC) throws IOException
    {
        // TODO(gp) implement lazy parsing.
        // TODO(gp) standarize RTCPFBPacket parsing.

        FIRPacket firPacket = new FIRPacket(base);

        int numOfFciEntries = (length - 12) / 8;

        firPacket.fciEntries = new FIRPacketEntry[numOfFciEntries];

        for (int i = 0; i < numOfFciEntries; i++)
        {
            long ssrc = in.readInt() & 0xffffffffL;
            int sequenceNumber = in.readUnsignedByte();
            in.readByte();
            in.readByte();
            in.readByte();

            firPacket.fciEntries[i] = new FIRPacketEntry(ssrc, sequenceNumber);
        }

        return firPacket;
    }

    @Override
    public void assemble(DataOutputStream out)
        throws IOException
    {
        out.writeByte((byte) (0x80 /* version */ | FMT));
        out.writeByte((byte) PSFB);

        int numOfFciEntries = fciEntries != null ? fciEntries.length : 0;
        out.writeShort(2 + (2* numOfFciEntries));
        writeSsrc(out, senderSSRC);
        writeSsrc(out, sourceSSRC);

        if (fciEntries != null || fciEntries.length != 0)
        {
            for (int i = 0; i < fciEntries.length; i++)
            {
                FIRPacketEntry entry = fciEntries[i];
                writeSsrc(out, entry.ssrc);
                out.writeByte(entry.sequenceNumber);
                out.writeByte(0);
                out.writeByte(0);
                out.writeByte(0);
            }
        }

    }

    @Override
    public int calcLength()
    {
        // Length (16 bits):  The length of this packet in 32-bit words minus
        // one, including the header and any padding.

        int len = 12; // header+ssrc+ssrc
        if (fciEntries != null && fciEntries.length != 0)
        {
            len += 2 * fciEntries.length;
        }

        return len;
    }

    /**
     * Structure that represents an FCI entry in the FIR packet.
     */
    public static class FIRPacketEntry
    {
        /**
         * Ctor.
         *
         * @param ssrc
         * @param sequenceNumber
         */
        public FIRPacketEntry(long ssrc, int sequenceNumber)
        {
            this.ssrc = ssrc;
            this.sequenceNumber = sequenceNumber;
        }

        /**
         * (32 bits): The SSRC value of the media sender that is requested to
         * send a decoder refresh point.
         */
        private final long ssrc;

        /**
         * (8 bits): Command sequence number.  The sequence number
         * space is unique for each pairing of the SSRC of command
         * source and the SSRC of the command target.  The sequence
         * number SHALL be increased by 1 modulo 256 for each new
         * command.  A repetition SHALL NOT increase the sequence
         * number.  The initial value is arbitrary.
         */
        private final int sequenceNumber;
    }
}
