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
 */
public interface RTCPPacketType
{
    /**
     *
     */
    public static final int SR = RTCPPacket.SR;

    /**
     *
     */
    public static final int RR = RTCPPacket.RR;

    /**
     *
     */
    public static final int SDES = RTCPPacket.SDES;

    /**
     *
     */
    public static final int BYE = RTCPPacket.BYE;

    /**
     *
     */
    public static final int APP = RTCPPacket.APP;

    /**
     *
     */
    public static final int RTPFB = 205;

    /**
     * Payload-specific FB message
     */
    public static final int PSFB = 206;
}
