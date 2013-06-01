/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.io.*;
import java.net.*;
import java.util.*;

import org.jitsi.service.neomedia.event.*;

/**
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class RTCPConnectorInputStream
    extends RTPConnectorUDPInputStream
{
    /**
     * List of feedback listeners;
     */
    private final List<RTCPFeedbackListener> listeners
        = new ArrayList<RTCPFeedbackListener>();

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
     * Add an <tt>RTCPFeedbackListener</tt>.
     *
     * @param listener object that will listen to incoming RTCP feedback
     * messages.
     */
    public void addRTCPFeedbackListener(RTCPFeedbackListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");
        if(!listeners.contains(listener))
            listeners.add(listener);
    }

    /**
     * Notifies a specific list of <tt>RTCPFeedbackListener</tt>s about a
     * specific RTCP feedback message if such a message can be parsed out of a
     * specific <tt>byte</tt> buffer.
     *
     * @param source the object to be reported as the source of the
     * <tt>RTCPFeedbackEvent</tt> to be fired
     * @param buffer the <tt>byte</tt> buffer which may specific an RTCP
     * feedback message
     * @param offset the offset in <tt>buffer</tt> at which the reading of bytes
     * is to begin
     * @param length the number of bytes in <tt>buffer</tt> to be read for the
     * purposes of parsing an RTCP feedback message and firing an
     * <tt>RTPCFeedbackEvent</tt>
     * @param listeners the list of <tt>RTCPFeedbackListener</tt>s to be
     * notified about the specified RTCP feedback message if such a message can
     * be parsed out of the specified <tt>buffer</tt>
     */
    public static void fireRTCPFeedbackReceived(
            Object source,
            byte[] buffer, int offset, int length,
            List<RTCPFeedbackListener> listeners)
    {
        /*
         * RTCP feedback message size is minimum 12 bytes:
         * Version/Padding/Feedback message type: 1 byte
         * Payload type: 1 byte
         * Length: 2 bytes
         * SSRC of packet sender: 4 bytes
         * SSRC of media source: 4 bytes
         */
        if ((length >= 12) && !listeners.isEmpty())
        {
            int pt = buffer[offset + 1] & 0xFF;

            if ((pt == RTCPFeedbackEvent.PT_PS)
                    || (pt == RTCPFeedbackEvent.PT_TL))
            {
                int fmt = buffer[offset] & 0x1F;
                RTCPFeedbackEvent evt = new RTCPFeedbackEvent(source, fmt, pt);

                for (RTCPFeedbackListener l : listeners)
                    l.rtcpFeedbackReceived(evt);
            }
        }
    }

    /**
     * Remove an <tt>RTCPFeedbackListener</tt>.
     *
     * @param listener object to remove from listening RTCP feedback messages.
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

        fireRTCPFeedbackReceived(this, buffer, offset, pktLength, listeners);

        return pktLength;
    }
}
