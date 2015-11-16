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
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

/**
 * Holds the NTP timestamp and the associated RTP timestamp for a given RTP
 * stream.
 *
 * @author George Politis
 */
class RemoteClock
{
    /**
     * The last NTP timestamp that we received for {@link this.ssrc} expressed
     * in milliseconds. Should be treated a signed long.
     */
    private final long remoteTime;

    /**
     * The RTP timestamp associated to {@link this.ntpTimestamp}. The RTP
     * timestamp is an unsigned int.
     */
    private final int rtpTimestamp;

    /**
     * Ctor.
     *
     * @param remoteTime
     * @param rtpTimestamp
     */
    public RemoteClock(long remoteTime, int rtpTimestamp)
    {
        this.remoteTime = remoteTime;
        this.rtpTimestamp = rtpTimestamp;
    }

    /**
     *
     * @return
     */
    public int getRtpTimestamp()
    {
        return rtpTimestamp;
    }

    /**
     *
     * @return
     */
    public long getRemoteTime()
    {
        return remoteTime;
    }
}
