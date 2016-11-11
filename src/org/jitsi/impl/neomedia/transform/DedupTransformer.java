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
import org.jitsi.util.*;

import java.util.*;

/**
 * De-duplicates RTP packets from incoming RTP streams.
 *
 * @author George Politis
 */
public class DedupTransformer
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    /**
     * The <tt>Logger</tt> used by the <tt>DedupTransformer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(DedupTransformer.class);

    /**
     * The {@code ReplayContext} for every SSRC that this instance has seen.
     */
    private final Map<Integer, ReplayContext> replayContexts = new TreeMap<>();

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
        int mediaSSRC = pkt.getSSRC();

        ReplayContext replayContext = replayContexts.get(mediaSSRC);
        if (replayContext == null)
        {
            replayContext = new ReplayContext();
            replayContexts.put(mediaSSRC, replayContext);
        }

        boolean seen = replayContext.isSeen(pkt);

        if (seen && logger.isDebugEnabled())
        {
            logger.debug("drop_duplicate "
                + "ssrc=" + pkt.getSSRCAsLong()
                + ",seqNum=" + pkt.getSequenceNumber());
        }

        return  seen ? null : pkt;
    }

}
