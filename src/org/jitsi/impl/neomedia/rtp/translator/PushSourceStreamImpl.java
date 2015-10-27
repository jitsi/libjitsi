/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.media.protocol.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * Implements <tt>PushSourceStream</tt> for an <tt>RTPTranslatorImpl</tt>. Reads
 * packets from endpoint <tt>PushSourceStream</tt>s and pushes them to an
 * <tt>RTPTranslatorImpl</tt> to be translated.
 *
 * @author Lyubomir Marinov
 */
class PushSourceStreamImpl
    implements PushSourceStream,
               Runnable,
               SourceTransferHandler
{
    /**
     * The <tt>Logger</tt> used by the <tt>PushSourceStreamImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(PushSourceStreamImpl.class);

    /**
     * The indicator which determines whether {@link #close()} has been
     * invoked on this instance.
     */
    private boolean closed = false;

    private final RTPConnectorImpl connector;

    private final boolean data;

    /**
     * The indicator which determines whether
     * {@link #read(byte[], int, int)} read a <tt>SourcePacket</tt> from
     * {@link #readQ} after a <tt>SourcePacket</tt> was written there.
     */
    private boolean read = false;

    /**
     * The <tt>Queue</tt> of <tt>SourcePacket</tt>s to be read out of this
     * instance via {@link #read(byte[], int, int)}.
     */
    private final Queue<SourcePacket> readQ;

    /**
     * The capacity of {@link #readQ}.
     */
    private final int readQCapacity;

    /**
     * The pool of <tt>SourcePacket</tt> instances to reduce their
     * allocations and garbage collection.
     */
    private final Queue<SourcePacket> sourcePacketPool
        = new LinkedBlockingQueue<SourcePacket>();

    private final List<PushSourceStreamDesc> streams
        = new LinkedList<PushSourceStreamDesc>();

    /**
     * The <tt>Thread</tt> which invokes
     * {@link SourceTransferHandler#transferData(PushSourceStream)} on
     * {@link #transferHandler}. 
     */
    private Thread transferDataThread;

    private SourceTransferHandler transferHandler;

    public PushSourceStreamImpl(RTPConnectorImpl connector, boolean data)
    {
        this.connector = connector;
        this.data = data;

        readQCapacity = MaxPacketsPerMillisPolicy.PACKET_QUEUE_CAPACITY;
        readQ = new ArrayBlockingQueue<SourcePacket>(readQCapacity);

        transferDataThread = new Thread(this, getClass().getName());
        transferDataThread.setDaemon(true);
        transferDataThread.start();
    }

    public synchronized void addStream(
            RTPConnectorDesc connectorDesc,
            PushSourceStream stream)
    {
        for (PushSourceStreamDesc streamDesc : streams)
        {
            if ((streamDesc.connectorDesc == connectorDesc)
                    && (streamDesc.stream == stream))
            {
                return;
            }
        }
        streams.add(
                new PushSourceStreamDesc(connectorDesc, stream, this.data));
        stream.setTransferHandler(this);
    }

    public void close()
    {
        closed = true;
        sourcePacketPool.clear();
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public boolean endOfStream()
    {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public ContentDescriptor getContentDescriptor()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getContentLength()
    {
        return LENGTH_UNKNOWN;
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public Object getControl(String controlType)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public Object[] getControls()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public synchronized int getMinimumTransferSize()
    {
        int minimumTransferSize = 0;

        for (PushSourceStreamDesc streamDesc : streams)
        {
            int streamMinimumTransferSize
                = streamDesc.stream.getMinimumTransferSize();

            if (minimumTransferSize < streamMinimumTransferSize)
                minimumTransferSize = streamMinimumTransferSize;
        }
        return minimumTransferSize;
    }

    private RTPTranslatorImpl getTranslator()
    {
        return connector.translator;
    }

    @Override
    public int read(byte[] buffer, int offset, int length)
        throws IOException
    {
        if (closed)
            return -1;

        SourcePacket pkt;
        int pktLength;

        synchronized (readQ)
        {
            pkt = readQ.peek();
            if (pkt == null)
                return 0;

            pktLength = pkt.getLength();
            if (length < pktLength)
            {
                throw new IOException(
                        "Length " + length
                            + " is insuffient. Must be at least "
                            + pktLength + ".");
            }

            readQ.remove();
            read = true;
            readQ.notifyAll();
        }

        System.arraycopy(
                pkt.getBuffer(), pkt.getOffset(),
                buffer, offset,
                pktLength);

        PushSourceStreamDesc streamDesc = pkt.streamDesc;
        int read = pktLength;

        pkt.streamDesc = null;
        sourcePacketPool.offer(pkt);

        if (read > 0)
        {
            RTPTranslatorImpl translator = getTranslator();

            if (translator != null)
                read = translator.didRead(streamDesc, buffer, offset, read);
        }

        return read;
    }

    public synchronized void removeStreams(RTPConnectorDesc connectorDesc)
    {
        Iterator<PushSourceStreamDesc> streamIter = streams.iterator();

        while (streamIter.hasNext())
        {
            PushSourceStreamDesc streamDesc = streamIter.next();

            if (streamDesc.connectorDesc == connectorDesc)
            {
                streamDesc.stream.setTransferHandler(null);
                streamIter.remove();
            }
        }
    }

    /**
     * Runs in {@link #transferDataThread} and invokes
     * {@link SourceTransferHandler#transferData(PushSourceStream)} on
     * {@link #transferHandler}.
     */
    @Override
    public void run()
    {
        try
        {
            while (!closed)
            {
                SourceTransferHandler transferHandler
                    = this.transferHandler;

                synchronized (readQ)
                {
                    if (readQ.isEmpty() || (transferHandler == null))
                    {
                        try
                        {
                            readQ.wait(100);
                        }
                        catch (InterruptedException ie)
                        {
                        }
                        continue;
                    }
                }

                try
                {
                    transferHandler.transferData(this);
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
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
        finally
        {
            if (Thread.currentThread().equals(transferDataThread))
                transferDataThread = null;
        }
    }

    public synchronized void setTransferHandler(
            SourceTransferHandler transferHandler)
    {
        if (this.transferHandler != transferHandler)
        {
            this.transferHandler = transferHandler;
            for (PushSourceStreamDesc streamDesc : streams)
                streamDesc.stream.setTransferHandler(this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link SourceTransferHandler#transferData(PushSourceStream)}. This
     * instance sets itself as the <tt>transferHandler</tt> of all
     * <tt>PushSourceStream</tt>s that get added to it (i.e.
     * {@link #streams}). When either one of these pushes media data, this
     * instance pushes that media data.
     */
    @Override
    public void transferData(PushSourceStream stream)
    {
        if (closed)
            return;

        PushSourceStreamDesc streamDesc = null;

        synchronized (this)
        {
            for (PushSourceStreamDesc aStreamDesc : streams)
            {
                if (aStreamDesc.stream == stream)
                {
                    streamDesc = aStreamDesc;
                    break;
                }
            }
        }
        if (streamDesc == null)
            return;

        int len = stream.getMinimumTransferSize();

        if (len < 1)
            len = 2 * 1024;

        SourcePacket pkt = sourcePacketPool.poll();
        byte[] buf;

        if ((pkt == null) || ((buf = pkt.getBuffer()).length < len))
        {
            buf = new byte[len];
            pkt = new SourcePacket(buf, 0, len);
        }
        else
        {
            buf = pkt.getBuffer();
            len = buf.length;
        }

        int read = 0;

        try
        {
            read = stream.read(buf, 0, len);
        }
        catch (IOException ioe)
        {
            logger.error("Failed to read from an RTP stream!", ioe);
        }
        finally
        {
            if (read > 0)
            {
                pkt.setLength(read);
                pkt.setOffset(0);
                pkt.streamDesc = streamDesc;

                boolean yield;

                synchronized (readQ)
                {
                    int readQSize = readQ.size();

                    if (readQSize < 1)
                        yield = false;
                    else if (readQSize < readQCapacity)
                        yield = (this.read == false);
                    else
                        yield = true;
                    if (yield)
                        readQ.notifyAll();
                }
                if (yield)
                    Thread.yield();

                synchronized (readQ)
                {
                    if (readQ.size() >= readQCapacity)
                    {
                        readQ.remove();
                        logger.warn(
                                "Discarded an RTP packet because the read"
                                    + " queue is full.");
                    }

                    if (readQ.offer(pkt))
                    {
                        /*
                         * TODO It appears that it is better to not yield
                         * based on whether the read method has read after
                         * the last write.
                         */
                        // this.read = false;
                    }
                    readQ.notifyAll();
                }
            }
            else
            {
                pkt.streamDesc = null;
                sourcePacketPool.offer(pkt);
            }
        }
    }

    private static class SourcePacket
        extends RawPacket
    {
        public PushSourceStreamDesc streamDesc;

        public SourcePacket(byte[] buf, int off, int len)
        {
            super(buf, off, len);
        }
    }
}
