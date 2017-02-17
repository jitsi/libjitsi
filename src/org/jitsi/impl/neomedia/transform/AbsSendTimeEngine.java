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
import org.jitsi.util.*;

/**
 * Implements a <tt>TransformEngine</tt> which replaces the timestamps in
 * abs-send-time RTP extensions with timestamps generated locally.
 *
 * See http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time
 *
 * @author Boris Grozev
 */
public class AbsSendTimeEngine
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    /**
     * One billion.
     */
    private static final int b = 1_000_000_000;

    /**
     * The <tt>Logger</tt> used by the {@link AbsSendTimeEngine} class and its
     * instances.
     */
    private static final Logger logger
        = Logger.getLogger(AbsSendTimeEngine.class);

    /**
     * The ID of the abs-send-time RTP header extension.
     */
    private int extensionID = -1;

    /**
     * Initializes a new {@link AbsSendTimeEngine} instance.
     */
    public AbsSendTimeEngine()
    {
        super(RTPPacketPredicate.INSTANCE);
    }

    /**
     * Implements {@link SinglePacketTransformer#transform(RawPacket)}.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        if (extensionID != -1)
        {
            // If the packet already has as extension with this ID, replace its
            // value.
            // TODO: PERC-related logic (don't modify header extensions unless
            // they come after an OHB extension.
            if (!replaceExtension(pkt))
            {
                // If it doesn't, add a new extension.
                addExtension(pkt);
            }
        }
        return pkt;
    }

    /**
     * Implements {@link TransformEngine#getRTPTransformer()}.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Implements {@link TransformEngine#getRTCPTransformer()}.
     *
     * This <tt>TransformEngine</tt> does not transform RTCP packets.
     *
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Tries to find an RTP header extensions with an ID of {@link #extensionID}
     * in <tt>pkt</tt> and tries to replace its timestamp with one
     * generated locally (based on {@link System#nanoTime()}).
     * @param pkt the packet to work on.
     * @return true if and only if an RTP extension with an ID of {@link
     * #extensionID} was found in the packet, and its value was replaced.
     */
    private boolean replaceExtension(RawPacket pkt)
    {
        if (!pkt.getExtensionBit())
            return false;

        byte[] buf = pkt.getBuffer();
        int extensionOffset = pkt.getOffset();

        // Skip the fixed header.
        extensionOffset += RawPacket.FIXED_HEADER_SIZE;
        // Skip the list of CSRCs.
        extensionOffset += pkt.getCsrcCount() * 4;

        // We need at least 4 bytes for the "defined by profile" and "length"
        // fields.
        if (buf.length < extensionOffset + 4)
        {
            return false;
        }

        // We only understand the RFC5285 one-byte header format recognized
        // by the 0xBEDE value in the 'defined by profile' field.
        if (buf[extensionOffset++] != (byte) 0xBE)
        {
            return false;
        }
        if (buf[extensionOffset++] != (byte) 0xDE)
        {
            return false;
        }

        int lengthInWords = (buf[extensionOffset++] & 0xFF) << 8
            | (buf[extensionOffset++] & 0xFF);

        // Length in bytes of the header extensions (excluding the 4 bytes for
        // the "defined by profile" (0xBEDE) and length field itself, which
        // we have already incremented past).
        int lengthInBytes = 4 * lengthInWords;

        int innerOffset = 0;
        while (extensionOffset < buf.length && innerOffset < lengthInBytes)
        {
            int id = (buf[extensionOffset] & 0xf0) >> 4;
            int len = buf[extensionOffset] & 0x0f;
            if (id == extensionID)
            {
                if (len == 2 && extensionOffset + 3 < buf.length)
                {
                    setTimestamp(buf, extensionOffset + 1);
                    return true;
                }
                else
                {
                    logger.warn("An existing extension with ID " + id
                                + " was found, but it doesn't look like "
                                + "abs-send-time: len=" + len);
                    // Suppress the addition of another header extension.
                    return true;
                }
            }
            else
            {
                // 1 byte for id/len, one more byte by the definition of
                // len, see RFC5285
                innerOffset += 1 + len + 1;
                extensionOffset += 1 + len + 1;
            }
        }

        return false;
    }

    /**
     * Adds an abs-send-time RTP header extension with an ID of {@link
     * #extensionID} and value derived from the current system time to the
     * packet {@code pkt}.
     * @param pkt the packet to add an extension to.
     */
    private void addExtension(RawPacket pkt)
    {
        // one byte for ID and length (see RFC5285) and three bytes for a
        // timestamp (see
        byte[] extensionBytes = new byte[4];
        extensionBytes[0] = (byte) ((extensionID << 4) | 2);
        setTimestamp(extensionBytes, 1);

        pkt.addExtension(extensionBytes, extensionBytes.length);
    }

    /**
     * Sets the 3 bytes at offset <tt>off</tt> in <tt>buf</tt> to the value of
     * {@link System#nanoTime()} converted to the fixed point (6.18) format
     * specified in
     * {@link "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time"}.
     *
     * @param buf the buffer where to write the timestamp.
     * @param off the offset at which to write the timestamp.
     */
    private void setTimestamp(byte[] buf, int off)
    {
        long ns = System.nanoTime();
        int fraction = (int) ( (ns % b) * (1 << 18) / b );
        int seconds = (int) ((ns / b) % 64); //6 bits only

        int timestamp = (seconds << 18 | fraction) & 0x00FFFFFF;

        buf[off] = (byte) (timestamp >> 16);
        buf[off+1] = (byte) (timestamp >> 8);
        buf[off+2] = (byte) timestamp;
    }

    /**
     * Sets the ID of the abs-send-time RTP extension. Set to -1 to effectively
     * disable this transformer.
     * @param id the ID to set.
     */
    public void setExtensionID(int id)
    {
        extensionID = id;
    }
}
