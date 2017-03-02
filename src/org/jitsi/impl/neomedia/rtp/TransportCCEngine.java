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
package org.jitsi.impl.neomedia.rtp;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Implements transport-cc functionality as a {@link TransformEngine}. The
 * intention is to have the same instance shared between all media streams of
 * a transport channel, so we expect it will be accessed by multiple threads.
 *
 * @author Boris Grozev
 */
public class TransportCCEngine
    implements TransformEngine
{
    /**
     * The transformer which handles RTP packets for this instance.
     */
    private final RTPTransformer rtpTransformer = new RTPTransformer();

    /**
     * The transformer which handles RTCP packets for this instance.
     */
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();

    /**
     * The ID of the transport-cc RTP header extension, or -1 if one is not
     * configured.
     */
    private byte extensionId = -1;

    /**
     * The next sequence number to use for outgoing packets.
     */
    private AtomicInteger outgoingSeq
        = new AtomicInteger(new Random().nextInt() & 0xffff);

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return rtpTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    /**
     * Sets the ID of the transport-cc RTP extension. Set to -1 to effectively
     * disable.
     * @param id the ID to set.
     */
    public void setExtensionID(byte id)
    {
        extensionId = id;
    }

    /**
     * Handles RTP packets for this {@link TransportCCEngine}.
     */
    private class RTPTransformer
        extends SinglePacketTransformerAdapter
    {
        /**
         * Initializes a new {@link RTPTransformer} instance.
         */
        private RTPTransformer()
        {
            super(RTPPacketPredicate.INSTANCE);
        }

        /**
         * {@inheritDoc}
         * <p></p>
         * If the transport-cc extension is configured, update the
         * transport-wide sequence number (adding a new extension if one doesn't
         * exist already).
         */
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            if (extensionId != -1)
            {
                RawPacket.HeaderExtension ext
                    = pkt.getHeaderExtension(extensionId);
                if (ext == null)
                {
                    ext = pkt.addExtension(extensionId, 2);
                }

                int seq = outgoingSeq.getAndIncrement() & 0xffff;
                RTPUtils.writeShort(
                    ext.getBuffer(),
                    ext.getOffset() + 1,
                    (short) seq);
            }
            return pkt;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            return pkt;
        }
    }

    /**
     * Handles RTP packets for this {@link TransportCCEngine}.
     */
    private static class RTCPTransformer
        extends SinglePacketTransformerAdapter
    {
        /**
         * Initializes a new {@link RTPTransformer} instance.
         */
        private RTCPTransformer()
        {
            super(RTCPPacketPredicate.INSTANCE);
        }
    }
}
