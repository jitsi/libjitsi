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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import javax.media.rtp.*;

import org.jitsi.service.libjitsi.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.util.*;

/**
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public abstract class RTPConnectorOutputStream
    implements OutputDataStream
{
    /**
     * The <tt>Logger</tt> used by the <tt>RTPConnectorOutputStream</tt> class
     * and its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(RTPConnectorOutputStream.class);

    /**
     * Determines whether a <tt>RawPacket</tt> which has a specific number in
     * the total number of sent <tt>RawPacket</tt>s is to be logged by
     * {@link PacketLoggingService}.
     *
     * @param numOfPacket the number of the <tt>RawPacket</tt> in the total
     * number of sent <tt>RawPacket</tt>s
     * @return <tt>true</tt> if the <tt>RawPacket</tt> with the specified
     * <tt>numOfPacket</tt> is to be logged by <tt>PacketLoggingService</tt>;
     * otherwise, <tt>false</tt>
     */
    static boolean logPacket(long numOfPacket)
    {
        return
            (numOfPacket == 1)
                || (numOfPacket == 300)
                || (numOfPacket == 500)
                || (numOfPacket == 1000)
                || ((numOfPacket % 5000) == 0);
    }

    /**
     * Whether this <tt>RTPConnectorOutputStream</tt> is enabled or disabled.
     * While the stream is disabled, it suppresses actually sending any packets
     * via {@link #write(byte[],int,int)}.
     */
    private boolean enabled = true;

    /**
     * The functionality which allows this <tt>OutputDataStream</tt> to control
     * how many RTP packets it sends through its <tt>DatagramSocket</tt> per a
     * specific number of milliseconds.
     */
    protected MaxPacketsPerMillisPolicy maxPacketsPerMillisPolicy;

    /**
     * Number of bytes sent through this stream.
     */
    private long numberOfBytesSent = 0;

    /**
     * Used for debugging. As we don't log every packet
     * we must count them and decide which to log.
     */
    private long numberOfPackets = 0;

    /**
     * The {@code PacketLoggingService} instance (to be) utilized by this
     * instance. Cached for the sake of performance because fetching OSGi
     * services is not inexpensive.
     */
    private PacketLoggingService pktLogging;

    /**
     * The pool of <tt>RawPacket[]</tt> instances which reduces the number of
     * allocations performed by {@link #createRawPacket(byte[], int, int)}.
     * Always contains arrays full with <tt>null</tt>
     */
    private final LinkedBlockingQueue<RawPacket[]> rawPacketArrayPool
        = new LinkedBlockingQueue<>();

    /**
     * The pool of <tt>RawPacket</tt> instances which reduces the number of
     * allocations performed by {@link #createRawPacket(byte[], int, int)}.
     */
    private final LinkedBlockingQueue<RawPacket> rawPacketPool
        = new LinkedBlockingQueue<>();

    /**
     * Stream targets' IP addresses and ports.
     */
    protected final List<InetSocketAddress> targets = new LinkedList<>();

    /**
     * Initializes a new <tt>RTPConnectorOutputStream</tt> which is to send
     * packet data out through a specific socket.
     */
    public RTPConnectorOutputStream()
    {
    }

    /**
     * Add a target to stream targets list
     *
     * @param remoteAddr target ip address
     * @param remotePort target port
     */
    public void addTarget(InetAddress remoteAddr, int remotePort)
    {
        InetSocketAddress target
            = new InetSocketAddress(remoteAddr, remotePort);

        if (!targets.contains(target))
            targets.add(target);
    }

    /**
     * Close this output stream.
     */
    public void close()
    {
        if (maxPacketsPerMillisPolicy != null)
        {
            maxPacketsPerMillisPolicy.close();
            maxPacketsPerMillisPolicy = null;
        }
        removeTargets();
    }

    /**
     * Creates a <tt>RawPacket</tt> element from a specific <tt>byte[]</tt>
     * buffer in order to have this instance send its packet data through its
     * {@link #write(byte[], int, int)} method. Returns an array of one or more
     * elements, with the created <tt>RawPacket</tt> as its first element (and
     * <tt>null</tt> for all other elements)
     *
     * Allows extenders to intercept the array and possibly filter and/or
     * modify it.
     *
     * @param buf the packet data to be sent to the targets of this instance.
     * The contents of {@code buf} starting at {@code off} with the specified
     * {@code len} is copied into the buffer of the returned {@code RawPacket}.
     * @param off the offset of the packet data in <tt>buf</tt>
     * @param len the length of the packet data in <tt>buf</tt>
     * @param context the {@code Object} provided to
     * {@link #write(byte[], int, int, java.lang.Object)}. The implementation of
     * {@code RTPConnectorOutputStream} ignores the {@code context}.
     * @return an array with a single <tt>RawPacket</tt> containing the packet
     * data of the specified <tt>byte[]</tt> buffer.
     */
    protected RawPacket[] packetize(
            byte[] buf, int off, int len,
            Object context)
    {
        // get an array (full with null-s) from the pool or create a new one
        RawPacket[] pkts = rawPacketArrayPool.poll();
        if (pkts == null)
            pkts = new RawPacket[1];

        RawPacket pkt = rawPacketPool.poll();
        byte[] pktBuffer;

        if (pkt == null)
        {
            pktBuffer = new byte[len];
            pkt = new RawPacket();
        }
        else
        {
            pktBuffer = pkt.getBuffer();
        }

        if (pktBuffer.length < len)
        {
            /*
             * XXX It may be argued that if the buffer length is insufficient
             * once, it will be insufficient more than once. That is why we
             * recreate it without returning a packet to the pool.
             */
            pktBuffer = new byte[len];
        }

        pkt.setBuffer(pktBuffer);
        pkt.setFlags(0);
        pkt.setLength(len);
        pkt.setOffset(0);

        System.arraycopy(buf, off, pktBuffer, 0, len);

        pkts[0] = pkt;
        return pkts;
    }

    /**
     * Logs a specific <tt>RawPacket</tt> associated with a specific remote
     * address.
     *
     * @param packet packet to log
     * @param target the remote address associated with the <tt>packet</tt>
     */
    protected abstract void doLogPacket(
            RawPacket packet,
            InetSocketAddress target);

    /**
     * Returns the number of bytes sent trough this stream
     * @return the number of bytes sent
     */
    public long getNumberOfBytesSent()
    {
        return numberOfBytesSent;
    }

    /**
     * Gets the {@code PacketLoggingService} (to be) utilized by this instance.
     *
     * @return the {@code PacketLoggingService} (to be) utilized by this
     * instance
     */
    protected PacketLoggingService getPacketLoggingService()
    {
        if (pktLogging == null)
            pktLogging = LibJitsi.getPacketLoggingService();
        return pktLogging;
    }

    /**
     * Returns whether or not this <tt>RTPConnectorOutputStream</tt> has a valid
     * socket.
     *
     * @return <tt>true</tt> if this <tt>RTPConnectorOutputStream</tt> has a
     * valid socket; <tt>false</tt>, otherwise
     */
    protected abstract boolean isSocketValid();

    /**
     * Remove a target from stream targets list
     *
     * @param remoteAddr target ip address
     * @param remotePort target port
     * @return <tt>true</tt> if the target is in stream target list and can be
     * removed; <tt>false</tt>, otherwise
     */
    public boolean removeTarget(InetAddress remoteAddr, int remotePort)
    {
        for (Iterator<InetSocketAddress> targetIter = targets.iterator();
                targetIter.hasNext();)
        {
            InetSocketAddress target = targetIter.next();

            if (target.getAddress().equals(remoteAddr)
                    && (target.getPort() == remotePort))
            {
                targetIter.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Remove all stream targets from this session.
     */
    public void removeTargets()
    {
        targets.clear();
    }

    /**
     * Sends a specific RTP packet through the <tt>DatagramSocket</tt> of this
     * <tt>OutputDataSource</tt>.
     *
     * Warning: the <tt>RawPacket</tt> passed to this method, and its underlying
     * buffer will be consumed and might later be reused by this
     * <tt>RTPConnectorOutputStream</tt>. They should not be used by the
     * user afterwards.
     *
     * Note: this method has been exposed as package-private in order to
     * facilitate the injection of packets by a <tt>MediaStream</tt>. It should
     * be used with caution due to the above warning!
     *
     * @param packet the RTP packet to be sent through the
     * <tt>DatagramSocket</tt> of this <tt>OutputDataSource</tt>
     * @return <tt>true</tt> if the specified <tt>packet</tt> was successfully
     * sent; otherwise, <tt>false</tt>
     */
    boolean send(RawPacket packet)
    {
        if(!isSocketValid())
        {
            rawPacketPool.offer(packet);
            return false;
        }

        numberOfPackets++;
        for (InetSocketAddress target : targets)
        {
            try
            {
                sendToTarget(packet, target);

                numberOfBytesSent += (long)packet.getLength();

                if(logPacket(numberOfPackets))
                {
                    PacketLoggingService pktLogging = getPacketLoggingService();

                    if (pktLogging != null
                            && pktLogging.isLoggingEnabled(
                                    PacketLoggingService.ProtocolName.RTP))
                    {
                        doLogPacket(packet, target);
                    }
                }
            }
            catch (IOException ioe)
            {
                rawPacketPool.offer(packet);
                // TODO error handling
                return false;
            }
        }
        rawPacketPool.offer(packet);
        return true;
    }

    /**
     * Sends a specific <tt>RawPacket</tt> through this
     * <tt>OutputDataStream</tt> to a specific <tt>InetSocketAddress</tt>.
     *
     * @param packet the <tt>RawPacket</tt> to send through this
     * <tt>OutputDataStream</tt> to the specified <tt>target</tt>
     * @param target the <tt>InetSocketAddress</tt> to which the specified
     * <tt>packet</tt> is to be sent through this <tt>OutputDataStream</tt>
     * @throws IOException if anything goes wrong while sending the specified
     * <tt>packet</tt> through this <tt>OutputDataStream</tt> to the specified
     * <tt>target</tt>
     */
    protected abstract void sendToTarget(
            RawPacket packet,
            InetSocketAddress target)
        throws IOException;

    /**
     * Enables or disables this <tt>RTPConnectorOutputStream</tt>.
     * While the stream is disabled, it suppresses actually sending any packets
     * via {@link #send(RawPacket)}.
     *
     * @param enabled <tt>true</tt> to enable, <tt>false</tt> to disable.
     */
    public void setEnabled(boolean enabled)
    {
        if (this.enabled != enabled)
        {
            if (logger.isDebugEnabled())
                logger.debug("setEnabled: " + enabled);

            this.enabled = enabled;
        }
    }

    /**
     * Sets the maximum number of RTP packets to be sent by this
     * <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt> per
     * a specific number of milliseconds.
     *
     * @param maxPackets the maximum number of RTP packets to be sent by this
     * <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt> per the
     * specified number of milliseconds; <tt>-1</tt> if no maximum is to be set
     * @param perMillis the number of milliseconds per which <tt>maxPackets</tt>
     * are to be sent by this <tt>OutputDataStream</tt> through its
     * <tt>DatagramSocket</tt>
     */
    public void setMaxPacketsPerMillis(int maxPackets, long perMillis)
    {
        if (maxPacketsPerMillisPolicy == null)
        {
            if (maxPackets > 0)
            {
                if (perMillis < 1)
                    throw new IllegalArgumentException("perMillis");

                maxPacketsPerMillisPolicy
                    = new MaxPacketsPerMillisPolicy(maxPackets, perMillis)
                    {
                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        protected void send(RawPacket packet)
                        {
                            RTPConnectorOutputStream.this.send(packet);
                        }
                    };
            }
        }
        else
        {
            maxPacketsPerMillisPolicy
                .setMaxPacketsPerMillis(maxPackets, perMillis);
        }
    }

    /**
     * Changes current thread priority.
     * @param priority the new priority.
     */
    public void setPriority(int priority)
    {
        // currently no priority is set
    }

    /**
     * Implements {@link OutputDataStream#write(byte[], int, int)}.
     *
     * @param buf the {@code byte[]} to write into this {@code OutputDataStream}
     * @param off the offset in {@code buf} at which the {@code byte}s to be
     * written into this {@code OutputDataStream} start
     * @param len the number of {@code byte}s in {@code buf} starting at
     * {@code off} to be written into this {@code OutputDataStream}
     * @return the number of {@code byte}s read from {@code buf} starting at
     * {@code off} and not exceeding {@code len} and written into this
     * {@code OutputDataStream}
     */
    @Override
    public int write(byte[] buf, int off, int len)
    {
        return write(buf, off, len, /* context */ null);
    }

    /**
     * Implements {@link OutputDataStream#write(byte[], int, int)}. Allows
     * extenders to provide a context {@code Object} to invoked overrideable
     * methods such as {@link #packetize(byte[],int,int,Object)}.
     *
     * @param buf the {@code byte[]} to write into this {@code OutputDataStream}
     * @param off the offset in {@code buf} at which the {@code byte}s to be
     * written into this {@code OutputDataStream} start
     * @param len the number of {@code byte}s in {@code buf} starting at
     * {@code off} to be written into this {@code OutputDataStream}
     * @param context the {@code Object} to provide to invoked overridable
     * methods such as {@link #packetize(byte[],int,int,Object)}
     * @return the number of {@code byte}s read from {@code buf} starting at
     * {@code off} and not exceeding {@code len} and written into this
     * {@code OutputDataStream}
     */
    protected int write(byte[] buf, int off, int len, Object context)
    {
        if (enabled)
        {
            // While calling write without targets can be carried out without a
            // problem, such a situation may be a symptom of a problem. For
            // example, it was discovered during testing that RTCP was
            // seemingly-endlessly sent after hanging up a call.
            if (logger.isDebugEnabled() && targets.isEmpty())
                logger.debug("Write called without targets!", new Throwable());

            // Get the array of RawPackets we need to send.
            RawPacket[] pkts = packetize(buf, off, len, context);

            return write(pkts) ? len : -1;
        }
        else
        {
            // No need to handle the buffer at all if we are disabled apart from
            // simulating a successful operation.
            return len;
        }
    }

    /**
     * Writes an array of {@code RawPacket}s into this {@code OutputDataStream}.
     *
     * @param pkts the array of {@code RawPacket}s to write into this
     * {@code OutputDataStream}
     * @return {@code true} if all {@code pkts} were written into this
     * {@code OutputDataStream}; otherwise, {@code false}
     */
    private boolean write(RawPacket[] pkts)
    {
        boolean success = true;

        if (pkts == null)
            return success;

        for(int i = 0; i < pkts.length; i++)
        {
            RawPacket pkt = pkts[i];

            pkts[i] = null; // Clear the array before returning to the pool.

            // If we got extended, the delivery of the packet may have been
            // canceled.
            if (pkt != null)
            {
                if (success)
                {
                    if (maxPacketsPerMillisPolicy == null)
                    {
                        if (!send(pkt))
                        {
                            // Skip sending the remaining RawPackets but return
                            // them to the pool and clear pkts. The current pkt
                            // was returned to the pool.
                            success = false;
                        }
                    }
                    else
                    {
                        maxPacketsPerMillisPolicy.write(pkt);
                    }
                }
                else
                {
                    rawPacketPool.offer(pkt);
                }
            }
        }

        rawPacketArrayPool.offer(pkts);

        return success;
    }
}
