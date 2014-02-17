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

import javax.media.*;

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
     * The list of <tt>RTCPFeedbackMessageListener</tt>s.
     */
    private final List<RTCPFeedbackMessageListener> listeners
        = new LinkedList<RTCPFeedbackMessageListener>();

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
     * Adds an <tt>RTCPFeedbackMessageListener</tt>.
     *
     * @param listener the <tt>RTCPFeedbackMessageListener</tt> to add
     */
    public void addRTCPFeedbackMessageListener(
            RTCPFeedbackMessageListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");
        synchronized (listeners)
        {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Removes an <tt>RTCPFeedbackMessageListener</tt>.
     *
     * @param listener the <tt>RTCPFeedbackMessageListener</tt> to remove
     */
    public void removeRTCPFeedbackMessageListener(
            RTCPFeedbackMessageListener listener)
    {
        if (listener != null)
        {
            synchronized (listeners)
            {
                listeners.remove(listener);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int read(Buffer buffer, byte[] data, int offset, int length)
        throws IOException
    {
        int pktLength = super.read(buffer, data, offset, length);

        RTCPConnectorInputStream.fireRTCPFeedbackMessageReceived(
                this,
                data, offset, pktLength,
                listeners);

        return pktLength;
    }
}
