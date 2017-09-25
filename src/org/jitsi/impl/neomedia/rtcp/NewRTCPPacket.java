package org.jitsi.impl.neomedia.rtcp;

import net.sf.fmj.media.rtp.*;


/**
 * For now this class is basically acting as a shim.  In trying to move away
 * from FMJ for RTCP-related processing, this class will serve as the parent
 * for the copied-over RTCP packet classes from FMJ.  Having it extend the
 * FMJ RTCPPacket means that we can do this progressively without
 * breaking everything.  Eventually we can remove the FMJ class from the
 * hierarchy and have things only depend on other classes in FMJ.
 */
public abstract class NewRTCPPacket extends RTCPPacket
{
}
