/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;

/**
 * The engine chain allows using numerous <tt>TransformEngine</tt>s on a single
 * stream.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 */
public class TransformEngineChain
    implements TransformEngine
{

    /**
     * The sequence of <tt>TransformEngine</tt>s whose
     * <tt>PacketTransformer</tt>s that this engine chain will be applying to
     * RTP and RTCP packets.
     */
    private final TransformEngine[] engineChain;

    /**
     * The sequence of <tt>PacketTransformer</tt>s that this engine chain will
     * be applying to RTP packets.
     */
    private PacketTransformerChain rtpTransformChain;

    /**
     * The sequence of <tt>PacketTransformer</tt>s that this engine chain will
     * be applying to RTCP packets.
     */
    private PacketTransformerChain rtcpTransformChain;

    /**
     * Creates a new <tt>TransformEngineChain</tt> using the
     * <tt>engineChain</tt> array. Engines will be applied in the order
     * specified by the <tt>engineChain</tt> array for outgoing packets
     * and in the reverse order for incoming packets.
     *
     * @param engineChain an array containing <tt>TransformEngine</tt>s in the
     * order that they are to be applied on outgoing packets.
     */
    public TransformEngineChain(TransformEngine[] engineChain)
    {
        this.engineChain = engineChain.clone();
    }

    /**
     * Returns the meta <tt>PacketTransformer</tt> that will be applying
     * RTCP transformations from all engines registered in this
     * <tt>TransformEngineChain</tt>.
     *
     * @return a <tt>PacketTransformerChain</tt> over all RTP transformers in
     * this engine chain.
     */
    public PacketTransformer getRTPTransformer()
    {
        /*
         * XXX Certain TransformEngine implementations in engineChain may
         * postpone the initialization of their PacketTransformer until it is
         * requested for the first time AND may have to send packets at that
         * very moment, not earlier (e.g. DTLS-SRTP may have to send Client
         * Hello). Make sure that, when the PacketTransformer of this
         * TransformEngine is requested for the first time, the same method will
         * be invoked on each of the TransformEngines in engineChain.
         */
        boolean invokeOnEngineChain;
        PacketTransformer rtpTransformer;

        synchronized (this)
        {
            if (rtpTransformChain == null)
            {
                rtpTransformChain = new PacketTransformerChain(true);
                invokeOnEngineChain = true;
            }
            else
            {
                invokeOnEngineChain = false;
            }
            rtpTransformer = rtpTransformChain;
        }
        if (invokeOnEngineChain)
        {
            for (TransformEngine engine : engineChain)
                engine.getRTPTransformer();
        }
        return rtpTransformer;
    }

    /**
     * Returns the meta <tt>PacketTransformer</tt> that will be applying
     * RTCP transformations from all engines registered in this
     * <tt>TransformEngineChain</tt>.
     *
     * @return a <tt>PacketTransformerChain</tt> over all RTCP transformers in
     * this engine chain.
     */
    public PacketTransformer getRTCPTransformer()
    {
        /*
         * XXX Certain TransformEngine implementations in engineChain may
         * postpone the initialization of their PacketTransformer until it is
         * requested for the first time AND may have to send packets at that
         * very moment, not earlier (e.g. DTLS-SRTP may have to send Client
         * Hello). Make sure that, when the PacketTransformer of this
         * TransformEngine is requested for the first time, the same method will
         * be invoked on each of the TransformEngines in engineChain.
         */
        boolean invokeOnEngineChain;
        PacketTransformer rtpTransformer;

        synchronized (this)
        {
            if (rtcpTransformChain == null)
            {
                rtcpTransformChain = new PacketTransformerChain(false);
                invokeOnEngineChain = true;
            }
            else
            {
                invokeOnEngineChain = false;
            }
            rtpTransformer = rtcpTransformChain;
        }
        if (invokeOnEngineChain)
        {
            for (TransformEngine engine : engineChain)
                engine.getRTCPTransformer();
        }
        return rtpTransformer;
    }

    /**
     * A <tt>PacketTransformerChain</tt> is a meta <tt>PacketTransformer</tt>
     * that applies all transformers present in this engine chain. The class
     * respects the order of the engine chain for outgoing packets and reverses
     * it for incoming packets.
     */
    private class PacketTransformerChain
        implements PacketTransformer
    {
        /**
         * Indicates whether this transformer will be dealing with RTP or,
         * in other words, whether it will transform packets via the RTP
         * transformers in this chain rather than the RTCP ones.
         */
        private final boolean isRtp;

        /**
         * Creates an instance of this packet transformer and prepares it to
         * deal with RTP or RTCP according to the <tt>isRtp</tt> arg.
         *
         * @param isRtp <tt>true</tt> if this transformer will be dealing with
         * RTP (i.e. will transform packets via the RTP transformers in this
         * chain rather than the RTCP ones) and <tt>false</tt> otherwise.
         */
        public PacketTransformerChain(boolean isRtp)
        {
            this.isRtp = isRtp;
        }

        /**
         * Close the transformer and underlying transform engines.
         *
         * Propagate the close to all transformers in chain.
         */
        public void close()
        {
            for (TransformEngine engine : engineChain)
            {
                PacketTransformer pTransformer
                    = isRtp
                        ? engine.getRTPTransformer()
                        : engine.getRTCPTransformer();

                //the packet transformer may be null if for example the engine
                //only does RTP transformations and this is an RTCP transformer.
                if( pTransformer != null)
                    pTransformer.close();
            }
        }

        /**
         * {@inheritDoc}
         *
         * Transforms the given packets using each of the
         * <tt>TransformEngine</tt>-s in the engine chain in order.
         */
        public RawPacket[] transform(RawPacket[] pkts)
        {
            for (TransformEngine engine : engineChain)
            {
                PacketTransformer pTransformer
                    = isRtp
                        ? engine.getRTPTransformer()
                        : engine.getRTCPTransformer();

                //the packet transformer may be null if for example the engine
                //only does RTP transformations and this is an RTCP transformer.
                if (pTransformer != null)
                    pkts = pTransformer.transform(pkts);
            }

            return pkts;
        }

        /**
         * {@inheritDoc}
         *
         * Reverse-transforms the given packets using each of the
         * <tt>TransformEngine</tt>-s in the engine chain in reverse order.
         */
        public RawPacket[] reverseTransform(RawPacket pkts[])
        {
            for (int i = engineChain.length - 1 ; i >= 0; i--)
            {
                TransformEngine engine = engineChain[i];
                PacketTransformer pTransformer
                    = isRtp
                        ? engine.getRTPTransformer()
                        : engine.getRTCPTransformer();

                //the packet transformer may be null if for example the engine
                //only does RTP transformations and this is an RTCP transformer.
                if (pTransformer != null)
                    pkts = pTransformer.reverseTransform(pkts);
            }

            return pkts;
        }
    }
}
