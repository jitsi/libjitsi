/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.dtls;

import java.io.*;
import java.util.concurrent.*;

import javax.media.rtp.*;

import org.bouncycastle.crypto.tls.*;
import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.video.h264.*;
import org.jitsi.util.*;

/**
 * Implements {@link DatagramTransport} in order to integrate the Bouncy Castle
 * Crypto APIs in libjitsi for the purposes of implementing DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class DatagramTransportImpl
    implements DatagramTransport
{
    /**
     * The <tt>Logger</tt> used by the <tt>DatagramTransportImpl</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(DatagramTransportImpl.class);

    /**
     * The ID of the component which this instance works for/is associated with.
     */
    private final int componentID;

    /**
     * The <tt>RTPConnector</tt> which represents and implements the actual
     * <tt>DatagramSocket</tt> adapted by this instance.
     */
    private AbstractRTPConnector connector;

    /**
     * The queue of <tt>RawPacket</tt>s which have been received from the
     * network are awaiting to be received by the application through this
     * <tt>DatagramTransport</tt>.
     */
    private final ArrayBlockingQueue<MutableRawPacket> receiveQ;

    /**
     * The capacity of {@link #receiveQ}.
     */
    private final int receiveQCapacity;

    /**
     * Initializes a new <tt>DatagramTransportImpl</tt>.
     *
     * @param data {@link Component#RTP} if the new instance is to work on
     * data/RTP packets or {@link Component#RTCP} if the new instance is to work
     * on control/RTCP packets
     */
    public DatagramTransportImpl(int componentID)
    {
        switch (componentID)
        {
        case Component.RTCP:
        case Component.RTP:
            this.componentID = componentID;
            break;
        default:
            throw new IllegalArgumentException("componentID");
        }

        receiveQCapacity
            = RTPConnectorOutputStream
                .MAX_PACKETS_PER_MILLIS_POLICY_PACKET_QUEUE_CAPACITY;
        receiveQ = new ArrayBlockingQueue<MutableRawPacket>(receiveQCapacity);
    }

    /**
     * Works around a bug in the Bouncy Castle Crypto APIs which may cause
     * <tt>org.bouncycastle.crypto.tls.DTLSReliableHandshake.receiveMessage()</tt>
     * to enter an endless loop.
     *
     * @param cause the <tt>Throwable</tt> which would have been thrown if the
     * bug did not exist 
     */
    private void breakOutOfDTLSReliableHandshakeReceiveMessage(Throwable cause)
    {
        for (StackTraceElement stackTraceElement : cause.getStackTrace())
        {
            if ("org.bouncycastle.crypto.tls.DTLSReliableHandshake".equals(
                        stackTraceElement.getClassName())
                    && "receiveMessage".equals(
                            stackTraceElement.getMethodName()))
            {
                throw new IllegalStateException(cause);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void close()
        throws IOException
    {
        setConnector(null);
    }

    /**
     * {@inheritDoc}
     */
    public int getReceiveLimit()
        throws IOException
    {
        AbstractRTPConnector connector = this.connector;
        int receiveLimit
            = (connector == null) ? -1 : connector.getReceiveBufferSize();

        if (receiveLimit <= 0)
            receiveLimit = RTPConnectorInputStream.PACKET_RECEIVE_BUFFER_LENGTH;
        return receiveLimit;
    }

    /**
     * {@inheritDoc}
     */
    public int getSendLimit()
        throws IOException
    {
        AbstractRTPConnector connector = this.connector;
        int sendLimit
            = (connector == null) ? -1 : connector.getSendBufferSize();

        if (sendLimit <= 0)
        {
            /*
             * XXX The estimation bellow is wildly inaccurate and hardly
             * related... but we have to start somewhere.
             */
            sendLimit
                = DtlsPacketTransformer.DTLS_RECORD_HEADER_LENGTH
                    + Packetizer.MAX_PAYLOAD_SIZE;
        }
        return sendLimit;
    }

    /**
     * Queues a packet received from the network to be received by the
     * application through this <tt>DatagramTransport</tt>.
     *
     * @param buf the array of <tt>byte</tt>s which contains the packet to be
     * queued
     * @param off the offset within <tt>buf</tt> at which the packet to be
     * queued starts
     * @param len the length within <tt>buf</tt> starting at <tt>off</tt> of the
     * packet to be queued
     */
    void queue(byte[] buf, int off, int len)
    {
        if (len > 0)
        {
            synchronized (receiveQ)
            {
                if (connector == null)
                {
                    String msg = getClass().getName() + " is closed!";
                    IllegalStateException ise = new IllegalStateException(msg);

                    logger.error(msg, ise);
                    throw ise;
                }

                byte[] pktBuf = new byte[len];
                int pktOff = 0;

                System.arraycopy(buf, off, pktBuf, pktOff, len);

                MutableRawPacket pkt
                    = new MutableRawPacket(pktBuf, pktOff, len);

                if (receiveQ.size() == receiveQCapacity)
                    receiveQ.remove();
                receiveQ.add(pkt);
                receiveQ.notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public int receive(byte[] buf, int off, int len, int waitMillis)
        throws IOException
    {
        long enterTime = System.currentTimeMillis();
        int received = 0;
        boolean interrupted = false;

        while (received < len)
        {
            long timeout;

            if (waitMillis > 0)
            {
                timeout = waitMillis - System.currentTimeMillis() + enterTime;
                if (timeout == 0 /* wait forever */)
                    timeout = -1 /* do not wait */;
            }
            else
            {
                timeout = waitMillis;
            }

            synchronized (receiveQ)
            {
                if (connector == null)
                {
                    String msg = getClass().getName() + " is closed!";
                    IOException ioe = new IOException(msg);

                    logger.error(msg, ioe);
                    breakOutOfDTLSReliableHandshakeReceiveMessage(ioe);
                    throw ioe;
                }

                MutableRawPacket pkt = receiveQ.peek();

                if (pkt != null)
                {
                    int toReceive = len - received;
                    boolean toReceiveIsPositive = (toReceive > 0);

                    if (toReceiveIsPositive)
                    {
                        int pktLength = pkt.getLength();
                        int pktOffset = pkt.getOffset();

                        if (toReceive > pktLength)
                        {
                            toReceive = pktLength;
                            toReceiveIsPositive = (toReceive > 0);
                        }
                        if (toReceiveIsPositive)
                        {
                            System.arraycopy(
                                    pkt.getBuffer(), pktOffset,
                                    buf, off + received,
                                    toReceive);
                            received += toReceive;
                        }
                        if (toReceive == pktLength)
                        {
                            receiveQ.remove();
                        }
                        else
                        {
                            pkt.setLength(pktLength - toReceive);
                            pkt.setOffset(pktOffset + toReceive);
                        }
                        if (toReceiveIsPositive)
                        {
                            /*
                             * The specified buf has received toReceive bytes
                             * and we do not concatenate RawPackets.
                             */
                            break;
                        }
                    }
                    else
                    {
                        // The specified buf has received at least len bytes.
                        break;
                    }
                }

                if (receiveQ.isEmpty())
                {
                    if (timeout >= 0)
                    {
                        try
                        {
                            receiveQ.wait(timeout);
                        }
                        catch (InterruptedException ie)
                        {
                            interrupted = true;
                        }
                    }
                    else
                    {
                        // The specified waitMillis has been exceeded.
                        break;
                    }
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
        return received;
    }

    /**
     * {@inheritDoc}
     */
    public void send(byte[] buf, int off, int len)
        throws IOException
    {
        AbstractRTPConnector connector = this.connector;

        if (connector == null)
        {
            String msg = getClass().getName() + " is closed!";
            IOException ioe = new IOException(msg);

            logger.error(msg, ioe);
            throw ioe;
        }

        OutputDataStream outputStream;

        switch (componentID)
        {
        case Component.RTCP:
            outputStream = connector.getControlOutputStream();
            break;
        case Component.RTP:
            outputStream = connector.getDataOutputStream();
            break;
        default:
            String msg = "componentID";
            IllegalStateException ise = new IllegalStateException(msg);

            logger.error(msg, ise);
            throw ise;
        }

        outputStream.write(buf, off, len);
    }

    /**
     * Sets the <tt>RTPConnector</tt> which represents and implements the actual
     * <tt>DatagramSocket</tt> to be adapted by this instance.
     * 
     * @param connector the <tt>RTPConnector</tt> which represents and
     * implements the actual <tt>DatagramSocket</tt> to be adapted by this
     * instance
     */
    void setConnector(AbstractRTPConnector connector)
    {
        synchronized (receiveQ)
        {
            this.connector = connector;
            receiveQ.notifyAll();
        }
    }

    /**
     * Exposes the <tt>setLength</tt> and <tt>setOffset</tt> methods of
     * <tt>RawPacket</tt> to <tt>DatagramTransportImpl</tt>.
     */
    private static class MutableRawPacket
        extends RawPacket
    {
        /**
         * Initializes a new <tt>MutableRawPacket</tt> instance.
         *
         * @param buf the array of <tt>byte</tt>s to be represented by the new
         * instance
         * @param off the offset within <tt>buf</tt> at which the actual packet
         * content to be represented by the new instance starts
         * @param len the number of bytes within <tt>buf</tt> starting at
         * <tt>offset</tt> which comprise the actual packet content to be
         * represented by the new instance
         */
        public MutableRawPacket(byte[] buf, int off, int len)
        {
            super(buf, off, len);
        }

        /**
         * {@inheritDoc}
         *
         * Makes the super implementation public to the
         * <tt>DatagramTransportImpl</tt> class.
         */
        @Override
        public void setLength(int length)
        {
            super.setLength(length);
        }

        /**
         * {@inheritDoc}
         *
         * Makes the super implementation public to the
         * <tt>DatagramTransportImpl</tt> class.
         */
        @Override
        public void setOffset(int offset)
        {
            super.setOffset(offset);
        }
    }
}
