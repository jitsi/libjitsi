package org.jitsi.impl.neomedia.rtcp.fmj_port;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.util.BadVersionException
 */
public class BadVersionException extends BadFormatException
{
    public BadVersionException(String s)
    {
        super(s);
    }
}
