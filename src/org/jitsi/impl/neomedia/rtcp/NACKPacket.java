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

import java.io.*;
import java.util.*;
import net.sf.fmj.media.rtp.*;
import org.jitsi.service.neomedia.*;

/**
 * A class which represents an RTCP Generic NACK feedback message, as defined
 * in RFC4585 Section 6.2.1.
 *
 * The RTCP packet structure is:
 *
 * <pre>{@code
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P| FMT=1   |   PT=205      |             length            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of packet sender                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of media source                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           FCI                                 |
 * |                          [...]                                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The Feedback Control Information (FCI) field consists of one or more
 * 32-bit words, each with the following structure:
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |             PID               |             BLP               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * }</pre>
 *
 * @author Boris Grozev
 * @author George Politis
 */
public class NACKPacket
    extends RTCPFBPacket
{
    /**
     * Gets a boolean indicating whether or not the RTCP packet specified in the
     * {@link ByteArrayBuffer} that is passed as an argument is a NACK packet or
     * not.
     *
     * @param baf the {@link ByteArrayBuffer}
     * @return true if the byte array buffer holds a NACK packet, otherwise
     * false.
     */
    public static boolean isNACKPacket(ByteArrayBuffer baf)
    {
        int rc = RTCPHeaderUtils.getReportCount(baf);
        return isRTPFBPacket(baf) && rc == FMT;
    }

    /**
     * @return the set of sequence numbers reported lost in a NACK packet
     * represented by a {@link ByteArrayBuffer}.
     * @param baf the NACK packet.
     */
    public static Collection<Integer> getLostPackets(ByteArrayBuffer baf)
    {
        Collection<Integer> lostPackets = new LinkedList<>();
        ByteArrayBuffer fciBuffer = getFCI(baf);
        if (fciBuffer == null)
        {
            return lostPackets;
        }

        byte[] fci = fciBuffer.getBuffer();
        int off = fciBuffer.getOffset(), len = fciBuffer.getLength();

        for (int i = 0; i < (len / 4); i++)
        {
            int pid = (0xFF & fci[off + i * 4 + 0]) << 8 | (0xFF & fci[off + i * 4 + 1]);
            lostPackets.add(pid);

            // First byte of the BLP
            for (int j = 0; j < 8; j++)
                if (0 != (fci[off + i * 4 + 2] & (1 << j)))
                    lostPackets.add((pid + 1 + 8 + j) % (1 << 16));

            // Second byte of the BLP
            for (int j = 0; j < 8; j++)
                if (0 != (fci[off + i * 4 + 3] & (1 << j)))
                    lostPackets.add((pid + 1 + j) % (1 << 16));
        }

        return lostPackets;
    }

    /**
     * The value of the "fmt" field for a NACK packet.
     */
    public static final int FMT = 1;

    /**
     * The set of sequence numbers described by this packet.
     */
    private Collection<Integer> lostPackets = null;

    /**
     * Initializes a new <tt>NACKPacket</tt> instance.
     * @param base
     */
    public NACKPacket(RTCPCompoundPacket base)
    {
        super(base);
    }

    /**
     * Initializes a new <tt>NACKPacket</tt> instance with specific "packet
     * sender SSRC" and "media source SSRC" values and which describes a
     * specific set of sequence numbers.
     * @param senderSSRC the value to use for the "packet sender SSRC" field.
     * @param sourceSSRC the value to use for the "media source SSRC" field.
     * @param lostPackets the set of RTP sequence numbers which this NACK
     * packet is to describe.
     *
     * Note that this implementation is not optimized and might not always use
     * the minimal possible number of bytes to describe a given set of packets.
     * Specifically, it does not take into account that sequence numbers wrap
     * at 2^16 and fails to pack numbers close to 2^16 with those close to 0.
     */
    public NACKPacket(long senderSSRC, long sourceSSRC,
                      Collection<Integer> lostPackets)
    {
        super(FMT, RTPFB, senderSSRC, sourceSSRC);

        List<Integer> sorted = new LinkedList<>(lostPackets);
        Collections.sort(sorted);
        List<byte[]> nackList = new LinkedList<>();

        int currentPid = -1;
        byte[] currentNack = null;
        for (int seq : sorted)
        {
            if (currentPid == -1 || currentPid + 16 <= seq)
            {
                currentPid = seq;
                currentNack = new byte[4];
                currentNack[0] = (byte) ((seq & 0xff00) >> 8);
                currentNack[1] = (byte) (seq & 0x00ff);
                currentNack[2] = 0;
                currentNack[3] = 0;

                nackList.add(currentNack);

                continue;
            }

            // Add seq to the current fci
            int diff = seq - currentPid;
            if (diff <= 8)
                currentNack[3] |= (byte) (1<<(diff-1));
            else
                currentNack[2] |= (byte) (1<<(diff-8-1));
        }

        // Set the fci field, which is used when assembling
        fci = new byte[nackList.size() * 4];
        for (int i = 0; i < nackList.size(); i++)
            System.arraycopy(nackList.get(i), 0, fci, i*4, 4);

        this.lostPackets = sorted;
    }

    /**
     * @return the set of sequence numbers reported lost in this NACK packet.
     */
    synchronized public Collection<Integer> getLostPackets()
    {
        if (lostPackets == null)
        {
            // parse this.fci as containing NACK entries and initialize
            // this.lostPackets
            lostPackets = getLostPackets(new RawPacket(fci, 0, fci.length));
        }

        return lostPackets;
    }

    private void writeSsrc(DataOutputStream dataOutputStream, long ssrc)
        throws IOException
    {
        dataOutputStream.writeByte((byte) (ssrc >> 24));
        dataOutputStream.writeByte((byte) ((ssrc >> 16) & 0xFF));
        dataOutputStream.writeByte((byte) ((ssrc >> 8) & 0xFF));
        dataOutputStream.writeByte((byte) (ssrc & 0xFF));
    }

    @Override
    public void assemble(DataOutputStream dataoutputstream)
        throws IOException
    {
        dataoutputstream.writeByte((byte) (0x80 /* version */ | FMT));
        dataoutputstream.writeByte((byte) RTPFB);
        dataoutputstream.writeShort(2 + (fci.length / 4));
        writeSsrc(dataoutputstream, senderSSRC);
        writeSsrc(dataoutputstream, sourceSSRC);
        dataoutputstream.write(fci);
    }

    @Override
    public String toString()
    {
        return "RTCP NACK packet; packet sender: " + senderSSRC
                + "; media sources: " + sourceSSRC
                + "; NACK entries: " + (fci == null ? "none" : (fci.length / 4))
                + "; lost packets: "
                + (lostPackets == null ? "none" :
                        Arrays.toString(lostPackets.toArray()));
    }

}

