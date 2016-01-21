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
 *
 * @author George Politis
 */
class ReceivedRemoteClock
{
    /**
     * The SSRC.
     */
    private final int ssrc;

    /**
     * The <tt>RemoteClock</tt> which was received at {@link #receivedTime} for
     * this RTP stream.
     */
    private final RemoteClock remoteClock;

    /**
     * The local time (in milliseconds since the epoch) when we received the
     * RTCP report with the RTP/NTP timestamps. It's a signed long.
     */
    private final long receivedTime;

    /**
     * The clock rate for {@link #ssrc}. We need to have received at least two
     * SRs in order to be able to calculate this. Unsigned short.
     */
    private final int frequencyHz;

    /**
     * Ctor.
     *
     * @param ssrc
     * @param remoteTime the remote time in milliseconds since the epoch
     * @param rtpTimestamp the RTP timestamp corresponding to
     * <tt>remoteTime</tt>.
     * @param frequencyHz the RTP clock rate.
     */
    ReceivedRemoteClock(int ssrc,
                        long remoteTime,
                        int rtpTimestamp,
                        int frequencyHz)
    {
        this.ssrc = ssrc;
        this.remoteClock = new RemoteClock(remoteTime, rtpTimestamp);
        this.frequencyHz = frequencyHz;
        this.receivedTime = System.currentTimeMillis();
    }

    /**
     *
     * @return
     */
    public RemoteClock getRemoteClock()
    {
        return remoteClock;
    }

    /**
     *
     * @return
     */
    public long getReceivedTime()
    {
        return receivedTime;
    }

    /**
     *
     * @return
     */
    public int getSsrc()
    {
        return ssrc;
    }

    /**
     *
     * @return
     */
    public int getFrequencyHz()
    {
        return frequencyHz;
    }
}
