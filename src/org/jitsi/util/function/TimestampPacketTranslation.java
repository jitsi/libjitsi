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
package org.jitsi.util.function;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * @author George Politis
 */
public class TimestampPacketTranslation<T extends ByteArrayBuffer>
extends AbstractFunction<T, T>
{
    /**
     * The {@link TimestampTranslation} to apply to the timestamp of the
     * {@link RawPacket} that is specified as an argument in the apply method.
     */
    private final TimestampTranslation tsTranslation;

    /**
     * Ctor.
     *
     * @param tsDelta The delta to apply to the timestamp of the
     * {@link RawPacket} that is specified as an argument in the apply method.
     */
    public TimestampPacketTranslation(long tsDelta)
    {
        this.tsTranslation = new TimestampTranslation(tsDelta);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T apply(T pktIn)
    {
        if (RTPPacketPredicate.INSTANCE.test(pktIn))
        {
            long srcTs = RawPacket.getTimestamp(pktIn);
            long dstTs = tsTranslation.apply(srcTs);

            if (dstTs != srcTs)
            {
                RawPacket.setTimestamp(pktIn, dstTs);
            }

            return pktIn;
        }
        else if (RTCPPacketPredicate.INSTANCE.test(pktIn)
            && RTCPUtils.getPacketType(pktIn) == RTCPPacket.SR)
        {
            // Rewrite the timestamp of an SR packet.
            long srcTs = RTCPSenderInfoUtils.getTimestamp(pktIn);
            long dstTs = tsTranslation.apply(srcTs);

            if (srcTs != dstTs)
            {
                RTCPSenderInfoUtils.setTimestamp(pktIn, (int) dstTs);
            }

            return pktIn;
        }
        else
        {
            return pktIn;
        }
    }
}
