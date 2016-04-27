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
package org.jitsi.service.neomedia;

/**
 * Media stream statistics per ssrc.
 * @author Damian Minkov
 */
public interface MediaStreamSSRCStats
{
    /**
     * Returns a {@code long} the SSRC of these stats.
     * @return a {@code long} the SSRC of these stats.
     */
    public long getSSRC();

    /**
     * The jitter received/sent in a RTCP feedback (in ms).
     * @return the last jitter received/sent in a RTCP feedback.
     */
    public double getJitter();

    /**
     * The number of bytes sent or received by the stream.
     * @return number of bytes.
     */
    public long getNbBytes();

    /**
     * The number of packets sent or received by the stream.
     * @return number of packets.
     */
    public long getNbPackets();

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits").
     *
     * @return The RTT computed with the RTCP feedback. Returns <tt>-1</tt> if
     * the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    public long getRttMs();
}
