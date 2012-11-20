/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.portaudio;

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
        this.hostApiType
            = (hostApiType < 0) ? null : Pa.HostApiTypeId.valueOf(hostApiType);
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
     * Returns a human-readable representation/description of this
     * <tt>Throwable</tt>.
     *
     * @return a human-readable representation/description of this
     * <tt>Throwable</tt>
     */
    @Override
    public String toString()
    {
        String s = super.toString();

        long errorCode = getErrorCode();
        String errorCodeStr
            = (errorCode == Pa.paNoError) ? null : Long.toString(errorCode);

        Pa.HostApiTypeId hostApiType = getHostApiType();
        String hostApiTypeStr
            = (hostApiType == null) ? null : hostApiType.toString();

        if ((errorCodeStr !=null) || (hostApiTypeStr != null))
        {
            StringBuilder sb = new StringBuilder(s);

            sb.append(": ");
            if (errorCodeStr != null)
            {
                sb.append("errorCode= ");
                sb.append(errorCodeStr);
                sb.append(';');
            }
            if (hostApiTypeStr != null)
            {
                if (errorCodeStr != null)
                    sb.append(' ');
                sb.append("hostApiType= ");
                sb.append(hostApiTypeStr);
                sb.append(';');
            }

            s = sb.toString();
        }

        return s;
    }
}
