package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * NOTE(brian): was javax.media.rtp.rtcp.SourceDescription
 */
public class SourceDescription implements java.io.Serializable
{
    public static final int SOURCE_DESC_CNAME = 1;

    public static final int SOURCE_DESC_NAME = 2;

    public static final int SOURCE_DESC_EMAIL = 3;

    public static final int SOURCE_DESC_PHONE = 4;

    public static final int SOURCE_DESC_LOC = 5;

    public static final int SOURCE_DESC_TOOL = 6;

    public static final int SOURCE_DESC_NOTE = 7;

    public static final int SOURCE_DESC_PRIV = 8;

    public static String generateCNAME()
    {
        // generates something like user@host
        final String hostname;
        try
        {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e)
        {
            throw new RuntimeException(e);
        }

        return System.getProperty("user.name") + '@' + hostname;

    }

    private int type;

    private String description;

    private int frequency;

    private boolean encrypted;

    public SourceDescription(int type, String description, int frequency,
                             boolean encrypted)
    {
        this.type = type;
        this.description = description;
        this.frequency = frequency;
        this.encrypted = encrypted;
    }

    public String getDescription()
    {
        return description;
    }

    public boolean getEncrypted()
    {
        return encrypted;
    }

    public int getFrequency()
    {
        return frequency;
    }

    public int getType()
    {
        return type;
    }

    public void setDescription(String desc)
    {
        this.description = desc;
    }
}
