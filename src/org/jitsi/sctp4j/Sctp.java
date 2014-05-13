/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import org.jitsi.util.*;

import java.io.*;
import java.util.*;

/**
 * Class encapsulates native SCTP counterpart.
 *
 * @author Pawel Domas
 */
public class Sctp
{
    /**
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(Sctp.class);

    static
    {
        String lib = "jnsctp";

        try
        {
            System.loadLibrary(lib);
        }
        catch (Throwable t)
        {
            logger.error(
                "Failed to load native library " + lib + ": " + t.getMessage());
            throw new RuntimeException(t);
        }
    }

    /**
     * SCTP notification
     */
    public static final int MSG_NOTIFICATION = 0x2000;

    /********  Notifications  **************/

    /* notification types */
    public static final int SCTP_ASSOC_CHANGE                = 0x0001;
    public static final int SCTP_PEER_ADDR_CHANGE            = 0x0002;
    public static final int SCTP_REMOTE_ERROR                = 0x0003;
    public static final int SCTP_SEND_FAILED                 = 0x0004;
    public static final int SCTP_SHUTDOWN_EVENT              = 0x0005;
    public static final int SCTP_ADAPTATION_INDICATION       = 0x0006;
    public static final int SCTP_PARTIAL_DELIVERY_EVENT      = 0x0007;
    public static final int SCTP_AUTHENTICATION_EVENT        = 0x0008;
    public static final int SCTP_STREAM_RESET_EVENT          = 0x0009;
    public static final int SCTP_SENDER_DRY_EVENT            = 0x000a;
    public static final int SCTP_NOTIFICATIONS_STOPPED_EVENT = 0x000b;
    public static final int SCTP_ASSOC_RESET_EVENT           = 0x000c;
    public static final int SCTP_STREAM_CHANGE_EVENT         = 0x000d;
    public static final int SCTP_SEND_FAILED_EVENT           = 0x000e;

    /* notification event structures */

    /* association change event */
    /*struct sctp_assoc_change {
	    uint16_t sac_type;
	    uint16_t sac_flags;
	    uint32_t sac_length;
	    uint16_t sac_state;
	    uint16_t sac_error;
	    uint16_t sac_outbound_streams;
	    uint16_t sac_inbound_streams;
	    sctp_assoc_t sac_assoc_id;
	    uint8_t sac_info[]; // not available yet
    };*/

    /* sac_state values */
    public static final int SCTP_COMM_UP        = 0x0001;
    public static final int SCTP_COMM_LOST      = 0x0002;
    public static final int SCTP_RESTART        = 0x0003;
    public static final int SCTP_SHUTDOWN_COMP  = 0x0004;
    public static final int SCTP_CANT_STR_ASSOC = 0x0005;

    /* sac_info values */
    public static final int SCTP_ASSOC_SUPPORTS_PR        = 0x01;
    public static final int SCTP_ASSOC_SUPPORTS_AUTH      = 0x02;
    public static final int SCTP_ASSOC_SUPPORTS_ASCONF    = 0x03;
    public static final int SCTP_ASSOC_SUPPORTS_MULTIBUF  = 0x04;
    public static final int SCTP_ASSOC_SUPPORTS_RE_CONFIG = 0x05;
    public static final int SCTP_ASSOC_SUPPORTS_MAX       = 0x05;

    /* Address event */
    /*struct sctp_paddr_change {
	    uint16_t spc_type;
	    uint16_t spc_flags;
	    uint32_t spc_length;
	    struct sockaddr_storage spc_aaddr;
	    uint32_t spc_state;
	    uint32_t spc_error;
	    sctp_assoc_t spc_assoc_id;
	    uint8_t spc_padding[4];
    };*/

    /* paddr state values */
    public static final int SCTP_ADDR_AVAILABLE   = 0x0001;
    public static final int SCTP_ADDR_UNREACHABLE = 0x0002;
    public static final int SCTP_ADDR_REMOVED     = 0x0003;
    public static final int SCTP_ADDR_ADDED       = 0x0004;
    public static final int SCTP_ADDR_MADE_PRIM   = 0x0005;
    public static final int SCTP_ADDR_CONFIRMED   = 0x0006;

