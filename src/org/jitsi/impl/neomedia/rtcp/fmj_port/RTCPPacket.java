package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPPacket
 */
public abstract class RTCPPacket
    extends Packet
{
    public Packet base;
    public int type;
    public static final int SR = 200;
    public static final int RR = 201;
    public static final int SDES = 202;
    public static final int BYE = 203;
    public static final int APP = 204;
    public static final int COMPOUND = -1;

    public RTCPPacket()
    {
    }

    public RTCPPacket(Packet p)
    {
        super(p);
        base = p;
    }

    public RTCPPacket(RTCPPacket parent)
    {
        super(parent);
        base = parent.base;
    }

    /**
     * Serializes/writes the binary representation of this <tt>RTCPPacket</tt>
     * into a specific <tt>DataOutputStream</tt>.
     *
     * @param dataoutputstream the <tt>DataOutputStream</tt> into which the
     * binary representation of this <tt>RTCPPacket</tt> is to be
     * serialized/written.
     * @throws IOException if an input/output error occurs during the
     * serialization/writing of the binary representation of this
     * <tt>RTCPPacket</tt>
     */
    public abstract void assemble(DataOutputStream dataoutputstream)
            throws IOException;

    /**
     * Computes the length in <tt>byte</tt>s of this <tt>RTCPPacket</tt>,
     * including the header and any padding. The value will be used to calculate
     * the 16-bit <tt>length</tt> of this RTCP packet in 32-bit words minus one,
     * including the header and any padding.
     *
     * @return the length in <tt>byte</tt>s of this <tt>RTCPPacket</tt>,
     * including the header and any padding
     */
    public abstract int calcLength();
}
