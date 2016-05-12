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
 * Media stream statistics per send or receive SSRC.
 *
 * @author Damian Minkov
 */
public abstract class AbstractMediaStreamSSRCStats
    implements MediaStreamSSRCStats
{
    /**
     * The <tt>StatisticsEngine</tt> of this instance.
     */
    protected final StatisticsEngine statisticsEngine;

    /**
     * The SSRC of this piece of statistics.
     */
    protected final long ssrc;

    /**
     * The last jitter received/sent in an RTCP feedback (in ms).
     */
    private double jitter = 0;

    /**
     * The RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1,
     * subsection "delay since last SR (DLSR): 32 bits"). {@code -1} if the RTT
     * has not been computed yet. Otherwise, the RTT in milliseconds.
     */
    private long rttMs = -1;

    /**
     * Initializes a new {@code AbstractMediaStreamSSRCStats} instance.
     *
     * @param ssrc the SSRC of the {@code MediaStream} to associate with the new
     * instance.
     * @param statisticsEngine the {@code StatisticsEngine} instance to use.
     */
    AbstractMediaStreamSSRCStats(long ssrc, StatisticsEngine statisticsEngine)
    {
        this.ssrc = ssrc;
        this.statisticsEngine = statisticsEngine;
    }

    /**
     * {@inheritDoc}
     */
    public long getSSRC()
    {
        return ssrc;
    }

    /**
     * {@inheritDoc}
     */
    public double getJitter()
    {
        return jitter;
    }

    /**
     * Sets the last jitter that was sent/received.
     *
     * @param jitter the new value to set on this instance as the last
     * sent/received jitter
     */
    public void setJitter(double jitter)
    {
        this.jitter = jitter;
    }

    /**
     * {@inheritDoc}
     */
    public long getRttMs()
    {
        return rttMs;
    }

    /**
     * Sets a specific value on {@link #rttMs}.
     *
     * @param rttMs the new value to set on {@link #rttMs}
     */
    public void setRttMs(long rttMs)
    {
        this.rttMs = rttMs;
    }
}
