/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import java.util.*;

import net.sf.fmj.media.rtp.util.*;

/**
 * webrtc/webrtc/modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h
 *
 * @author Lyubomir Marinov
 */
public interface RemoteBitrateEstimator
{
    /**
     * Returns the estimated payload bitrate in bits per second if a valid
     * estimate exists; otherwise, <tt>-1</tt>.
     *
     * @return the estimated payload bitrate in bits per seconds if a valid
     * estimate exists; otherwise, <tt>-1</tt>
     */
    long getLatestEstimate();

    Collection<Integer> getSsrcs();

    /**
     * Called for each incoming packet. Updates the incoming payload bitrate
     * estimate and the over-use detector. If an over-use is detected the remote
     * bitrate estimate will be updated.
     *
     * @param arrivalTimeMs can be of an arbitrary time base
     * @param payloadSize the packet size excluding headers
     * @param header
     */
    void incomingPacket(long arrivalTimeMs, int payloadSize, RTPPacket header);

    /**
     * Removes all data for <tt>ssrc</tt>.
     *
     * @param ssrc
     */
    void removeStream(int ssrc);
}
