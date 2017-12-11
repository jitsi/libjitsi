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
package org.jitsi.impl.neomedia;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.media.*;

import org.jitsi.service.neomedia.event.*;

/**
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class RTCPConnectorInputStream
    extends RTPConnectorUDPInputStream
{
    /**
     * List of RTCP feedback message listeners;
     */
    private final List<RTCPFeedbackMessageListener> listeners
        = new ArrayList<RTCPFeedbackMessageListener>();

    /**
     * Initializes a new <tt>RTCPConnectorInputStream</tt> which is to receive
     * packet data from a specific UDP socket.
     *
     * @param socket the UDP socket the new instance is to receive data from
     */
    public RTCPConnectorInputStream(DatagramSocket socket)
    {
        super(socket);
    }

    /**
     * Add an <tt>RTCPFeedbackMessageListener</tt>.
     *
     * @param listener object that will listen to incoming RTCP feedback
     * messages.
     */
    public void addRTCPFeedbackMessageListener(
            RTCPFeedbackMessageListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");
        if(!listeners.contains(listener))
            listeners.add(listener);
    }

    /**
     * Notifies a specific list of <tt>RTCPFeedbackMessageListener</tt>s about a
     * specific RTCP feedback message if such a message can be parsed out of a
     * specific <tt>byte</tt> buffer.
     *
     * @param source the object to be reported as the source of the
     * <tt>RTCPFeedbackMessageEvent</tt> to be fired
     * @param buffer the <tt>byte</tt> buffer which may specific an RTCP
     * feedback message
     * @param offset the offset in <tt>buffer</tt> at which the reading of bytes
     * is to begin
     * @param length the number of bytes in <tt>buffer</tt> to be read for the
     * purposes of parsing an RTCP feedback message and firing an
     * <tt>RTPCFeedbackEvent</tt>
     * @param listeners the list of <tt>RTCPFeedbackMessageListener</tt>s to be
     * notified about the specified RTCP feedback message if such a message can
     * be parsed out of the specified <tt>buffer</tt>
     */
    public static void fireRTCPFeedbackMessageReceived(
            Object source,
            byte[] buffer, int offset, int length,
            List<RTCPFeedbackMessageListener> listeners)
    {
        /*
         * RTCP feedback message length is minimum 12 bytes:
         * 1. Version/Padding/Feedback message type: 1 byte
         * 2. Payload type: 1 byte
         * 3. Length: 2 bytes
         * 4. SSRC of packet sender: 4 bytes
         * 5. SSRC of media source: 4 bytes
         */
        if ((length >= 12) && !listeners.isEmpty())
        {
            int pt = buffer[offset + 1] & 0xFF;

            if ((pt == RTCPFeedbackMessageEvent.PT_PS)
                    || (pt == RTCPFeedbackMessageEvent.PT_TL))
            {
                int fmt = buffer[offset] & 0x1F;
                RTCPFeedbackMessageEvent ev
                    = new RTCPFeedbackMessageEvent(source, fmt, pt);

                for (RTCPFeedbackMessageListener l : listeners)
                    l.rtcpFeedbackMessageReceived(ev);
            }
        }
    }

    /**
     * Remove an <tt>RTCPFeedbackMessageListener</tt>.
     *
     * @param listener object to remove from listening RTCP feedback messages.
     */
    public void removeRTCPFeedbackMessageListener(
            RTCPFeedbackMessageListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int read(Buffer buffer, byte[] data, int offset, int length)
        throws IOException
    {
        int pktLength = super.read(buffer, data, offset, length);

        fireRTCPFeedbackMessageReceived(
                this,
                data, offset, pktLength,
                listeners);

        return pktLength;
    }
}
