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
 * Forwards whatever it receives from the network but it doesn't generate
 * anything. This strategy will be useful for conferences of up to 2
 * participants.
 *
 * @author George Politis
 */
public class SilentBridgeRTCPTerminationStrategy
    implements RTCPTerminationStrategy
{

    public RawPacket report()
    {
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
