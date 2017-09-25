package org.jitsi.impl.neomedia.rtcp;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.io.*;

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
public class NewRTCPSRPacket extends NewRTCPPacket
{
    protected long ssrc;
    protected long ntpTimestampMsw;
    protected long ntpTimestampLsw;
    protected long rtpTimestamp;
    protected long packetCount;
    protected long octetCount;
    protected NewRTCPReportBlock reports[];

    private NewRTCPSRPacket(long ssrc,
                            long ntpTimestampMsw,
                            long ntpTimestampLsw,
                            long rtpTimestamp,
                            long packetCount,
                            long octetCount,
                            NewRTCPReportBlock[] reports)
    {
        super.type = SR;
        this.ssrc = ssrc;
        this.ntpTimestampMsw = ntpTimestampMsw;
        this.ntpTimestampLsw = ntpTimestampLsw;
        this.rtpTimestamp = rtpTimestamp;
        this.packetCount = packetCount;
        this.octetCount = octetCount;
        this.reports = reports;
    }

    public long getSsrc()
    {
        return ssrc;
    }

    public long getNtpTimestampMsw()
    {
        return ntpTimestampMsw;
    }

    public long getNtpTimestampLsw()
    {
        return ntpTimestampLsw;
    }

    public long getRtpTimestamp()
    {
        return rtpTimestamp;
    }

    public long getPacketCount()
    {
        return packetCount;
    }

    public long getOctetCount()
    {
        return octetCount;
    }

    public NewRTCPReportBlock[] getReports()
    {
        return reports;
    }

    /**
     * Given a RawPacket, parse it into a NewRTCPSRPacket
     * @param srPacket the srPacket (in {@link RawPacket} form) to be parsed
     * @return the parsed {@link NewRTCPSRPacket}, null if parsing failed
     */
    static NewRTCPSRPacket parse(RawPacket srPacket)
        throws IOException
    {
        if (srPacket.getRTCPPacketType() != NewRTCPPacket.SR)
        {
            return null;
        }
        DataInputStream dataInputStream =
            new DataInputStream(new ByteArrayInputStream(
                srPacket.getBuffer(), srPacket.getOffset(), srPacket.getLength()));

        byte firstByte = dataInputStream.readByte();
        byte pt = dataInputStream.readByte();
        //TODO(brian): validate length
        short length = dataInputStream.readShort();

        long ssrc = dataInputStream.readInt() & 0xFFFFFFFFL;
        long ntpTimestampMsw = dataInputStream.readInt() & 0xFFFFFFFFL;
        long ntpTimestampLsw = dataInputStream.readInt() & 0xFFFFFFFFL;
        long rtpTimestamp = dataInputStream.readInt() & 0xFFFFFFFFL;
        long packetCount = dataInputStream.readInt() & 0xFFFFFFFFL;
        long octetCount = dataInputStream.readInt() & 0xFFFFFFFFL;
        int reportCount = RTCPHeaderUtils.getReportCount(srPacket);
        NewRTCPReportBlock[] reports = new NewRTCPReportBlock[reportCount];
        for (int i = 0; i < reportCount; ++i)
        {
            reports[i] = NewRTCPReportBlock.parse(dataInputStream);
        }

        return new NewRTCPSRPacket(ssrc,
            ntpTimestampMsw,
            ntpTimestampLsw,
            rtpTimestamp,
            packetCount,
            octetCount,
            reports);
    }

    @Override
    public void assemble(DataOutputStream out) throws IOException
    {
        out.writeByte(128 + reports.length);
        out.writeByte(SR);
        out.writeShort(6 + reports.length * 6);
        out.writeInt((int)RTPUtils.as32Bits(ssrc));
        out.writeInt((int) ntpTimestampMsw);
        out.writeInt((int) ntpTimestampLsw);
        out.writeInt((int) rtpTimestamp);
        out.writeInt((int) packetCount);
        out.writeInt((int) octetCount);
        for (NewRTCPReportBlock block : reports)
        {
            out.writeInt((int)RTPUtils.as32Bits(block.ssrc));
            out.writeInt((block.packetsLost & 0xffffff)
                + (block.fractionLost << 24));
            out.writeInt((int) block.lastSeq);
            out.writeInt(block.jitter);
            out.writeInt((int) block.lsr);
            out.writeInt((int) block.dlsr);
        }
    }

    @Override
    public int calcLength()
    {
        return 28 + reports.length * 24;
    }

    @Override
    public String toString()
    {
        return "\tRTCP SR (sender report) packet for sync source " + ssrc
            + "\n\t\tNTP timestampMSW: " + ntpTimestampMsw
            + "\n\t\tNTP timestampLSW: " + ntpTimestampLsw
            + "\n\t\tRTP timestamp: " + rtpTimestamp
            + "\n\t\tnumber of packets sent: " + packetCount
            + "\n\t\tnumber of octets (bytes) sent: " + octetCount + "\n"
            + NewRTCPReportBlock.toString(reports);
    }
}
