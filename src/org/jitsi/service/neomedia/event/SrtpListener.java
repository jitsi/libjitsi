/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.event;

import org.jitsi.service.neomedia.*;

/**
 * The <tt>SrtpListener</tt> is meant to be used by the media stream creator, as
 * the name indicates in order to be notified when a security event has occurred
 * that concerns a secure (media) transport i.e. <tt>SrtpControl</tt> such as
 * ZRTP, SDES and DTLS-SRTP.
 *
 * @author Yana Stamcheva
 */
public interface SrtpListener
{
    /**
     * This is a information message. Security will be established.
     */
    public static final int INFORMATION = 0;

    /**
     * This is a warning message. Security will not be established.
     */
    public static final int WARNING = 1;

    /**
     * This is a severe error. Security will not be established.
     */
    public static final int SEVERE = 2;

    /**
     * This is an error message. Security will not be established.
     */
    public static final int ERROR = 3;

    /**
     * Indicates that the security has been turned on. When we are in the case
     * of using multistreams when the master stream ZRTP is initialized and
     * established the param multiStreamData holds the data needed for the
     * slave streams to establish their sessions. If this is a securityTurnedOn
     * event on non master stream the multiStreamData is null.
     *
     * @param mediaType the <tt>MediaType</tt> of the call session
     * @param cipher the security cipher that encrypts the call
     * @param sender the control that initiated the event.
     */
    public void securityTurnedOn(
            MediaType mediaType,
            String cipher,
            SrtpControl sender);

    /**
     * Indicates that the security has been turned off.
     *
     * @param mediaType the <tt>MediaType</tt> of the call session
     */
    public void securityTurnedOff(MediaType mediaType);

    /**
     * Indicates that a security message has occurred associated with a
     * failure/warning or information coming from the encryption protocol/secure
     * transport.
     *
     * @param message the message.
     * @param i18nMessage the internationalized message
     * @param severity severity level
     */
    public void securityMessageReceived(
            String message,
            String i18nMessage,
            int severity);

    /**
     * Indicates that the other party has timed out replying to our offer to
     * secure the connection.
     *
     * @param mediaType the <tt>MediaType</tt> of the call session
     */
    public void securityTimeout(MediaType mediaType);

    /**
     * Indicates that we started the process of securing the connection.
     *
     * @param mediaType the <tt>MediaType</tt> of the call session
     * @param sender the control that initiated the event.
     */
    public void securityNegotiationStarted(
            MediaType mediaType,
            SrtpControl sender);
}
