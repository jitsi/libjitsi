/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;
import org.jitsi.service.neomedia.*;

/**
 * @author George Politis
 */
public abstract class AbstractRTCPTerminationStrategy
        implements RTCPTerminationStrategy,
        Transformer<RTCPCompoundPacket>,
        RTCPReportBuilder
{
    //region fields and ctor

    /**
     * The <tt>RTCPTransmitter</tt> of this <tt>RTCPTerminationStrategy</tt>
     * seen as an <tt>RTCPReportBuilder</tt>.
     */
    private RTCPTransmitter rtcpTransmitter;

    /**
     * The <tt>RTPTranslator</tt> associated with this strategy.
     */
    private RTPTranslator translator;

    /**
     * The <tt>Transformer</tt> to apply to incoming RTCP compound packets.
     */
    private Transformer<RTCPCompoundPacket>[] transformerChain;

    //endregion

    //region RTCPReportBuilder implementation.

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset()
    {
        /* Nothing to do here */
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRTCPTransmitter(RTCPTransmitter rtcpTransmitter)
    {
        if (rtcpTransmitter != this.rtcpTransmitter)
        {
            this.rtcpTransmitter = rtcpTransmitter;
            onRTCPTransmitterChanged();
        }
    }

    /**
     * Notifies this instance that the {@link rtcpTransmitter} has changed.
     */
    private void onRTCPTransmitterChanged()
    {
        RTCPTransmitter t;
        SSRCCache c;
        if ((t = this.rtcpTransmitter) != null
                && (c = t.cache) != null)
        {
            // Make the SSRCCache to "calculate" an RTCP reporting interval of
            // 1s.
            c.audio = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPTransmitter getRTCPTransmitter()
    {
        return this.rtcpTransmitter;
    }

    //endregion

    //region Transformer implementation

    /**
     * {@inheritDoc}
     */
    @Override
    public Transformer<RTCPCompoundPacket> getRTCPCompoundPacketTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPCompoundPacket transform(RTCPCompoundPacket inPacket)
    {
        Transformer[] transformers = this.transformerChain;
        if (transformers != null && transformers.length != 0)
        {
            for (Transformer<RTCPCompoundPacket> transformer : transformers)
            {
                inPacket = transformer.transform(inPacket);
            }
        }

        return inPacket;
    }

    public void setTransformerChain(
            Transformer<RTCPCompoundPacket>[] transformers)
    {
        this.transformerChain = transformers;
    }

    //endregion

    //region RTCPTerminationStrategy implementation

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPReportBuilder getRTCPReportBuilder()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRTPTranslator(RTPTranslator translator) {
        this.translator = translator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTPTranslator getRTPTranslator()
    {
        return this.translator;
    }

    //endregion
}
