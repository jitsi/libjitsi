/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.rtcp;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;

/**
 * Uses the <tt>Transformer</tt> of the <tt>RTCPTerminationStrategy</tt> of the
 * <tt>RTPTranslator</tt> of the associated <tt>MediaStream</tt> to transform
 * incoming RTCP packets. Advanced RTCP termination strategies can drop incoming
 * RTCP packets.
 *
 * @author George Politis
 */
public class RTCPTerminationTransformEngine
    extends SingleRTCPPacketTransformer
    implements TransformEngine
{

    /**
     * The associated <tt>MediaStream</tt> of this
     * <tt><RTCPTerminationTransformEngine/tt>.
     */
    private final MediaStream mediaStream;

    /**
     * Ctor.
     *
     * @param mediaStream
     */
    public RTCPTerminationTransformEngine(MediaStream mediaStream)
    {
        this.mediaStream = mediaStream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        // Nothing to do here.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        // RTP packets need not be transformed.
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPCompoundPacket reverseTransform(RTCPCompoundPacket pkt)
    {
        // Get the RTCP termination strategy from the associated media stream
        // translator.
        RTPTranslator rtpTranslator = mediaStream.getRTPTranslator();
        if (rtpTranslator == null)
            return pkt;

        RTCPTerminationStrategy rtcpTerminationStrategy
                = rtpTranslator.getRTCPTerminationStrategy();

        if (rtcpTerminationStrategy == null)
            return pkt;

        RTCPPacketTransformer rtcpPacketTransformer
                = rtcpTerminationStrategy.getRTCPCompoundPacketTransformer();

        if (rtcpPacketTransformer == null)
            return pkt;

        return rtcpPacketTransformer.reverseTransform(pkt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPCompoundPacket transform(RTCPCompoundPacket rtcpCompoundPacket)
    {
        return rtcpCompoundPacket;
    }
}
