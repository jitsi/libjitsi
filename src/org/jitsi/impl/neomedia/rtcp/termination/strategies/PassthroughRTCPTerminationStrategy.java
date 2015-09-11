/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

/**
 * Forwards whatever it receives from the network and it also generates RTCP
 * receiver reports using the FMJ built-in algorithm. This emulates the default
 * behavior.
 *
 * @author George Politis
 */
public class PassthroughRTCPTerminationStrategy
    implements RTCPTerminationStrategy
{

    public RawPacket report()
    {
        // TODO Implement the default FMJ behavior here.
        return null;
    }

    public PacketTransformer getRTPTransformer()
    {
        return null;
    }

    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }
}
