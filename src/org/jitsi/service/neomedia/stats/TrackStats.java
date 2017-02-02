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
package org.jitsi.service.neomedia.stats;

/**
 * Basic statistics for a single "stream". A stream can be defined either as
 * the packets with a particular SSRC, or all packets of a
 * {@link org.jitsi.service.neomedia.MediaStream}, or something else.
 *
 * This class does not make a distinction between packets sent or received.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
public interface TrackStats
{
    /**
     * The value that indicates that no values has been set for the jitter
     * field.
     */
    double JITTER_UNSET = Double.MIN_VALUE;

    /**
     * @return the SSRC associated with this {@link TrackStats}.
     */
    long getSSRC();

    /**
     * @return the jitter in milliseconds.
     */
    double getJitter();

    /**
     * @return the total number of bytes.
     */
    long getBytes();

    /**
     * @return the total number of packets.
     */
    long getPackets();

    /**
     * @return the round trip time in milliseconds.
     */
    long getRtt();

    /**
     * @return the current bitrate in bits per second.
     */
    long getBitrate();

    /**
     * @return the current packet rate in packets per second.
     */
    long getPacketRate();

    /**
     * @return the number of bytes in the last interval.
     */
    long getCurrentBytes();

    /**
     * @return the number of packets in the last interval.
     */
    long getCurrentPackets();

    /**
     * @return the interval length in milliseconds over which
     * {@link #getCurrentBytes()} and {@link #getCurrentPackets()} operate.
     */
    long getInterval();

    /**
     * @return an estimate for the recent loss rate.
     */
    double getLossRate();

    /**
     * Gets the number of packets for which retransmission was requested,
     * but they were missing from the cache.
     * @return the number of packets for which retransmission was requested,
     * but they were missing from the cache.
     */
    long getPacketsMissingFromCache();

    /**
     * Gets the number of bytes retransmitted.
     *
     * @return the number of bytes retransmitted.
     */
    long getBytesRetransmitted();

    /**
     * Gets the number of bytes for packets which were requested and found
     * in the cache, but were intentionally not retransmitted.
     *
     * @return the number of bytes for packets which were requested and
     * found in the cache, but were intentionally not retransmitted.
     */
    long getBytesNotRetransmitted();

    /**
     * Gets the number of packets retransmitted.
     *
     * @return the number of packets retransmitted.
     */
    long getPacketsRetransmitted();

    /**
     * Gets the number of packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     *
     * @return the number of packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     */
    long getPacketsNotRetransmitted();
}
