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
package org.jitsi.impl.neomedia;

import org.jitsi.impl.neomedia.transform.rtcp.*;
import org.jitsi.service.neomedia.*;

/**
 * Media stream statistics per ssrc send or receive.
 *
 * @author Damian Minkov
 */
public abstract class AbstractMediaStreamSSRCStats
    implements MediaStreamSSRCStats
{
    /**
     * The <tt>StatisticsEngine</tt> of this instance.
     */
    protected StatisticsEngine statisticsEngine;

    /**
     * The ssrc which stats are we handling.
     */
    protected final long ssrc;

    /**
     * The last jitter received/sent in a RTCP feedback (in ms).
     */
    private double jitter = 0;

    /**
     * The RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1,
     * subsection "delay since last SR (DLSR): 32 bits").
     * -1 if the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    private long rttMs = -1;

    /**
     * Inits AbstractMediaStreamSSRCStats.
     * @param ssrc the ssrc of the stream.
     * @param statisticsEngine the stats engine instance to use.
     */
    AbstractMediaStreamSSRCStats(long ssrc, StatisticsEngine statisticsEngine)
    {
        this.ssrc = ssrc;
        this.statisticsEngine = statisticsEngine;
    }

    /**
     * Returns a {@code long} the SSRC of these stats.
     * @return a {@code long} the SSRC of these stats.
     */
    public long getSSRC()
    {
        return ssrc;
    }

    /**
     * The jitter received/sent in a RTCP feedback (in ms).
     * @return the last jitter received/sent in a RTCP feedback.
     */
    public double getJitter()
    {
        return jitter;
    }

    /**
     * Sets the last jitter that was sent/received.
     *
     * @param jitter the new value
     */
    public void setJitter(double jitter)
    {
        this.jitter = jitter;
    }

    /**
     * The number of bytes sent or received by the stream.
     * @return number of bytes.
     */
    public abstract long getNbBytes();

    /**
     * The number of packets sent or received by the stream.
     * @return number of packets.
     */
    public abstract long getNbPackets();

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits").
     *
     * @return The RTT computed with the RTCP feedback. Returns <tt>-1</tt> if
     * the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    public long getRttMs()
    {
        return rttMs;
    }

    /**
     * Sets a specific value on {@link #rttMs}. If there is an actual difference
     * between the old and the new values, notifies the (known)
     * <tt>CallStatsObserver</tt>s.
     *
     * @param rttMs the value to set on <tt>MediaStreamStatsImpl.rttMs</tt>
     */
    public void setRttMs(long rttMs)
    {
        this.rttMs = rttMs;
    }
}
