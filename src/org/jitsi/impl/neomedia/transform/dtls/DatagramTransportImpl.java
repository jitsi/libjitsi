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
package org.jitsi.impl.neomedia.transform.dtls;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.bouncycastle.crypto.tls.*;
import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.video.h264.*;
import org.jitsi.service.neomedia.*;
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
     * The pool of <tt>RawPacket</tt>s instances to reduce their allocations
     * and garbage collection.
     */
    private final Queue<RawPacket> rawPacketPool
        = new LinkedBlockingQueue<>(RTPConnectorOutputStream.POOL_CAPACITY);

    /**
     * The queue of <tt>RawPacket</tt>s which have been received from the
     * network are awaiting to be received by the application through this
     * <tt>DatagramTransport</tt>.
     */
    private final ArrayBlockingQueue<RawPacket> receiveQ;

    /**
     * The capacity of {@link #receiveQ}.
     */
    private final int receiveQCapacity;

    /**
     * The <tt>byte</tt> buffer which represents a datagram to be sent. It may
     * consist of multiple DTLS records which are simple encoded consecutively.
     */
    private byte[] sendBuf;

    /**
     * The length in <tt>byte</tt>s of {@link #sendBuf} i.e. the number of
     * <tt>sendBuf</tt> elements which constitute actual DTLS records.
     */
    private int sendBufLength;

    /**
     * The <tt>Object</tt> that synchronizes the access to {@link #sendBuf},
     * {@link #sendBufLength}.
     */
    private final Object sendBufSyncRoot = new Object();

    /**
     * Initializes a new <tt>DatagramTransportImpl</tt>.
     *
     * @param componentID {@link Component#RTP} if the new instance is to work
     * on data/RTP packets or {@link Component#RTCP} if the new instance is to
     * work on control/RTCP packets
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

        receiveQCapacity = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY;
        receiveQ = new ArrayBlockingQueue<>(receiveQCapacity);
    }

    private AbstractRTPConnector assertNotClosed(
            boolean breakOutOfDTLSReliableHandshakeReceiveMessage)
        throws IOException
    {
        AbstractRTPConnector connector = this.connector;

        if (connector == null)
        {
            IOException ioe
                = new IOException(getClass().getName() + " is closed!");

            if (breakOutOfDTLSReliableHandshakeReceiveMessage)
                breakOutOfDTLSReliableHandshakeReceiveMessage(ioe);
            throw ioe;
        }
        else
        {
            return connector;
        }
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
    @Override
    public void close()
        throws IOException
    {
        setConnector(null);
    }

    private void doSend(byte[] buf, int off, int len)
        throws IOException
    {
        // Do preserve the sequence of sends.
        flush();

        AbstractRTPConnector connector = assertNotClosed(false);
        RTPConnectorOutputStream outputStream;

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

        // Write synchronously in order to avoid our packet getting stuck in the
        // write queue (in case it is blocked waiting for DTLS to finish, for
        // example).
        outputStream.syncWrite(buf, off, len);
    }

    private void flush()
        throws IOException
    {
        assertNotClosed(false);

        byte[] buf;
        int len;

        synchronized (sendBufSyncRoot)
        {
            if ((sendBuf != null) && (sendBufLength != 0))
            {
                buf = sendBuf;
                sendBuf = null;
                len = sendBufLength;
                sendBufLength = 0;
            }
            else
            {
                buf = null;
                len = 0;
            }
        }
        if (buf != null)
        {
            doSend(buf, 0, len);

            // Attempt to reduce allocations and garbage collection.
            synchronized (sendBufSyncRoot)
            {
                if (sendBuf == null)
                    sendBuf = buf;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
    @Override
    public int getSendLimit()
        throws IOException
    {
        AbstractRTPConnector connector = this.connector;
        int sendLimit
            = (connector == null) ? -1 : connector.getSendBufferSize();

        if (sendLimit <= 0)
        {
            /*
             * XXX The estimation bellow is wildly inaccurate and hardly related
             * but we have to start somewhere.
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
    void queueReceive(byte[] buf, int off, int len)
    {
        if (len > 0)
        {
            synchronized (receiveQ)
            {
                try
                {
                    assertNotClosed(false);
                }
                catch (IOException ioe)
                {
                    throw new IllegalStateException(ioe);
                }

                RawPacket pkt = rawPacketPool.poll();
                byte[] pktBuf;

                if ((pkt == null) || ((pktBuf = pkt.getBuffer()).length < len))
                {
                    pktBuf = new byte[len];
                    pkt = new RawPacket(pktBuf, 0, len);
                }
                else
                {
                    pktBuf = pkt.getBuffer();
                    pkt.setLength(len);
                    pkt.setOffset(0);
                }
                System.arraycopy(buf, off, pktBuf, 0, len);

                if (receiveQ.size() == receiveQCapacity)
                {
                    RawPacket oldPkt = receiveQ.remove();

                    rawPacketPool.offer(oldPkt);
                }
                receiveQ.add(pkt);
                receiveQ.notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int receive(byte[] buf, int off, int len, int waitMillis)
        throws IOException
    {
        long enterTime = System.currentTimeMillis();

        /*
         * If this DatagramTransportImpl is to be received from, then what
         * is to be received may be a response to a request that was earlier
         * scheduled for send.
         */
        /*
         * XXX However, it may unnecessarily break up a flight into multiple
         * datagrams. Since we have implemented the recognition of the end of
         * flights, it should be fairly safe to rely on it alone.
         */
