package org.jitsi.impl.neomedia.rtcp.fmj_port;

import org.jitsi.util.ByteBufferOutputStream;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPCompoundPacket
 */
public class RTCPCompoundPacket
    extends RTCPPacket
{
    public RTCPPacket packets[];

    public RTCPCompoundPacket(Packet base)
    {
        super(base);
        super.type = COMPOUND;
    }

    public RTCPCompoundPacket(RTCPPacket packets[])
    {
        this.packets = packets;
        super.type = COMPOUND;
        super.received = false;
    }

    @Override
    public void assemble(DataOutputStream out) throws IOException
    {
        throw new IllegalArgumentException("Recursive Compound Packet");
    }

    public void assemble(int len, boolean encrypted)
    {
        length = len;
        offset = 0;

        /*
         * XXX We cannot reuse the data of super/this because it may be the same
         * as the data of base at this time.
         */
        byte[] d = new byte[len];
        ByteBufferOutputStream bbos = new ByteBufferOutputStream(d, 0, len);
        DataOutputStream dos = new DataOutputStream(bbos);
        int laststart;
        try
        {
            if (encrypted)
                offset += 4;
            laststart = offset;
            for (int i = 0; i < packets.length; i++)
            {
                laststart = bbos.size();
                packets[i].assemble(dos);
            }

        } catch (IOException e)
        {
            throw new NullPointerException("Impossible IO Exception");
        }
        int prelen = bbos.size();
        super.data = d;
        if (prelen > len)
            throw new NullPointerException("RTCP Packet overflow");
        if (prelen < len)
        {
            if (data.length < len)
                System.arraycopy(data, 0, data = new byte[len], 0, prelen);
            data[laststart] |= 0x20;
            data[len - 1] = (byte) (len - prelen);
            int temp = (data[laststart + 3] & 0xff) + (len - prelen >> 2);
            if (temp >= 256)
                data[laststart + 2] += len - prelen >> 10;
            data[laststart + 3] = (byte) temp;
        }
    }

    @Override
    public int calcLength()
    {
        if (packets == null || packets.length < 1)
            throw new IllegalArgumentException("Bad RTCP Compound Packet");

        int len = 0;

        for (int i = 0; i < packets.length; i++)
            len += packets[i].calcLength();
        return len;
    }

    @Override
    public String toString()
    {
        return "RTCP Packet with the following subpackets:\n"
                + toString(packets);
    }

    public String toString(RTCPPacket packets[])
    {
        String s = "";
        for (int i = 0; i < packets.length; i++)
            s = s + packets[i];

        return s;
    }
}
