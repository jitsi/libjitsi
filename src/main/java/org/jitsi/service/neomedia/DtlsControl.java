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

import java.util.*;

/**
 * Implements {@link SrtpControl} for DTSL-SRTP.
 *
 * @author Lyubomir Marinov
 */
public interface DtlsControl
    extends SrtpControl
{
    /**
     * The transport protocol (i.e. <tt>&lt;proto&gt;</tt>) to be specified in
     * a SDP media description (i.e. <tt>m=</tt> line) in order to denote a
     * RTP/SAVP stream transported over DTLS with UDP. 
     */
    public static final String UDP_TLS_RTP_SAVP = "UDP/TLS/RTP/SAVP";

    /**
     * The transport protocol (i.e. <tt>&lt;proto&gt;</tt>) to be specified in
     * a SDP media description (i.e. <tt>m=</tt> line) in order to denote a
     * RTP/SAVPF stream transported over DTLS with UDP. 
     */
    public static final String UDP_TLS_RTP_SAVPF = "UDP/TLS/RTP/SAVPF";

    /**
     * Gets the fingerprint of the local certificate that this instance uses to
     * authenticate its ends of DTLS sessions.
     *
     * @return the fingerprint of the local certificate that this instance uses
     * to authenticate its ends of DTLS sessions
     */
    public String getLocalFingerprint();

    /**
     * Gets the hash function with which the fingerprint of the local
     * certificate is computed i.e. the digest algorithm of the signature
     * algorithm of the local certificate.
     * 
     * @return the hash function with which the fingerprint of the local
     * certificate is computed
     */
    public String getLocalFingerprintHashFunction();

    /**
     * Sets the certificate fingerprints presented by the remote endpoint via
     * the signaling path.
     * 
     * @param remoteFingerprints a <tt>Map</tt> of hash functions to certificate
     * fingerprints that have been presented by the remote endpoint via the
     * signaling path
     */
    public void setRemoteFingerprints(Map<String,String> remoteFingerprints);

    /**
     * Sets the value of the <tt>setup</tt> SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance is to act as a DTLS
     * client or a DTLS server.
     *
     * @param setup the value of the <tt>setup</tt> SDP attribute to set on this
     * instance in order to determine whether this instance is to act as a DTLS
     * client or a DTLS server
     */
    public void setSetup(Setup setup);

    /**
     * Enables/disables rtcp-mux.
     * @param rtcpmux whether to enable or disable.
     */
    public void setRtcpmux(boolean rtcpmux);

    /**
     * Enumerates the possible values of the <tt>setup</tt> SDP attribute
     * defined by RFC 4145 &quot;TCP-Based Media Transport in the Session
     * Description Protocol (SDP)&quot;.
     *
     * @author Lyubomir Marinov
     */
    public enum Setup
    {
        ACTIVE,
        ACTPASS,
        HOLDCONN,
        PASSIVE;

        /**
         * Parses a <tt>String</tt> into a <tt>Setup</tt> enum value. The
         * specified <tt>String</tt> to parse must be in a format as produced by
         * {@link #toString()}; otherwise, the method will throw an exception.
         *
         * @param s the <tt>String</tt> to parse into a <tt>Setup</tt> enum
         * value
         * @return a <tt>Setup</tt> enum value on which <tt>toString()</tt>
         * produces the specified <tt>s</tt>
         * @throws IllegalArgumentException if none of the <tt>Setup</tt> enum
         * values produce the specified <tt>s</tt> when <tt>toString()</tt> is
         * invoked on them
         * @throws NullPointerException if <tt>s</tt> is <tt>null</tt>
         */
        public static Setup parseSetup(String s)
        {
            if (s == null)
                throw new NullPointerException("s");
            for (Setup v : values())
            {
                if (v.toString().equalsIgnoreCase(s))
                    return v;
            }
            throw new IllegalArgumentException(s);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
            return name().toLowerCase();
        }
    }
}
