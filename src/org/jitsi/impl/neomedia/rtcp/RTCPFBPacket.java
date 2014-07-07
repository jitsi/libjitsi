/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp;

import net.sf.fmj.media.rtp.*;

/**
 * Created by gp on 6/27/14.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|   FMT   |       PT      |          length               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of packet sender                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of media source                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * :            Feedback Control Information (FCI)                 :
 * :                                                               :
 */
public abstract class RTCPFBPacket extends RTCPPacket
{
    public RTCPFBPacket(int fmt, int type, long senderSSRC, long sourceSSRC)
    {
        this.fmt = fmt;
        super.type = type;
        this.senderSSRC = senderSSRC;
        this.sourceSSRC = sourceSSRC;
    }

    public RTCPFBPacket(RTCPCompoundPacket base)
    {
        super(base);
    }

    /**
     * Feedback message type (FMT).
     */
    public int fmt;

    /**
     * SSRC of packet sender.
     */
    public long senderSSRC;

    /**
     * SSRC of media source.
     */
    public long sourceSSRC;
}
