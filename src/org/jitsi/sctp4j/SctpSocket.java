/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import java.io.*;
import java.util.concurrent.*;

import org.jitsi.util.*;

/**
 * SCTP socket implemented using "usrsctp" lib.
 *
 * @author Pawel Domas
 */
public class SctpSocket
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(SctpSocket.class);

    /**
     * The pool of <tt>Thread</tt>s which run <tt>SctpSocket</tt>s.
     */
    private static final ExecutorService threadPool
        = ExecutorUtils.newCachedThreadPool(
                true,
                SctpSocket.class.getName());

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

    static void debugChunks(byte[] packet)
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

    public synchronized static void debugSctpPacket(byte[] packet, String id)
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
    int localPort;

    /**
     * SCTP notification listener.
     */
    private NotificationListener notificationListener
        = new NotificationListener()
        {
            @Override
            public void onSctpNotification(
                    SctpSocket socket,
                    SctpNotification notification)
            {
                if (logger.isTraceEnabled())
                {
                    logger.trace(
                            "SctpSocket 0x" + Long.toHexString(socketPtr)
                                + " notification: " + notification);
                }
            }
        };

    /**
     * Pointer to native socket counterpart.
     */
    long socketPtr;

    /**
     * Creates new instance of <tt>SctpSocket</tt>.
     * @param socketPtr native socket pointer.
     * @param localPort local SCTP port on which this socket is bound.
     */
    SctpSocket(long socketPtr, int localPort)
    {
        if(socketPtr == 0)
            throw new NullPointerException("socketPtr");

        this.socketPtr = socketPtr;
        this.localPort = localPort;
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
    public synchronized boolean accept()
        throws IOException
    {
        checkIsPointerValid();

        return Sctp.usrsctp_accept(socketPtr);
    }

    /**
     * Checks if {@link #socketPtr} is not null. Otherwise throws
     * <tt>IOException</tt>.
     *
     * @throws IOException in case this socket pointer is invalid.
     */
    private void checkIsPointerValid()
        throws IOException
    {
        if(socketPtr == 0)
        {
            throw new IOException("Socket is closed");
        }
    }

    /**
     * Closes this socket. After call to this method this instance MUST NOT be
     * used.
     */
    public synchronized void close()
    {
        if(socketPtr != 0)
        {
            Sctp.closeSocket(socketPtr);
            socketPtr = 0;
        }
    }

    /**
     * Initializes SCTP connection by sending INIT message.
     * @param remotePort remote SCTP port.
     * @throws java.io.IOException if this socket is closed or an error occurs
     *         while trying to connect the socket.
     */
    public synchronized void connect(int remotePort)
        throws IOException
    {
        checkIsPointerValid();

        if(!Sctp.usrsctp_connect(socketPtr, remotePort))
        {
            throw new IOException("Failed to connect SCTP");
        }
    }

    /**
     * Returns SCTP port used by this socket.
     * @return SCTP port used by this socket.
     */
    public int getPort()
    {
        return this.localPort;
    }

    /**
     * Makes SCTP socket passive.
     */
    public synchronized void listen()
        throws IOException
    {
        checkIsPointerValid();

        Sctp.usrsctp_listen(socketPtr);
    }

    /**
     * Call this method to pass network packets received on the link.
     * @param packet network packet received.
     * @param offset the position in the packet buffer where actual data starts
     * @param len length of packet data in the buffer.
     */
    public synchronized void onConnIn(byte[] packet, int offset, int len)
        throws IOException
    {
        if(packet == null)
            throw new NullPointerException("packet");

        if(offset < 0 || len <= 0 || offset + len > packet.length)
        {
            throw new IllegalArgumentException(
                "o: " + offset + " l: " + len + " packet l: " + packet.length);
        }

        // Prevent JVM crash by throwing IOException
        checkIsPointerValid();

        //debugSctpPacket(packet);

        Sctp.onConnIn(socketPtr, packet, offset, len);
    }

    /**
     * Fired when usrsctp stack sends notification.
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
        if(dataCallback != null)
        {
            dataCallback.onSctpPacket(
                    data, sid, ssn, tsn, ppid, context, flags);
        }
    }

    void onSctpInboundPacket(
            final byte[] data,
            final int sid,
            final int ssn,
            final int tsn,
            final long ppid,
            final int context,
            final int flags)
    {
        // FIXME fix threads
        threadPool.execute(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if((flags & Sctp.MSG_NOTIFICATION) > 0)
                        {
                            onNotification(SctpNotification.parse(data));
                        }
                        else
                        {
                            onSctpIn(data, sid, ssn, tsn, ppid, context, flags);
                        }
                    }
                });
    }

    /**
     * Callback triggered by Sctp stack whenever it wants to send some
     * network packet.
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
     * @param data the data to send.
     * @param offset postion of the data inside the buffer
     * @param len data length
     * @param ordered should we care about message order ?
     * @param sid SCTP stream identifier
     * @param ppid payload protocol identifier
     * @return sent bytes count or <tt>-1</tt> in case of an error.
     */
    public synchronized int send(byte[] data,     int offset,  int len,
                                 boolean ordered, int sid,     int ppid)
        throws IOException
    {
        if(data == null)
            throw new NullPointerException("data");

        if(offset < 0 || len <= 0 || offset + len > data.length)
        {
            throw new IllegalArgumentException(
                "o: " + offset + " l: " + len + " data l: " + data.length);
        }

        // Prevent JVM crash by throwing IOException
        checkIsPointerValid();

        return Sctp.usrsctp_send(
            socketPtr, data, offset, len, ordered, sid, ppid);
    }
    /**
     * Sets the callback that will be fired when new data is received.
     * @param callback the callback that will be fired when new data
     *                 is received.
     */
    public void setDataCallback(SctpDataCallback callback)
    {
        this.dataCallback = callback;
    }

    /**
     * Sets the link that will be used to send network packets.
     * @param link <tt>NetworkLink</tt> that will be used by this instance to
     *             send network packets.
     */
    public void setLink(NetworkLink link)
    {
        this.link = link;
    }

    /**
     * Sets the listener that will be notified about SCTP event.
     * @param l the {@link NotificationListener} to set.
     */
    public void setNotificationListener(NotificationListener l)
    {
        this.notificationListener = l;
    }

    /**
     * Interface used to listen for SCTP notifications on specific socket.
     */
    public interface NotificationListener
    {
        /**
         * Fired when usrsctp stack sends notification.
         * @param socket the {@link SctpSocket} notification source.
         * @param notification the <tt>SctpNotification</tt> triggered.
         */
        void onSctpNotification(
                SctpSocket socket,
                SctpNotification notification);
    }
}
