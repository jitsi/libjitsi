package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.util.Vector;

/**
 * NOTE(brian): was javax.media.rtp.rtcp.Report
 */
public interface Report
{
    public Vector getFeedbackReports();

    public Participant getParticipant();

    public Vector getSourceDescription();

    public long getSSRC();
}
