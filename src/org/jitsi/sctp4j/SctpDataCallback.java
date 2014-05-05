/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

/**
 * Callback used to listen for incoming data on SCTP socket.
 *
 * @author Pawel Domas
 */
public interface SctpDataCallback
{
    /**
     * Callback fired by <tt>SctpSocket</tt> to notify about incoming data.
     * @param data buffer holding received data.
     * @param sid SCTP stream identifier.
     * @param ssn
     * @param tsn
     * @param ppid payload protocol identifier.
     * @param context
     * @param flags
     */
    void onSctpPacket(byte[] data, int sid, int ssn, int tsn, long ppid,
                      int context, int flags);
}
