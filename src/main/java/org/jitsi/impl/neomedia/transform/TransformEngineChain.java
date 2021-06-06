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

import java.util.*;

import org.jitsi.service.neomedia.*;

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
     * The view of {@link #engineChain} as a {@code List}.
     */
    private List<TransformEngine> engineChainAsList;

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
        setEngineChain(engineChain.clone());
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

    /**
     * Appends a {@link TransformEngine} to this chain.
     * @param engine the engine to add.
     * @return {@code true} if the engine was added, and {@code false} if the
     * engine was not added because it is already a member of the chain.
     */
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

            addEngine(engine, oldValue.length);
        }

        return true;
    }

    /**
     * Adds a {@link TransformEngine} to this chain, at the position after the
     * {@code after} instance.
     * @param engine the engine to add.
     * @param after the {@link TransformEngine} instance from this chain, after
     * which {@code engine} should be inserted.
     * @return {@code true} if the engine was added, and {@code false} if the
     * engine was not added because it is already a member of the chain.
     */
    public boolean addEngine(TransformEngine engine, TransformEngine after)
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

            int position = -1;
            if (after == null)
            {
                position = 0;
            }
            for (int i = 0; i < oldValue.length; i++)
            {
                if (oldValue[i] == after)
                {
                    position = i + 1;
                    break;
                }
            }

            if (position == -1)
            {
                return false;
            }
            else
            {
                addEngine(engine, position);
            }
        }

        return true;
    }

    /**
     * Adds a {@link TransformEngine} at a specific position in this chain.
     * @param engine the engine to add.
     * @param position the position at which to add the engine.
     */
    public void addEngine(TransformEngine engine, int position)
    {
        if (engine == null)
            throw new NullPointerException("engine");

        synchronized (this)
        {
            TransformEngine[] oldValue = this.engineChain;
            if (position < 0 || position > oldValue.length)
            {
                throw new IllegalArgumentException(
                    "position=" + position + "; len=" + oldValue.length);
            }

            TransformEngine[] newValue
                = new TransformEngine[oldValue.length + 1];
            System.arraycopy(oldValue, 0, newValue, 0, position);
            System.arraycopy(
                oldValue, position,
                newValue, position + 1,
                oldValue.length - position);
            newValue[position] = engine;
            setEngineChain(newValue);
        }
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
     * Sets the sequence of {@code TransformEngine}s whose
     * {@code PacketTransformer}s this engine chain will be applying to RTP and
     * RTCP packets.
     *
     * @param engineChain the sequence of {@code TransformEngine}s whose
     * {@code PacketTransformer}s this engine chain will be applying to RTP and
     * RTCP packets
     */
    private void setEngineChain(TransformEngine[] engineChain)
    {
        this.engineChain = engineChain;
        this.engineChainAsList = Arrays.asList(engineChain);
    }

    /**
     * A <tt>PacketTransformerChain</tt> is a meta <tt>PacketTransformer</tt>
     * that applies all transformers present in this engine chain. The class
     * respects the order of the engine chain for outgoing packets and reverses
     * it for incoming packets.
     */
    public class PacketTransformerChain
        implements PacketTransformer
    {
        /**
         * Indicates whether this transformer will be dealing with RTP or,
         * in other words, whether it will transform packets via the RTP
         * transformers in this chain rather than the RTCP ones.
         */
        private final boolean rtp;

        /**
         * Creates an instance of this packet transformer and prepares it to
         * deal with RTP or RTCP according to the <tt>isRtp</tt> argument.
         *
         * @param rtp <tt>true</tt> if this transformer will be dealing with RTP
         * (i.e. will transform packets via the RTP transformers in this chain
         * rather than the RTCP ones) and <tt>false</tt> otherwise.
         */
        public PacketTransformerChain(boolean rtp)
        {
            this.rtp = rtp;
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
                    = rtp
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
                    = rtp
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
         * Transforms the specified {@code pkts} using each of the
         * {@code TransformEngine}s in the chain in order.
         */
        @Override
        public RawPacket[] transform(RawPacket[] pkts)
        {
            return transform(pkts, /* after */ null);
        }

        /**
         * Transforms the specified {@code pkts} using the
         * {@code TransformEngine}s in the chain in order starting after a
         * specific {@code TransformEngine}.
         *
         * @param pkts the array of {@code RawPacket}s to transform
         * @param after the {@code TransformEngine} in the chain after which the
         * transformation is to begin. If {@code after} is not in the chain, the
         * transformation executes through the whole chain.
         * @return the array of {@code RawPacket}s that is the result of the
         * transformation of {@code pkts} using the {@code TransformEngine}s in
         * the chain
         */
        public RawPacket[] transform(RawPacket[] pkts, TransformEngine after)
        {
            // If the specified after is in the transformation chain, the
            // transformation is to start after it.
            boolean lookForAfter
                = after != null && engineChainAsList.contains(after);

            for (TransformEngine engine : engineChain)
            {
                // Start the transformation after the specified TransformEngine.
                if (lookForAfter)
                {
                    if (engine.equals(after))
                    {
                        lookForAfter = false;
                    }
                    continue;
                }

                // Transform.
                PacketTransformer transformer
                    = rtp
                        ? engine.getRTPTransformer()
                        : engine.getRTCPTransformer();

                // The transformer may be null if for example the engine does
                // RTP transformations only and this is an RTCP transformer.
                if (transformer != null)
                    pkts = transformer.transform(pkts);
            }

            return pkts;
        }
    }
}
