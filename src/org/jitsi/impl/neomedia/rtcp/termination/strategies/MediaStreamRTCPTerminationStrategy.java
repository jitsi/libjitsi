/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import org.jitsi.service.neomedia.*;

/**
 * @author George Politis
 */
public abstract class MediaStreamRTCPTerminationStrategy
    implements RTCPTerminationStrategy
{
    /**
     * The <tt>MediaStream</tt> who owns this
     * <tt>BasicRTCPTerminationStrategy</tt> and whose RTCP packets this
     * <tt>RTCPTerminationStrategy</tt> terminates.
     */
    private MediaStream stream;

    /**
     * Gets the <tt>MediaStream</tt> that owns this RTCP termination strategy.
     * @return
     */
    public MediaStream getStream()
    {
        return stream;
    }

    /**
     * Initializes the RTCP termination.
     *
     * @param stream The <tt>MediaStream</tt> who owns this
     * <tt>BasicRTCPTerminationStrategy</tt> and whose RTCP traffic this
     * <tt>BasicRTCPTerminationStrategy</tt> is terminating.
     */
    public void initialize(MediaStream stream)
    {
        this.stream = stream;
    }
}
