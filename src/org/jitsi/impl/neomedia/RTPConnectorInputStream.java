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

import javax.media.*;
import javax.media.protocol.*;

import net.sf.fmj.media.util.*;

import org.ice4j.socket.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.util.*;

/**
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public abstract class RTPConnectorInputStream<T>
    implements PushSourceStream
{
    /**
     * The value of the property <tt>controls</tt> of
     * <tt>RTPConnectorInputStream</tt> when there are no controls. Explicitly
     * defined in order to reduce unnecessary allocations.
     */
    private static final Object[] EMPTY_CONTROLS = new Object[0];

    /**
     * The <tt>Logger</tt> used by the <tt>RTPConnectorInputStream</tt> class
     * and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(RTPConnectorInputStream.class);

    /**
     * The length in bytes of the buffers of <tt>RTPConnectorInputStream</tt>
     * receiving packets from the network.
     */
    public static final int PACKET_RECEIVE_BUFFER_LENGTH = 4 * 1024;

    /**
     * Sets a specific priority on a specific <tt>Thread</tt>.
     * 
     * @param thread the <tt>Thread</tt> to set the specified <tt>priority</tt>
     * on
     * @param priority the priority to set on the specified <tt>thread</tt>
     */
    public static void setThreadPriority(Thread thread, int priority)
    {
        int oldPriority = thread.getPriority();

        if (priority != oldPriority)
        {
            Throwable throwable = null;

            try
            {
                thread.setPriority(priority);
            }
            catch (IllegalArgumentException | SecurityException iae)
            {
                throwable = iae;
            }
            if (throwable != null)
            {
                logger.warn("Failed to use Thread priority: " + priority);
            }
            if (logger.isDebugEnabled())
            {
                int newPriority = thread.getPriority();

                if (priority != newPriority)
                {
                    logger.debug(
                            "Did not change Thread priority from "
                                + oldPriority + " to " + priority + ", "
                                + newPriority + " instead.");
                }
            }
        }
    }

    /**
     * Packet receive buffer
     */
    private final byte[] buffer = new byte[PACKET_RECEIVE_BUFFER_LENGTH];

    /**
     * Whether this stream is closed. Used to control the termination of worker
     * thread.
     */
    private boolean closed;

    /**
     * The <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     */
    private DatagramPacketFilter[] datagramPacketFilters;

    /**
     * Whether this <tt>RTPConnectorInputStream</tt> is enabled or disabled.
     * While disabled, the stream does not accept any packets.
     */
    private boolean enabled = true;

    /**
     * Caught an IO exception during read from socket
     */
    private boolean ioError = false;

    /**
     * Number of received bytes.
     */
    private long numberOfReceivedBytes = 0;

    /**
     * The packet data to be read out of this instance through its
     * {@link #read(byte[], int, int)} method.
     */
    private RawPacket pkt;

    /**
     * The {@code PacketLoggingService} instance (to be) utilized by this
     * instance. Cached for the sake of performance because fetching OSGi
     * services is not inexpensive.
     */
    private PacketLoggingService pktLogging;

    /**
     * The <tt>Object</tt> which synchronizes the access to {@link #pkt}.
     */
    private final Object pktSyncRoot = new Object();

    /**
     * The adapter of this <tt>PushSourceStream</tt> to the
     * <tt>PushBufferStream</tt> interface.
     */
    private final PushBufferStream pushBufferStream;

    /**
     * The pool of <tt>RawPacket</tt> instances to reduce their allocations and
     * garbage collection.
     */
    private final Queue<RawPacket> rawPacketPool
            = new LinkedBlockingQueue<>(RTPConnectorOutputStream.POOL_CAPACITY);

    /**
     * The background/daemon <tt>Thread</tt> which invokes
     * {@link #receive(DatagramPacket)}.
     */
    private Thread receiveThread;

    protected final T socket;

    /**
     * SourceTransferHandler object which is used to read packets.
     */
    private SourceTransferHandler transferHandler;

    /**
     * Initializes a new <tt>RTPConnectorInputStream</tt> which is to receive
     * packet data from a specific UDP socket.
     *
     * @param socket
     */
    protected RTPConnectorInputStream(T socket)
    {
        this.socket = socket;

        if (this.socket == null)
        {
            closed = true;
        }
        else
        {
            closed = false;

            try
            {
                setReceiveBufferSize(65535);
            }
            catch (Throwable t)
            {
                if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                else if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
            }
        }

        // PacketLoggingService
        addDatagramPacketFilter(
                new DatagramPacketFilter()
                {
                    /**
                     * Used for debugging. As we don't log every packet, we must
                     * count them and decide which to log.
                     */
                    private long numberOfPackets = 0;

                    @Override
                    public boolean accept(DatagramPacket p)
                    {
                        numberOfPackets++;
                        if (RTPConnectorOutputStream.logPacket(numberOfPackets))
                        {
                            PacketLoggingService pktLogging
                                = getPacketLoggingService();

                            if (pktLogging != null
                                    && pktLogging.isLoggingEnabled(
                                            PacketLoggingService.ProtocolName
                                                    .RTP))
                            {
                                doLogPacket(p);
                            }
                        }

                        return true;
                    }
                });

        /*
         * Adapt this PushSourceStream to the PushBufferStream interface in
         * order to make it possible to read the Buffer flags of RawPacket.
         */
        pushBufferStream
            = new PushBufferStreamAdapter(this, null)
            {
                @Override
                protected int doRead(
                        Buffer buffer,
                        byte[] data, int offset, int length)
                    throws IOException
                {
                    return
                        RTPConnectorInputStream.this.read(
                                buffer,
                                data, offset, length);
                }
            };

        maybeStartReceiveThread();
    }

    /**
     * Determines whether all {@link #datagramPacketFilters} accept a received
     * <tt>DatagramPacket</tt> for pushing out of this
     * <tt>PushSourceStream</tt>. In other words, determines whether <tt>p</tt>
     * is to be discarded/dropped/ignored.
     *
     * @param p the <tt>DatagramPacket</tt> to be considered for acceptance by
     * all <tt>datagramPacketFilters</tt>
     * @return <tt>true</tt> if all <tt>datagramPacketFilters</tt> accept
     * <tt>p</tt>; otherwise, <tt>false</tt>
     */
    private boolean accept(DatagramPacket p)
    {
        boolean accept;

        if (enabled)
        {
            DatagramPacketFilter[] filters = getDatagramPacketFilters();

            if (filters == null)
            {
                accept = true;
            }
            else
            {
                accept = true;
                for (DatagramPacketFilter filter : filters)
                {
                    try
                    {
                        if (!filter.accept(p))
                        {
                            accept = false;
                            break;
                        }
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof InterruptedException)
                            Thread.currentThread().interrupt();
                        else if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            }
        }
        else
        {
            accept = false;
            if (logger.isTraceEnabled() && !closed)
            {
                logger.trace(
                        "Will drop received packet because this is disabled: "
                            + p.getLength() + " bytes.");
            }
        }
        return accept;
    }

    /**
     * Adds a <tt>DatagramPacketFilter</tt> which allows dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     *
     * @param datagramPacketFilter the <tt>DatagramPacketFilter</tt> which
     * allows dropping <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s
     */
    public synchronized void addDatagramPacketFilter(
            DatagramPacketFilter datagramPacketFilter)
    {
        datagramPacketFilters
            = ArrayUtils.add(
                    datagramPacketFilters,
                    DatagramPacketFilter.class,
                    datagramPacketFilter);
    }

    /**
     * Close this stream, stops the worker thread.
     */
    public synchronized void close()
    {
        closed = true;
        if(socket != null)
        {
            /*
             * The classes DatagramSocket and Socket implement the interface
             * Closeable since Java Runtime Environment 7.
             */
            try
            {
                if (socket instanceof Closeable)
                {
                    ((Closeable) socket).close();
                }
                else if (socket instanceof DatagramSocket)
                {
                    ((DatagramSocket) socket).close();
                }
                else if (socket instanceof Socket)
                {
                    ((Socket) socket).close();
                }
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Creates a new <tt>RawPacket</tt> from a specific <tt>DatagramPacket</tt>
     * in order to have this instance receive its packet data through its
     * {@link #read(byte[], int, int)} method. Returns an array of
     * <tt>RawPacket</tt> with the created packet as its first element (and
     * <tt>null</tt> for the other elements).
     *
     * Allows extenders to intercept the packet data and possibly filter and/or
     * modify it.
     *
     * @param datagramPacket the <tt>DatagramPacket</tt> containing the packet
     * data
     * @return an array of <tt>RawPacket</tt> containing the <tt>RawPacket</tt>
     * which contains the packet data of the
     * specified <tt>DatagramPacket</tt> as its first element.
     */
    protected RawPacket[] createRawPacket(DatagramPacket datagramPacket)
    {
        RawPacket[] pkts = new RawPacket[1];

        RawPacket pkt = rawPacketPool.poll();
        if (pkt == null)
            pkt = new RawPacket();

        byte[] buffer = pkt.getBuffer();
        int length = datagramPacket.getLength();
        if (buffer == null || buffer.length < length)
        {
            buffer = new byte[length];
            pkt.setBuffer(buffer);
        }

        System.arraycopy(
                datagramPacket.getData(), datagramPacket.getOffset(),
                buffer, 0,
                length);

        pkt.setBuffer(buffer);
        pkt.setOffset(0);
        pkt.setLength(length);
        pkt.setFlags(0);

        pkts[0] = pkt;
        return pkts;
    }

    /**
     * Log the packet.
     *
     * @param packet packet to log
     */
    protected abstract void doLogPacket(DatagramPacket packet);

    /**
     * Provides a dummy implementation of {@link
     * PushSourceStream#endOfStream()} that always returns
     * <tt>false</tt>.
     *
     * @return <tt>false</tt>, no matter what.
     */
    public boolean endOfStream()
    {
        return false;
    }

    /**
     * Provides a dummy implementation of {@link
     * PushSourceStream#getContentDescriptor()} that always returns
     * <tt>null</tt>.
     *
     * @return <tt>null</tt>, no matter what.
     */
    public ContentDescriptor getContentDescriptor()
    {
        return null;
    }

    /**
     * Provides a dummy implementation of {@link
     * PushSourceStream#getContentLength()} that always returns
     * <tt>LENGTH_UNKNOWN</tt>.
     *
     * @return <tt>LENGTH_UNKNOWN</tt>, no matter what.
     */
    public long getContentLength()
    {
        return LENGTH_UNKNOWN;
    }

    /**
     * Provides a dummy implementation of {@link
     * PushSourceStream#getControl(String)} that always returns
     * <tt>null</tt>.
     *
     * @param controlType ignored.
     * @return <tt>null</tt>, no matter what.
     */
    public Object getControl(String controlType)
    {
        if (AbstractPushBufferStream.PUSH_BUFFER_STREAM_CLASS_NAME.equals(
                controlType))
        {
            return pushBufferStream;
        }
        else
        {
            return null;
        }
    }

    /**
     * Provides a dummy implementation of {@link
     * PushSourceStream#getControls()} that always returns
     * <tt>EMPTY_CONTROLS</tt>.
     *
     * @return <tt>EMPTY_CONTROLS</tt>, no matter what.
     */
    public Object[] getControls()
    {
        return EMPTY_CONTROLS;
    }

    /**
     * Gets the <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     *
     * @return the <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     */
    protected synchronized DatagramPacketFilter[] getDatagramPacketFilters()
    {
        return datagramPacketFilters;
    }

    /**
     * Provides a dummy implementation of {@link
     * PushSourceStream#getMinimumTransferSize()} that always returns
     * <tt>2 * 1024</tt>.
     *
     * @return <tt>2 * 1024</tt>, no matter what.
     */
    @Override
    public int getMinimumTransferSize()
    {
        return 2 * 1024; // twice the MTU size, just to be safe.
    }

    /**
     * Returns the number of received bytes for the stream.
     * @return the number of received bytes
     */
    public long getNumberOfReceivedBytes()
    {
        return numberOfReceivedBytes;
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

    private synchronized void maybeStartReceiveThread()
    {
        if (receiveThread == null)
        {
            if ((socket != null) && !closed && (transferHandler != null))
            {
                receiveThread
                    = new Thread()
                    {
                        @Override
                        public void run()
                        {
                            RTPConnectorInputStream.this.runInReceiveThread();
                        }
                    };
                receiveThread.setDaemon(true);
                receiveThread.setName(
                        RTPConnectorInputStream.class.getName()
                            + ".receiveThread");

                setThreadPriority(
                        receiveThread,
                        MediaThread.getNetworkPriority());

                receiveThread.start();
            }
        }
        else
        {
            notifyAll();
        }
    }

    /**
     * Pools the specified <tt>RawPacket</tt> in order to avoid future
     * allocations and to reduce the effects of garbage collection.
     *
     * @param pkt the <tt>RawPacket</tt> to be offered to {@link #rawPacketPool}
     */
    private void poolRawPacket(RawPacket pkt)
    {
        pkt.setFlags(0);
        pkt.setLength(0);
        pkt.setOffset(0);
        rawPacketPool.offer(pkt);
    }

    /**
     * Copies the content of the most recently received packet into
     * <tt>data</tt>.
     *
     * @param buffer an optional <tt>Buffer</tt> instance associated with the
     * specified <tt>data</tt>, <tt>offset</tt> and <tt>length</tt> and
     * provided to the method in case the implementation would like to provide
     * additional <tt>Buffer</tt> properties such as <tt>flags</tt>
     * @param data the <tt>byte[]</tt> that we'd like to copy the content of
     * the packet to.
     * @param offset the position where we are supposed to start writing in
     * <tt>data</tt>.
     * @param length the number of <tt>byte</tt>s available for writing in
     * <tt>data</tt>.
     * @return the number of bytes read
     * @throws IOException if <tt>length</tt> is less than the size of the
     * packet.
     */
    protected int read(Buffer buffer, byte[] data, int offset, int length)
        throws IOException
    {
        if (data == null)
            throw new NullPointerException("data");

        if (ioError)
            return -1;

        RawPacket pkt;

        synchronized (pktSyncRoot)
        {
            pkt = this.pkt;
            this.pkt = null;
        }

        int pktLength;

        if (pkt == null)
        {
            pktLength = 0;
        }
        else
        {
            // By default, pkt will be returned to the pool after it was read.
            boolean poolPkt = true;

            try
            {
                pktLength = pkt.getLength();
                if (length < pktLength)
                {
                    /*
                     * If pkt is still the latest RawPacket made available to
                     * reading, reinstate it for the next invocation of read;
                     * otherwise, return it to the pool.
                     */
                    poolPkt = false;
                    throw new IOException(
                            "Input buffer not big enough for " + pktLength);
                }
                else
                {
                    byte[] pktBuffer = pkt.getBuffer();

                    if (pktBuffer == null)
                    {
                        throw new NullPointerException(
                                "pkt.buffer null, pkt.length " + pktLength
                                    + ", pkt.offset " + pkt.getOffset());
                    }
                    else
                    {
                        System.arraycopy(
                                pkt.getBuffer(), pkt.getOffset(),
                                data, offset,
                                pktLength);
                        if (buffer != null)
                            buffer.setFlags(pkt.getFlags());
                    }
                }
            }
            finally
            {
                if (!poolPkt)
                {
                    synchronized (pktSyncRoot)
                    {
                        if (this.pkt == null)
                            this.pkt = pkt;
                        else
                            poolPkt = true;
                    }
                }
                if (poolPkt)
                {
                    // Return pkt to the pool because it was successfully read.
                    poolRawPacket(pkt);
                }
            }
        }

        return pktLength;
    }

    /**
     * Copies the content of the most recently received packet into
     * <tt>buffer</tt>.
     *
     * @param buffer the <tt>byte[]</tt> that we'd like to copy the content of
     * the packet to.
     * @param offset the position where we are supposed to start writing in
     * <tt>buffer</tt>.
     * @param length the number of <tt>byte</tt>s available for writing in
     * <tt>buffer</tt>.
     * @return the number of bytes read
     * @throws IOException if <tt>length</tt> is less than the size of the
     * packet.
     */
    @Override
    public int read(byte[] buffer, int offset, int length)
        throws IOException
    {
        return read(null, buffer, offset, length);
    }

    /**
     * Receive packet.
     *
     * @param p packet for receiving
     * @throws IOException if something goes wrong during receiving
     */
    protected abstract void receive(DatagramPacket p)
        throws IOException;

    /**
     * Listens for incoming datagram packets, stores them for reading by the
     * <tt>read</tt> method and notifies the local <tt>transferHandler</tt>
     * that there's data to be read.
     */
    private void runInReceiveThread()
    {
        DatagramPacket p
            = new DatagramPacket(buffer, 0, PACKET_RECEIVE_BUFFER_LENGTH);

        while (!closed)
        {
            // http://code.google.com/p/android/issues/detail?id=24765
            if (OSUtils.IS_ANDROID)
                p.setLength(PACKET_RECEIVE_BUFFER_LENGTH);

            try
            {
                receive(p);
            }
            catch (IOException e)
            {
                ioError = true;
                break;
            }

            numberOfReceivedBytes += (long) p.getLength();

            try
            {
                // Do the DatagramPacketFilters accept the received DatagramPacket?

                if (accept(p))
                {
                    RawPacket[] pkts = createRawPacket(p);
                    transferData(pkts);
                }
            }
            catch (Exception e)
            {
                // The receive thread should not die as a result of a failure in
                // the packetization (converting to RawPacket[] and transforming)
                // or a failure in any of the DatagramPacketFilters.
                logger.error("Failed to receive a packet: ", e);
            }
        }
    }

    /**
     * Enables or disables this <tt>RTPConnectorInputStream</tt>.
     * While the stream is disabled, it does not accept any packets.
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
     * Changes current thread priority.
     * @param priority the new priority.
     */
    public void setPriority(int priority)
    {
//        if (receiverThread != null)
//            receiverThread.setPriority(priority);
    }

    protected abstract void setReceiveBufferSize(int receiveBufferSize)
        throws IOException;

    /**
     * Sets the <tt>transferHandler</tt> that this connector should be notifying
     * when new data is available for reading.
     *
     * @param transferHandler the <tt>transferHandler</tt> that this connector
     * should be notifying when new data is available for reading.
     */
    @Override
    public synchronized void setTransferHandler(
            SourceTransferHandler transferHandler)
    {
        if (this.transferHandler != transferHandler)
        {
            this.transferHandler = transferHandler;
            maybeStartReceiveThread();
        }
    }

    /**
     * Invokes {@link SourceTransferHandler#transferData(PushSourceStream)} on
     * {@link #transferHandler} for each of <tt>pkts</tt> in order to
     * consecutively push them out of/make them available outside this
     * <tt>PushSourceStream</tt>.
     *
     * @param pkts the set of <tt>RawPacket</tt>s to push out of this
     * <tt>PushSourceStream</tt>
     */
    private void transferData(RawPacket[] pkts)
    {
        for (int i = 0; i < pkts.length; i++)
        {
            RawPacket pkt = pkts[i];

            pkts[i] = null;

            if (pkt != null)
            {
                if (pkt.isInvalid())
                {
                    /*
                     * Return pkt to the pool because it is invalid and,
                     * consequently, will not be made available to reading.
                     */
                    poolRawPacket(pkt);
                }
                else
                {
                    RawPacket oldPkt;

                    synchronized (pktSyncRoot)
                    {
                        oldPkt = this.pkt;
                        this.pkt = pkt;
                    }
                    if (oldPkt != null)
                    {
                        /*
                         * Return oldPkt to the pool because it was made
                         * available to reading and it was not read.
                         */
                        poolRawPacket(oldPkt);
                    }

                    if (transferHandler != null && !closed)
                    {
                        try
                        {
                            transferHandler.transferData(this);
                        }
                        catch (Throwable t)
                        {
                            // XXX We cannot allow transferHandler to kill us.
                            if (t instanceof InterruptedException)
                            {
                                Thread.currentThread().interrupt();
                            }
                            else if (t instanceof ThreadDeath)
                            {
                                throw (ThreadDeath) t;
                            }
                            else
                            {
                                logger.warn(
                                    "An RTP packet may have not been fully"
                                        + " handled.",
                                    t);
                            }
                        }
                    }
                }
            }
        }
    }
}
