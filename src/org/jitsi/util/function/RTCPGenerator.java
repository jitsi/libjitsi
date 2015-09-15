/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util.function;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;

/**
 * A <tt>Function</tt> that produces <tt>RawPacket</tt>s from
 * <tt>RTCPCompoundPacket</tt>s.
 *
 * @author George Politis
 */
public class RTCPGenerator
    extends AbstractFunction<RTCPCompoundPacket, RawPacket>
{
    public RawPacket apply(RTCPCompoundPacket input)
    {
        if (input == null)
        {
            return null;
        }

        // Assemble the RTP packet.
        int len = input.calcLength();

        // TODO we need to be able to re-use original RawPacket buffer.
        input.assemble(len, false);
        byte[] buf = input.data;

        RawPacket pktOut = new RawPacket();

        pktOut.setBuffer(buf);
        pktOut.setLength(buf.length);
        pktOut.setOffset(0);

        return pktOut;
    }
}
