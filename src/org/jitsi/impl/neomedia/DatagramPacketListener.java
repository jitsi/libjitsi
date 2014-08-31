/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.net.*;

/**
 * Represents a listener of a source of a type of events involving/caused by
 * <tt>DatagramPacket</tt>s.
 *
 * @author Lyubomir Marinov
 */
public interface DatagramPacketListener
{
    /**
     * Notifies this listener about an event fired by a specific <tt>source</tt>
     * and involving/caused by a specific <tt>DatagramPacket</tt>.
     *
     * @param source the source of/which fired the event
     * @param p the <tt>DatagramPacket</tt> which caused/is involved in the
     * event fired by <tt>source</tt>
     */
    void update(Object source, DatagramPacket p);
}
