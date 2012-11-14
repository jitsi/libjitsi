/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.portaudio;

import org.jitsi.impl.neomedia.portaudio.Pa.*;
import org.jitsi.util.*;

/**
 * Implements <tt>Exception</tt> for the PortAudio capture and playback system.
 * 
 * @author Lyubomir Marinov
 * @author Damian Minkov
 */
public class PortAudioException
    extends Exception
{
    /**
     * The <tt>Logger</tt> used by the <tt>PortAudioException</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(PortAudioException.class);

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The code of the error as defined by the native PortAudio library
     * represented by this instance if it is known or {@link Pa#paNoError} if it
     * is not known.
     */
    private final long errorCode;

    /**
     * The host API, if any, which returned the error code and (detailed)
     * message represented by this instance.
     */
    private final Pa.HostApiTypeId hostApiType;

    /**
     * Initializes a new <tt>PortAudioException</tt> instance with a specific
     * detail message.
     *
     * @param message the detail message to initialize the new instance with
     */
    public PortAudioException(String message)
    {
        this(message, Pa.paNoError, -1);
    }

    /**
     * Initializes a new <tt>PortAudioException</tt> instance with a specific
     * detail message.
     *
     * @param message the detail message to initialize the new instance with
     * @param errorCode
     * @param hostApiType
     */
    public PortAudioException(String message, long errorCode, int hostApiType)
    {
        super(message);

        this.errorCode = errorCode;

        if (-1 == hostApiType)
            this.hostApiType = null;
        else
        {
            this.hostApiType = Pa.HostApiTypeId.valueOf(hostApiType);
            if (this.hostApiType == null)
                throw new IllegalArgumentException("hostApiType");
        }
    }

    /**
     * Gets the code of the error as defined by the native PortAudio library
     * represented by this instance if it is known.
     *
     * @return the code of the error as defined by the native PortAudio library
     * represented by this instance if it is known or {@link Pa#paNoError} if it
     * is not known
     */
    public long getErrorCode()
    {
        return errorCode;
    }

    /**
     * Gets the host API, if any, which returned the error code and (detailed)
     * message represented by this instance.
     *
     * @return the host API, if any, which returned the error code and
     * (detailed) message represented by this instance; otherwise, <tt>null</tt>
     */
    public Pa.HostApiTypeId getHostApiType()
    {
        return hostApiType;
    }

    /**
     * Logs an ERROR message with the respective details if this
     * <tt>PortAudioException</tt> represents a <tt>PaHostErrorInfo</tt> (as
     * defined by the native PortAudio library).
     */
    public void printHostErrorInfo()
    {
        HostApiTypeId hostApiType = getHostApiType();

        if (hostApiType != null)
        {
            logger.error(
                    getMessage() + " (hostApiType: " + hostApiType
                        + ", errorCode: " + getErrorCode() + ")");
        }
    }
}
