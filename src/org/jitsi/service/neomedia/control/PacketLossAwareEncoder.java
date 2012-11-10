/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.control;

import javax.media.*;

/**
 * An interface used to notify encoders about the packet loss which is expected.
 *
 * @author Boris Grozev
 */
public interface PacketLossAwareEncoder extends Control
{
    /**
     * Tells the encoder to expect <tt>percentage</tt> percent packet loss.
     *
     * @return the percentage of expected packet loss
     */
    public void setExpectedPacketLoss(int percentage);
}
