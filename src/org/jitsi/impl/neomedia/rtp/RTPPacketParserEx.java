/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp;

import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * Extends the FMJ <tt>RTPPacketParser</tt> with additional functionality.
 *
 * @author George Politis
 */
public class RTPPacketParserEx
        extends net.sf.fmj.media.rtp.util.RTPPacketParser
{
    private static final Logger logger
            = Logger.getLogger(RTPPacketParserEx.class);

    public RTPPacket parse(RawPacket pkt) throws BadFormatException
    {
        if (pkt == null)
        {
            logger.warn("pkt is null.");
            return null;
        }

        return parse(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
    }

    public RTPPacket parse(byte[] data, int offset, int length) throws BadFormatException
    {
        UDPPacket udp = new UDPPacket();

        udp.data = data;
        udp.length = length;
        udp.offset = offset;
        udp.received = false;
        return parse(udp);
    }
}
