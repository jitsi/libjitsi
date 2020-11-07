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
    extends RTPConnectorUDPInputStream
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