    /* flags in stream_reset_event (strreset_flags) */
    public static final int SCTP_STREAM_RESET_INCOMING_SSN = 0x0001;
    public static final int SCTP_STREAM_RESET_OUTGOING_SSN = 0x0002;
    public static final int SCTP_STREAM_RESET_DENIED       = 0x0004;
    public static final int SCTP_STREAM_RESET_FAILED       = 0x0008;
    public static final int SCTP_STREAM_CHANGED_DENIED     = 0x0010;

    public static final int SCTP_STREAM_RESET_INCOMING     = 0x00000001;
    public static final int SCTP_STREAM_RESET_OUTGOING     = 0x00000002;

    /**
     * Track the number of currently running SCTP engines.
     * Each engine calls {@link #init()} on startup and {@link #finish()}
     * on shutdown. We want {@link #init()} to be effectively called only when
     * there are 0 engines currently running and {@link #finish()} when the last
     * one is performing a shutdown.
     */
    private static int sctpEngineCount;

    /**
     * FIXME: Remove once usrsctp_finish is fixed
     */
    private static boolean initialized;

    /**
     * Initializes native SCTP counterpart.
     */
    public static synchronized void init()
    {
        // Skip if we're not the first one
        //if(sctpEngineCount++ > 0)
        //    return;
        if(!initialized)
        {
            usrsctp_init(0);
            initialized = true;
        }
    }

    /**
     * Initializes native SCTP counterpart.
     * @param port UDP encapsulation port.
     * @return <tt>true</tt> on success.
     */
    native private static boolean usrsctp_init(int port);

    /**
     * List of instantiated <tt>SctpSockets</tt> mapped by native pointer.
     */
    private static Map<Long, SctpSocket> sockets
        = new HashMap<Long, SctpSocket>();

    /**
     * Creates new <tt>SctpSocket</tt> for given SCTP port. Allocates native
     * resources bound to the socket.
     * @param localPort local SCTP socket port.
     * @return new <tt>SctpSocket</tt> for given SCTP port.
     */
    public static SctpSocket createSocket(int localPort)
    {
        Long ptr = usersctp_socket(localPort);
        if(ptr != 0)
        {
            SctpSocket sock = new SctpSocket(ptr, localPort);
            sockets.put(ptr, sock);            
            return sock;
        }
        else
            return null;
    }

    /**
     * Creates native SCTP socket and returns pointer to it.
     * @param localPort local SCTP socket port.
     * @return native socket pointer or 0 if operation failed.
     */
    native private static long usersctp_socket(int localPort);

    /**
     * Sends given <tt>data</tt> on selected SCTP stream using given payload
     * protocol identifier.
     * FIXME: add offset and length buffer parameters.
     * @param socketPtr native socket pointer.
     * @param data the data to send.
     * @param ordered should we care about message order ?
     * @param sid SCTP stream identifier
     * @param ppid payload protocol identifier
     * @return sent bytes count or <tt>-1</tt> in case of an error.
     */
    native static int usrsctp_send(long socketPtr,  byte[] data,
                                   boolean ordered, int sid, int ppid);

    /**
     * Makes socket passive.
     * @param socketPtr native socket pointer.
     */
    native static void usrsctp_listen(long socketPtr);

    /**
     * Waits for incoming connection.
     * @param socketPtr native socket pointer.
     */
    native static void usrsctp_accept(long socketPtr);

    /**
     * Connects SCTP socket to remote socket on given SCTP port.
     * @param socketPtr native socket pointer.
     * @param remotePort remote SCTP port.
     * @return <tt>true</tt> if the socket has been successfully connected.
     */
    native static boolean usrsctp_connect(long socketPtr, int remotePort);

