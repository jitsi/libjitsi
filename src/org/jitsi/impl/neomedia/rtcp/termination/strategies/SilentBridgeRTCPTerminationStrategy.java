/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.transform.rtcp.*;
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
    private final RTCPReportBuilder reportBuilder =
            new NullRTCPReportBuilderImpl();

    private final Transformer<RTCPCompoundPacket> packetTransformer =
            new NullRTCPPacketTransformer();


    @Override
    public Transformer<RTCPCompoundPacket> getRTCPCompoundPacketTransformer()
    {
        return packetTransformer;
    }

    @Override
    public RTCPReportBuilder getRTCPReportBuilder()
    {
        return reportBuilder;
    }

    @Override
    public void setRTPTranslator(RTPTranslator translator) {
        // Nothing to do here.
    }

    @Override
    public RTPTranslator getRTPTranslator()
    {
        return null;
    }
}