//        flush();

        /*
         * If no datagram is received at all and the specified waitMillis
         * expires, a negative value is to be returned in order to have the
         * outbound flight retransmitted.
         */
        int received = -1;
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
                assertNotClosed(true);

                RawPacket pkt = receiveQ.peek();

                if (pkt != null)
                {
                    /*
                     * If a datagram has been received and even if it carries
                     * no/zero bytes, a non-negative value is to be returned in
                     * order to distinguish the case with that of no received
                     * datagram. If the received bytes do not represent a DTLS
                     * record, the record layer may still not retransmit the
                     * outbound flight. But that should not be much of a concern
                     * because we queue DTLS records into DatagramTransportImpl.  
                     */
                    if (received < 0)
                        received = 0;

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
                            rawPacketPool.offer(pkt);
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
    @Override
    public void send(byte[] buf, int off, int len)
        throws IOException
    {
        assertNotClosed(false);

        // If possible, construct a single datagram from multiple DTLS records.
        if (len >= DtlsPacketTransformer.DTLS_RECORD_HEADER_LENGTH)
        {
            short type = TlsUtils.readUint8(buf, off);
            boolean endOfFlight = false;

            switch (type)
            {
            case ContentType.handshake:
                short msg_type
                    = TlsUtils.readUint8(
                            buf,
                            off
                                + 1 /* type */
                                + 2 /* version */
                                + 2 /* epoch */
                                + 6 /* sequence_number */
                                + 2 /* length */);

                switch (msg_type)
                {
                case HandshakeType.certificate:
                case HandshakeType.certificate_request:
                case HandshakeType.certificate_verify:
                case HandshakeType.client_key_exchange:
                case HandshakeType.server_hello:
                case HandshakeType.server_key_exchange:
                case HandshakeType.session_ticket:
                case HandshakeType.supplemental_data:
                    endOfFlight = false;
                    break;
                case HandshakeType.client_hello:
                case HandshakeType.finished:
                case HandshakeType.hello_request:
                case HandshakeType.hello_verify_request:
                case HandshakeType.server_hello_done:
                    endOfFlight = true;
                    break;
                default:
                    endOfFlight = true;
                    logger.warn(
                            "Unknown DTLS handshake message type: " + msg_type);
                    break;
                }
                // Do fall through!
            case ContentType.change_cipher_spec:
                synchronized (sendBufSyncRoot)
                {
                    int newSendBufLength = sendBufLength + len;
                    int sendLimit = getSendLimit();

                    if (newSendBufLength <= sendLimit)
                    {
                        if (sendBuf == null)
                        {
                            sendBuf = new byte[sendLimit];
                            sendBufLength = 0;
                        }
                        else if (sendBuf.length < sendLimit)
                        {
                            byte[] oldSendBuf = sendBuf;

                            sendBuf = new byte[sendLimit];
                            System.arraycopy(
                                    oldSendBuf, 0,
                                    sendBuf, 0,
                                    Math.min(sendBufLength, sendBuf.length));
                        }

                        System.arraycopy(buf, off, sendBuf, sendBufLength, len);
                        sendBufLength = newSendBufLength;

                        if (endOfFlight)
                            flush();
                    }
                    else
                    {
                        if (endOfFlight)
                        {
                            doSend(buf, off, len);
                        }
                        else
                        {
                            flush();
                            send(buf, off, len);
                        }
                    }
                }
                break;

            case ContentType.alert:
            case ContentType.application_data:
            default:
                doSend(buf, off, len);
                break;
            }
        }
        else
        {
            doSend(buf, off, len);
        }
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
}
