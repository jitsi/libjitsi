package org.jitsi.impl.neomedia.transform.fec;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.util.*;

/**
 * Created by bbaldino on 11/9/17.
 */
//FIXME: anywhere we use getBuffer needs to take into account getOffset
public class FlexFecReceiver
    extends AbstractFECReceiver
{
    /**
     * The <tt>Logger</tt> used by the <tt>FlexFecReceiver</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(FlexFecReceiver.class);

    /**
     * FEC-related statistics
     */
    private Statistics statistics;

    /**
     * Helper class to reconstruct missing packets
     */
    private Reconstructor reconstructor;

    public FlexFecReceiver(long mediaSsrc, byte fecPayloadType)
    {
        super(mediaSsrc, fecPayloadType);
        this.statistics = new Statistics();
        this.reconstructor = new Reconstructor(mediaPackets);
    }

    @Override
    public synchronized RawPacket[] doReverseTransform(RawPacket[] pkts)
    {
        Set<Integer> flexFecPacketsToRemove = new HashSet<>();
        // Try to recover any missing media packets
        for (Map.Entry<Integer, RawPacket> entry : fecPackets.entrySet())
        {
            FlexFecPacket flexFecPacket = FlexFecPacket.create(entry.getValue());
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
                logger.debug("Attemping recovery of missing sequence " +
                    "number " + reconstructor.missingSequenceNumber);
                flexFecPacketsToRemove.add(flexFecPacket.getSequenceNumber());
                RawPacket recovered = reconstructor.recover();
                if (recovered != null)
                {
                    logger.info("Recovered packet " +
                        recovered.getSequenceNumber());
                    statistics.numRecoveredPackets++;
                    saveMedia(recovered);
                    pkts = insert(recovered, pkts);
                }
            }
        }

        for (Integer flexFecSeqNum : flexFecPacketsToRemove)
        {
            fecPackets.remove(flexFecSeqNum);
        }
        return pkts;
    }

    /**
     * Inserts packet into an empty slot in pkts, or allocates a new
     * array and inserts packet into it.  Returns either the original
     * array (with packet insert) or a new array containing the original contents
     * of pkts and with packet inserted
     * @param packet the packet to be inserted
     * @param pkts the array in which to insert packet
     * @return the original pkts array with packet inserted, or, a new array
     * containing all elements in pkts as well as packet
     */
    private RawPacket[] insert(RawPacket packet, RawPacket[] pkts)
    {
        for (int i = 0; i < pkts.length; ++i)
        {
            if (pkts[i] == null)
            {
                pkts[i] = packet;
                return pkts;
            }
        }

        RawPacket[] newPkts = new RawPacket[pkts.length + 1];
        System.arraycopy(pkts, 0, newPkts, 0, pkts.length);
        newPkts[pkts.length] = packet;
        return newPkts;
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
        private FlexFecPacket fecPacket = null;

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

        public void setFecPacket(FlexFecPacket p)
        {
            numMissing = 0;
            logger.debug("Reconstructor checking if recovery is " +
                "possible: fec packet " + p.getSequenceNumber() +
                " protects packets:\n" + p.getProtectedSequenceNumbers());
            this.fecPacket = p;
            if (p == null)
            {
                logger.error("Error creating flexfec packet");
                return;
            }

            for (Integer protectedSeqNum : fecPacket.getProtectedSequenceNumbers())
            {
                logger.debug("Checking if we've received media " +
                    "packet " + protectedSeqNum);
                if (!mediaPackets.containsKey(protectedSeqNum))
                {
                    logger.debug("We haven't, mark as missing");
                    numMissing++;
                    missingSequenceNumber = protectedSeqNum;
                }
            }
            logger.debug("There were " + numMissing + " missing media " +
                "packets for flexfec " + p.getSequenceNumber());
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
        private boolean startPacketRecovery(FlexFecPacket fecPacket, RawPacket recoveredPacket)
        {
            if (fecPacket.getLength() < FlexFecPacket.FIXED_HEADER_SIZE)
            {
                logger.error("Given FlexFEC packet is too small");
                return false;
            }
            if (recoveredPacket.getBuffer().length < RawPacket.FIXED_HEADER_SIZE)
            {
                logger.error("Given RawPacket buffer is too small");
                return false;
            }
            // Copy over the recovery RTP header data from the fec packet
            // (fecPacket contains the RTP header, so we need to copy from it
            // starting after that)
            System.arraycopy(fecPacket.getBuffer(), fecPacket.getHeaderLength(),
                recoveredPacket.getBuffer(), 0, RawPacket.FIXED_HEADER_SIZE);

            // Copy over the recovery rtp payload data from the fec packet
            System.arraycopy(
                fecPacket.getBuffer(),
                fecPacket.getHeaderLength() + fecPacket.flexFecHeaderSizeBytes,
                recoveredPacket.getBuffer(),
                RawPacket.FIXED_HEADER_SIZE,
                fecPacket.getPayloadLength());

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
            dest.getBuffer()[0] ^= source.getBuffer()[0];
            dest.getBuffer()[1] ^= source.getBuffer()[1];

            // XOR the length recovery field.
            int length = (source.getLength() & 0xffff) - RawPacket.FIXED_HEADER_SIZE;
            dest.getBuffer()[2] ^= (length >> 8);
            dest.getBuffer()[3] ^= (length & 0x00ff);

            // XOR the 5th to 8th bytes of the header: the timestamp field.
            dest.getBuffer()[4] ^= source.getBuffer()[4];
            dest.getBuffer()[5] ^= source.getBuffer()[5];
            dest.getBuffer()[6] ^= source.getBuffer()[6];
            dest.getBuffer()[7] ^= source.getBuffer()[7];

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
        private void xorPayloads(RawPacket source, int sourceOffset,
                                 RawPacket dest, int destOffset,
                                 int payloadLength)
        {
            for (int i = 0; i < payloadLength; ++i)
            {
                dest.getBuffer()[destOffset + i] ^=
                    source.getBuffer()[sourceOffset + i];
            }
        }

        /**
         * Do the final work when recovering an RTP packet (set the RTP version,
         * the length, the sequence number, and the ssrc)
         * @param fecPacket the fec packet
         * @param recoveredPacket the media packet which was recovered
         */
        private boolean finishPacketRecovery(FlexFecPacket fecPacket, RawPacket recoveredPacket)
        {
            // Set the RTP version to 2.
            recoveredPacket.getBuffer()[0] |= 0x80; // Set the 1st bit
            recoveredPacket.getBuffer()[0] &= 0xbf; // Clear the second bit

            // Recover the packet length, from temporary location.
            int length = RTPUtils.readUint16AsInt(recoveredPacket.getBuffer(), 2);
            if (length > recoveredPacket.getBuffer().length)
            {
                logger.error("Length field of recovered packet is larger" +
                    " than its buffer");
                return false;
            }
            // The length field used in the xor does not include the header
            // length, but we want to include the fixed header length when
            // setting the length on the packet object
            recoveredPacket.setLength(length + RawPacket.FIXED_HEADER_SIZE);

            // Set the SN field.
            RTPUtils.writeShort(recoveredPacket.getBuffer(), 2,
                (short)missingSequenceNumber);

            // Set the SSRC field.
            RTPUtils.writeInt(recoveredPacket.getBuffer(), 8,
                (int)fecPacket.protectedSsrc);

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
                        mediaPacket,
                        RawPacket.FIXED_HEADER_SIZE,
                        recoveredPacket,
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
