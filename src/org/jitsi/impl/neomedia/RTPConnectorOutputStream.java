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
import java.util.concurrent.locks.*;

import javax.media.rtp.*;

import net.sf.fmj.media.util.*;
import org.ice4j.util.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.util.*;
import org.jitsi.util.Logger; // Disambiguation.

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
     * The maximum number of packets to be sent to be kept in the queue of
     * {@link RTPConnectorOutputStream}. When the maximum is reached, the next
     * attempt to write a new packet in the queue will result in the first
     * packet in the queue being dropped.
     * Defined in order to prevent <tt>OutOfMemoryError</tt>s which may arise if
     * the capacity of the queue is unlimited.
     */
    public static final int PACKET_QUEUE_CAPACITY;

    /**
     * The maximum size of the queues used as pools for unused objects.
     */
    public static final int POOL_CAPACITY;

    /**
     * The size of the window over which average bitrate will be calculated.
     */
    private static final int AVERAGE_BITRATE_WINDOW_MS;

    /**
     * The flag which controls whether this {@link RTPConnectorOutputStream}
     * should create its own thread which will perform the packetization
     * (and potential transformation) and sending of packets to the targets.
     *
     * If {@code true}, calls to {@link #write(byte[], int, int)} will only
     * add the given bytes to {@link #queue}. Otherwise, packetization (via
     * {@link #packetize(byte[], int, int, Object)}) and output (via {@link
     * #sendToTarget(RawPacket, InetSocketAddress)} will be performed by the
     * calling thread. Note that these are potentially blocking operations.
     *
     * Note: if pacing is to be
     */
    private static final boolean USE_SEND_THREAD;

    /**
     * The name of the property which controls the value of {@link
     * #USE_SEND_THREAD}.
     */
    private static final String USE_SEND_THREAD_PNAME
        = RTPConnectorOutputStream.class.getName() + ".USE_SEND_THREAD";

    /**
     * The name of the <tt>ConfigurationService</tt> and/or <tt>System</tt>
     * integer property which specifies the value of
     * {@link #PACKET_QUEUE_CAPACITY}.
     */
    private static final String PACKET_QUEUE_CAPACITY_PNAME
        = RTPConnectorOutputStream.class.getName() + ".PACKET_QUEUE_CAPACITY";

    /**
     * The name of the property which specifies the value of {@link
     * #POOL_CAPACITY}.
     */
    private static final String POOL_CAPACITY_PNAME
        = RTPConnectorOutputStream.class.getName() + ".POOL_CAPACITY";

    /**
     * The name of the property which specifies the value of {@link
     * #AVERAGE_BITRATE_WINDOW_MS}.
     */
    private static final String AVERAGE_BITRATE_WINDOW_MS_PNAME
        = RTPConnectorOutputStream.class.getName()
            + ".AVERAGE_BITRATE_WINDOW_MS";

    static
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        // Set USE_SEND_THREAD
        USE_SEND_THREAD
            = ConfigUtils.getBoolean(cfg, USE_SEND_THREAD_PNAME, true);

        POOL_CAPACITY = ConfigUtils.getInt(cfg, POOL_CAPACITY_PNAME, 100);

        AVERAGE_BITRATE_WINDOW_MS
            = ConfigUtils.getInt(cfg, AVERAGE_BITRATE_WINDOW_MS_PNAME, 5000);

        // Set PACKET_QUEUE_CAPACITY
        int packetQueueCapacity
            = ConfigUtils.getInt(cfg, PACKET_QUEUE_CAPACITY_PNAME, -1);

        if (packetQueueCapacity == -1)
        {
            // Backward-compatibility with the old property name.
            String oldPropertyName
                = "org.jitsi.impl.neomedia.MaxPacketsPerMillisPolicy"
                    + ".PACKET_QUEUE_CAPACITY";

            packetQueueCapacity = ConfigUtils.getInt(cfg, oldPropertyName, -1);
        }

        PACKET_QUEUE_CAPACITY
            = packetQueueCapacity >= 0 ? packetQueueCapacity : 1024;

        if (logger.isDebugEnabled())
        {
            logger.debug("Initialized configuration. "
                         + "Send thread: " + USE_SEND_THREAD
                         + ". Pool capacity: " + POOL_CAPACITY
                         + ". Queue capacity: " + PACKET_QUEUE_CAPACITY
                         + ". Avg bitrate window: " + AVERAGE_BITRATE_WINDOW_MS);

        }
    }

    /**
     * Returns true if a warning should be logged after a queue has dropped
     * {@code numDroppedPackets} packets.
     * @param numDroppedPackets the number of dropped packets.
     * @return {@code true} if a warning should be logged.
     */
    public static boolean logDroppedPacket(int numDroppedPackets)
    {
        return
                numDroppedPackets == 1 ||
                (numDroppedPackets <= 1000 && numDroppedPackets % 100 == 0) ||
                numDroppedPackets % 1000 == 0;
    }

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
     * Number of bytes sent through this stream to any of its targets.
     */
    private long numberOfBytesSent = 0;

    /**
     * Number of packets sent through this stream, not taking into account the
     * number of its targets.
     */
    private long numberOfPackets = 0;

    /**
     * The number of packets dropped because a packet was inserted while
     * {@link #queue} was full.
     */
    private int numDroppedPackets = 0;

    /**
     * The {@code PacketLoggingService} instance (to be) utilized by this
     * instance. Cached for the sake of performance because fetching OSGi
     * services is not inexpensive.
     */
    private PacketLoggingService pktLogging;

    /**
     * The pool of <tt>RawPacket</tt> instances which reduces the number of
     * allocations performed by {@link #packetize(byte[], int, int, Object)}.
     */
    private final LinkedBlockingQueue<RawPacket> rawPacketPool
        = new LinkedBlockingQueue<>(POOL_CAPACITY);

    /**
     * Stream targets' IP addresses and ports.
     */
    protected final List<InetSocketAddress> targets = new LinkedList<>();

    /**
     * The {@link Queue} which will hold packets to be processed, if using a
     * separate thread for sending is enabled.
     */
    private final Queue queue;

    /**
     * Whether this {@link RTPConnectorOutputStream} is closed.
     */
    private boolean closed = false;

    /**
     * The {@code RateStatistics} instance used to calculate the sending bitrate
     * of this output stream.
     */
    private final RateStatistics rateStatistics
        = new RateStatistics(AVERAGE_BITRATE_WINDOW_MS);

    /**
     * Initializes a new <tt>RTPConnectorOutputStream</tt> which is to send
     * packet data out through a specific socket.
     */
    public RTPConnectorOutputStream()
    {
        if (USE_SEND_THREAD)
        {
            queue = new Queue();
        }
        else
        {
            queue = null;
        }
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
        if (!closed)
        {
            closed = true;

            removeTargets();
        }
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
        RawPacket[] pkts = new RawPacket[1];

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
     * @param packet the RTP packet to be sent through the
     * <tt>DatagramSocket</tt> of this <tt>OutputDataSource</tt>
     * @return <tt>true</tt> if the specified <tt>packet</tt> was successfully
     * sent to all targets; otherwise, <tt>false</tt>.
     */
    private boolean send(RawPacket packet)
    {
        if(!isSocketValid())
        {
            rawPacketPool.offer(packet);
            return false;
        }

        numberOfPackets++;
        if(targets.isEmpty())
            logger.warn("targets list empty, not sending packet");
        for (InetSocketAddress target : targets)
        {
            try
            {
                sendToTarget(packet, target);

                numberOfBytesSent += (long)packet.getLength();

                if (logPacket(numberOfPackets))
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
                logger.error(
                    "Failed to send a packet to target " + target + ":" + ioe);
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
    public boolean setMaxPacketsPerMillis(int maxPackets, long perMillis)
    {
        if (queue != null)
        {
            queue.setMaxPacketsPerMillis(maxPackets, perMillis);
        }
        else
        {
            logger.error("Cannot enable pacing: send thread disabled.");
        }

        return queue != null;
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
     * Writes a byte[] to this {@link RTPConnectorOutputStream} synchronously (
     * even when {@link #USE_SEND_THREAD} is enabled).
     *
     * @param buf
     * @param off
     * @param len
     * @return the number of bytes written.
     */
    public int syncWrite(byte[] buf, int off, int len)
    {
        return syncWrite(buf, off, len, null);
    }

    /**
     * Writes a byte[] to this {@link RTPConnectorOutputStream} synchronously (
     * even when {@link #USE_SEND_THREAD} is enabled).
     *
     * @param buf
     * @param off
     * @param len
     * @return the number of bytes written.
     */
    private int syncWrite(byte[] buf, int off, int len, Object context)
    {
        int result = -1;
        RawPacket[] pkts = packetize(buf, off, len, context);

        if (pkts != null)
        {
            if (write(pkts))
            {
                result = len;
            }
        }
        else
        {
            result = len; // there was nothing to send
        }

        return result;
    }

    /**
     * Implements {@link OutputDataStream#write(byte[], int, int)}. Allows
     * extenders to provide a context {@code Object} to invoked overridable
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
            // seemingly endlessly sent after hanging up a call.
            if (logger.isDebugEnabled() && targets.isEmpty())
                logger.debug("Write called without targets!", new Throwable());

            if (queue != null)
            {
                queue.write(buf, off, len, context);
            }
            else
            {
                syncWrite(buf, off, len, context);
            }
        }

        return len;
    }

    /**
     * Sends an array of {@link RawPacket}s to this
     * {@link RTPConnectorOutputStream}'s targets.
     *
     * @param pkts the array of {@link RawPacket}s to send.
     * @return {@code true} if all {@code pkts} were written into this
     * {@code OutputDataStream}; otherwise, {@code false}
     */
    private boolean write(RawPacket[] pkts)
    {
        if (closed)
            return false;
        if (pkts == null)
            return true;

        boolean success = true;
        long now = System.currentTimeMillis();

        for (RawPacket pkt : pkts)
        {
            // If we got extended, the delivery of the packet may have been
            // canceled.
            if (pkt != null)
            {
                if (success)
                {
                    if (!send(pkt))
                    {
                        // Skip sending the remaining RawPackets but return
                        // them to the pool and clear pkts. The current pkt
                        // was returned to the pool by send().
                        success = false;
                    }
                    else
                    {
                        rateStatistics.update(pkt.getLength(), now);
                    }
                }
                else
                {
                    rawPacketPool.offer(pkt);
                }
            }
        }

        return success;
    }

    /**
     * @return the current output bitrate in bits per second.
     */
    public long getOutputBitrate()
    {
        return getOutputBitrate(System.currentTimeMillis());
    }

    /**
     * @return the current output bitrate in bits per second.
     * @param now the current time.
     */
    public long getOutputBitrate(long now)
    {
        return rateStatistics.getRate(now);
    }

    private class Queue
    {
        /**
         * The {@link java.util.Queue} which holds {@link Buffer}s to be
         * processed by {@link #sendThread}.
         */
        final ArrayBlockingQueue<Buffer> queue
            = new ArrayBlockingQueue<>(PACKET_QUEUE_CAPACITY);

        /**
         * A pool of {@link
         * org.jitsi.impl.neomedia.RTPConnectorOutputStream.Queue.Buffer}
         * instances.
         */
        final ArrayBlockingQueue<Buffer> pool
            = new ArrayBlockingQueue<>(15);

        /**
         * The maximum number of {@link Buffer}s to be processed by {@link
         * #sendThread} per {@link #perNanos} nanoseconds.
         */
        int maxBuffers = -1;

        /**
         * The time interval in nanoseconds during which no more than {@link
         * #maxBuffers} {@link Buffer}s are to be processed by {@link
         * #sendThread}.
         */
        long perNanos = -1;

        /**
         * The number of {@link Buffer}s already processed during the current
         * <tt>perNanos</tt> interval.
         */
        long buffersProcessedInCurrentInterval = 0;

        /**
         * The time stamp in nanoseconds of the start of the current
         * <tt>perNanos</tt> interval.
         */
        long intervalStartTimeNanos = 0;

        /**
         * The {@link Thread} which is to read {@link Buffer}s from this
         * {@link Queue} and send them to this {@link
         * RTPConnectorOutputStream}'s targets.
         */
        final Thread sendThread;

        /**
         * The instance optionally used to gather and print statistics about
         * this queue.
         */
        QueueStatistics queueStats = null;

        /**
         * Initializes a new {@link Queue} instance and starts its send thread.
         */
        private Queue()
        {
            if (logger.isTraceEnabled())
            {
                queueStats = new QueueStatistics(
                    getClass().getSimpleName() + "-" + hashCode());
            }

            sendThread
                = new Thread()
            {
                @Override
                public void run()
                {
                    runInSendThread();
                }
            };
            sendThread.setDaemon(true);
            sendThread.setName(Queue.class.getName() + ".sendThread");

            RTPConnectorInputStream.setThreadPriority(
                    sendThread,
                    MediaThread.getNetworkPriority());

            sendThread.start();
        }

        /**
         * Adds the given buffer (and its context) to this queue.
         * @param buf
         * @param off
         * @param len
         * @param context
         */
        private void write(byte[] buf, int off, int len, Object context)
        {
            if (closed)
                return;

            Buffer buffer = getBuffer(len);
            System.arraycopy(buf, off, buffer.buf, 0, len);
            buffer.len = len;
            buffer.context = context;

            long now = System.currentTimeMillis();
            if (queue.size() >= PACKET_QUEUE_CAPACITY)
            {
                // Drop from the head of the queue.
                Buffer b = queue.poll();
                if (b != null)
                {
                    if (queueStats != null)
                    {
                        queueStats.remove(now);
                    }
                    pool.offer(b);
                    numDroppedPackets++;
                    if (logDroppedPacket(numDroppedPackets))
                    {
                        logger.warn(
                                "Packets dropped (hashCode=" + hashCode() + "): "
                                        + numDroppedPackets);
                    }
                }
            }

            if (queue.offer(buffer) && queueStats != null)
            {
                queueStats.add(now);
            }
        }

        /**
         * Reads {@link Buffer}s from {@link #queue}, "packetizes" them through
         * {@link RTPConnectorOutputStream#packetize(byte[], int, int, Object)}
         * and sends the resulting packets to this
         * {@link RTPConnectorOutputStream}'s targets.
         *
         * If a pacing policy is configured, makes sure that it is respected.
         * Note that this pacing is done on the basis of the number of
         * {@link Buffer}s read from the queue, which technically could be
         * different than the number of {@link RawPacket}s sent. This is done
         * in order to keep the implementation simpler, and because in the
         * majority of the cases (and in all current cases where pacing is
         * enabled) the numbers do match.
         */
        private void runInSendThread()
        {
            if (!Thread.currentThread().equals(sendThread))
            {
                logger.warn(
                        "runInSendThread executing in the wrong thread: "
                                + Thread.currentThread().getName(),
                        new Throwable());
                return;
            }

            try
            {
                while (!closed)
                {
                    Buffer buffer;
                    try
                    {
                        buffer = queue.poll(500, TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException iex)
                    {
                        continue;
                    }

                    // The current thread has potentially waited.
                    if (closed)
                    {
                        break;
                    }

                    if (buffer == null)
                    {
                        continue;
                    }

                    if (queueStats != null)
                    {
                        queueStats.remove(System.currentTimeMillis());
                    }

                    RawPacket[] pkts;
                    try
                    {
                        // We will sooner or later process the Buffer. Since this

                        // may take a non-negligible amount of time, do it
                        // before
                        // taking pacing into account.
                        pkts
                            = packetize(
                                buffer.buf, 0, buffer.len,
                                buffer.context);
                    }
                    catch (Exception e)
                    {
                        // The sending thread must not die because of a failure
                        // in the conversion to RawPacket[] or any of the
                        // transformations (because of e.g. parsing errors).
                        logger.error("Failed to handle an outgoing packet: ", e);
                        continue;
                    }
                    finally
                    {
                        pool.offer(buffer);
                    }

                    if (perNanos > 0 && maxBuffers > 0)
                    {
                        long time = System.nanoTime();
                        long nanosRemainingTime = time - intervalStartTimeNanos;

                        if (nanosRemainingTime >= perNanos)
                        {
                            intervalStartTimeNanos = time;
                            buffersProcessedInCurrentInterval = 0;
                        }
                        else if (buffersProcessedInCurrentInterval >= maxBuffers)
                        {
                            LockSupport.parkNanos(nanosRemainingTime);
                        }
                    }

                    try
                    {
                        RTPConnectorOutputStream.this.write(pkts);
                    }
                    catch (Exception e)
                    {
                        logger.error("Failed to send a packet: ", e);
                        continue;
                    }

                    buffersProcessedInCurrentInterval++;

                }
            }
            finally
            {
                queue.clear();
            }
        }

        public void setMaxPacketsPerMillis(int maxPackets, long perMillis)
        {
            if (maxPackets < 1)
            {
                // This doesn't make sense. Disable pacing.
                this.maxBuffers = -1;
                this.perNanos = -1;
            }
            else
            {
                if (perMillis < 1)
                    throw new IllegalArgumentException("perMillis");

                this.maxBuffers = maxPackets;
                this.perNanos = perMillis * 1000000;
            }
        }

        /**
         * @return a free {@link Buffer} instance with a byte array with a
         * length of at least {@code len}.
         */
        private Buffer getBuffer(int len)
        {
            Buffer buffer = pool.poll();
            if (buffer == null)
                buffer = new Buffer();
            if (buffer.buf == null || buffer.buf.length < len)
                buffer.buf = new byte[len];

            return buffer;
        }

        private class Buffer
        {
            byte[] buf;
            int len;
            Object context;
            private Buffer() {}
        }
    }
}
