package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.util.Vector;

/**
 * NOTE(brian): was javax.media.rtp.Participant
 */
public interface Participant
{
    public String getCNAME();

    public Vector getReports();

    public Vector getSourceDescription();

    public Vector getStreams();
}
