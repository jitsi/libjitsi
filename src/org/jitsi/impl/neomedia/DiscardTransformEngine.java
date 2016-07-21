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
package org.jitsi.impl.neomedia;

import org.jitsi.impl.neomedia.transform.*;

import javax.media.*;
import java.util.*;

/**
 * As the name suggests, the DiscardTransformEngine discards packets that are
 * flagged for discard. The packets that are passed on in the chain have their
 * sequence numbers rewritten hiding the gaps created by the dropped packets.
 *
 * Instances of this class are not thread-safe. If multiple threads access an
 * instance concurrently, it must be synchronized externally.
 *
 * @author George Politis
 */
public class DiscardTransformEngine
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    /**
     * A map of source ssrc to last accepted sequence number
     */
    private final Map<Long, SequenceNumberRewriter> ssrcToRewriter
        = new HashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        if (pkt == null)
        {
            return null;
        }

        boolean dropPkt
            = (pkt.getFlags() & Buffer.FLAG_DISCARD) == Buffer.FLAG_DISCARD;

        SequenceNumberRewriter rewriter = ssrcToRewriter.get(pkt.getSSRCAsLong());
        if (rewriter == null)
        {
            rewriter = new SequenceNumberRewriter();
            ssrcToRewriter.put(pkt.getSSRCAsLong(), rewriter);
        }

        rewriter.rewrite(
            !dropPkt, pkt.getBuffer(), pkt.getOffset(), pkt.getLength());

        return dropPkt ? null : pkt;
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
        // There's nothing to be done for RTCP.
        return null;
    }
}
