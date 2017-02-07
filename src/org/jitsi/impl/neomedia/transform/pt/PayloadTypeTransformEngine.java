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
package org.jitsi.impl.neomedia.transform.pt;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

import java.util.*;

/**
 * We use this engine to change payload types of outgoing RTP packets if needed.
 * This is necessary so that we can support the RFC3264 case where the answerer
 * has the right to declare what payload type mappings it wants to receive even
 * if they are different from those in the offer. RFC3264 claims this is for
 * support of legacy protocols such as H.323 but we've been bumping with
 * a number of cases where multi-component pure SIP systems also need to
 * behave this way.
 *
 * @author Damian Minkov
 */
public class PayloadTypeTransformEngine
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    /**
     * The mapping we use to override payloads. By default it is empty
     * and we do nothing, packets are passed through without modification.
     * Maps source payload to target payload.
     */
    private Map<Byte, Byte> mappingOverrides = new HashMap<Byte, Byte>();

    /**
     * This map is a copy of <tt>mappingOverride</tt> that we use during actual
     * transformation
     */
    private Map<Byte, Byte> mappingOverridesCopy = null;

    /**
     * Checks if there are any override mappings, if no setting just pass
     * through the packet.
     * If the <tt>RawPacket</tt> payload has entry in mappings to override,
     * we override packet payload type.
     *
     * @param pkt the RTP <tt>RawPacket</tt> that we check for need to change
     * payload type.
     *
     * @return the updated <tt>RawPacket</tt> instance containing the changed
     * payload type.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        if (mappingOverridesCopy == null
                || mappingOverridesCopy.isEmpty()
                || pkt.getVersion() != RTPHeader.VERSION)
            return pkt;

        Byte newPT = mappingOverridesCopy.get(pkt.getPayloadType());
        if(newPT != null)
            pkt.setPayloadType(newPT);

        return pkt;
    }

    /**
     * Closes this <tt>PacketTransformer</tt> i.e. releases the resources
     * allocated by it and prepares it for garbage collection.
     */
    public void close()
    {
    }

    /**
     * Returns a reference to this class since it is performing RTP
     * transformations in here.
     *
     * @return a reference to <tt>this</tt> instance of the
     * <tt>PayloadTypeTransformEngine</tt>.
     */
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Always returns <tt>null</tt> since this engine does not require any
     * RTCP transformations.
     *
     * @return <tt>null</tt> since this engine does not require any
     * RTCP transformations.
     */
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Adds an additional RTP payload type mapping used to override the payload
     * type of outgoing RTP packets. If an override for <tt>originalPT<tt/>,
     * was already being overridden, this call is simply going to update the
     * override to the new one.
     * <p>
     * This method creates a copy of the local overriding map so that mapping
     * overrides could be set during a call (e.g. after a SIP re-INVITE) in a
     * thread-safe way without using synchronization.
     *
     * @param originalPt the payload type that we are overriding
     * @param overridePt the payload type that we are overriding it with
     */
    public void addPTMappingOverride(byte originalPt, byte overridePt)
    {
        Byte existingOverride = mappingOverrides.get(originalPt);

        if ((existingOverride == null) || (existingOverride != overridePt))
        {
            mappingOverrides.put(originalPt, overridePt);
            mappingOverridesCopy = new HashMap<Byte, Byte>(mappingOverrides);
        }
    }
}
