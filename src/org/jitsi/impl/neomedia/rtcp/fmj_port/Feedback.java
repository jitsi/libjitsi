package org.jitsi.impl.neomedia.rtcp.fmj_port;

/**
 * NOTE(brian): was javax.media.rtp.rtcp.Feedback
 */
public interface Feedback {
    long getDLSR();

    int getFractionLost();

    long getJitter();

    long getLSR();

    long getNumLost();

    long getSSRC();

    long getXtndSeqNum();
}
