package org.jitsi.impl.neomedia.transform.fec;

import io.pkts.*;
import io.pkts.packet.*;
import io.pkts.packet.rtp.*;
import io.pkts.protocol.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.io.*;
import java.util.*;

/**
 * Created by bbaldino on 11/15/17.
 */
public class FecPacketReader
{
    private Pcap pcap;

    public FecPacketReader(String file)
        throws IOException
    {
        pcap = Pcap.openStream(file);
    }

    private static boolean checkIfComplete(FlexFecPacket flexFecPacket,
                                           Map<Integer, RawPacket> mediaPackets)
    {
        for (Integer protectedSeqNum : flexFecPacket.getProtectedSequenceNumbers())
        {
            if (!mediaPackets.containsKey(protectedSeqNum))
            {
                return false;
            }
        }
        return true;
    }

    private static Set<FlexFecPacket> checkIfAnyComplete(List<FlexFecPacket> flexFecPackets,
                                                    Map<Integer, RawPacket> mediaPackets)
    {
        Set<FlexFecPacket> completeFlexFecPackets = new HashSet<>();
        for (FlexFecPacket flexFecPacket : flexFecPackets)
        {
            if (checkIfComplete(flexFecPacket, mediaPackets))
            {
                completeFlexFecPackets.add(flexFecPacket);
            }
        }
        return completeFlexFecPackets;
    }

    /**
     * This method looks for a tcc header extension (id 5) and sets its value
     * to 0.
     * Tcc header extensions are a problem for verification because chrome
     * generates the fec packet after the tcc header has been allocated, but
     * before the actual value field in the extension is set, since the tcc
     * code doesn't run until later on in chrome's stack.  Because the input
     * to this test is a wireshark capture, the media packets will have the
     * actual tcc value set in the extension and therefore it will be
     * impossible to perfectly 'rebuild' a missing media packet, since the
     * xor for the fec packet didn't include any tcc data.  This isn't a bug,
     * more a side-effect of doing the test this way, so we set any tcc header
     * extension value fields to 0 so things verify correctly.
     * @param packet the packet in which to clear the tcc header extension's
     * value
     */
    private static void clearTccData(RawPacket packet)
    {
        RawPacket.HeaderExtension tcc = packet.getHeaderExtension((byte)5);
        if (tcc != null)
        {
            RTPUtils.writeUint24(tcc.getBuffer(), tcc.getOffset() + 1, 0);
        }
    }

    public void run(int videoPt, int flexFecPt, FlexFecReceiverTest.FecCaptureReadResult fecCaptureReadResult)
        throws IOException
    {
        pcap.loop(new PacketHandler() {
            List<FlexFecPacket> flexFecPackets = new ArrayList<>();
            Map<Integer, RawPacket> mediaPackets = new HashMap<>();

            @Override
            public boolean nextPacket(final Packet pkt) throws IOException
            {
                 // NOTE(brian): although pkts has support for parsing RTP,
                 // RTPFramer.java in pkts doesn't parse it correctly, so we'll
                 // only parse UDP here and then use our own classes to parse
                 // RTP.  It'd be nice to contribute some fixes to the RTP
                 // parsing back to pkts.
                if (pkt.hasProtocol(Protocol.UDP))
                {
                    UDPPacket udpPacket = (UDPPacket)pkt.getPacket(Protocol.UDP);
                    boolean checkForComplete = false;
                    RawPacket packet =
                        new RawPacket(udpPacket.getPayload().getArray(),
                            0, udpPacket.getPayload().getArray().length);

                    if (packet.getPayloadType() == flexFecPt)
                    {
                        FlexFecPacket flexFecPacket = FlexFecPacket.create(packet);
                        if (flexFecPacket == null)
                        {
                            return true;
                        }
                        flexFecPackets.add(flexFecPacket);
                        checkForComplete = true;
                    }
                    else if (packet.getPayloadType() == videoPt)
                    {
                        clearTccData(packet);
                        mediaPackets.put(packet.getSequenceNumber(), packet);
                        checkForComplete = true;
                    }

                    if (checkForComplete)
                    {
                        Set<FlexFecPacket> flexFecPacketsWithAllMediaPackets =
                            checkIfAnyComplete(flexFecPackets, mediaPackets);
                        if (!flexFecPacketsWithAllMediaPackets.isEmpty())
                        {
                            fecCaptureReadResult.flexFecPackets.addAll(flexFecPacketsWithAllMediaPackets);
                            fecCaptureReadResult.mediaPackets = mediaPackets;
                        }
                    }
                }

                return true;
            }
        });
    }
}
