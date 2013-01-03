/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * Some of the code in this class is derived from ccRtp's SRTP implementation,
 * which has the following copyright notice:
 *
  Copyright (C) 2004-2006 the Minisip Team

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public
  License along with this library; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
*/
package org.jitsi.impl.neomedia.transform.srtp;

import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;

/**
 * SRTPTransformer implements PacketTransformer and provides implementations
 * for RTP packet to SRTP packet transformation and SRTP packet to RTP packet
 * transformation logic.
 *
 * It will first find the corresponding SRTPCryptoContext for each packet based
 * on their SSRC and then invoke the context object to perform the
 * transformation and reverse transformation operation.
 *
 * @author Bing SU (nova.su@gmail.com)
 */
public class SRTPTransformer
    implements PacketTransformer
{
    private final SRTPContextFactory forwardFactory;
    private final SRTPContextFactory reverseFactory;

    /**
     * All the known SSRC's corresponding SRTPCryptoContexts
     */
    private final Hashtable<Long, SRTPCryptoContext> contexts;

    /**
     * Initializes a new <tt>SRTPTransformer</tt> instance.
     * 
     * @param factory the context factory to be used by the new
     * instance for both directions.
     */
    public SRTPTransformer(SRTPContextFactory factory)
    {
        this(factory, factory);
    }

    /**
     * Constructs a SRTPTransformer object.
     * 
     * @param forwardFactory The associated context factory for forward
     *            transformations.
     * @param reverseFactory The associated context factory for reverse
     *            transformations.
     */
    public SRTPTransformer(
            SRTPContextFactory forwardFactory,
            SRTPContextFactory reverseFactory)
    {
        this.forwardFactory = forwardFactory;
        this.reverseFactory = reverseFactory;
        this.contexts = new Hashtable<Long, SRTPCryptoContext>();
    }

    /**
     * Closes this <tt>SRTPTransformer</tt> and the underlying transform
     * engines.It closes all stored crypto contexts. It deletes key data and
     * forces a cleanup of the crypto contexts.
     */
    public void close()
    {
        synchronized (contexts)
        {
            forwardFactory.close();
            if (reverseFactory != forwardFactory)
                reverseFactory.close();

            Iterator<Map.Entry<Long, SRTPCryptoContext>> iter
                = contexts.entrySet().iterator();

            while (iter.hasNext())
            {
                Map.Entry<Long, SRTPCryptoContext> entry = iter.next();
                SRTPCryptoContext context = entry.getValue();

                iter.remove();
                if (context != null)
                    context.close();
            }
        }
    }

    private SRTPCryptoContext getContext(
            long ssrc,
            SRTPContextFactory engine,
            int deriveSrtpKeysIndex)
    {
        SRTPCryptoContext context;

        synchronized (contexts)
        {
            context = contexts.get(ssrc);
            if (context == null)
            {
                context = engine.getDefaultContext();
                if (context != null)
                {
                    context = context.deriveContext(ssrc, 0, 0);
                    context.deriveSrtpKeys(deriveSrtpKeysIndex);
                    contexts.put(ssrc, context);
                }
            }
        }

        return context;
    }

    /**
     * Reverse-transforms a specific packet (i.e. transforms a transformed
     * packet back).
     *
     * @param pkt the transformed packet to be restored
     * @return the restored packet
     */
    public RawPacket reverseTransform(RawPacket pkt)
    {
        // only accept RTP version 2 (SNOM phones send weird packages when on
        // hold, ignore them with this check (RTP Version must be equal to 2)
        if((pkt.readByte(0) & 0xC0) != 0x80)
            return null;

        SRTPCryptoContext context
            = getContext(pkt.getSSRC(), reverseFactory, pkt.getSequenceNumber());

        return
            ((context != null) && context.reverseTransformPacket(pkt))
                ? pkt
                : null;
    }

    /**
     * Transforms a specific packet.
     *
     * @param pkt the packet to be transformed
     * @return the transformed packet
     */
    public RawPacket transform(RawPacket pkt)
    {
        SRTPCryptoContext context = getContext(pkt.getSSRC(), forwardFactory, 0);

        context.transformPacket(pkt);
        return pkt;
    }
}
