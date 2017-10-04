package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPBYEPacket
 */
public class RTCPBYEPacket
    extends RTCPPacket
{
    int ssrc[];
    byte reason[];

    public RTCPBYEPacket(int ssrc[], byte reason[])
    {
        if (ssrc.length > 31)
            throw new IllegalArgumentException("Too many SSRCs");

        this.ssrc = ssrc;
        this.reason = (reason == null) ? new byte[0] : reason;
    }

    public RTCPBYEPacket(RTCPPacket parent)
    {
        super(parent);
        super.type = BYE;
    }

    @Override
    public void assemble(DataOutputStream out) throws IOException
    {
        out.writeByte(128 + ssrc.length);
        out.writeByte(BYE);
        out.writeShort(ssrc.length
                + (reason.length <= 0 ? 0 : reason.length + 4 >> 2));
        for (int i = 0; i < ssrc.length; i++)
            out.writeInt(ssrc[i]);

        if (reason.length > 0)
        {
            out.writeByte(reason.length);
            out.write(reason);
            for (int i = (reason.length + 4 & -4) - reason.length - 1; i > 0; i--)
                out.writeByte(0);
        }
    }

    @Override
    public int calcLength()
    {
        return 4 + (ssrc.length << 2)
                + (reason.length <= 0 ? 0 : reason.length + 4 & -4);
    }

    @Override
    public String toString()
    {
        return "\tRTCP BYE packet for sync source(s) "
                + toString(ssrc)
                + " for "
                + (reason.length <= 0 ? "no reason" : "reason "
                + new String(reason)) + "\n";
    }

    public String toString(int ints[])
    {
        if (ints.length == 0)
            return "(none)";
        String s = "" + ints[0];
        for (int i = 1; i < ints.length; i++)
            s = s + ", " + ints[i];

        return s;
    }
}