    /**
     * Closes SCTP socket addressed by given native pointer.
     * @param ptr native socket pointer.
     */
    static void closeSocket(Long ptr)
    {
        usrsctp_close(ptr);
    
        sockets.remove(ptr);
    }

    /**
     * Closes SCTP socket.
     * @param socketPtr native socket pointer.
     */
    native private static void usrsctp_close(long socketPtr);

    /**
     * Disposes of the resources held by native counterpart.
     *
     * @throws IOException if usrsctp stack has failed to shutdown.
     */
    public static synchronized void finish()
        throws IOException
    {
        // Skip if we're not the last one
        //if(--sctpEngineCount > 0)
          //  return;

        //try
        //{
            // FIXME: fix this loop ?
            // it comes from SCTP samples written in C

            // Retry limited amount of times
            /*
              FIXME: usrsctp issue:
              SCTP stack is now never deinitialized in order to prevent deadlock
              in usrsctp_finish.
              https://code.google.com/p/webrtc/issues/detail?id=2749

            final int CLOSE_RETRY_COUNT = 20;

            for(int i=0; i < CLOSE_RETRY_COUNT; i++)
            {
                if(usrsctp_finish())
                    return;

                Thread.sleep(50);
            }*/

            //FIXME: after throwing we might end up with other SCTP users broken
            // (or stack not disposed) at this point because engine count will
            // be out of sync for the purpose of calling init() and finish()
            // methods.
        //    throw new IOException("Failed to shutdown usrsctp stack" +
        //                              " after 20 retries");
        //}
        //catch(InterruptedException e)
        //{
        //    logger.error("Finish interrupted", e);
        //    Thread.currentThread().interrupt();
        //}
    }

    /**
     * Disposes of the resources held by native counterpart.
     * @return <tt>true</tt> if stack successfully released resources.
     */
    native private static boolean usrsctp_finish();

    /**
     * Method fired by native counterpart when SCTP stack wants to send
     * network packet.
     * @param socketAddr native socket pointer
     * @param data buffer holding packet data
     * @param tos type of service???
     * @param set_df use IP don't fragment option
     * @return 0 if the packet has been successfully sent or -1 otherwise.
     */
    public static int onSctpOutboundPacket(long socketAddr, byte[] data,
                                            int  tos,        int set_df)
    {
        // FIXME: handle tos and set_df

        SctpSocket socket = sockets.get(socketAddr);
        if(socket != null)
        {
            return socket.onSctpOut(data, tos, set_df);
        }
        else
        {
            logger.error("No SctpSocket found for ptr: " + socketAddr);
            return -1;
        }
    }

    /**
     * Method fired by native counterpart to notify about incoming data.
     *
     * @param socketAddr native socket pointer
     * @param data buffer holding received data
     * @param sid stream id
     * @param ssn
     * @param tsn
     * @param ppid payload protocol identifier
     * @param context
     * @param flags
     */
    public static void onSctpInboundPacket(long   socketAddr,
                                           byte[] data,
                                           int    sid,  int ssn,     int tsn,
                                           long   ppid, int context, int flags)
    {
        SctpSocket socket = sockets.get(socketAddr);
        if(socket != null)
        {
            socket.onSctpIn(data, sid, ssn, tsn, ppid, context, flags);
        }
        else
        {
            logger.error("No SctpSocket found for ptr: " + socketAddr);
        }
    }

    /**
     * Used by {@link SctpSocket} to pass received network packet to native
     * counterpart.
     *
     * FIXME: add offset and length parameters
     *
     * @param socketPtr native socket pointer.
     * @param packet network packet data.
     */
    static void onConnIn(long socketPtr, byte[] packet)
    {
        on_network_in(socketPtr, packet);
    }

    /**
     * Passes network packet to native SCTP stack counterpart.
     * @param socketPtr native socket pointer.
     * @param packet buffer holding network packet data.
     */
    native private static void on_network_in(long socketPtr, byte[] packet);

    /*
    FIXME: to be added ?
    int
    usrsctp_shutdown(struct socket *so, int how);
    */

}
