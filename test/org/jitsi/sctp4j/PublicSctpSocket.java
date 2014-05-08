/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

/**
 * {@link SctpSocket} with public constructor.
 *
 * @author Pawel Domas
 */
public class PublicSctpSocket
    extends SctpSocket
{
    /**
     * Creates new instance of <tt>SctpSocket</tt>.
     *
     * @param socketPtr native socket pointer.
     * @param localPort local SCTP port on which this socket is bound.
     */
    PublicSctpSocket(long socketPtr, int localPort)
    {
        super(socketPtr, localPort);
    }
}
