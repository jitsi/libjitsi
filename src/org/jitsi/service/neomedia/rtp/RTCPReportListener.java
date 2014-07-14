/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.rtp;

import net.sf.fmj.media.rtp.*;

/**
 *
 * @author Lyubomir Marinov
 */
public interface RTCPReportListener
{
    /**
     * Notifies this listener that a specific RTCP XR was received by the local
     * endpoint.
     *
     * @param extendedReport the received RTCP XR
     */
    public void rtcpExtendedReportReceived(RTCPExtendedReport extendedReport);

    /**
     * Notifies this listener that a specific RTCP XR was sent by the local
     * endpoint.
     *
     * @param extendedReport the sent RTCP XR
     */
    public void rtcpExtendedReportSent(RTCPExtendedReport extendedReport);

    /**
     * Notifies this listener that a specific RTCP SR or RR was received by the
     * local endpoint.
     *
     * @param report the received RTCP SR or RR
     */
    public void rtcpReportReceived(RTCPReport report);

    /**
     * Notifies this listener that a specific RTCP SR or RR was sent by the
     * local endpoint.
     *
     * @param report the sent RTCP SR or RR
     */
    public void rtcpReportSent(RTCPReport report);
}
