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
 * @author Lyubomir Marinov
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
        int bytesSent
            = pkt.getLength() - pkt.getHeaderLength() - pkt.getPaddingSize();
        int packetsSent = 1;

        RTPStatsEntry oldRtpStatsEntry = get(ssrc);

        if (oldRtpStatsEntry != null)
        {
            bytesSent += oldRtpStatsEntry.getBytesSent();
            packetsSent += oldRtpStatsEntry.getPacketsSent();
        }

        put(ssrc, new RTPStatsEntry(ssrc, bytesSent, packetsSent));
    }
}
