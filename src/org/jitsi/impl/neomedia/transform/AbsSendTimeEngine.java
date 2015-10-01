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

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;

/**
 * Implements a <tt>TransformEngine</tt> which replaces the timestamps in
 * abs-send-time RTP extensions with timestamps generated locally.
 *
 * See http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time
 *
 * @author Boris Grozev
 */
public class AbsSendTimeEngine
    extends SinglePacketTransformer
    implements TransformEngine
{
    /**
     * One billion.
     */
    private static final int b = 1000 * 1000 * 1000;

    /**
     * The ID of the abs-send-time RTP header extension.
     */
    private int extensionID = -1;

    /**
     * Implements {@link SinglePacketTransformer#reverseTransform(RawPacket)}.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        if (extensionID != -1
              && pkt != null
              && pkt.getVersion() == RTPHeader.VERSION
              && pkt.getExtensionBit())
        {
            replaceAbsSendTime(pkt);
        }
        return pkt;
    }

    /**
     * Implements {@link SinglePacketTransformer#transform(RawPacket)}.
     *
     * This transformer does not perform transformation on incoming packets.
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        return pkt;
    }

    /**
     * Implements {@link SinglePacketTransformer#close()}.
     */
    @Override
    public void close()
    {
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
     * generated locally (based on {@link System#nanoTime()}.
     * @param pkt the packet to work on.
     */
    private void replaceAbsSendTime(RawPacket pkt)
    {
        byte[] buf = pkt.getBuffer();
        int extensionOffset = pkt.getOffset();

        // Skip the fixed header.
        extensionOffset += RawPacket.FIXED_HEADER_SIZE;
        // Skip the list of CSRCs.
        extensionOffset += pkt.getCsrcCount() * 4;

        // Skip the 16-bit "defined by profile" field (RFC3550).
        extensionOffset += 2;

        if (extensionOffset + 1 < buf.length) //we can read 'length'
        {
            int lengthInWords
                    = buf[extensionOffset] << 8
                      | buf[extensionOffset + 1];
            // Length in bytes of the header extensions
            int lengthInBytes = 4 * (1 + lengthInWords);
            extensionOffset += 2;

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
                    }
                    return;
                }
                else
                {
                    // 1 byte for id/len, one more byte by the definition of
                    // len, see RFC5285
                    innerOffset += 1 + len + 1;
                    extensionOffset += 1 + len + 1;
                }
            }
        }
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
