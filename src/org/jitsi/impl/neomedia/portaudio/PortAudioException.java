/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.portaudio;

import net.java.sip.communicator.impl.neomedia.portaudio.*;

/**
 * Implements <tt>Exception</tt> for the PortAudio capture and playback system.
 * 
 * @author Lyubomir Marinov
 */
public class PortAudioException
    extends Exception
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The host error info if any.
     */
    private final PortAudioHostErrorInfo portAudioHostErrorInfo;

    /**
     * Initializes a new <tt>PortAudioException</tt> instance with a specific
     * detail message.
     *
     * @param message the detail message to initialize the new instance with
     */
    public PortAudioException(String message)
    {
        super(message);

        this.portAudioHostErrorInfo = null;
    }

    /**
     * Initializes a new <tt>PortAudioException</tt> instance with a specific
     * detail message.
     *
     * @param message the detail message to initialize the new instance with
     */
    public PortAudioException(String message,
                              int hostApiInfo,
                              long errorCode,
                              String errorText)
    {
        super(message);

        this.portAudioHostErrorInfo =
            new PortAudioHostErrorInfo(
                    PortAudio.PaHostApiTypeId.valueOf(hostApiInfo),
                    errorCode,
                    errorText);
    }

    /**
     * Returns any host specific error info if any.
     * @return any host specific error info if any.
     */
    public PortAudioHostErrorInfo getPortAudioHostErrorInfo()
    {
        return portAudioHostErrorInfo;
    }
}
