/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import net.sf.fmj.media.util.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;

/**
 * Implements the functionality which allows this <tt>OutputDataStream</tt>
 * to control how many RTP packets it sends through its
 * <tt>DatagramSocket</tt> per a specific number of milliseconds.
 */
public abstract class MaxPacketsPerMillisPolicy
{
    /**
     * The maximum number of packets to be sent to be kept in the queue of
     * <tt>MaxPacketsPerMillisPolicy</tt>. When the maximum is reached, the next
     * attempt to write a new packet in the queue will block until at least one
     * packet from the queue is sent. Defined in order to prevent
     * <tt>OutOfMemoryError</tt>s which, technically, may arise if the capacity
     * of the queue is unlimited.
     */
    public static final int PACKET_QUEUE_CAPACITY;

    /**
     * The name of the <tt>ConfigurationService</tt> and/or <tt>System</tt>
     * integer property which specifies the value of
     * {@link #PACKET_QUEUE_CAPACITY}.
     */
    private static final String PACKET_QUEUE_CAPACITY_PNAME
        = MaxPacketsPerMillisPolicy.class.getName() + ".PACKET_QUEUE_CAPACITY";

    static
    {
        // Read the value of PACKET_QUEUE_CAPACITY from the ConfigurationService
        // and/or System property PACKET_QUEUE_CAPACITY_PNAME.
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        int packetQueueCapacity = 256;

        if (cfg == null)
        {
            packetQueueCapacity
                = Integer.getInteger(
                        PACKET_QUEUE_CAPACITY_PNAME,
                        packetQueueCapacity);
        }
        else
        {
            packetQueueCapacity
                = cfg.getInt(
                        PACKET_QUEUE_CAPACITY_PNAME,
                        packetQueueCapacity);
        }
        PACKET_QUEUE_CAPACITY = packetQueueCapacity;
    }

    /**
     * The indicator which determines whether {@link #close()} has been
     * invoked on this instance.
     */
    private boolean closed = false;

    /**
     * The maximum number of RTP packets to be sent by this
     * <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt> per
     * {@link #perNanos} nanoseconds.
     */
    private int maxPackets = -1;

    /**
     * The time stamp in nanoseconds of the start of the current
     * <tt>perNanos</tt> interval.
     */
    private long millisStartTime = 0;

    /**
     * The list of RTP packets to be sent through the
     * <tt>DatagramSocket</tt> of this <tt>OutputDataSource</tt>.
     */
    private final ArrayBlockingQueue<RawPacket> packetQueue
        = new ArrayBlockingQueue<RawPacket>(PACKET_QUEUE_CAPACITY);

    /**
     * The number of RTP packets already sent during the current
     * <tt>perNanos</tt> interval.
     */
    private long packetsSentInMillis = 0;

    /**
     * The time interval in nanoseconds during which {@link #maxPackets}
     * number of RTP packets are to be sent through the
     * <tt>DatagramSocket</tt> of this <tt>OutputDataSource</tt>.
     */
    private long perNanos = -1;

    /**
     * The <tt>Thread</tt> which is to send the RTP packets in
     * {@link #packetQueue} through the <tt>DatagramSocket</tt> of this
     * <tt>OutputDataSource</tt>.
     */
    private Thread sendThread;

    /**
     * The flag which indicates whether packets added while the queue is full
     * should be dropped or not. See {@link #write(RawPacket)}.
     */
    private boolean dropPacketsWhenFull = false;

    /**
     * Initializes a new <tt>MaxPacketsPerMillisPolicy</tt> instance which
     * is to control how many RTP packets this <tt>OutputDataSource</tt> is
     * to send through its <tt>DatagramSocket</tt> per a specific number of
     * milliseconds.
     *
     * @param maxPackets the maximum number of RTP packets to be sent per
     * <tt>perMillis</tt> milliseconds through the <tt>DatagramSocket</tt>
     * of this <tt>OutputDataStream</tt>
     * @param perMillis the number of milliseconds per which a maximum of
     * <tt>maxPackets</tt> RTP packets are to be sent through the
     * <tt>DatagramSocket</tt> of this <tt>OutputDataStream</tt>
     */
    public MaxPacketsPerMillisPolicy(int maxPackets, long perMillis)
    {
        setMaxPacketsPerMillis(maxPackets, perMillis);

        if (sendThread == null)
        {
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
            sendThread.setName(
                    MaxPacketsPerMillisPolicy.class.getName() + ".sendThread");

            RTPConnectorInputStream.setThreadPriority(
                    sendThread,
                    MediaThread.getNetworkPriority());

            sendThread.start();
        }
    }

