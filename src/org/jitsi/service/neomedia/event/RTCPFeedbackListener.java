/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.event;

/**
 * Represents a listener of RTCP feedback messages such as PLI (Picture Loss
 * Indication) or FIR (Full Intra-frame Request).
 *
 * @author Sebastien Vincent
 */
public interface RTCPFeedbackListener
{
    /**
     * Notifies this <tt>RTCPFeedbackListener</tt> that an RTCP feedback message
     * has been received
     *
     * @param event an <tt>RTCPFeedbackEvent</tt> which specifies the details of
     * the notification event such as the feedback message type and the payload
     * type
     */
    public void rtcpFeedbackReceived(RTCPFeedbackEvent event);
}

