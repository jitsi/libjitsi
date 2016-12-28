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

import org.jitsi.impl.neomedia.*;

/**
 * An simple interface which allows a packet to be retrieved from a
 * cache/storage by an SSRC identifier and a sequence number.
 * @author Boris Grozev
 */
public interface RawPacketCache
{
    /**
     * Gets the packet with the given SSRC and RTP sequence number from the
     * cache. If no such packet is found, returns <tt>null</tt>.
     * @param ssrc The SSRC of the packet.
     * @param seq the RTP sequence number of the packet.
     * @return the packet with the given SSRC and RTP sequence number from the
     * cache. If no such packet is found, returns <tt>null</tt>.
     */
    public RawPacket get(long ssrc, int seq);

    /**
     * Gets the packet, encapsulated in a {@link Container} with the given SSRC
     * and RTP sequence number from the cache. If no such packet is found,
     * returns <tt>null</tt>.
     * @param ssrc The SSRC of the packet.
     * @param seq the RTP sequence number of the packet.
     * @return the packet, encapsulated in a {@link Container} with the given
     * SSRC and RTP sequence number from the cache. If no such packet is found,
     * returns <tt>null</tt>.
     */
    public Container getContainer(long ssrc, int seq);

    /**
     * Saves a packet in the cache.
     * @param pkt the packet to save.
     */
    public void cachePacket(RawPacket pkt);

    /**
     * Enables/disables the caching of packets.
     *
     * @param enabled {@code true} if the caching of packets is to be enabled or
     * {@code false} if the caching of packets is to be disabled
     */
    public void setEnabled(boolean enabled);

    /**
     * A container for packets in the cache.
     */
    class Container
    {
        /**
         * The {@link RawPacket} which this container holds.
         */
        public RawPacket pkt;

        /**
         * The time (in milliseconds since the epoch) that the packet was
         * added to the cache.
         */
        public long timeAdded;

        /**
         * Initializes a new empty {@link Container} instance.
         */
        public Container()
        {
            this(null, -1);
        }

        /**
         * Initializes a new {@link Container} instance.
         * @param pkt the packet to hold.
         * @param timeAdded the time the packet was added.
         */
        public Container(RawPacket pkt, long timeAdded)
        {
            this.pkt = pkt;
            this.timeAdded = timeAdded;
        }
    }
}
