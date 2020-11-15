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
package org.jitsi.impl.neomedia.transform.srtp;

import java.security.*;
import java.util.*;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.srtp.*;
import org.jitsi.utils.logging.*;

/**
 * SRTCPTransformer implements PacketTransformer.
 * It encapsulate the encryption / decryption logic for SRTCP packets
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Werner Dittmann &lt;Werner.Dittmann@t-online.de&gt;
 */
public class SRTCPTransformer
    extends SinglePacketTransformer
{
    private static final Logger logger = Logger.getLogger(SRTCPTransformer.class);
    private SrtpContextFactory forwardFactory;
    private SrtpContextFactory reverseFactory;

    /**
     * All the known SSRC's corresponding SrtcpCryptoContexts
     */
    private final Map<Integer,SrtcpCryptoContext> contexts;

    /**
     * Constructs an <tt>SRTCPTransformer</tt>, sharing its
     * <tt>SrtpContextFactory</tt> instances with a given
     * <tt>SRTPTransformer</tt>.
     *
     * @param srtpTransformer the <tt>SRTPTransformer</tt> with which this
     * <tt>SRTCPTransformer</tt> will share its <tt>SrtpContextFactory</tt>
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
    public SRTCPTransformer(SrtpContextFactory factory)
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
            SrtpContextFactory forwardFactory,
            SrtpContextFactory reverseFactory)
    {
        this.forwardFactory = forwardFactory;
        this.reverseFactory = reverseFactory;
        this.contexts = new HashMap<>();
    }

    /**
     * Sets a new key factory when key material has changed.
     * 
     * @param factory The associated context factory for transformations.
     * @param forward <tt>true</tt> if the supplied factory is for forward
     *            transformations, <tt>false</tt> for the reverse transformation
     *            factory.
     */
    public void updateFactory(SrtpContextFactory factory, boolean forward)
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

            contexts.clear();
        }
    }

    private SrtcpCryptoContext getContext(
            RawPacket pkt,
            SrtpContextFactory engine)
    {
        int ssrc = (int) pkt.getRTCPSSRC();
        SrtcpCryptoContext context;

        synchronized (contexts)
        {
            context = contexts.get(ssrc);
            if (context == null && engine != null)
            {
                try
                {
                    context = engine.deriveControlContext(ssrc);
                }
                catch (GeneralSecurityException e)
                {
                    logger.error("Could not get context for ssrc " + ssrc, e);
                    return null;
                }
                contexts.put(ssrc, context);
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
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        SrtcpCryptoContext context = getContext(pkt, reverseFactory);

        if (context == null)
        {
            return null;
        }

        try
        {
            return context.reverseTransformPacket(pkt) == SrtpErrorStatus.OK
                ? pkt
                : null;
        }
        catch (GeneralSecurityException e)
        {
            // the error was already logged in SinglePacketTransformer
            return null;
        }
    }

    /**
     * Encrypts a SRTCP packet
     *
     * @param pkt plain SRTCP packet to be encrypted
     * @return encrypted SRTCP packet
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        SrtcpCryptoContext context = getContext(pkt, forwardFactory);

        if(context != null)
        {
            try
            {
                context.transformPacket(pkt);
                return pkt;
            }
            catch (GeneralSecurityException e)
            {
                // the error was already logged in SinglePacketTransformer
                return null;
            }
        }
        else
        {
            // The packet cannot be encrypted. Thus, do not send it.
            return null;
        }
    }
}
