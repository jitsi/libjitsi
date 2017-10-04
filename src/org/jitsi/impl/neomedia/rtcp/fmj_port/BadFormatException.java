package org.jitsi.impl.neomedia.rtcp.fmj_port;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.util.BadFormatException
 */
public class BadFormatException extends Exception
{
    public BadFormatException()
    {
    }

    public BadFormatException(String m)
    {
        super(m);
    }
}
