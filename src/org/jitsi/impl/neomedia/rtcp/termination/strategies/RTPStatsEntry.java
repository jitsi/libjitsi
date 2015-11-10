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
 * The <tt>RTPStatsEntry</tt> class contains information about an outgoing
 * SSRC.
 *
 * @author George Politis
 */
public class RTPStatsEntry
{
    /**
     * The SSRC of the stream that this instance tracks.
     */
    private final int ssrc;

    /**
     * The total number of _payload_ octets (i.e., not including header or
     * padding) transmitted in RTP data packets by the sender since
     * starting transmission up until the time this SR packet was
     * generated. This should be treated as an unsigned int.
     */
    private final int bytesSent;

    /**
     * The total number of RTP data packets transmitted by the sender
     * (including re-transmissions) since starting transmission up until
     * the time this SR packet was generated. Re-transmissions using an RTX
     * stream are tracked in the RTX SSRC. This should be treated as an
     * unsigned int.
     */
    private final int packetsSent;

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
    public int getBytesSent()
    {
        return bytesSent;
    }

    /**
     *
     * @return
     */
    public int getPacketsSent()
    {
        return packetsSent;
    }

    /**
     * Ctor.
     *
     * @param ssrc
     * @param bytesSent
     */
    RTPStatsEntry(int ssrc, int bytesSent, int packetsSent)
    {
        this.ssrc = ssrc;
        this.bytesSent = bytesSent;
        this.packetsSent = packetsSent;
    }
}
