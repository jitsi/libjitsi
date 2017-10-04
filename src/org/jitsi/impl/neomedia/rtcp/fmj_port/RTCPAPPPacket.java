package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPAPPPacket
 */
public class RTCPAPPPacket
    extends RTCPPacket
{
    public int ssrc;
    public int name;
    public int subtype;
    public byte data[];

    public RTCPAPPPacket(int ssrc, int name, int subtype, byte data[])
    {
        if ((data.length & 3) != 0)
            throw new IllegalArgumentException("Bad data length");
        if (subtype < 0 || subtype > 31)
            throw new IllegalArgumentException("Bad subtype");

        this.ssrc = ssrc;
        this.name = name;
        this.subtype = subtype;
        this.data = data;
        super.type = APP;
        super.received = false;
    }

    public RTCPAPPPacket(RTCPPacket parent)
    {
        super(parent);
        super.type = APP;
    }

    @Override
    public void assemble(DataOutputStream out) throws IOException
    {
        out.writeByte(128 + subtype);
        out.writeByte(APP);
        out.writeShort(2 + (data.length >> 2));
        out.writeInt(ssrc);
        out.writeInt(name);
        out.write(data);
    }

    @Override
    public int calcLength()
    {
        return 12 + data.length;
    }

    public String nameString(int name)
    {
        return "" + (char) (name >>> 24) + (char) (name >>> 16 & 0xff)
                + (char) (name >>> 8 & 0xff) + (char) (name & 0xff);
    }

    @Override
    public String toString()
    {
        return "\tRTCP APP Packet from SSRC " + ssrc + " with name "
                + nameString(name) + " and subtype " + subtype
                + "\n\tData (length " + data.length + "): " + new String(data)
                + "\n";
    }
}
