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
package org.jitsi.impl.neomedia.transform.delay;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

/**
 * A {@link TransformEngine} that delays the RTP stream by specified packet
 * count by holding them in a buffer.
 *
 * @author Boris Grozev
 * @author Pawel Domas
 */
public class DelayingTransformEngine
    implements TransformEngine
{
    private DelayingTransformer delayingTransformer;

    /**
     * Creates new instance of <tt>DelayingTransformEngine</tt> which will delay
     * the RTP stream by given amount of packets.
     * @param packetCount the delay counted in packets.
     */
    public DelayingTransformEngine(int packetCount)
    {
        delayingTransformer = new DelayingTransformer(packetCount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return delayingTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    private class DelayingTransformer extends SinglePacketTransformer
    {
        private RawPacket[] buffer;

        private int idx = 0;

        DelayingTransformer(int pktCount)
        {
            this.buffer = new RawPacket[pktCount];
        }

        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            if (pkt != null)
            {
                RawPacket ret = buffer[idx];
                buffer[idx] = pkt;
                idx = (idx + 1) % buffer.length;
                return ret;
            }
            return null;
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            return pkt;
        }
    }
}
