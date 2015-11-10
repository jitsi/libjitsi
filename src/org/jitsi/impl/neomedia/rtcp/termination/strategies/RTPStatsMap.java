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

import org.jitsi.impl.neomedia.*;

import java.util.concurrent.*;

/**
 * The <tt>RtpStatsMap</tt> gathers stats from RTP packets that the
 * <tt>RTCPReportBuilder</tt> uses to build its reports.
 *
 * @author George Politis
 */
public class RTPStatsMap
        extends ConcurrentHashMap<Integer, RTPStatsEntry>
{
    /**
     * Updates this <tt>RTPStatsMap</tt> with information it gets from the
     * <tt>RawPacket</tt>.
     *
     * @param pkt the <tt>RawPacket</tt> that is being transmitted.
     */
    public void apply(RawPacket pkt)
    {
        int ssrc = pkt.getSSRC();
        if (this.containsKey(ssrc))
        {
            RTPStatsEntry oldRtpStatsEntry = this.get(ssrc);

            // Replace whatever was in there before. A feature of the two's
            // complement encoding (which is used by Java integers) is that
            // the bitwise results for add, subtract, and multiply are the
            // same if both inputs are interpreted as signed values or both
            // inputs are interpreted as unsigned values. (Other encodings
            // like one's complement and signed magnitude don't have this
            // properly.)
            this.put(ssrc, new RTPStatsEntry(
                ssrc, oldRtpStatsEntry.getBytesSent() + pkt.getLength()
                - pkt.getHeaderLength() - pkt.getPaddingSize(),
                oldRtpStatsEntry.getPacketsSent() + 1));
        }
        else
        {
            // Add a new <tt>RTPStatsEntry</tt> in this map.
            this.put(ssrc, new RTPStatsEntry(
                ssrc, pkt.getLength()
                - pkt.getHeaderLength() - pkt.getPaddingSize(),
                1));
        }
    }
}
