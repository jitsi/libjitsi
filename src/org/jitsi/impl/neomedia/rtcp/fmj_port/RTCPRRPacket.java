package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPRRPacket
 */
public class RTCPRRPacket
    extends RTCPPacket
{
    public int ssrc;
    public RTCPReportBlock reports[];

    public RTCPRRPacket(int ssrc, RTCPReportBlock reports[])
    {
        if (reports.length > 31)
            throw new IllegalArgumentException("Too many reports");

        this.ssrc = ssrc;
        this.reports = reports;
    }

    RTCPRRPacket(RTCPPacket parent)
    {
        super(parent);
        super.type = RR;
    }

    @Override
    public void assemble(DataOutputStream out) throws IOException
    {
        out.writeByte(128 + reports.length);
        out.writeByte(RR);
        out.writeShort(1 + reports.length * 6);
        out.writeInt(ssrc);
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
        return 8 + reports.length * 24;
    }

    @Override
    public String toString()
    {
        return "\tRTCP RR (receiver report) packet for sync source " + ssrc
                + ":\n" + RTCPReportBlock.toString(reports);
    }
}
