/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.service.neomedia.rtp;

import java.util.*;

import net.sf.fmj.media.rtp.util.*;

/**
 * webrtc/modules/remote_bitrate_estimator/include/remote_bitrate_estimator.cc
 * webrtc/modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h
 *
 * @author Lyubomir Marinov
 */
public interface RemoteBitrateEstimator
{
    /**
     * webrtc/modules/remote_bitrate_estimator/include/bwe_defines.h
     */
    int kBitrateWindowMs = 1000;

    int kDefaultMinBitrateBps = 30000;

    int kProcessIntervalMs = 500;

    int kStreamTimeOutMs = 2000;

    int kTimestampGroupLengthMs = 5;

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
     * @param ssrc
     * @param absSendTime24Bit Timestamp in seconds, 24 bit 6.18 fixed point, yielding 64s wraparound and 3.8us resolution.
     *      @see <a href=https://webrtc.org/experiments/rtp-hdrext/abs-send-time/>abs-send-time</a>
     * @param wasPaced
     */
    void incomingPacket(
            long arrivalTimeMs,
            int payloadSize,
            int ssrc,
            long absSendTime24Bit,
            boolean wasPaced);

    /**
     * Removes all data for <tt>ssrc</tt>.
     *
     * @param ssrc
     */
    void removeStream(int ssrc);

    void setMinBitrate(int minBitrateBps);
}
