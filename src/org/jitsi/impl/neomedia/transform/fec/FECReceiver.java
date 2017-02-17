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

import java.util.*;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * A <tt>PacketTransformer</tt> which handles incoming ulpfec packets
 * for a single SSRC.
 *
 * @author Boris Grozev
 */
class FECReceiver
    implements PacketTransformer
{
    /**
     * The <tt>Logger</tt> used by the <tt>FECReceiver</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
            = Logger.getLogger(FECReceiver.class);

    /**
     * A <tt>Comparator</tt> implementation for RTP sequence numbers.
     * Compares <tt>a</tt> and <tt>b</tt>, taking into account the wrap at 2^16.
     *
     * IMPORTANT: This is a valid <tt>Comparator</tt> implementation only if
     * used for subsets of [0, 2^16) which don't span more than 2^15 elements.
     *
     * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000])
     * Doesn't work for: [0, 2^15] and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
     */
    private static final Comparator<? super Integer> seqNumComparator
            = new Comparator<Integer>() {
        @Override
        public int compare(Integer a, Integer b)
        {
            if (a.equals(b))
                return 0;
            else if (a > b)
            {
                if (a - b < 32768)
                    return 1;
                else
                    return -1;
            }
            else //a < b
            {
                if (b - a < 32768)
                    return -1;
                else
                    return 1;
            }
        }
    };
    /**
     * Number of received ulpfec packets.
     */
    private int nbFec = 0;

    /**
     * Number of packets recovered using fec.
     */
    private int nbRecovered = 0;

    /**
     * The single SSRC with which this <tt>FECReceiver</tt> works.
     */
    private long ssrc;

    /**
     * The number of media packets to keep.
     */
    private static final int MEDIA_BUF_SIZE;

    /**
     * The maximum number of ulpfec packets to keep.
     */
    private static final int FEC_BUF_SIZE;

    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the value of {@link #MEDIA_BUF_SIZE}.
     */
    private static final String MEDIA_BUF_SIZE_PNAME
            = FECReceiver.class.getName() + ".MEDIA_BUFF_SIZE";

    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the value of {@link #FEC_BUF_SIZE}.
     */
    private static final String FEC_BUF_SIZE_PNAME
            = FECReceiver.class.getName() + ".FEC_BUFF_SIZE";

    static
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        int fecBufSize = 32;
        int mediaBufSize = 64;

        if (cfg != null)
        {
            fecBufSize = cfg.getInt(FEC_BUF_SIZE_PNAME, fecBufSize);
            mediaBufSize = cfg.getInt(MEDIA_BUF_SIZE_PNAME, mediaBufSize);
        }
        FEC_BUF_SIZE = fecBufSize;
        MEDIA_BUF_SIZE = mediaBufSize;
    }

    /**
     * Buffer which keeps (copies of) received media packets.
     *
     * We keep them ordered by their RTP sequence numbers, so that
     * we can easily select the oldest one to discard when the buffer is
     * full (when the map has more than <tt>MEDIA_BUFF_SIZE</tt> entries).
     *
     * We keep them in a <tt>Map</tt> so that we can easily search for a
     * packet with a specific sequence number.
     *
     * Note: This might turn out to be inefficient, especially with increased
     * buffer sizes. In the vast majority of cases (e.g. on every received
     * packet) we do an insert at one end and a delete from the other -- this
     * can be optimized. We very rarely (when we receive a packet out of order)
     * need to insert at an arbitrary location.
     */
    private final SortedMap<Integer, RawPacket> mediaPackets
        = new TreeMap<Integer, RawPacket>(seqNumComparator);

    /**
     * Buffer which keeps (copies of) received fec packets.
     *
     * We keep them ordered by their RTP sequence numbers, so that
     * we can easily select the oldest one to discard when the buffer is
     * full (when the map has more than <tt>FEC_BUFF_SIZE</tt> entries.
     *
     * We keep them in a <tt>Map</tt> so that we can easily search for a
     * packet with a specific sequence number.
     *
     * Note: This might turn out to be inefficient, especially with increased
     * buffer sizes. In the vast majority of cases (e.g. on every received
     * packet) we do an insert at one end and a delete from the other -- this
     * can be optimized. We very rarely (when we receive a packet out of order)
     * need to insert at an arbitrary location.
     */
    private final SortedMap<Integer,RawPacket> fecPackets
        = new TreeMap<Integer, RawPacket>(seqNumComparator);

    /**
     * Used to check whether a media packet can be recovered using
     * available media and ulpfec packets, and to do the recovery.
     */
    private final Reconstructor reconstructor;

    /**
     * A <tt>Set</tt> of packets which will be reused every time a
     * packet is recovered. Defined here to avoid recreating it on every call
     * to <tt>reverseTransform</tt>.
     */
    private final Set<RawPacket> packetsToRemove = new HashSet<RawPacket>();

    /**
     * Allow disabling of handling of ulpfec packets for testing purposes.
     */
    private boolean handleFec = true;

    /**
     * The payload type for ulpfec.
     */
    private byte ulpfecPT;

    /**
     * Initialize a new <tt>FECReceiver</tt> which is to handle
     * packets with SSRC equal to <tt>ssrc</tt>
     * @param ssrc the SSRC for the <tt>FECReceiver</tt>
     */
    FECReceiver(long ssrc, byte ulpfecPT)
    {
        this.ssrc = ssrc;
        this.ulpfecPT = ulpfecPT;
        reconstructor = new Reconstructor(mediaPackets, ssrc);
        if (logger.isInfoEnabled())
            logger.info("New FECReceiver for SSRC="+ssrc);
    }

    /**
     * {@inheritDoc}
     *
     * Don't touch "outgoing".
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts)
    {
        return pkts;
    }

    /**
     * {@inheritDoc}
     *
     * Handles incoming ulpfec packets. Modifies the sequence numbers of
     * media packets, by subtracting the number of received ulpfec packets.
     * This is necessary because ulpfec packets are not passed to the rest
     * of the application, and if sequence numbers aren't rewritten, the
     * missing fec packets will be incorrectly assumed lost.
     *
     * We transform a stream that looks like this:
     * Packets: M1 M2 M3 F1 M4 M5 F2 M6 M7
     * Seq:     1  2  3  4  5  6  7  8  9
     *
     * Into
     * Packets: M1 M2 M3 M4 M5 M6 M7
     * Seq:     1  2  3  4  5  6  7
     */
    @Override
    public synchronized RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        // first read all input packets
        for (int i = 0; i < pkts.length; i++)
        {
            RawPacket pkt = pkts[i];
            if (pkt == null)
                continue;

            if (pkt.getPayloadType() == ulpfecPT)
            {
                // TODO: handle the case of FEC+Media in a single RED packet
                nbFec++;
                pkts[i] = null; // don't forward it

                if(handleFec)
                    saveFec(pkt);
            }
            else
            {
                if (handleFec)
                    saveMedia(pkt);
            }
        }

        // now that we've read the input packets, see if there's a packet
        // we could recover
        if (handleFec)
        {
            // go over our saved fec packets and see if any of them can be
            // used to recover a media packet. Add packets which aren't
            // needed anymore to packetsToRemove
            packetsToRemove.clear();
            for (Map.Entry<Integer, RawPacket> entry : fecPackets.entrySet())
            {
                RawPacket fecPacket = entry.getValue();
                reconstructor.setFecPacket(fecPacket);
                if (reconstructor.numMissing == 0)
                {
                    // We already have all media packets for this fec packet,
                    // no need to keep it and keep checking.
                    packetsToRemove.add(fecPacket);
                    continue;
                }

                if (reconstructor.canRecover())
                {
                    packetsToRemove.add(fecPacket);
                    RawPacket recovered
                            = reconstructor.recover();

                    // save it
                    if (recovered != null)
                    {
                        nbRecovered++;
                        saveMedia(recovered);

                        // search for an empty spot in pkts where to place
                        // recovered
                        boolean found = false;
                        for (int i = 0; i < pkts.length; i++)
                        {
                            if (pkts[i] == null)
                            {
                                pkts[i] = recovered;
                                found = true;
                                break;
                            }
                        }

                        if (!found)
                        {
                            RawPacket[] pkts2 = new RawPacket[pkts.length+1];
                            System.arraycopy(pkts, 0, pkts2, 0, pkts.length);
                            pkts2[pkts.length] = recovered;
                            pkts = pkts2;
                        }
                    }
                }
            }

            for (RawPacket p : packetsToRemove)
                fecPackets.remove(p.getSequenceNumber());
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
            logger.info("Closing FECReceiver for SSRC=" + ssrc
                    + ". Received " + nbFec +" ulpfec packets, recovered "
                    + nbRecovered + " media packets.");
        }
    }

    /**
     * Sets the ulpfec payload type.
     * @param ulpfecPT the payload type.
     */
    public void setUlpfecPT(byte ulpfecPT)
    {
        this.ulpfecPT = ulpfecPT;
    }

    /**
     * Saves <tt>p</tt> into <tt>fecPackets</tt>. If the size of
     * <tt>fecPackets</tt> has reached <tt>FEC_BUFF_SIZE</tt> discards the
     * oldest packet from it.
     * @param p the packet to save.
     */
    private void saveFec(RawPacket p)
    {
        if (fecPackets.size() >= FEC_BUF_SIZE)
            fecPackets.remove(fecPackets.firstKey());

        fecPackets.put(p.getSequenceNumber(), p);
    }

    /**
     * Makes a copy of <tt>p</tt> into <tt>mediaPackets</tt>. If the size of
     * <tt>mediaPackets</tt> has reached <tt>MEDIA_BUFF_SIZE</tt> discards
     * the oldest packet from it and reuses it.
     * @param p the packet to copy.
     */
    private void saveMedia(RawPacket p)
    {
        RawPacket newMedia;
        if (mediaPackets.size() < MEDIA_BUF_SIZE)
        {
            newMedia = new RawPacket();
            newMedia.setBuffer(new byte[FECTransformEngine.INITIAL_BUFFER_SIZE]);
            newMedia.setOffset(0);
        }
        else
        {
            newMedia = mediaPackets.remove(mediaPackets.firstKey());
        }

        int pLen = p.getLength();
        if (pLen > newMedia.getBuffer().length)
        {
            newMedia.setBuffer(new byte[pLen]);
        }

        System.arraycopy(p.getBuffer(), p.getOffset(), newMedia.getBuffer(),
                0, pLen);
        newMedia.setLength(pLen);

        mediaPackets.put(newMedia.getSequenceNumber(), newMedia);
    }


    /**
     * A class that allows the recovery of a <tt>RawPacket</tt> given a set
     * of media packets and an ulpfec packet.
     *
     * Usage:
     * 0. Create an instance specifying the map off all available media packets
     *
     * 1. Call setFecPacket() with an ulpfec packet
     * 2. Check if a recovery is possible using canRecover()
     * 3. Recover a packet with recover()
     *
     */
    private static class Reconstructor
    {
        /**
         * Subset of the media packets which is needed for recovery, given a
         * specific value of <tt>fecPacket</tt>.
         */
        private Set<RawPacket> neededPackets = new HashSet<RawPacket>();

        /**
         * The ulpfec packet to be used for recovery.
         */
        private RawPacket fecPacket = null;

        /**
         * Number of media packet which are needed for recovery (given a
         * specific value of <tt>fecPacket</tt>) which are not available.
         * If the value is <tt>0</tt>, this indicates that all media packets
         * referenced in <tt>fecPacket</tt> *are* available, and so no recovery
         * is needed.
         */
        private int numMissing = -1;

        /**
         * Sequence number of the packet to be reconstructed.
         */
        private int sequenceNumber = -1;

        /**
         * SSRC to set on reconstructed packets.
         */
        private long ssrc;

        /**
         * All available media packets.
         */
        private Map<Integer, RawPacket> mediaPackets;

        /**
         * Initializes a new instance.
         * @param mediaPackets all available media packets.
         * @param ssrc the ssrc to use
         */
        Reconstructor(Map<Integer, RawPacket> mediaPackets, long ssrc)
        {
            this.mediaPackets = mediaPackets;
            this.ssrc = ssrc;
        }

        /**
         * Returns <tt>true</tt> if the <tt>RawPacket</tt> last set using
         * <tt>setFecPacket</tt> can be used to recover a media packet,
         * <tt>false</tt>otherwise.
         *
         * @return <tt>true</tt> if the <tt>RawPacket</tt> last set using
         * <tt>setFecPacket</tt> can be used to recover a media packet,
         * <tt>false</tt>otherwise.
         */
        private boolean canRecover()
        {
            return (numMissing == 1);
        }

        /**
         * Sets the ulpfec packet to be used for recovery and also
         * updates the values of <tt>numMissing</tt>, <tt>sequenceNumber</tt>
         * and <tt>neededPackets</tt>.
         * @param p the ulpfec packet to set.
         */
        private void setFecPacket(RawPacket p)
        {
            // reset all fields specific to fecPacket
            neededPackets.clear();
            numMissing = 0;
            sequenceNumber = -1;
            fecPacket = p;

            RawPacket pkt;

            byte[] buf = fecPacket.getBuffer();
            int idx = fecPacket.getOffset() + fecPacket.getHeaderLength();

            // mask length in bytes
            int maskLen = (buf[idx] & 0x40) == 0 ? 2 : 6;
            int base
                = fecPacket.readUint16AsInt(fecPacket.getHeaderLength() + 2);


            idx+=12; // skip FEC Header and Protection Length, point to mask

            outer:
            for (int i=0; i<maskLen; i++)
            {
                for (int j=0; j<8; j++)
                {
                    if ( (buf[idx+i] & (1<<(7-j) & 0xff)) != 0 )
                    {
                        //j-th bit in i-th byte in the mask is set
                        pkt = mediaPackets.get(base + i*8 + j);
                        if (pkt != null)
                        {
                            neededPackets.add(pkt);
                        }
                        else
                        {
                            sequenceNumber = base + i*8 + j;
                            numMissing++;
                        }
                    }
                    if (numMissing > 1)
                        break outer;
                }
            }

            if (numMissing != 1)
                sequenceNumber = -1;
        }

        /**
         * Recovers a media packet using the ulpfec packet <tt>fecPacket</tt>
         * and the packets in <tt>neededPackets</tt>.
         * @return the recovered packet.
         */
        private RawPacket recover()
        {
            if (!canRecover())
                return null;

            byte[] fecBuf = fecPacket.getBuffer();
            int idx = fecPacket.getOffset() + fecPacket.getHeaderLength();

            int lengthRecovery = (fecBuf[idx+8] & 0xff) <<8 |
                    (fecBuf[idx+9] & 0xff);
            for (RawPacket p : neededPackets)
                lengthRecovery ^= p.getLength()-12;
            lengthRecovery &= 0xffff;

            byte[] recoveredBuf
                    = new byte[lengthRecovery + 12]; //include RTP header

            // restore the first 8 bytes of the header
            System.arraycopy(fecBuf, idx, recoveredBuf, 0, 8);
            for (RawPacket p : neededPackets)
            {
                int pOffset = p.getOffset();
                byte[] pBuf = p.getBuffer();
                for (int i = 0; i < 8; i++)
                    recoveredBuf[i] ^= pBuf[pOffset+i];
            }

            // set the version to 2
            recoveredBuf[0] &= 0x3f;
            recoveredBuf[0] |= 0x80;
            // the RTP header is now set, except for SSRC and seq. which are not
            // recoverable in this way and will be set later

            // check how many bytes of the payload are in the FEC packet
            boolean longMask = (fecBuf[idx] & 0x40) != 0;
            idx+=10; // skip FEC Header, point to FEC Level 0 Header
            int protectionLength = ((fecBuf[idx] & 0xff) << 8) |
                    (fecBuf[idx+1] & 0xff);
            if (protectionLength < lengthRecovery)
            {
                // The FEC Level 0 payload only covers part of the media
                // packet, which isn't useful for us.
                logger.warn("Recovered only a partial RTP packet. Discarding.");
                return null;
            }

            idx+=4; //skip the FEC Level 0 Header
            if (longMask)
                idx+=4; //long mask

            // copy the payload protection bits from the FEC packet
            System.arraycopy(fecBuf, idx, recoveredBuf, 12, lengthRecovery);

            // restore payload from media packets
            for (RawPacket p : neededPackets)
            {
                byte[] pBuf = p.getBuffer();
                int pLen = p.getLength();
                int pOff = p.getOffset();
                for (int i = 12; i < lengthRecovery+12 && i < pLen; i++)
                    recoveredBuf[i] ^= pBuf[pOff + i];
            }

            RawPacket recovered
                    = new RawPacket(recoveredBuf, 0, lengthRecovery + 12);
            recovered.setSSRC((int)this.ssrc);
            recovered.setSequenceNumber(sequenceNumber);

            return recovered;
        }
    }
}

