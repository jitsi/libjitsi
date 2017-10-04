package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Created by gp on 6/23/14.
 */
public class RTCPPacketParser
{
    private final List<RTCPPacketParserListener> listeners
            = new ArrayList<>();

    public void addRtcpPacketParserListener(RTCPPacketParserListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (listeners)
        {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    private void onEnterSenderReport()
    {
        synchronized (listeners)
        {
            for (RTCPPacketParserListener l : listeners)
            {
                l.enterSenderReport();
            }
        }
    }

    private void onMalformedEndOfParticipation()
    {
        synchronized (listeners)
        {
            for (RTCPPacketParserListener l : listeners)
            {
                l.malformedEndOfParticipation();
            }
        }
    }

    private void onMalformedReceiverReport()
    {
        synchronized (listeners)
        {
            for (RTCPPacketParserListener l : listeners)
            {
                l.malformedReceiverReport();
            }
        }
    }


    private void onMalformedSenderReport()
    {
        synchronized (listeners)
        {
            for (RTCPPacketParserListener l : listeners)
            {
                l.malformedSenderReport();
            }
        }
    }

    private void onMalformedSourceDescription()
    {
        synchronized (listeners)
        {
            for (RTCPPacketParserListener l : listeners)
            {
                l.malformedSourceDescription();
            }
        }
    }

    private void onPayloadUknownType()
    {
        synchronized (listeners)
        {
            for (RTCPPacketParserListener l : listeners)
            {
                l.uknownPayloadType();
            }
        }
    }

    private void onVisitSenderReport(RTCPSRPacket rtcpSrPacket)
    {
        synchronized (listeners)
        {
            for (RTCPPacketParserListener l : listeners)
            {
                l.visitSendeReport(rtcpSrPacket);
            }
        }
    }

    public RTCPPacket parse(Packet packet) throws BadFormatException
    {
        RTCPCompoundPacket base = new RTCPCompoundPacket(packet);
        Vector<RTCPPacket> subpackets = new Vector<RTCPPacket>(2);
        DataInputStream in
                = new DataInputStream(
                new ByteArrayInputStream(
                        base.data,
                        base.offset,
                        base.length));
        try
        {
            int length;
            for (int offset = 0; offset < base.length; offset += length)
            {
/*
        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
header |V=2|P|    NA   |      PT       |             length            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

                // version (2 bits), padding (1 bit), 5 bits of data
                int firstbyte = in.readUnsignedByte();

                // version must be 2.
                if ((firstbyte & 0xc0) != 128)
                {
                    throw new BadVersionException(
                            "version must be 2. (base.length " + base.length
                                    + ", base.offset " + base.offset
                                    + ", firstbyte 0x"
                                    + Integer.toHexString(firstbyte) + ", offset "
                                    + offset + ")");
                }

                int type = in.readUnsignedByte(); // 8 bits

                // length: 16 bits The length of this RTCP packet in 32-bit
                // words minus one, including the header and any padding.
                length = in.readUnsignedShort();
                // packet length in bytes
                length = length + 1 << 2;

                if (offset + length > base.length)
                {
                    throw new BadFormatException(
                            "Packet length less than actual packet length");
                }

                // find padding length, if any.
                int padlen = 0;
                if (offset + length == base.length)
                {
                    if ((firstbyte & 0x20) != 0)
                    {
                        // packet has padding.
                        padlen
                                = base.data[base.offset + base.length - 1] & 0xff;
                        if (padlen == 0)
                            throw new BadFormatException();
                    }
                } else if ((firstbyte & 0x20) != 0)
                    throw new BadFormatException("No padding found.");

                int inlength = length - padlen;

                // discard version and padding from firstbyte.
                int rc = firstbyte & 0x1f;
                RTCPPacket p;
                switch (type)
                {
                    case RTCPPacket.SR:
/*
        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
header |V=2|P|    RC   |   PT=SR=200   |             length            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         SSRC of sender                        |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
sender |              NTP timestamp, most significant word             |
info   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |             NTP timestamp, least significant word             |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         RTP timestamp                         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                     sender's packet count                     |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                      sender's octet count                     |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_1 (SSRC of first source)                 |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  1    | fraction lost |       cumulative number of packets lost       |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |           extended highest sequence number received           |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                      interarrival jitter                      |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         last SR (LSR)                         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                   delay since last SR (DLSR)                  |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_2 (SSRC of second source)                |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  2    :                               ...                             :
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
       |                  profile-specific extensions                  |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
                        onEnterSenderReport();
                        if (inlength != 28 + 24 * rc)
                        {
                            onMalformedSenderReport();
                            System.out.println("bad format.");
                            throw new BadFormatException(
                                    "inlength != 28 + 24 * firstbyte");
                        }
                        RTCPSRPacket srp = new RTCPSRPacket(base);
                        p = srp;
                        srp.ssrc = in.readInt();
                        srp.ntptimestampmsw = in.readInt() & 0xffffffffL;
                        srp.ntptimestamplsw = in.readInt() & 0xffffffffL;
                        srp.rtptimestamp = in.readInt() & 0xffffffffL;
                        srp.packetcount = in.readInt() & 0xffffffffL;
                        srp.octetcount = in.readInt() & 0xffffffffL;
                        srp.reports = new RTCPReportBlock[rc];
                        onVisitSenderReport(srp);
                        readRTCPReportBlock(in, srp.reports);
                        break;

                    case RTCPPacket.RR:
/*
        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
header |V=2|P|    RC   |   PT=RR=201   |             length            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                     SSRC of packet sender                     |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_1 (SSRC of first source)                 |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  1    | fraction lost |       cumulative number of packets lost       |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |           extended highest sequence number received           |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                      interarrival jitter                      |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         last SR (LSR)                         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                   delay since last SR (DLSR)                  |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_2 (SSRC of second source)                |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  2    :                               ...                             :
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
       |                  profile-specific extensions                  |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
                        if (inlength != 8 + 24 * rc)
                        {
                            onMalformedReceiverReport();
                            throw new BadFormatException(
                                    "inlength != 8 + 24 * firstbyte");
                        }
                        RTCPRRPacket rrp = new RTCPRRPacket(base);
                        p = rrp;
                        rrp.ssrc = in.readInt();
                        rrp.reports = new RTCPReportBlock[rc];
                        readRTCPReportBlock(in, rrp.reports);
                        break;

                    case RTCPPacket.SDES:
/*
        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
header |V=2|P|    SC   |  PT=SDES=202  |             length            |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
chunk  |                          SSRC/CSRC_1                          |
  1    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                           SDES items                          |
       |                              ...                              |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
chunk  |                          SSRC/CSRC_2                          |
  2    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                           SDES items                          |
       |                              ...                              |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 */
                        RTCPSDESPacket sdesp = new RTCPSDESPacket(base);
                        p = sdesp;
                        sdesp.sdes = new RTCPSDES[rc];
                        int sdesoff = 4;
                        for (int i = 0; i < sdesp.sdes.length; i++)
                        {
                            RTCPSDES chunk = new RTCPSDES();
                            sdesp.sdes[i] = chunk;
                            chunk.ssrc = in.readInt();
                            sdesoff += 5;
                            Vector<RTCPSDESItem> items
                                    = new Vector<RTCPSDESItem>();
                            boolean gotcname = false;
                            int j;
                            while ((j = in.readUnsignedByte()) != 0)
                            {
                                if (j < 1 || j > 8)
                                {
                                    onMalformedSourceDescription();
                                    throw new BadFormatException(
                                            "j < 1 || j > 8");
                                }
                                if (j == 1)
                                    gotcname = true;
                                RTCPSDESItem item = new RTCPSDESItem();
                                items.addElement(item);
                                item.type = j;
                                int sdeslen = in.readUnsignedByte();
                                item.data = new byte[sdeslen];
                                in.readFully(item.data);
                                sdesoff += 2 + sdeslen;
                            }
                            if (!gotcname)
                            {
                                onMalformedSourceDescription();
                                throw new BadFormatException("!gotcname");
                            }
                            chunk.items = new RTCPSDESItem[items.size()];
                            items.copyInto(chunk.items);
                            if ((sdesoff & 3) != 0)
                            {
                                in.skip(4 - (sdesoff & 3));
                                sdesoff = sdesoff + 3 & -4;
                            }
                        }

                        if (inlength != sdesoff)
                        {
                            onMalformedSourceDescription();
                            throw new BadFormatException("inlength != sdesoff");
                        }
                        break;

                    case RTCPPacket.BYE:
/*
       0                   1                   2                   3
       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |V=2|P|    SC   |   PT=BYE=203  |             length            |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                           SSRC/CSRC                           |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      :                              ...                              :
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
(opt) |     length    |               reason for leaving            ...
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
                        RTCPBYEPacket byep = new RTCPBYEPacket(base);
                        p = byep;
                        byep.ssrc = new int[rc];
                        for (int i = 0; i < byep.ssrc.length; i++)
                            byep.ssrc[i] = in.readInt();

                        int reasonlen;
                        if (inlength > 4 + 4 * rc)
                        {
                            reasonlen = in.readUnsignedByte();
                            byep.reason = new byte[reasonlen];
                            reasonlen++;
                        } else
                        {
                            reasonlen = 0;
                            byep.reason = new byte[0];
                        }
                        reasonlen = reasonlen + 3 & -4;
                        if (inlength != 4 + 4 * rc + reasonlen)
                        {
                            onMalformedEndOfParticipation();
                            throw new BadFormatException(
                                    "inlength != 4 + 4 * firstbyte + reasonlen");
                        }
                        in.readFully(byep.reason);
                        in.skip(reasonlen - byep.reason.length);
                        break;

                    case RTCPPacket.APP:
/*
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P| subtype |   PT=APP=204  |             length            |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                           SSRC/CSRC                           |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                          name (ASCII)                         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                   application-dependent data                ...
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
                        if (inlength < 12)
                            throw new BadFormatException("inlength < 12");
                        RTCPAPPPacket appp = new RTCPAPPPacket(base);
                        p = appp;
                        appp.ssrc = in.readInt();
                        appp.name = in.readInt();
                        appp.subtype = rc;
                        appp.data = new byte[inlength - 12];
                        in.readFully(appp.data);
                        in.skip(inlength - 12 - appp.data.length);
                        break;
                    default:

                        // Give a chance to an extended parser to parse the packet.
                        p = parse(base, firstbyte, type, inlength, in);

                        if (p == null)
                        {
                            onPayloadUknownType();
                            throw new BadFormatException("p == null");
                        }
                        break;
                }

                p.offset = offset;
                p.length = length;
                subpackets.addElement(p);
                in.skipBytes(padlen);
            }

        } catch (EOFException e)
        {
            throw new BadFormatException(
                    "Failed to parse an RTCP packet: " + e.getMessage());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException(
                    "Failed to parse an RTCP packet: ", e);
        }
        base.packets = new RTCPPacket[subpackets.size()];
        subpackets.copyInto(base.packets);
        return base;
    }

    protected RTCPPacket parse(
            RTCPCompoundPacket base,
            int firstbyte,
            int type,
            int length,
            DataInputStream in)
            throws BadFormatException, IOException
    {
        onPayloadUknownType();
        throw new BadFormatException("Unknown payload type");
    }

    private void readRTCPReportBlock(
            DataInputStream in,
            RTCPReportBlock[] reports)
            throws IOException
    {
        for (int i = 0; i < reports.length; i++)
        {
            RTCPReportBlock report = new RTCPReportBlock();
            reports[i] = report;
            report.ssrc = in.readInt();
            long val = in.readInt();
            val &= 0xffffffffL;
            report.fractionlost = (int) (val >> 24);
            report.packetslost = (int) (val & 0xffffffL);
            report.lastseq = in.readInt() & 0xffffffffL;
            report.jitter = in.readInt();
            report.lsr = in.readInt() & 0xffffffffL;
            report.dlsr = in.readInt() & 0xffffffffL;
        }
    }

    public void removeRtcpPacketParserListener(
            RTCPPacketParserListener listener)
    {
        if (listener != null)
        {
            synchronized (listeners)
            {
                listeners.remove(listener);
            }
        }
    }
}

