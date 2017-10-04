package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPSDESPacket
 */
public class RTCPSDESPacket
    extends RTCPPacket
{
    public RTCPSDES sdes[];

    public RTCPSDESPacket(RTCPPacket parent)
    {
        super(parent);
        super.type = SDES;
    }

    public RTCPSDESPacket(RTCPSDES sdes[])
    {
        if (sdes.length > 31)
            throw new IllegalArgumentException("Too many SDESs");

        this.sdes = sdes;
    }

    @Override
    public void assemble(DataOutputStream out) throws IOException
    {
        out.writeByte(128 + sdes.length);
        out.writeByte(SDES);
        out.writeShort(calcLength() - 4 >> 2);
        for (int i = 0; i < sdes.length; i++)
        {
            out.writeInt(sdes[i].ssrc);
            int sublen = 0;
            for (int j = 0; j < sdes[i].items.length; j++)
            {
                out.writeByte(sdes[i].items[j].type);
                out.writeByte(sdes[i].items[j].data.length);
                out.write(sdes[i].items[j].data);
                sublen += 2 + sdes[i].items[j].data.length;
            }

            for (int j = (sublen + 4 & -4) - sublen; j > 0; j--)
                out.writeByte(0);
        }
    }

    @Override
    public int calcLength()
    {
        int len = 4;
        for (int i = 0; i < sdes.length; i++)
        {
            int sublen = 5;
            for (int j = 0; j < sdes[i].items.length; j++)
                sublen += 2 + sdes[i].items[j].data.length;

            sublen = sublen + 3 & -4;
            len += sublen;
        }

        return len;
    }

    @Override
    public String toString()
    {
        return "\tRTCP SDES Packet:\n" + RTCPSDES.toString(sdes);
    }
}
