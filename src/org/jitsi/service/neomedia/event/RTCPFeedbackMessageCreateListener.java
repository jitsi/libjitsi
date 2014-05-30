/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.event;

/**
 * Represents a listener of <tt>RTCPFeedbackMessageListener</tt> instances.
 *
 * @author Hristo Terezov
 */
public interface RTCPFeedbackMessageCreateListener
{
    /**
     * Notifies this <tt>RTCPFeedbackCreateListener</tt> that a
     * <tt>RTCPFeedbackMessageListener</tt> is created.
     *
     * @param rtcpFeedbackMessageListener the created
     * <tt>RTCPFeedbackMessageListener</tt> instance
     */
    public void onRTCPFeedbackMessageCreate(
            RTCPFeedbackMessageListener rtcpFeedbackMessageListener);
}
