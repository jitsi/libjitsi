/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.neomedia.portaudio;

/**
 * Provides compatibility with source code written prior to the inception of
 * libjitsi.
 *
 * @author Lyubomir Marinov
 */
public class PortAudioException
    extends org.jitsi.impl.neomedia.portaudio.PortAudioException
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Initializes a new <tt>PortAudioException</tt> instance with a specific
     * detail message.
     *
     * @param message the detail message to initialize the new instance with
     */
    public PortAudioException(String message)
    {
        super(message);
    }
}
