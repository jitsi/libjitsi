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
package org.jitsi.impl.neomedia.transform.fec;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.Logger;

/**
 * <tt>PacketTransformer</tt> which adds ulpfec packets. Works for a
 * specific SSRC.
 *
 * @author Boris Grozev
 */
class FECSender
    implements PacketTransformer
{
    /**
     * The <tt>Logger</tt> used by the <tt>FECSender</tt> class and its
     * instances to print debug information.
     */
    private static final Logger logger = Logger.getLogger(FECSender.class);

    /**
     * The single SSRC with which this <tt>FECSender</tt> works.
     */
    private long ssrc;

    /**
     * The ulpfec payload type.
     */
    private byte ulpfecPT;

    /**
     * An ulpfec packet will be generated for every <tt>fecRate</tt> media
     * packets.
     * If set to 0, no ulpfec packets will be generated.
     */
    private int fecRate;

    /**
     * A counter of packets. Incremented for every media packet.
     */
    private int counter = 0;

    /**
     * Number of ulpfec packets added.
     */
    private int nbFec = 0;

    /**
     * A fec packet, which will be sent once enough (that is <tt>fecRate</tt>)
     * media packets have passed, and have been "added" to the fec packet.
     * Should be always non-null.
     */
    private FECPacket fecPacket;

    /**
     * Creates a new <tt>FECSender</tt> instance.
     * @param ssrc the SSRC with which this <tt>FECSender</tt> will work.
     * @param fecRate the rate at which to add ulpfec packets.
     * @param ulpfecPT the payload to use for ulpfec packets.
     */
    FECSender(long ssrc, int fecRate, byte ulpfecPT)
    {
        this.ssrc = ssrc;
        this.fecRate = fecRate;
        this.ulpfecPT = ulpfecPT;
        fecPacket = new FECPacket(ssrc, ulpfecPT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts) {
        return pkts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized RawPacket[] transform(RawPacket[] pkts)
    {
        RawPacket pkt = null;
        for (RawPacket p : pkts)
        {
            if (p != null && p.getVersion() == RTPHeader.VERSION)
            {
                pkt = p;
                break;
            }
        }

        if (pkt == null)
            return pkts;

        return transformSingle(pkt, pkts);
    }

    /**
     * Processes <tt>pkt</tt> and, if <tt>fecRate</tt> packets have
     * passed, creates a fec packet protecting the last <tt>fecRate</tt> media
     * packets and adds this fec packet to <tt>pkts</tt>.
     *
     * @param pkt media packet to process.
     * @param pkts array to try to use for output.
     * @return an array that contains <tt>pkt</tt> (after processing)
     * and possible an ulpfec packet if one was added.
     */
    private RawPacket[] transformSingle(RawPacket pkt, RawPacket[] pkts)
    {
        // TODO due to the overhead introduced by adding any redundant data it
        // is usually a good idea to activate it only when the network
        // conditions require it.
        counter++;
        pkt.setSequenceNumber(pkt.getSequenceNumber() + nbFec);

        if (fecRate != 0)
            fecPacket.addMedia(pkt);

        if (fecRate != 0 && (counter % fecRate) == 0)
        {
            fecPacket.finish();

            boolean found = false;
            for (int i = 0; i < pkts.length; i++)
            {
                if (pkts[i] == null)
                {
                    found = true;
                    pkts[i] = fecPacket;
                    break;
                }
            }

            if (!found)
            {
                RawPacket[] pkts2 = new RawPacket[pkts.length + 1];
                System.arraycopy(pkts, 0, pkts2, 0, pkts.length);
                pkts2[pkts.length] = fecPacket;
                pkts = pkts2;
            }

            fecPacket = new FECPacket(ssrc, ulpfecPT);
            nbFec++;
        }

        return pkts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        if (logger.isInfoEnabled())
        {
            logger.info(
                    "Closing FECSender for ssrc=" + ssrc + ". Added " + nbFec
                        + " ulpfec packets.");
        }
    }

    /**
     * Sets the ulpfec payload type.
     * @param ulpfecPT the payload type.
     */
    public void setUlpfecPT(byte ulpfecPT)
    {
        this.ulpfecPT = ulpfecPT;
        if (fecPacket != null)
            fecPacket.payloadType = ulpfecPT;
    }

    /**
     * Updates the <tt>fecRate</tt> property. Re-allocates buffers, if
     * needed.
     * @param newFecRate the new rate to set.
     */
    public void setFecRate(int newFecRate)
    {
        if (fecRate != newFecRate)
        {
            fecPacket = new FECPacket(ssrc, ulpfecPT); //reset it
            fecRate = newFecRate;
            counter = 0;
        }
    }

    /**
     * A <tt>RawPacket</tt> extension which represents an ulpfec packet. Allows
     * for a media packet to be protected to be added via the <tt>addMedia()</tt>
     * method.
     *
     * The format of this packet (see RFC3350 and RFC5109) is as follows:
     *
     * 12 byte RTP header (no CSRC or extensions):
     * 0                   1                   2                   3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                           timestamp                           |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |           synchronization source (SSRC) identifier            |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * 10 byte FEC Header:
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |E|L|P|X|  CC   |M| PT recovery |            SN base            |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                          TS recovery                          |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |        length recovery        |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * 4 byte FEC Level 0 Header (the short mask is always used):
     *  0                   1                   2                   3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |       Protection Length       |             mask              |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * Followed by 'Protection Length' bytes of 'FEC Level 0 Payload'.
     */
    private static class FECPacket
        extends RawPacket
    {
        /**
         * SN base. The sequence number of the first media packet added.
         */
        int base = -1;

        /**
         * Number of media packets added.
         */
        int numPackets = 0;

        /**
         * The biggest payload (in the sense of RFC5109) of the media packets
         * added.
         */
        int protectionLength = -1;

        /**
         * The sequence of the last media packet added.
         */
        int lastAddedSeq = -1;

        /**
         * This <tt>RawPacket</tt>'s buffer.
         */
        private byte[] buf;

        /**
         * The SSRC of this packet.
         */
        private final long ssrc;

        /**
         * The RTP timestamp of the last added media packet.
         */
        private long lastAddedTS = -1;

        /**
         * The payload type for this packet.
         */
        byte payloadType;

        /**
         * Length of the RTP header of this packet.
         */
        private static final int RTP_HDR_LEN = 12;

        /**
         * Length of the additional headers added to this packet (in bytes):
         * 10 bytes FEC Header + 4 bytes FEC Level 0 Header (short mask)
         */
        private static final int FEC_HDR_LEN = 14;

        /**
         * Creates a new instance, initialized with a buffer obtained using
         * <tt>new</tt>.
         * @param ssrc the SSRC
         */
        FECPacket(long ssrc, byte payloadType)
        {
            super(
                    new byte[FECTransformEngine.INITIAL_BUFFER_SIZE],
                    0,
                    FECTransformEngine.INITIAL_BUFFER_SIZE);

            buf = getBuffer();
            this.ssrc = ssrc;
            this.payloadType = payloadType;
        }

        /**
         * Adds a media packet to be protected by this <tt>FECPacket</tt>.
         * @param media the media packet to add.
         */
        private void addMedia(RawPacket media)
        {
            byte[] mediaBuf = media.getBuffer();
            int mediaOff = media.getOffset();
            // payload length in the sense of RFC5109
            int mediaPayloadLen = media.getLength() - 12;

            // make sure that the buffer is big enough
            if (buf.length < mediaPayloadLen + RTP_HDR_LEN + FEC_HDR_LEN)
            {
                byte[] newBuff
                    = new byte[mediaPayloadLen + RTP_HDR_LEN + FEC_HDR_LEN];
                System.arraycopy(buf, 0, newBuff, 0, buf.length);
                for (int i = buf.length; i < newBuff.length; i++)
                    newBuff[i] = (byte) 0;
                buf = newBuff;
                setBuffer(buf);
            }

            if (base == -1)
            {
                // first packet, make a copy and not XOR
                base = media.getSequenceNumber();

                // 8 bytes from media's RTP header --> the FEC Header
                System.arraycopy(mediaBuf, mediaOff, buf, RTP_HDR_LEN, 8);
                // set the 'length recovery' field
                buf[RTP_HDR_LEN+8] = (byte) (mediaPayloadLen>>8 & 0xff);
                buf[RTP_HDR_LEN+9] = (byte) (mediaPayloadLen & 0xff);

                // copy the payload
                System.arraycopy(
                        mediaBuf, mediaOff + RTP_HDR_LEN,
                        buf, RTP_HDR_LEN + FEC_HDR_LEN,
                        mediaPayloadLen);
            }
            else
            {
                // not the first packet, do XOR

                // 8 bytes from media's RTP header --> the FEC Header
                for (int i = 0; i < 8; i++)
                    buf[RTP_HDR_LEN + i] ^= mediaBuf[mediaOff + i];

                // 'length recovery'
                buf[RTP_HDR_LEN+8] ^= (byte) (mediaPayloadLen>>8 & 0xff);
                buf[RTP_HDR_LEN+9] ^= (byte) (mediaPayloadLen & 0xff);

                // payload
                for (int i = 0; i < mediaPayloadLen; i++)
                {
                    buf[RTP_HDR_LEN + FEC_HDR_LEN + i]
                            ^= mediaBuf[mediaOff + RTP_HDR_LEN + i];
                }
            }

            lastAddedSeq = media.getSequenceNumber();
            lastAddedTS = media.getTimestamp();
            if (mediaPayloadLen > protectionLength)
                protectionLength = mediaPayloadLen;
            numPackets++;
        }

        /**
         * Fill in the required header fields and prepare this packet to be
         * sent.
         * @return the finished packet.
         */
        private RawPacket finish()
        {
            // RTP header fields
            buf[0] = (byte) 0x80; //no Padding, no Extension, no CSRCs
            setPayloadType(payloadType);
            setSequenceNumber(lastAddedSeq + 1);
            setSSRC((int)ssrc);
            setTimestamp(lastAddedTS); //TODO: check 5109 -- which TS should be used?

            // FEC Header
            buf[RTP_HDR_LEN + 2] = (byte) (base>>8 & 0xff);
            buf[RTP_HDR_LEN + 3] = (byte) (base & 0xff);

            // FEC Level 0 header
            buf[RTP_HDR_LEN + 10] = (byte) (protectionLength>>8 & 0xff);
            buf[RTP_HDR_LEN + 11] = (byte) (protectionLength & 0xff);

            // assume all packets from base to lastAddedSeq were added
            int mask = ((1<<numPackets) - 1) << (16-numPackets);

            buf[RTP_HDR_LEN + 12] = (byte) (mask>>8 & 0xff);
            buf[RTP_HDR_LEN + 13] = (byte) (mask & 0xff);

            setLength(RTP_HDR_LEN + FEC_HDR_LEN + protectionLength);
            return this;
        }
    }
}
