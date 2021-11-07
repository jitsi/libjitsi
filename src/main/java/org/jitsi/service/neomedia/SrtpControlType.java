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
 * The <tt>SrtpControlType</tt> enumeration contains all currently known
 * <tt>SrtpControl</tt> implementations.
 *
 * @author Ingo Bauersachs
 * @author Lyubomir Marinov
 */
public enum SrtpControlType
{
    /**
     * Datagram Transport Layer Security (DTLS) Extension to Establish Keys for
     * the Secure Real-time Transport Protocol (SRTP)
     */
    DTLS_SRTP("DTLS-SRTP"),

    /**
     * Multimedia Internet KEYing (RFC 3830)
     */
    MIKEY("MIKEY"),

    /**
     * Session Description Protocol (SDP) Security Descriptions for Media
     * Streams (RFC 4568)
     */
    SDES("SDES"),

    /**
     * ZRTP: Media Path Key Agreement for Unicast Secure RTP (RFC 6189)
     */
    ZRTP("ZRTP"),

    /**
     * A no-op implementation.
     */
    NULL("NULL");

    /**
     * The human-readable non-localized name of the (S)RTP transport protocol
     * represented by this <tt>SrtpControlType</tt> and its respective
     * <tt>SrtpControl</tt> class.
     */
    private final String protoName;

    /**
     * Initializes a new <tt>SrtpControlType</tt> instance with a specific
     * human-readable non-localized (S)RTP transport protocol name.
     *
     * @param protoName the human-readable non-localized name of the (S)RTP
     * transport protocol represented by the new instance and its respective
     * <tt>SrtpControl</tt> class
     */
    private SrtpControlType(String protoName)
    {
        this.protoName = protoName;
    }

    @Override
    public String toString()
    {
        return protoName;
    }

    /**
     * @see SrtpControlType#valueOf(String)
     */
    public static SrtpControlType fromString(String protoName)
    {
        if (protoName.equals(SrtpControlType.DTLS_SRTP.toString()))
        {
            return SrtpControlType.DTLS_SRTP;
        }
        else
        {
            return SrtpControlType.valueOf(protoName);
        }
    }
}
