package org.jitsi.impl.neomedia.rtcp.fmj_port;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPPacketParserListener
 */
public interface RTCPPacketParserListener
{
    void enterSenderReport();

    void malformedSenderReport();

    void malformedReceiverReport();

    void malformedSourceDescription();

    void malformedEndOfParticipation();

    void uknownPayloadType();

    void visitSendeReport(RTCPSRPacket rtcpSrPacket);
}
