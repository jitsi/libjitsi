/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

/**
 * Interface used by {@link SctpSocket} for sending network packets.
 *
 * FIXME: introduce offset and length parameters in order to be able to
 *        re-use single buffer instance
 *
 * @author Pawel Domas
 */
public interface NetworkLink
{
    /**
     * Callback triggered by <tt>SctpSocket</tt> whenever it wants to send some
     * network packet.
     * @param s source <tt>SctpSocket</tt> instance.
     * @param packet network packet buffer.
     */
    public void onConnOut(final SctpSocket s, final byte[] packet);
}
