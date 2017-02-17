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
package org.jitsi.impl.neomedia.transform.fec;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.util.*;

/**
 * Implements a {@link org.jitsi.impl.neomedia.transform.PacketTransformer} and
 * {@link org.jitsi.impl.neomedia.transform.TransformEngine} for RFC5109.
 *
 * @author Boris Grozev
 */
public class FECTransformEngine
        implements TransformEngine,
        PacketTransformer
{
    /**
     * The <tt>Logger</tt> used by the <tt>FECTransformEngine</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
            = Logger.getLogger(FECTransformEngine.class);

    /**
     * Initial size for newly allocated byte arrays.
     */
    public static final int INITIAL_BUFFER_SIZE = 1500;

    /**
     * The payload type for incoming ulpfec (RFC5109) packets.
     *
     * The special value "-1" is used to effectively disable reverse-transforming
     * packets.
     */
    private byte incomingPT = -1;

    /**
     * The payload type for outgoing ulpfec (RFC5109) packets.
     *
     * The special value "-1" is used to effectively disable transforming
     * packets.
     */
    private byte outgoingPT = -1;

    /**
     * The rate at which ulpfec packets will be generated and added to the
     * stream by this <tt>PacketTransformer</tt>.
     * An ulpfec packet will be generated for every <tt>fecRate</tt> media
     * packets.
     * If set to 0, no ulpfec packets will be generated.
     */
    private int fecRate = 0;

    /**
     * Maps an SSRC to a <tt>FECReceiver</tt> to be used for packets
     * with that SSRC.
     */
    private final Map<Long,FECReceiver> fecReceivers
            = new HashMap<Long,FECReceiver>();

    /**
     * Maps an SSRC to a <tt>FECSender</tt> to be used for packets with that
     * SSRC.
     */
    private final Map<Long,FECSender> fecSenders
            = new HashMap<Long,FECSender>();

    /**
     * Initializes a new <tt>FECTransformEngine</tt> instance.
     *
     * @param incomingPT the RTP payload type number for incoming ulpfec packet.
     * @param outgoingPT the RTP payload type number for outgoing ulpfec packet.
     */
    public FECTransformEngine(byte incomingPT, byte outgoingPT)
    {
        setIncomingPT(incomingPT);
        setOutgoingPT(outgoingPT);
    }

    /**
     * Initializes a new <tt>FECTransformEngine</tt> instance.
     */
    public FECTransformEngine()
    {
        this((byte) -1, (byte) -1);
    }

    /**
     * {@inheritDoc}
     *
     * Assumes that all packets in <tt>pkts</tt> have the same SSRC. Reverse-
     * transforms using the <tt>FECReceiver</tt> for the SSRC found in
     * <tt>pkts</tt>.
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        if (incomingPT == -1 || pkts == null)
            return pkts;

        // Assumption: all packets in pkts have the same SSRC
        Long ssrc = findSSRC(pkts);
        if (ssrc == null)
            return pkts;

        FECReceiver fpt;
        synchronized (fecReceivers)
        {
            fpt = fecReceivers.get(ssrc);
            if (fpt == null)
            {
                fpt = new FECReceiver(ssrc, incomingPT);
                fecReceivers.put(ssrc, fpt);
            }
        }

        return fpt.reverseTransform(pkts);
    }

    /**
     * {@inheritDoc}
     *
     * Adds ulpfec packets to the stream (one ulpfec packet after every
     * <tt>fecRate</tt> media packets.
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts)
    {
        if (outgoingPT == -1 || pkts == null)
            return pkts;

        Long ssrc = findSSRC(pkts);
        if (ssrc == null)
            return pkts;

        FECSender fpt;
        synchronized (fecSenders)
        {
            fpt = fecSenders.get(ssrc);
            if (fpt == null)
            {
                fpt = new FECSender(ssrc, fecRate, outgoingPT);
                fecSenders.put(ssrc, fpt);
            }
        }

        return fpt.transform(pkts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        Collection<FECReceiver> receivers;
        Collection<FECSender> senders;

        synchronized (fecReceivers)
        {
            receivers = fecReceivers.values();
            fecReceivers.clear();
        }
        synchronized (fecSenders)
        {
            senders = fecSenders.values();
            fecSenders.clear();
        }

        for (FECReceiver fecReceiver : receivers)
            fecReceiver.close();
        for (FECSender fecSender : senders)
            fecSender.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * We don't touch RTCP.
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Sets the payload type for incoming ulpfec packets.
     * @param incomingPT the payload type to set
     */
    public void setIncomingPT(byte incomingPT)
    {
        this.incomingPT = incomingPT;
        synchronized (fecReceivers)
        {
            for (FECReceiver f : fecReceivers.values())
                f.setUlpfecPT(incomingPT);
        }
        if (logger.isDebugEnabled())
            logger.debug("Setting payload type for incoming ulpfec: "
                    + incomingPT);
    }

    /**
     * Sets the payload type for outgoing ulpfec packets.
     * @param outgoingPT the payload type to set
     */
    public void setOutgoingPT(byte outgoingPT)
    {
        this.outgoingPT = outgoingPT;
        synchronized (fecSenders)
        {
            for (FECSender f : fecSenders.values())
                f.setUlpfecPT(outgoingPT);
        }
        if (logger.isDebugEnabled())
            logger.debug("Setting payload type for outgoing ulpfec: "
                    + outgoingPT);
    }

    /**
     * Sets the rate at which ulpfec packets will be generated and added to the
     * stream by this <tt>PacketTransformer</tt>.
     * @param fecRate the rate to set, should be in [0, 16]
     */
    public void setFecRate(int fecRate)
    {
        synchronized (fecSenders)
        {
            for (FECSender f : fecSenders.values())
                f.setFecRate(fecRate);
        }

        this.fecRate = fecRate;
    }

    /**
     * Get the rate at which ulpfec packets will be generated and added to the
     * stream by this <tt>PacketTransformer</tt>.
     * @return the rate at which ulpfec packets will be generated and added to the
     * stream by this <tt>PacketTransformer</tt>.
     */
    public int getFecRate()
    {
        return fecRate;
    }

    /**
     * Returns the SSRC in the first non-null element of <tt>pkts</tt> or
     * <tt>null</tt> if all elements of <tt>pkts</tt> are <tt>null</tt>
     * @param pkts array of to search for SSRC
     * @return the SSRC in the first non-null element of <tt>pkts</tt> or
     * <tt>null</tt> if all elements of <tt>pkts</tt> are <tt>null</tt>
     */
    private Long findSSRC(RawPacket[] pkts)
    {
        Long ret = null;
        if (pkts != null)
        {
            for (RawPacket p : pkts)
            {
                if (p != null)
                {
                    ret = p.getSSRCAsLong();
                    break;
                }
            }
        }

        return ret;
    }
}
