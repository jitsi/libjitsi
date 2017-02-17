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
package org.jitsi.impl.neomedia.recording;

import java.util.*;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;

/**
 * A <tt>TransformEngine</tt> and <tt>PacketTransformer</tt> which implement
 * a fixed-size buffer. The class is specific to video recording. Buffered are
 * only VP8 RTP packets, and they are places in different buffers according
 * to their SSRC.
 *
 * @author Boris Grozev
 */
public class PacketBuffer
    implements TransformEngine,
               PacketTransformer
{
    /**
     * A <tt>Comparator</tt> implementation for RTP sequence numbers.
     * Compares the sequence numbers <tt>a</tt> and <tt>b</tt>
     * of <tt>pkt1</tt> and <tt>pkt2</tt>, taking into account the wrap at 2^16.
     *
     * IMPORTANT: This is a valid <tt>Comparator</tt> implementation only if
     * used for subsets of [0, 2^16) which don't span more than 2^15 elements.
     *
     * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000])
     * Doesn't work for: [0, 2^15] and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
     */
    private static final Comparator<? super RawPacket> seqNumComparator
            = new Comparator<RawPacket>() {
        @Override
        public int compare(RawPacket pkt1, RawPacket pkt2)
        {
            long a = pkt1.getSequenceNumber();
            long b = pkt2.getSequenceNumber();

            if (a == b)
                return 0;
            else if (a > b)
            {
                if (a - b < 32768)
                    return 1;
                else
                    return -1;
            }
            else //a < b
            {
                if (b - a < 32768)
                    return -1;
                else
                    return 1;
            }
        }
    };

    /**
     * The <tt>ConfigurationService</tt> used to load buffering configuration.
     */
    private final static ConfigurationService cfg =
            LibJitsi.getConfigurationService();

    /**
     * The payload type for VP8.
     * TODO: make this configurable.
     */
    private static int VP8_PAYLOAD_TYPE = 100;

    /**
     * The parameter name for the packet buffer size
     */
    private static final String PACKET_BUFFER_SIZE_PNAME =
            PacketBuffer.class.getCanonicalName() + ".SIZE";
    /**
     * The size of the buffer for each SSRC.
     */
    private static int SIZE = cfg.getInt(PACKET_BUFFER_SIZE_PNAME, 300);

    /**
     * The map of actual <tt>Buffer</tt> instances, one for each SSRC that this
     * <tt>PacketBuffer</tt> buffers in each instant.
     */
    private final Map<Long, Buffer> buffers = new HashMap<>();

    /**
     * Implements
     * {@link org.jitsi.impl.neomedia.transform.PacketTransformer#close()}.
     */
    @Override
    public void close()
    {

    }

    /**
     * Implements
     * {@link PacketTransformer#reverseTransform(RawPacket[])}.
     *
     * Replaces each packet in the input with a packet (or null) from the
     * <tt>Buffer</tt> instance for the packet's SSRC.
     *
     * @param pkts the transformed packets to be restored.
     * @return
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        for (int i = 0; i<pkts.length; i++)
        {
            RawPacket pkt = pkts[i];

            // Drop padding packets. We assume that any packets with padding
            // are no-payload probing packets.
            if (pkt != null && pkt.getPaddingSize() != 0)
                pkts[i] = null;
            pkt = pkts[i];

            if (willBuffer(pkt))
            {
                Buffer buffer = getBuffer(pkt.getSSRCAsLong());
                pkts[i] = buffer.insert(pkt);
            }

        }
        return pkts;
    }

    /**
     * Implements {@link PacketTransformer#transform(RawPacket[])}.
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts)
    {
        return pkts;
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
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Checks whether a particular <tt>RawPacket</tt> will be buffered or not
     * by this instance. Currently we only buffer VP8 packets, recognized by
     * their payload type number.
     * @param pkt the packet for which to check.
     * @return
     */
    private boolean willBuffer(RawPacket pkt)
    {
        return (pkt != null && pkt.getPayloadType() == VP8_PAYLOAD_TYPE);
    }

    /**
     * Disables the <tt>Buffer</tt> for a specific SSRC.
     * @param ssrc
     */
    void disable(long ssrc)
    {
        getBuffer(ssrc).disabled = true;
    }

    /**
     * Resets the buffer for a particular SSRC (effectively re-enabling it if
     * it was disabled).
     * @param ssrc
     */
    void reset(long ssrc)
    {
        synchronized (buffers)
        {
            buffers.remove(ssrc);
        }
    }

    /**
     * Gets the <tt>Buffer</tt> instance responsible for buffering packets with
     * SSRC <tt>ssrc</tt>. Creates it if necessary, always returns non-null.
     * @param ssrc the SSRC for which go get a <tt>Buffer</tt>.
     * @return the <tt>Buffer</tt> instance responsible for buffering packets with
     * SSRC <tt>ssrc</tt>. Creates it if necessary, always returns non-null.
     */
    private Buffer getBuffer(long ssrc)
    {
        synchronized (buffers)
        {
            Buffer buffer = buffers.get(ssrc);
            if (buffer == null)
            {
                buffer = new Buffer(SIZE, ssrc);
                buffers.put(ssrc, buffer);
            }
            return buffer;
        }
    }

    /**
     * Empties the <tt>Buffer</tt> for a specific SSRC, and returns its contents
     * as an ordered (by RTP sequence number) array.
     * @param ssrc the SSRC for which to empty the <tt>Buffer</tt>.
     * @return the contents of the <tt>Buffer</tt> for SSRC, or an empty array,
     * if there is no buffer for SSRC.
     */
    RawPacket[] emptyBuffer(long ssrc)
    {
        Buffer buffer;
        synchronized (buffers)
        {
            buffer = buffers.get(ssrc);
        }
        if (buffer != null)
        {
            return buffer.empty();
        }

        return new RawPacket[0];
    }

    /**
     * Represents a buffer for <tt>RawPacket</tt>s.
     */
    private static class Buffer
    {
        /**
         * The actual contents of this <tt>Buffer</tt>.
         */
        private final SortedSet<RawPacket> buffer;

        /**
         * The maximum capacity of this <tt>Buffer</tt>.
         */
        private final int capacity;

        /**
         * The SSRC that this <tt>Buffer</tt> is associated with.
         */
        private long ssrc;

        /**
         * Whether this buffer is disabled or not. If disabled, it will drop
         * incoming packets, and output 'null'.
         */
        private boolean disabled = false;

        /**
         * Constructs a <tt>Buffer</tt> with the given capacity and SSRC.
         * @param capacity the capacity.
         * @param ssrc the SSRC.
         */
        Buffer(int capacity, long ssrc)
        {
            buffer = new TreeSet<RawPacket>(seqNumComparator);
            this.capacity = capacity;
            this.ssrc = ssrc;
        }

        /**
         * Inserts a specific <tt>RawPacket</tt> in this <tt>Buffer</tt>. If,
         * after the insertion, the number of elements stored in the buffer
         * is more than <tt>this.capacity</tt>, removes from the buffer and
         * returns the 'first' packet in the buffer. Otherwise, return null.
         *
         * @param pkt the packet to insert.
         * @return Either the 'first' packet in the buffer, or null, according
         * to whether the buffer capacity has been reached after the insertion
         * of <tt>pkt</tt>.
         */
        RawPacket insert(RawPacket pkt)
        {
            if (disabled)
                return null;

            RawPacket ret = null;
            synchronized (buffer)
            {
                buffer.add(pkt);
                if (buffer.size() > capacity)
                {
                    ret = buffer.first();
                    buffer.remove(ret);
                }
            }

            return ret;
        }

        /**
         * Empties this <tt>Buffer</tt>, returning all its contents.
         * @return the contents of this <tt>Buffer</tt>.
         */
        RawPacket[] empty()
        {
            synchronized (buffer)
            {
                RawPacket[] ret = buffer.toArray(new RawPacket[buffer.size()]);
                buffer.clear();

                return ret;
            }

        }
    }
}
