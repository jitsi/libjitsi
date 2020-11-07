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
package org.jitsi.sctp4j;

import java.io.*;

import org.jitsi.utils.logging.*;

/**
 * SCTP socket implemented using "usrsctp" lib.
 *
 * @author Pawel Domas
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class SctpSocket
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(SctpSocket.class);

    /**
     * Reads 32 bit unsigned int from the buffer at specified offset
     *
     * @param buffer
     * @param offset
     * @return 32 bit unsigned value
     */
    private static long bytes_to_long(byte[] buffer, int offset)
    {
        int fByte = (0x000000FF & ((int) buffer[offset]));
        int sByte = (0x000000FF & ((int) buffer[offset + 1]));
        int tByte = (0x000000FF & ((int) buffer[offset + 2]));
        int foByte = (0x000000FF & ((int) buffer[offset + 3]));
        return ((long) (fByte << 24
            | sByte << 16
            | tByte << 8
            | foByte))
            & 0xFFFFFFFFL;
    }

    /**
     * Reads 16 bit unsigned int from the buffer at specified offset
     *
     * @param buffer
     * @param offset
     * @return 16 bit unsigned int
     */
    private static int bytes_to_short(byte[] buffer, int offset)
    {
        int fByte = (0x000000FF & ((int) buffer[offset]));
        int sByte = (0x000000FF & ((int) buffer[offset + 1]));
        return ((fByte << 8) | sByte) & 0xFFFF;
    }

    private static void debugChunks(byte[] packet)
    {
        int offset = 12;// After common header
        while((packet.length-offset) >= 4)
        {
            int chunkType = packet[offset++] & 0xFF;
            
            int chunkFlags = packet[offset++] & 0xFF;
            
            int chunkLength = bytes_to_short(packet, offset);
            offset+=2;
            
            logger.debug("CH: " + chunkType
                             + " FL: " + chunkFlags
                             + " L: "+chunkLength );
            if(chunkType == 1)
            {
                //Init chunk info
                
                long initTag = bytes_to_long(packet, offset);
                offset += 4;
                
                long a_rwnd = bytes_to_long(packet, offset);
                offset += 4;
                
                int nOutStream = bytes_to_short(packet, offset);
                offset += 2;
                
                int nInStream = bytes_to_short(packet, offset);
                offset += 2;
                
                long initTSN = bytes_to_long(packet, offset);
                offset += 4;
                
                logger.debug(
                    "ITAG: 0x" + Long.toHexString(initTag)
                    + " a_rwnd: " + a_rwnd
                    + " nOutStream: " + nOutStream
                    + " nInStream: " + nInStream
                    + " initTSN: 0x" + Long.toHexString(initTSN));

                // Parse Type-Length-Value chunks
                /*while(offset < chunkLength)
                {
                    //System.out.println(packet[offset++]&0xFF);
                    int type = bytes_to_short(packet, offset);
                    offset += 2;
                    
                    int length = bytes_to_short(packet, offset);
                    offset += 2;
                    
                    // value
                    offset += (length-4);
                    System.out.println(
                        "T: "+type+" L: "+length+" left: "+(chunkLength-offset));
                }*/

                offset += (chunkLength-4-16);
            }
            else if(chunkType == 0)
            {
                // Payload
                boolean U = (chunkFlags & 0x4) > 0;
                boolean B = (chunkFlags & 0x2) > 0;
                boolean E = (chunkFlags & 0x1) > 0;
                
                long TSN = bytes_to_long(packet, offset); offset += 4;

                int streamIdS = bytes_to_short(packet, offset); offset += 2;
                
                int streamSeq = bytes_to_short(packet, offset); offset += 2;
                
                long PPID = bytes_to_long(packet, offset); offset += 4;
                
                logger.debug(
                    "U: " + U + " B: " +B + " E: " + E 
                    + " TSN: 0x" + Long.toHexString(TSN)
                    + " SID: 0x" + Integer.toHexString(streamIdS)
                    + " SSEQ: 0x" + Integer.toHexString(streamSeq)
                    + " PPID: 0x" + Long.toHexString(PPID)
                );

                offset += (chunkLength-4-12);
            }
            else if(chunkType == 6)
            {
                // Abort
                logger.debug("We have abort!!!");

                if(offset >= chunkLength)
                    logger.debug("No abort CAUSE!!!");
                    
                while(offset < chunkLength)
                {
                    int causeCode = bytes_to_short(packet, offset);
                    offset += 2;
                    
                    int causeLength = bytes_to_short(packet, offset);
                    offset += 2;
                    
                    logger.debug("Cause: " + causeCode + " L: " + causeLength);
                }
            }
            else
            {
                offset += (chunkLength-4);
            }
        }
    }

    public static void debugSctpPacket(byte[] packet, String id)
    {
        System.out.println(id);
        if(packet.length >= 12)
        {
            //Common header
            int srcPort = bytes_to_short(packet, 0);
            int dstPort = bytes_to_short(packet, 2);
            
            long verificationTag = bytes_to_long(packet, 4);
            long checksum = bytes_to_long(packet, 8);
            
            logger.debug(
                  "SRC P: " + srcPort + " DST P: " + dstPort + " VTAG: 0x"
                      + Long.toHexString(verificationTag) + " CHK: 0x"
                      + Long.toHexString(checksum));

            debugChunks(packet);
        }
    }

    /**
     * The indicator which determines whether {@link #close()} has been invoked
     * on this <tt>SctpSocket</tt>. It does NOT indicate whether
     * {@link Sctp#closeSocket(long)} has been invoked with {@link #ptr}.
     */
    private boolean closed = false;

    /**
     * Callback used to notify about received data.
     */
    private SctpDataCallback dataCallback;

    /**
     * The link used to send network packets.
     */
    private NetworkLink link;

    /**
     * Local SCTP port.
     */
    private final int localPort;

    /**
     * SCTP notification listener.
     */
    private NotificationListener notificationListener
        = new NotificationListener()
        {
            /**
             * {@inheritDoc}
             */
            @Override
            public void onSctpNotification(
                    SctpSocket socket,
                    SctpNotification notification)
            {
                if (logger.isTraceEnabled())
                {
                    logger.trace(
                            "SctpSocket 0x" + Long.toHexString(ptr)
                                + " notification: " + notification);
                }
            }
        };

    /**
     * Pointer to native socket counterpart.
     */
    private long ptr;

    /**
     * The number of current readers of {@link #ptr} which are preventing the
     * writer (i.e. {@link #close()}) from invoking
     * {@link Sctp#closeSocket(long)}.
     */
    private int ptrLockCount = 0;

    /**
     * Creates new instance of <tt>SctpSocket</tt>.
     *
     * @param ptr native socket pointer.
     * @param localPort local SCTP port on which this socket is bound.
     */
    SctpSocket(long ptr, int localPort)
    {
        if (ptr == 0)
            throw new NullPointerException("ptr");

        this.ptr = ptr;
        this.localPort = localPort;

        // We slightly changed the synchronization scheme used in this class in
        // order to avoid a deadlock.
        //
        // What happened is that both A and B must have selected a participant
        // at about the same time (it's not important, but, for the shake of
        // example, suppose that A selected B and that B selected A). That
        // resulted in a data channel message being fired from both endpoints
        // notifying the bridge who's the selected participant at each endpoint
        // and locking 0x0000000775ec1010 and 0x0000000775e123b8.
        //
        // Upon reception of the selection notification from A, his simulcast
        // manager decided to request from B (by sending a data channel message)
        // to start its high quality stream (waiting to lock 0x0000000775ec1010)
        // and visa-versa, i.e. B's simulcast manager decided to request from
        // his endpoint to start A's high quality stream (waiting to lock
        // 0x0000000775e123b8). Boom!
        //
        // Possible solutions are:
        //
        // 1. use an incoming SCTP packets queue and a single processing thread.
        // 2. use an outgoing SCTP packets queue and a single sending thread.
        // 3. when there are incoming SCTP packets, execute each data callback
        //    in its own thread without queuing
        // 4. when there are outgoing SCTP packets, execute the send in its own
        //    thread without queuing (I'm not sure whether the underlying native
        //    SCTP socket is thread safe though, so this could be a little
        //    risky)
        // 5. a combination of the above
        // 6. change the synchronization scheme
        //
        // However, usrsctp seems to already be queuing packets and having
        // sending/processing threads so there's no need to duplicate this
        // functionality here. We implement a readers-writers scheme that
        // protects the native socket pointer instead.
    }

    /**
     * Accepts incoming SCTP connection.
     * FIXME:
     * Usrscp is currently configured to work in non blocking mode thus this
     * method should be polled in intervals.
     *
     * @return <tt>true</tt> if we have accepted incoming connection
     *         successfully.
     */
    public boolean accept()
        throws IOException
    {
        long ptr = lockPtr();
        boolean r;

        try
        {
            r = Sctp.usrsctp_accept(ptr);
        }
        finally
        {
            unlockPtr();
        }
        return r;
    }

    /**
     * Closes this socket. After call to this method this instance MUST NOT be
     * used.
     */
    public void close()
    {
        // The value of the field closed only ever changes from false to true.
        // Additionally, its reading is always synchronized and combined with
        // access to the field ptrLockCount governed by logic which binds the
        // meanings of the two values together. Consequently, the
        // synchronization with respect to closed is considered consistent.
        // Allowing the writing outside the synchronized block expedites the
        // actual closing of ptr.
        closed = true;

        long ptr;

        synchronized (this)
        {
            if (ptrLockCount == 0)
            {
                // The actual closing of ptr will not be deferred.
                ptr = this.ptr;
                this.ptr = 0;
            }
            else
            {
                // The actual closing of ptr will be deferred.
                ptr = 0;
            }
        }
        if (ptr != 0)
            Sctp.closeSocket(ptr);
    }

    /**
     * Initializes SCTP connection by sending INIT message.
     *
     * @param remotePort remote SCTP port.
     * @throws java.io.IOException if this socket is closed or an error occurs
     * while trying to connect the socket.
     */
    public void connect(int remotePort)
        throws IOException
    {
        long ptr = lockPtr();

        try
        {
            if (!Sctp.usrsctp_connect(ptr, remotePort))
                throw new IOException("Failed to connect SCTP");
        }
        finally
        {
            unlockPtr();
        }
    }

    /**
     * Returns SCTP port used by this socket.
     *
     * @return SCTP port used by this socket.
     */
    public int getPort()
    {
        return this.localPort;
    }

    /**
     * Makes SCTP socket passive.
     */
    public void listen()
        throws IOException
    {
        long ptr = lockPtr();

        try
        {
            Sctp.usrsctp_listen(ptr);
        }
        finally
        {
            unlockPtr();
        }
    }

    /**
     * Locks {@link #ptr} for reading and returns its value if this
     * <tt>SctpSocket</tt> has not been closed (yet). Each <tt>lockPtr</tt>
     * method invocation must be balanced with a subsequent <tt>unlockPtr</tt>
     * method invocation.
     *
     * @return <tt>ptr</tt>
     * @throws IOException if this <tt>SctpSocket</tt> has (already) been closed
     */
    private long lockPtr()
        throws IOException
    {
        long ptr;

        synchronized (this)
        {
            // It may seem that the synchronization with respect to the field
            // closed is inconsistent because there is no synchronization upon
            // writing its value. It is consistent though.
            if (closed)
            {
                throw new IOException("SctpSocket is closed!");
            }
            else
            {
                ptr = this.ptr;
                if (ptr == 0)
                    throw new IOException("SctpSocket is closed!");
                else
                    ++ptrLockCount;
            }
        }
        return ptr;
    }

    /**
     * Call this method to pass network packets received on the link.
     *
     * @param packet network packet received.
     * @param offset the position in the packet buffer where actual data starts
     * @param len length of packet data in the buffer.
     */
    public void onConnIn(byte[] packet, int offset, int len)
        throws IOException
    {
        if(packet == null)
        {
            throw new NullPointerException("packet");
        }
        if(offset < 0 || len <= 0 || offset + len > packet.length)
        {
            throw new IllegalArgumentException(
                "o: " + offset + " l: " + len + " packet l: " + packet.length);
        }

        long ptr = lockPtr();

        try
        {
            Sctp.onConnIn(ptr, packet, offset, len);
        }
        finally
        {
            unlockPtr();
        }
    }

    /**
     * Fired when usrsctp stack sends notification.
     *
     * @param notification the <tt>SctpNotification</tt> triggered.
     */
    private void onNotification(SctpNotification notification)
    {
        if(notificationListener != null)
        {
            notificationListener.onSctpNotification(this, notification);
        }
    }

    /**
     * Method fired by SCTP stack to notify about incoming data.
     *
     * @param data buffer holding received data
     * @param sid stream id
     * @param ssn
     * @param tsn
     * @param ppid payload protocol identifier
     * @param context
     * @param flags
     */
    private void onSctpIn(
            byte[] data, int sid, int ssn, int tsn, long ppid, int context,
            int flags)
    {
        if (dataCallback != null)
        {
            dataCallback.onSctpPacket(
                    data, sid, ssn, tsn, ppid, context, flags);
        }
        else
        {
            logger.warn("No dataCallback set, dropping a message from usrsctp");
        }
    }
    
    /**
     * Notifies this <tt>SctpSocket</tt> about incoming data.
     *
     * @param data buffer holding received data
     * @param sid stream id
     * @param ssn
     * @param tsn
     * @param ppid payload protocol identifier
     * @param context
     * @param flags
     */
    void onSctpInboundPacket(byte[] data, int sid, int ssn, int tsn, long ppid,
            int context, int flags)
    {
        if((flags & Sctp.MSG_NOTIFICATION) != 0)
        {
            onNotification(SctpNotification.parse(data));
        }
        else
        {
            onSctpIn(data, sid, ssn, tsn, ppid, context, flags);
        }
    }
    
    /**
     * Callback triggered by Sctp stack whenever it wants to send some network
     * packet.
     *
     * @param packet network packet buffer.
     * @param tos type of service???
     * @param set_df use IP don't fragment option
     * @return 0 if the packet was successfully sent or -1 otherwise.
     */
    int onSctpOut(byte[] packet, int tos, int set_df)
    {
        NetworkLink link = this.link;
        int ret = -1;

        if(link != null)
        {
            try
            {
                link.onConnOut(this, packet);
                ret = 0;
            }
            catch (IOException e)
            {
                logger.error(
                        "Error while sending packet trough the link: " + link,
                        e);
            }
        }
        return ret;
    }

    /**
     * Sends given <tt>data</tt> on selected SCTP stream using given payload
     * protocol identifier.
     *
     * @param data the data to send.
     * @param ordered should we care about message order ?
     * @param sid SCTP stream identifier
     * @param ppid payload protocol identifier
     * @return sent bytes count or <tt>-1</tt> in case of an error.
     */
    public int send(byte[] data, boolean ordered, int sid, int ppid)
        throws IOException
    {
        return send(data, 0, data.length, ordered, sid, ppid);
    }

    /**
     * Sends given <tt>data</tt> on selected SCTP stream using given payload
     * protocol identifier.
     *
     * @param data the data to send.
     * @param offset position of the data inside the buffer
     * @param len data length
     * @param ordered should we care about message order ?
     * @param sid SCTP stream identifier
     * @param ppid payload protocol identifier
     * @return sent bytes count or <tt>-1</tt> in case of an error.
     */
    public int send(
            byte[] data, int offset, int len,
            boolean ordered,
            int sid, int ppid)
        throws IOException
    {
        if(data == null)
        {
            throw new NullPointerException("data");
        }
        if(offset < 0 || len <= 0 || offset + len > data.length)
        {
            throw new IllegalArgumentException(
                "o: " + offset + " l: " + len + " data l: " + data.length);
        }

        long ptr = lockPtr();
        int r;

        try
        {
            r = Sctp.usrsctp_send(ptr, data, offset, len, ordered, sid, ppid);
        }
        finally
        {
            unlockPtr();
        }
        return r;
    }

    /**
     * Sets the callback that will be fired when new data is received.
     *
     * @param callback the callback that will be fired when new data is
     * received.
     */
    public void setDataCallback(SctpDataCallback callback)
    {
        this.dataCallback = callback;
    }

    /**
     * Sets the link that will be used to send network packets.
     *
     * @param link <tt>NetworkLink</tt> that will be used by this instance to
     * send network packets.
     */
    public void setLink(NetworkLink link)
    {
        this.link = link;
    }

    /**
     * Sets the listener that will be notified about SCTP event.
     *
     * @param l the {@link NotificationListener} to set.
     */
    public void setNotificationListener(NotificationListener l)
    {
        this.notificationListener = l;
    }

    /**
     * Unlocks {@link #ptr} for reading. If this <tt>SctpSocket</tt> has been
     * closed while <tt>ptr</tt> was locked for reading and there are no other
     * readers at the time of the method invocation, closes <tt>ptr</tt>. Each
     * <tt>unlockPtr</tt> method invocation must be balanced with a previous
     * <tt>lockPtr</tt> method invocation.
     */
    private void unlockPtr()
    {
        long ptr;

        synchronized (this)
        {
            int ptrLockCount = this.ptrLockCount - 1;

            if (ptrLockCount < 0)
            {
                throw new RuntimeException(
                        "Unbalanced SctpSocket#unlockPtr() method invocation!");
            }
            else
            {
                this.ptrLockCount = ptrLockCount;
                if (closed && (ptrLockCount == 0))
                {
                    // The actual closing of ptr was deferred until now.
                    ptr = this.ptr;
                    this.ptr = 0;
                }
                else
                {
                    // The actual closing of ptr may not have been requested or
                    // will be deferred.
                    ptr = 0;
                }
            }
        }
        if (ptr != 0)
            Sctp.closeSocket(ptr);
    }

    /**
     * Interface used to listen for SCTP notifications on specific socket.
     */
    public interface NotificationListener
    {
        /**
         * Fired when usrsctp stack sends notification.
         *
         * @param socket the {@link SctpSocket} notification source.
         * @param notification the <tt>SctpNotification</tt> triggered.
         */
        public void onSctpNotification(
                SctpSocket socket,
                SctpNotification notification);
    }
}
