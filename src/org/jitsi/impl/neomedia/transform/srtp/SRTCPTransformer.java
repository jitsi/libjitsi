/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;

/**
 * SRTCPTransformer implements PacketTransformer.
 * It encapsulate the encryption / decryption logic for SRTCP packets
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Werner Dittmann &lt;Werner.Dittmann@t-online.de>
 */
public class SRTCPTransformer
    extends SinglePacketTransformer
{
    private SRTPContextFactory forwardFactory;
    private SRTPContextFactory reverseFactory;

    /**
     * All the known SSRC's corresponding SRTCPCryptoContexts
     */
    private final Map<Integer,SRTCPCryptoContext> contexts;

    /**
     * Constructs an <tt>SRTCPTransformer</tt>, sharing its
     * <tt>SRTPContextFactory</tt> instances with a given
     * <tt>SRTPTransformer</tt>.
     *
     * @param srtpTransformer the <tt>SRTPTransformer</tt> with which this
     * <tt>SRTCPTransformer</tt> will share its <tt>SRTPContextFactory</tt>
     * instances.
     */
    public SRTCPTransformer(SRTPTransformer srtpTransformer)
    {
        this(srtpTransformer.forwardFactory,
             srtpTransformer.reverseFactory);
    }

    /**
     * Constructs a SRTCPTransformer object.
     *
     * @param factory The associated context factory for both
     *            transform directions.
     */
    public SRTCPTransformer(SRTPContextFactory factory)
    {
        this(factory, factory);
    }

    /**
     * Constructs a SRTCPTransformer object.
     *
     * @param forwardFactory The associated context factory for forward
     *            transformations.
     * @param reverseFactory The associated context factory for reverse
     *            transformations.
     */
    public SRTCPTransformer(
            SRTPContextFactory forwardFactory,
            SRTPContextFactory reverseFactory)
    {
        this.forwardFactory = forwardFactory;
        this.reverseFactory = reverseFactory;
        this.contexts = new HashMap<Integer,SRTCPCryptoContext>();
    }

    /**
     * Sets a new key factory when key material has changed.
     * 
     * @param factory The associated context factory for transformations.
     * @param forward <tt>true</tt> if the supplied factory is for forward
     *            transformations, <tt>false</tt> for the reverse transformation
     *            factory.
     */
    public void updateFactory(SRTPContextFactory factory, boolean forward)
    {
        synchronized (contexts)
        {
            if (forward)
            {
                if (this.forwardFactory != null
                    && this.forwardFactory != factory)
                {
                    this.forwardFactory.close();
                }

                this.forwardFactory = factory;
            }
            else
            {
                if (this.reverseFactory != null &&
                    this.reverseFactory != factory)
                {
                    this.reverseFactory.close();
                }

                this.reverseFactory = factory;
            }
        }
    }

    /**
     * Closes this <tt>SRTCPTransformer</tt> and the underlying transform
     * engine. It closes all stored crypto contexts. It deletes key data and
     * forces a cleanup of the crypto contexts.
     */
    public void close()
    {
        synchronized (contexts)
        {
            forwardFactory.close();
            if (reverseFactory != forwardFactory)
                reverseFactory.close();

            for (Iterator<SRTCPCryptoContext> i = contexts.values().iterator();
                    i.hasNext();)
            {
                SRTCPCryptoContext context = i.next();

                i.remove();
                if (context != null)
                    context.close();
            }
        }
    }

    private SRTCPCryptoContext getContext(
            RawPacket pkt,
            SRTPContextFactory engine)
    {
        int ssrc = pkt.getRTCPSSRC();
        SRTCPCryptoContext context = null;

        synchronized (contexts)
        {
            context = contexts.get(ssrc);
            if (context == null && engine != null)
            {
                context = engine.getDefaultContextControl();
                if (context != null)
                {
                    context = context.deriveContext(ssrc);
                    context.deriveSrtcpKeys();
                    contexts.put(ssrc, context);
                }
            }
        }

        return context;
    }

    /**
     * Decrypts a SRTCP packet
     *
     * @param pkt encrypted SRTCP packet to be decrypted
     * @return decrypted SRTCP packet
     */
    public RawPacket reverseTransform(RawPacket pkt)
    {
        SRTCPCryptoContext context = getContext(pkt, reverseFactory);

        return
            ((context != null) && context.reverseTransformPacket(pkt))
                ? pkt
                : null;
    }

    /**
     * Encrypts a SRTCP packet
     *
     * @param pkt plain SRTCP packet to be encrypted
     * @return encrypted SRTCP packet
     */
    public RawPacket transform(RawPacket pkt)
    {
        SRTCPCryptoContext context = getContext(pkt, forwardFactory);

        if(context != null)
        {
            context.transformPacket(pkt);
            return pkt;
        }
        else
        {
            // The packet cannot be encrypted. Thus, do not send it.
            return null;
        }
    }
}
