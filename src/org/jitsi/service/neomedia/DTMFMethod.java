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
package org.jitsi.service.neomedia;

/**
 * Enumerates all available DTMF methods.
 *
 * @author Vincent Lucas
 */
public enum DTMFMethod
{
    /**
     * {@link #RTP_DTMF} if telephony-event are available; otherwise,
     * {@link #INBAND_DTMF}.
     */
    AUTO_DTMF,

    /** RTP DTMF as defined in RFC4733. */
    RTP_DTMF,

    /** SIP INFO DTMF. */
    SIP_INFO_DTMF,

    /** INBAND DTMF as defined in ITU-T recommendation Q.23. */
    INBAND_DTMF
}
