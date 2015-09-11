/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

/**
 * Signals that a packet transmission exception of some sort has occurred. This
 * class is the general class of exceptions produced by failed or interrupted
 * transmissions.
 *
 * @author George Politis
 */
public class TransmissionFailedException
    extends Throwable
{
    /**
     * Ctor.
     *
     * @param e
     */
    public TransmissionFailedException(Exception e)
    {
        super(e);
    }
}
