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
import org.jitsi.service.neomedia.*;

import java.util.*;

/**
 * De-duplicates RTP packets from incoming RTP streams.
 *
 * @author George Politis
 */
public class PaddingTermination
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    /**
     * Ctor.
     */
    public PaddingTermination()
    {
        super(RTPPacketPredicate.INSTANCE);
    }

    /**
     * The {@code ReplayContext} for every SSRC that this instance has seen.
     */
    private final Map<Long, ReplayContext> replayContexts = new TreeMap<>();

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
    public RawPacket reverseTransform(RawPacket pkt)
    {
        Long mediaSSRC = pkt.getSSRCAsLong();

        // TODO maybe drop padding from the main RTP stream?
        ReplayContext replayContext = replayContexts.get(mediaSSRC);
        if (replayContext == null)
        {
            replayContext = new ReplayContext();
            replayContexts.put(mediaSSRC, replayContext);
        }

        return replayContext.isSeen(pkt) ? null : pkt;
    }

}
