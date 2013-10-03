/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
     * The DTLS protocol to be set on a <tt>DtlsControl</tt> instance via
     * {@link #setDtlsProtocol(int)} to indicate that the <tt>DtlsControl</tt>
     * is to act as a DTLS client.
     */
    public static final int DTLS_CLIENT_PROTOCOL = 1;

    /**
     * The DTLS protocol to be set on a <tt>DtlsControl</tt> instance via
     * {@link #setDtlsProtocol(int)} to indicate that the <tt>DtlsControl</tt>
     * is to act as a DTLS server.
     */
    public static final int DTLS_SERVER_PROTOCOL = 2;

    /**
     * The human-readable non-localized name of the (S)RTP transport protocol
     * represented by <tt>DtlsControl</tt>.
     */
    public static final String PROTO_NAME
        = SrtpControlType.DTLS_SRTP.toString();

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
     * Sets the DTLS protocol according to which this <tt>DtlsControl</tt> is to
     * act.
     *
     * @param dtlsProtocol {@link #DTLS_CLIENT_PROTOCOL} to have this instance
     * act as a DTLS client or {@link #DTLS_SERVER_PROTOCOL} to have this
     * instance act as a DTLS server
     */
    public void setDtlsProtocol(int dtlsProtocol);

    /**
     * Sets the certificate fingerprints presented by the remote endpoint via
     * the signaling path.
     * 
     * @param remoteFingerprints a <tt>Map</tt> of hash functions to certificate
     * fingerprints that have been presented by the remote endpoint via the
     * signaling path
     */
    public void setRemoteFingerprints(Map<String,String> remoteFingerprints);
}
