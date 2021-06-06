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
