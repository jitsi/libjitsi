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
 * Receive and process FlexFec03 packets, recovering missing packets where
 * possible
 * @author bbaldino
 */
public class FlexFec03Receiver
    extends AbstractFECReceiver
{
    /**
     * The <tt>Logger</tt> used by the <tt>FlexFec03Receiver</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(FlexFec03Receiver.class);

    /**
     * Helper class to reconstruct missing packets
     */
    private Reconstructor reconstructor;

    public FlexFec03Receiver(long mediaSsrc, byte fecPayloadType)
    {
        super(mediaSsrc, fecPayloadType);
        this.reconstructor = new Reconstructor(mediaPackets);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized RawPacket[] doReverseTransform(RawPacket[] pkts)
    {
        Set<Integer> flexFecPacketsToRemove = new HashSet<>();
        // Try to recover any missing media packets
        for (Map.Entry<Integer, RawPacket> entry : fecPackets.entrySet())
        {
            FlexFec03Packet flexFecPacket = FlexFec03Packet.create(entry.getValue());
            if (flexFecPacket == null)
            {
                continue;
            }
            reconstructor.setFecPacket(flexFecPacket);
            if (reconstructor.complete())
            {
                flexFecPacketsToRemove.add(flexFecPacket.getSequenceNumber());
                continue;
            }
            if (reconstructor.canRecover())
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Attempting recovery of missing sequence " +
                        "number " + reconstructor.missingSequenceNumber);
                }
                flexFecPacketsToRemove.add(flexFecPacket.getSequenceNumber());
                RawPacket recovered = reconstructor.recover();
                if (recovered != null)
                {
                    logger.info("Recovered packet " +
                        recovered.getSequenceNumber());
                    statistics.numRecoveredPackets++;
                    saveMedia(recovered);
                    pkts = ArrayUtils.insert(recovered, pkts, RawPacket.class);
                }
                else
                {
                    logger.error("Recovery of packet " +
                        reconstructor.missingSequenceNumber + " failed even" +
                        " though it should have been possible");
                    statistics.failedRecoveries++;
                }
            }
        }

        for (Integer flexFecSeqNum : flexFecPacketsToRemove)
        {
            fecPackets.remove(flexFecSeqNum);
        }
        return pkts;
    }

    private static class Reconstructor
    {
        /**
         * All available media packets.
         */
        private Map<Integer, RawPacket> mediaPackets;

        /**
         * The FlexFEC packet to be used for recovery.
         */
        private FlexFec03Packet fecPacket = null;

        /**
         * We can only recover a single missing packet, so when we check
         * for how many are missing, we'll keep track of the first one we find
         */
        int missingSequenceNumber = -1;


        /**
         * The number of missing media packets we've detected for the set
         * FlexFEC packet
         */
        int numMissing = -1;

        /**
         * Initializes a new instance.
         * @param mediaPackets the currently available media packets. Note that
         * this is a reference so it will remain up to date as the map is
         * filled out by the caller.
         */
        Reconstructor(Map<Integer, RawPacket> mediaPackets)
        {
            this.mediaPackets = mediaPackets;
        }

        public boolean complete()
        {
            return numMissing == 0;
        }

        public boolean canRecover()
        {
            return numMissing == 1;
        }

        /**
         * Set the {@link FlexFec03Packet} to be used for this reconstruction,
         * and check if any media packets protected by this fec packet are missing
         * and can be recovered
         * @param p the {@link FlexFec03Packet} to be used for this reconstruction
         */
        public void setFecPacket(FlexFec03Packet p)
        {
            if (p == null)
            {
                logger.error("Error setting flexfec packet");
                return;
            }
            this.fecPacket = p;
            if (logger.isDebugEnabled())
            {
                logger.debug("Have " + mediaPackets.size() + " saved media packets");
            }
            numMissing = 0;
            if (logger.isDebugEnabled())
            {
                logger.debug("Reconstructor checking if recovery is " +
                    "possible: fec packet " + p.getSequenceNumber() +
                    " protects packets:\n" + p.getProtectedSequenceNumbers());
            }

            for (Integer protectedSeqNum : fecPacket.getProtectedSequenceNumbers())
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Checking if we've received media " +
                        "packet " + protectedSeqNum);
                }
                if (!mediaPackets.containsKey(protectedSeqNum))
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("We haven't, mark as missing");
                    }
                    numMissing++;
                    missingSequenceNumber = protectedSeqNum;
                }
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("There were " + numMissing + " missing media " +
                    "packets for flexfec " + p.getSequenceNumber());
            }
            if (numMissing > 1)
            {
                missingSequenceNumber = -1;
            }
        }

        /**
         * Initialize the given RawPacket with the RTP header information
         * and payload from fecPacket
         * @param fecPacket the FlexFEC packet being used for recovery
         * @param recoveredPacket the blank RawPacket we're recreating the
         * recovered packet in
         * @return true on success, false otherwise
         */
        private boolean startPacketRecovery(FlexFec03Packet fecPacket, RawPacket recoveredPacket)
        {
            if (fecPacket.getLength() < RawPacket.FIXED_HEADER_SIZE)
            {
                logger.error("Given FlexFEC packet is too small");
                return false;
            }
            if (recoveredPacket.getBuffer().length < fecPacket.getLength())
            {
                logger.error("Given RawPacket buffer is too small");
                return false;
            }
            // Copy over the recovery RTP header data from the fec packet
            // (fecPacket contains the RTP header, so we need to copy from it
            // starting after that)
            System.arraycopy(fecPacket.getBuffer(), fecPacket.getFlexFecHeaderOffset(),
                recoveredPacket.getBuffer(), 0, RawPacket.FIXED_HEADER_SIZE);

            // Copy over the recovery rtp payload data from the fec packet
            System.arraycopy(
                fecPacket.getBuffer(),
                fecPacket.getFlexFecHeaderOffset() + fecPacket.getFlexFecHeaderSize(),
                recoveredPacket.getBuffer(),
                RawPacket.FIXED_HEADER_SIZE,
                fecPacket.getFlexFecPayloadLength());

            return true;
        }

        /**
         * Xor the RTP headers of source and destination
         * @param source the packet to xor the header from
         * @param dest the packet to xor the header into
         */
        private void xorHeaders(RawPacket source, RawPacket dest)
        {
            // XOR the first 2 bytes of the header: V, P, X, CC, M, PT fields.
            dest.getBuffer()[0] ^= source.getBuffer()[source.getOffset() + 0];
            dest.getBuffer()[1] ^= source.getBuffer()[source.getOffset() + 1];

            // XOR the length recovery field.
            int length = (source.getLength() & 0xffff) - RawPacket.FIXED_HEADER_SIZE;
            dest.getBuffer()[2] ^= (length >> 8);
            dest.getBuffer()[3] ^= (length & 0x00ff);

            // XOR the 5th to 8th bytes of the header: the timestamp field.
            dest.getBuffer()[4] ^= source.getBuffer()[source.getOffset() + 4];
            dest.getBuffer()[5] ^= source.getBuffer()[source.getOffset() + 5];
            dest.getBuffer()[6] ^= source.getBuffer()[source.getOffset() + 6];
            dest.getBuffer()[7] ^= source.getBuffer()[source.getOffset() + 7];

            // Skip the 9th to 12th bytes of the header.
        }

        /**
         * Xor the payloads of the source and destination
         * @param source the packet to xor the payload from
         * @param sourceOffset the offset in the source packet at which the
         * payload begins.  Note that this is not necessarily the location
         * at which the RTP payload starts...for the purpose of FlexFEC,
         * everything after the fixed RTP header is considered the 'payload'
         * @param dest the packet to xor the payload into
         * @param destOffset the offset in the dest packet at which the payload
         * begins
         * @param payloadLength the length of the source's payload
         */
        private void xorPayloads(byte[] source, int sourceOffset,
                                 byte[] dest, int destOffset,
                                 int payloadLength)
        {
            for (int i = 0; i < payloadLength; ++i)
            {
                dest[destOffset + i] ^=
                    source[sourceOffset + i];
            }
        }

        /**
         * Do the final work when recovering an RTP packet (set the RTP version,
         * the length, the sequence number, and the ssrc)
         * @param fecPacket the fec packet
         * @param recoveredPacket the media packet which was recovered
         */
        private boolean finishPacketRecovery(FlexFec03Packet fecPacket, RawPacket recoveredPacket)
        {
            // Set the RTP version to 2.
            recoveredPacket.getBuffer()[0] |= 0x80; // Set the 1st bit
            recoveredPacket.getBuffer()[0] &= 0xbf; // Clear the second bit

            // Recover the packet length, from temporary location.
            int length = RTPUtils.readUint16AsInt(recoveredPacket.getBuffer(), 2);
            // The length field used in the xor does not include the header
            // length, but we want to include the fixed header length when
            // setting the length on the packet object
            int lengthWithFixedHeader = length + RawPacket.FIXED_HEADER_SIZE;
            if (lengthWithFixedHeader > recoveredPacket.getBuffer().length)
            {
                logger.error("Length field of recovered packet is larger" +
                    " than its buffer");
                return false;
            }

            recoveredPacket.setLength(lengthWithFixedHeader);
            recoveredPacket.setSequenceNumber(missingSequenceNumber);
            recoveredPacket.setSSRC((int)fecPacket.getProtectedSsrc());

            return true;
        }

        /**
         * Recover a media packet based on the given FlexFEC packet and
         * received media packets
         * @return the recovered packet if successful, null otherwise
         */
        private RawPacket recover()
        {
            if (!canRecover())
            {
                return null;
            }

            //TODO: use a pool?
            byte[] buf = new byte[1500];
            RawPacket recoveredPacket = new RawPacket(buf, 0, 1500);
            if (!startPacketRecovery(this.fecPacket, recoveredPacket))
            {
                return null;
            }
            for (Integer protectedSeqNum : fecPacket.getProtectedSequenceNumbers())
            {
                if (protectedSeqNum != missingSequenceNumber)
                {
                    RawPacket mediaPacket = mediaPackets.get(protectedSeqNum);
                    xorHeaders(mediaPacket, recoveredPacket);
                    xorPayloads(
                        mediaPacket.getBuffer(),
                        mediaPacket.getOffset() + RawPacket.FIXED_HEADER_SIZE,
                        recoveredPacket.getBuffer(),
                        RawPacket.FIXED_HEADER_SIZE,
                        mediaPacket.getLength() - RawPacket.FIXED_HEADER_SIZE);
                }
            }
            if (!finishPacketRecovery(fecPacket, recoveredPacket))
            {
                return null;
            }
            return recoveredPacket;
        }
    }

}
