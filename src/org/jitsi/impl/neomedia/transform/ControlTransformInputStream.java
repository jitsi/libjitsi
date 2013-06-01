/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform;

import java.io.*;
import java.net.*;
import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.event.*;

/**
 * Implement control channel (RTCP) for <tt>TransformInputStream</tt>
 * which notify listeners when RTCP feedback messages are received.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class ControlTransformInputStream
    extends TransformUDPInputStream
{
    /**
     * The list of <tt>RTCPFeedbackListener</tt>.
     */
    private final List<RTCPFeedbackListener> listeners
        = new LinkedList<RTCPFeedbackListener>();

    /**
     * Initializes a new <tt>ControlTransformInputStream</tt> which is to
     * receive packet data from a specific UDP socket.
     *
     * @param socket the UDP socket the new instance is to receive data from
     */
    public ControlTransformInputStream(DatagramSocket socket)
    {
        super(socket);
    }

    /**
     * Adds an <tt>RTCPFeedbackListener</tt>.
     *
     * @param listener the <tt>RTCPFeedbackListener</tt> to add
     */
    public void addRTCPFeedbackListener(RTCPFeedbackListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");
        if(!listeners.contains(listener))
            listeners.add(listener);
    }

    /**
     * Removes an <tt>RTCPFeedbackListener</tt>.
     *
     * @param listener the <tt>RTCPFeedbackListener</tt> to remove
     */
    public void removeRTCPFeedbackListener(RTCPFeedbackListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * Copies the content of the most recently received packet into
     * <tt>inBuffer</tt>.
     *
     * @param buffer the <tt>byte[]</tt> that we'd like to copy the content of
     * the packet to.
     * @param offset the position where we are supposed to start writing in
     * <tt>buffer</tt>.
     * @param length the number of <tt>byte</tt>s available for writing in
     * <tt>buffer</tt>.
     *
     * @return the number of bytes read
     *
     * @throws IOException if <tt>length</tt> is less than the size of the
     * packet.
     */
    @Override
    public int read(byte[] buffer, int offset, int length)
        throws IOException
    {
        int pktLength = super.read(buffer, offset, length);

        RTCPConnectorInputStream.fireRTCPFeedbackReceived(
                this,
                buffer, offset, pktLength,
                listeners);

        return pktLength;
    }
}
