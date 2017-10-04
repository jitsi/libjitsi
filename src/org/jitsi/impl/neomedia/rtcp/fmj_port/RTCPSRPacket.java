package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPSRPacket
 */
public class RTCPSRPacket
    extends RTCPPacket
{
    public int ssrc;
    public long ntptimestampmsw;
    public long ntptimestamplsw;
    public long rtptimestamp;
    public long packetcount;
    public long octetcount;
    public RTCPReportBlock reports[];

    public RTCPSRPacket(int ssrc, RTCPReportBlock reports[])
    {
        if (reports.length > 31)
            throw new IllegalArgumentException("Too many reports");

        this.ssrc = ssrc;
        this.reports = reports;
    }

    RTCPSRPacket(RTCPPacket parent)
    {
        super(parent);
        super.type = SR;
    }

    @Override
    public void assemble(DataOutputStream out) throws IOException
    {
        out.writeByte(128 + reports.length);
        out.writeByte(SR);
        out.writeShort(6 + reports.length * 6);
        out.writeInt(ssrc);
        out.writeInt((int) ntptimestampmsw);
        out.writeInt((int) ntptimestamplsw);
        out.writeInt((int) rtptimestamp);
        out.writeInt((int) packetcount);
        out.writeInt((int) octetcount);
        for (int i = 0; i < reports.length; i++)
        {
            out.writeInt(reports[i].ssrc);
            out.writeInt((reports[i].packetslost & 0xffffff)
                    + (reports[i].fractionlost << 24));
            out.writeInt((int) reports[i].lastseq);
            out.writeInt(reports[i].jitter);
            out.writeInt((int) reports[i].lsr);
            out.writeInt((int) reports[i].dlsr);
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
                + "\n\t\tNTP timestampMSW: " + ntptimestampmsw
                + "\n\t\tNTP timestampLSW: " + ntptimestamplsw
                + "\n\t\tRTP timestamp: " + rtptimestamp
                + "\n\t\tnumber of packets sent: " + packetcount
                + "\n\t\tnumber of octets (bytes) sent: " + octetcount + "\n"
                + RTCPReportBlock.toString(reports);
    }
}

