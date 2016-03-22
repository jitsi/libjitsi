package org.jitsi.util;

import org.ice4j.util.*;
import org.jitsi.impl.neomedia.*;

/**
 * Implements {@link PacketQueue} for {@link RawPacket}.
 *
 * @author Boris Grozev
 */
public class RawPacketQueue extends PacketQueue<RawPacket>
{
    /**
     * Initializes a new {@link RawPacketQueue}. See
     * {@link PacketQueue#PacketQueue(int, boolean, boolean, boolean, String, PacketHandler)}
     */
    public RawPacketQueue(int capacity, boolean enableCache, boolean copy,
                          boolean enableStatistics, String id,
                          PacketHandler<RawPacket> handler)
    {
        super(capacity, enableCache, copy, enableStatistics, id, handler);
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

