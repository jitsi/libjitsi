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
import org.jitsi.service.neomedia.rtp.*;

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
    private final RTCPPacketTransformer packetTransformer
            = new NullRTCPPacketTransformer();

    private final RTCPReportBuilder reportBuilder
            = new DefaultRTCPReportBuilderImpl();

    @Override
    public RTCPPacketTransformer getRTCPCompoundPacketTransformer()
    {
        return packetTransformer;
    }

    @Override
    public RTCPReportBuilder getRTCPReportBuilder()
    {
        return reportBuilder;
    }

    @Override
    public void setRTPTranslator(RTPTranslator translator)
    {
        // Nothing to do here.
    }

    @Override
    public RTPTranslator getRTPTranslator()
    {
        return null;
    }
}
