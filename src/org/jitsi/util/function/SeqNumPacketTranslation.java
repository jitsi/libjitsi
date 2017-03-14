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

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;

/**
 * @author George Politis
 */
public class SeqNumPacketTranslation<T extends ByteArrayBuffer>
    extends AbstractFunction<T, T>
{
    /**
     * The {@link SeqNumTranslation} to apply to the sequence number of the
     * {@link RawPacket} that is specified as an argument in the apply method.
     */
    private final SeqNumTranslation seqNumTranslation;

    /**
     *
     * @param seqNumDelta The delta to apply to the sequence number of the
     * {@link RawPacket} that is specified as an argument in the apply method.
     */
    public SeqNumPacketTranslation(int seqNumDelta)
    {
        this.seqNumTranslation = new SeqNumTranslation(seqNumDelta);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteArrayBuffer apply(ByteArrayBuffer baf)
    {
        if (RTPPacketPredicate.INSTANCE.test(baf))
        {
            int srcSeqNum = RawPacket.getSequenceNumber(baf);
            int dstSeqNum = seqNumTranslation.apply(srcSeqNum);

            if (srcSeqNum != dstSeqNum)
            {
                RawPacket.setSequenceNumber(baf, dstSeqNum);
            }
        }

        return baf;
    }
}