/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.portaudio;

import net.java.sip.communicator.impl.neomedia.portaudio.*;

/**
 * Represents PaHostErrorInfo from PortAudio.
 * Return information about a host error condition.
 *
 * @author Damian Minkov
 */
public class PortAudioHostErrorInfo
{
    /**
     * The host API which returned the error code.
     */
    PortAudio.PaHostApiTypeId hostApiType;

    /**
     * The error code returned.
     */
    long errorCode;

    /**
     * A textual description of the error if available,
     * otherwise a zero-length string.
     */
    String errorText;

    /**
     * Constructs <tt>PortAudioHostErrorInfo</tt>.
     *
     * @param hostApiType the host API which returned the error code.
     * @param errorCode the error code returned.
     * @param errorText a textual description of the error if available.
     */
    public PortAudioHostErrorInfo(PortAudio.PaHostApiTypeId hostApiType, long errorCode, String errorText)
    {
        this.hostApiType = hostApiType;
        this.errorCode = errorCode;
        this.errorText = errorText;
    }

    /**
     * The host API which returned the error code.
     * @return the host API which returned the error code.
     */
    public PortAudio.PaHostApiTypeId getHostApiType()
    {
        return hostApiType;
    }

    /**
     * The error code returned.
     * @return the error code returned.
     */
    public long getErrorCode()
    {
        return errorCode;
    }

    /**
     * A textual description of the error if available.
     * @return a textual description of the error if available.
     */
    public String getErrorText()
    {
        return errorText;
    }
}