    /**
     * Closes the connector.
     */
    public void close()
    {
        // The value of the field closed ever changes from false to true.
        // Additionally, it is not a big deal if more than one thread offers a
        // new RawPacket to packetQueue in order to wake sendThread up.
        if (!closed)
        {
            closed = true;
            // Offer a new RawPacket to wake sendThread up in case it is waiting
            // on packetQueue.
            packetQueue.offer(new RawPacket());
        }
    }

    /**
     * Sends the RTP packets in {@link #packetQueue} in accord with
     * {@link #maxPackets} and {@link #perNanos}.
     */
    private void runInSendThread()
    {
        try
        {
            RawPacket packet = null;

            while (!closed)
            {
                if (packet == null)
                {
                    try
                    {
                        packet = packetQueue.take();
                    }
                    catch (InterruptedException iex)
                    {
                        continue;
                    }
                    // The current thread has potentially waited in order to
                    // take a RawPacket from packetQueue.
                    if (closed)
                        break;
                }

                long time = System.nanoTime();
                long millisRemainingTime = time - millisStartTime;

                if (perNanos < 1 || millisRemainingTime >= perNanos)
                {
                    millisStartTime = time;
                    packetsSentInMillis = 0;
                }
                else if (maxPackets > 0 && packetsSentInMillis >= maxPackets)
                {
                    LockSupport.parkNanos(millisRemainingTime);
                    continue;
                }

                try
                {
                    send(packet);
                }
                finally
                {
                    packet = null;
                }
                packetsSentInMillis++;
            }
        }
        finally
        {
            packetQueue.clear();
            if (Thread.currentThread().equals(sendThread))
                sendThread = null;
        }
    }

    protected abstract void send(RawPacket packet);

    /**
     * Sets the maximum number of RTP packets to be sent by this
     * <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt> per
     * a specific number of milliseconds.
     *
     * @param maxPackets the maximum number of RTP packets to be sent by
     * this <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt>
     * per the specified number of milliseconds; <tt>-1</tt> if no maximum
     * is to be set
     * @param perMillis the number of milliseconds per which
     * <tt>maxPackets</tt> are to be sent by this <tt>OutputDataStream</tt>
     * through its <tt>DatagramSocket</tt>
     */
    public void setMaxPacketsPerMillis(int maxPackets, long perMillis)
    {
        if (maxPackets < 1)
        {
            this.maxPackets = -1;
            this.perNanos = -1;
        }
        else
        {
            if (perMillis < 1)
                throw new IllegalArgumentException("perMillis");

            this.maxPackets = maxPackets;
            this.perNanos = perMillis * 1000000;
        }
    }

    /**
     * Queues a specific RTP packet to be sent through the
     * <tt>DatagramSocket</tt> of this <tt>OutputDataStream</tt>.
     *
     * When the queue ({@link #packetQueue}) is full, the behaviour of this
     * method depends on {@link #dropPacketsWhenFull}. If the flag is set,
     * the packet will be dropped and the method will return immediately.
     * Otherwise, the method will block until the packet can be accommodated.
     *
     * @param packet the RTP packet to be queued for sending through the
     * <tt>DatagramSocket</tt> of this <tt>OutputDataStream</tt>
     */
    public void write(RawPacket packet)
    {
        do
        {
            try
            {
                if (dropPacketsWhenFull)
                {
                    // Drop the first rather than the current packet, so that
                    // receivers can notice the loss earlier.
                    if (packetQueue.size() == PACKET_QUEUE_CAPACITY)
                        packetQueue.poll();
                    packetQueue.offer(packet);
                }
                else
                    packetQueue.put(packet);
                break;
            }
            catch (InterruptedException iex)
            {
            }
        }
        while (true);
    }

    /**
     * Sets the <tt>dropPacketsWhenFull</tt> flag.
     * @param dropPacketsWhenFull the value to set.
     */
    public void setDropPacketsWhenFull(boolean dropPacketsWhenFull)
    {
        this.dropPacketsWhenFull = dropPacketsWhenFull;
    }
}
