package org.jitsi.impl.neomedia.rtcp.fmj_port;

import javax.media.rtp.RTPStream;

/**
 * NOTE(brian): was javax.media.rtp.rtcp.SenderReport
 */
public interface SenderReport extends Report
{
    public long getNTPTimeStampLSW();

    public long getNTPTimeStampMSW();

    public long getRTPTimeStamp();

    public long getSenderByteCount();

    public Feedback getSenderFeedback();

    public long getSenderPacketCount();

    public RTPStream getStream();
}

