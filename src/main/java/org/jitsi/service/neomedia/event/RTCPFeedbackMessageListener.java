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
package org.jitsi.service.neomedia.event;

/**
 * Represents a listener of RTCP feedback messages such as PLI (Picture Loss
 * Indication) or FIR (Full Intra Request).
 *
 * @author Sebastien Vincent
 */
public interface RTCPFeedbackMessageListener
{
    /**
     * Notifies this <tt>RTCPFeedbackMessageListener</tt> that an RTCP feedback
     * message has been received
     *
     * @param event an <tt>RTCPFeedbackMessageEvent</tt> which specifies the
     * details of the notification event such as the feedback message type and
     * the payload type
     */
    public void rtcpFeedbackMessageReceived(RTCPFeedbackMessageEvent event);
}
