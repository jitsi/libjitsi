/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util.function;

import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.*;

/**
 * A <tt>Function</tt> that produces <tt>RawPacket</tt>s from
 * <tt>RTPPacket</tt>s.
 *
 * @author George Politis
 */
public class RTPGenerator extends AbstractFunction<RTPPacket, RawPacket>
{
    public RawPacket apply(RTPPacket input)
    {
        if (input == null)
        {
            throw new NullPointerException();
        }

        // Assemble the RTP packet.
        int len = input.calcLength();
        input.assemble(len, false);
        byte[] buf = input.data;

        RawPacket pktOut = new RawPacket();

        pktOut.setBuffer(buf);
        pktOut.setLength(buf.length);
        pktOut.setOffset(0);

        return pktOut;
    }
}
