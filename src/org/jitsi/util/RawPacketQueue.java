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
package org.jitsi.util;

import org.jitsi.utils.*;
import org.jitsi.service.neomedia.*;

/**
 * Implements {@link PacketQueue} for {@link RawPacket}.
 *
 * @author Boris Grozev
 */
public class RawPacketQueue extends PacketQueue<RawPacket>
{
    /**
     * Initializes a new {@link RawPacketQueue}. See
     * {@link PacketQueue#PacketQueue(int, boolean, boolean, String, PacketHandler)}
     */
    public RawPacketQueue(int capacity, boolean copy,
                          boolean enableStatistics, String id,
                          PacketHandler<RawPacket> handler)
    {
        super(capacity, copy, enableStatistics, id, handler);
    }

    /**
     * Initializes a new {@link RawPacketQueue}. See
     * {@link PacketQueue#PacketQueue(boolean, String, PacketHandler)}
     */
    public RawPacketQueue(
        boolean enableStatistics, String id,
        PacketHandler<RawPacket> packetHandler)
    {
        super(enableStatistics, id, packetHandler);
    }

    /**
     * Initializes a new {@link RawPacketQueue}. See
     * {@link PacketQueue#PacketQueue()}
     */
    public RawPacketQueue()
    {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getBuffer(RawPacket pkt)
    {
        return pkt == null ? null : pkt.getBuffer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLength(RawPacket pkt)
    {
        return pkt == null ? 0 : pkt.getLength();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOffset(RawPacket pkt)
    {
        return pkt == null ? 0 : pkt.getOffset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getContext(RawPacket pkt)
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected RawPacket createPacket(
        byte[] buf, int off, int len, Object context)
    {
        return new RawPacket(buf, off, len);
    }
}

