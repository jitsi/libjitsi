/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util.function;

import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.*;

import java.util.*;

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
        Objects.requireNonNull(input);

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
