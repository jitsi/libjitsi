/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;

/**
 * The engine chain allows using numerous <tt>TransformEngine</tt>s on a single
 * stream.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class TransformEngineChain
    implements TransformEngine
{

    /**
     * The sequence of <tt>TransformEngine</tt>s whose
     * <tt>PacketTransformer</tt>s this engine chain will be applying to RTP and
     * RTCP packets. Implemented as copy-on-write storage for the purposes of
     * performance.
     */
    protected TransformEngine[] engineChain;

    /**
     * The sequence of <tt>PacketTransformer</tt>s that this engine chain will
     * be applying to RTCP packets.
     */
    private PacketTransformerChain rtcpTransformChain;

    /**
     * The sequence of <tt>PacketTransformer</tt>s that this engine chain will
     * be applying to RTP packets.
     */
    private PacketTransformerChain rtpTransformChain;

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
     * Creates a new <tt>TransformEngineChain</tt> without initializing the
     * array of transformers to be used. Allows extending classes to initialize
     * the array on their own.
     */
    protected TransformEngineChain()
    {
        // Extenders must initialize this.engineChain
    }

    public boolean addEngine(TransformEngine engine)
    {
        if (engine == null)
            throw new NullPointerException("engine");

        synchronized (this)
        {
            TransformEngine[] oldValue = this.engineChain;

            for (TransformEngine e : oldValue)
            {
                if (engine.equals(e))
                    return false;
            }

            int oldLength = oldValue.length;
            TransformEngine[] newValue = new TransformEngine[oldLength + 1];

            if (oldLength != 0)
                System.arraycopy(oldValue, 0, newValue, 0, oldLength);
            newValue[oldLength] = engine;
            this.engineChain = newValue;
        }

        return true;
    }

    /**
     * Gets the sequence of <tt>TransformEngine</tt>s whose
     * <tt>PacketTransformer</tt>s this engine chain applies to RTP and RTCP
     * packets.
     *
     * @return the sequence of <tt>TransformEngine</tt>s whose
     * <tt>PacketTransformer</tt>s this engine chain applies to RTP and RTCP
     * packets
     */
    public TransformEngine[] getEngineChain()
    {
        return engineChain.clone();
    }

    /**
     * Returns the meta <tt>PacketTransformer</tt> that will be applying
     * RTCP transformations from all engines registered in this
     * <tt>TransformEngineChain</tt>.
     *
     * @return a <tt>PacketTransformerChain</tt> over all RTCP transformers in
     * this engine chain.
     */
    @Override
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
     * Returns the meta <tt>PacketTransformer</tt> that will be applying
     * RTCP transformations from all engines registered in this
     * <tt>TransformEngineChain</tt>.
     *
     * @return a <tt>PacketTransformerChain</tt> over all RTP transformers in
     * this engine chain.
     */
    @Override
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
         * deal with RTP or RTCP according to the <tt>isRtp</tt> argument.
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
        @Override
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
         * Reverse-transforms the given packets using each of the
         * <tt>TransformEngine</tt>-s in the engine chain in reverse order.
         */
        @Override
        public RawPacket[] reverseTransform(RawPacket pkts[])
        {
            TransformEngine[] engineChain
                = TransformEngineChain.this.engineChain;

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

        /**
         * {@inheritDoc}
         *
         * Transforms the given packets using each of the
         * <tt>TransformEngine</tt>-s in the engine chain in order.
         */
        @Override
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
    }
}
