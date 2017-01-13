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
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.*;

/**
 * Implements a cache of outgoing RTP packets.
 *
 * @author Boris Grozev
 * @author George Politis
 */
public class CachingTransformer
    extends SinglePacketTransformerAdapter
    implements TransformEngine,
               RecurringRunnable
{
    /**
     * The <tt>Logger</tt> used by the <tt>CachingTransformer</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(CachingTransformer.class);

    /**
     * The period of time between calls to {@link #run} will be requested
     * if this {@link CachingTransformer} is enabled.
     */
    private static final int PROCESS_INTERVAL_MS = 10000;

    /**
     * The outgoing packet cache.
     */
    private final RawPacketCache outgoingRawPacketCache;

    /**
     * Whether or not this <tt>TransformEngine</tt> has been closed.
     */
    private boolean closed = false;

    /**
     * Whether caching packets is enabled or disabled. Note that the default
     * value is {@code false}.
     */
    private boolean enabled = false;

    /**
     * The <tt>RecurringRunnableExecutor</tt> to be utilized by the
     * <tt>CachingTransformer</tt> class and its instances.
     */
    private static final RecurringRunnableExecutor
        recurringRunnableExecutor
            = new RecurringRunnableExecutor(
                    CachingTransformer.class.getSimpleName());

    /**
     * The last time {@link #run()} was called.
     */
    private long lastUpdateTime = -1;

    /**
     * Initializes a new {@link CachingTransformer} instance.
     * @param streamId the identifier of the owning stream.
     */
    public CachingTransformer(int streamId)
    {
        this.outgoingRawPacketCache = new RawPacketCache(streamId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        if (closed)
            return;
        closed = true;

        try
        {
            outgoingRawPacketCache.close();
        }
        catch (Exception e)
        {
            logger.error(e);
        }
        recurringRunnableExecutor.deRegisterRecurringRunnable(this);
    }

    /**
     * Enables/disables the caching of packets.
     *
     * @param enabled {@code true} if the caching of packets is to be enabled or
     * {@code false} if the caching of packets is to be disabled
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;

        if (enabled)
        {
            recurringRunnableExecutor.registerRecurringRunnable(this);
        }
        else
        {
            recurringRunnableExecutor.deRegisterRecurringRunnable(this);
        }

        if (logger.isDebugEnabled())
        {
            logger.debug((enabled ? "Enabling" : "Disabling")
                + " CachingTransformer " + hashCode());
        }
    }

    /**
     * {@inheritDoc}
     *
     * Transforms an outgoing packet.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        if (enabled && !closed && pkt != null && pkt.getVersion() == 2)
        {
            outgoingRawPacketCache.cachePacket(pkt);
        }
        return pkt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimeUntilNextRun()
    {
        return
                (lastUpdateTime < 0L)
                        ? 0L
                        : lastUpdateTime + PROCESS_INTERVAL_MS
                        - System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
        lastUpdateTime = System.currentTimeMillis();
        outgoingRawPacketCache.clean(lastUpdateTime);
    }

    /**
     * Gets the outgoing {@link RawPacketCache}.
     *
     * @return the outgoing {@link RawPacketCache}.
     */
    public RawPacketCache getOutgoingRawPacketCache()
    {
        return outgoingRawPacketCache;
    }
}
