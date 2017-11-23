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
import org.jitsi.util.*;

import java.util.*;

/**
 * This class handles the reception of incoming ULPFEC (RFC 5109) packets
 *
 * @author bgrozev
 * @author bbaldino
 */
public class ULPFECReceiver
    extends AbstractFECReceiver
{
    /**
     * The <tt>Logger</tt> used by the <tt>ULPFECReceiver</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(ULPFECReceiver.class);
    /**
     * A <tt>Set</tt> of packets which will be reused every time a
     * packet is recovered. Defined here to avoid recreating it on every call
     * to <tt>reverseTransform</tt>.
     */
    private final Set<RawPacket> packetsToRemove = new HashSet<RawPacket>();

    private Reconstructor reconstructor;

    public ULPFECReceiver(long ssrc, byte payloadType)
    {
        super(ssrc, payloadType);
        reconstructor = new Reconstructor(mediaPackets, ssrc);
    }

    protected RawPacket[] doReverseTransform(RawPacket[] pkts)
    {
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
                        statistics.numRecoveredPackets++;
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
