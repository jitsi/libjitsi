package org.jitsi.impl.neomedia.rtcp.fmj_port;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPSDESItem
 */
public class RTCPSDESItem
{
    public int type;
    public byte data[];
    public static final int CNAME = 1;
    public static final int NAME = 2;
    public static final int EMAIL = 3;
    public static final int PHONE = 4;
    public static final int LOC = 5;
    public static final int TOOL = 6;
    public static final int NOTE = 7;
    public static final int PRIV = 8;
    public static final int HIGHEST = 8;
    public static final String names[] = { "CNAME", "NAME", "EMAIL", "PHONE",
            "LOC", "TOOL", "NOTE", "PRIV" };

    public static String toString(RTCPSDESItem items[])
    {
        String s = "";
        for (int i = 0; i < items.length; i++)
            s = s + items[i];

        return s;
    }

    public RTCPSDESItem()
    {
    }

    public RTCPSDESItem(int type, byte[] data)
    {
        this.type = type;
        this.data = data;
    }

    public RTCPSDESItem(int type, String s)
    {
        this.type = type;
        data = new byte[s.length()];
        data = s.getBytes();
    }

    @Override
    public String toString()
    {
        return "\t\t\t" + names[type - 1] + ": " + new String(data) + "\n";
    }

}
