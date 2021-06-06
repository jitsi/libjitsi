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

import java.util.*;

/**
 * Represents an event coming from RTCP that meant to tell codec
 * to do something (i.e send a keyframe, ...).
 *
 * @author Sebastien Vincent
 */
public class RTCPFeedbackMessageEvent
    extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Full Intra Request (FIR) RTCP feedback message type.
     */
    public static final int FMT_FIR = 4;

    /**
     * Picture Loss Indication (PLI) feedback message type.
     */
    public static final int FMT_PLI = 1;

    /**
     * The payload type (PT) of payload-specific RTCP feedback messages.
     */
    public static final int PT_PS = 206;

    /**
     * The payload type (PT) of transport layer RTCP feedback messages.
     */
    public static final int PT_TL = 205;

    /**
     * Feedback message type (FMT).
     */
    private final int feedbackMessageType;

    /**
     * Payload type (PT).
     */
    private final int payloadType;

    /**
     * Constructor.
     *
     * @param source source
     * @param feedbackMessageType feedback message type (FMT)
     * @param payloadType payload type (PT)
     */
    public RTCPFeedbackMessageEvent(
            Object source,
            int feedbackMessageType,
            int payloadType)
    {
        super(source);

        this.feedbackMessageType = feedbackMessageType;
        this.payloadType = payloadType;
    }

    /**
     * Get feedback message type (FMT).
     *
     * @return message type
     */
    public int getFeedbackMessageType()
    {
        return feedbackMessageType;
    }

    /**
     * Get payload type (PT) of RTCP packet.
     *
     * @return payload type
     */
    public int getPayloadType()
    {
        return payloadType;
    }
}
