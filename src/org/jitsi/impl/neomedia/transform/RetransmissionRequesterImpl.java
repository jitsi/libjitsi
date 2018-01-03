/*
 * Copyright @ 2017 Atlassian Pty Ltd
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

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.*;

/**
 * Creates classes to handle both the detection of loss and the creation
 * and sending of nack packets, and a scheduler to allow for nacks to be
 * re-transmitted at a set interval
 *
 * @author bbaldino
 */
public class RetransmissionRequesterImpl
    extends SinglePacketTransformerAdapter
    implements TransformEngine, RetransmissionRequester
{
    /**
     * Whether this {@link RetransmissionRequester} is enabled or not.
     */
    private boolean enabled = true;

    /**
     * Whether this <tt>PacketTransformer</tt> has been closed.
     */
    private boolean closed = false;

    /**
     * The delegate for this {@link RetransmissionRequesterImpl} which handles
     * the main logic for determining when to send nacks
     */
    private final RetransmissionRequesterDelegate retransmissionRequesterDelegate;

    /**
     * Create a single executor to service the nack processing for all the
     * {@link RetransmissionRequesterImpl} instances
     */
    private static RecurringRunnableExecutor recurringRunnableExecutor = new RecurringRunnableExecutor();

    public RetransmissionRequesterImpl(MediaStream stream)
    {
        retransmissionRequesterDelegate = new RetransmissionRequesterDelegate(stream, new TimeProvider());
        recurringRunnableExecutor.registerRecurringRunnable(retransmissionRequesterDelegate);
        retransmissionRequesterDelegate.setWorkReadyCallback(new Runnable(){
            @Override
            public void run()
            {
                recurringRunnableExecutor.startOrNotifyThread();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * Implements {@link SinglePacketTransformer#reverseTransform(RawPacket)}.
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        if (enabled && !closed)
        {
            return retransmissionRequesterDelegate.reverseTransform(pkt);
        }
        return pkt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        closed = true;
        this.recurringRunnableExecutor.close();
    }

    // TransformEngine methods
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
        return this;
    }

    // RetransmissionRequester methods
    /**
     * {@inheritDoc}
     */
    @Override
    public void enable(boolean enable)
    {
        this.enabled = enable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSenderSsrc(long ssrc)
    {
        this.retransmissionRequesterDelegate.setSenderSsrc(ssrc);
    }
}
