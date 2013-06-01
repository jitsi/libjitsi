/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.event;

/**
 * Represents a listener of RTCPFeedbackListener instances.
 *
 * @author Hristo Terezov
 */
public interface RTCPFeedbackCreateListener
{

    /**
     * Notifies this <tt>RTCPFeedbackCreateListener</tt> that a
     * RTCPFeedbackListener is created
     *
     * @param rtcpFeedbackListener the created RTCPFeedbackListener instance
     * type
     */
    public void onRTCPFeedbackCreate(RTCPFeedbackListener rtcpFeedbackListener);
}
