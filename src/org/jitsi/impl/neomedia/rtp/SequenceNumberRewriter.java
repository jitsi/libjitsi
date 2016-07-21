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
package org.jitsi.impl.neomedia.rtp;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * Rewrites sequence numbers for RTP streams by hiding any gaps caused by
 * dropped packets. Rewriters are not thread-safe. If multiple threads access a
 * rewriter concurrently, it must be synchronized externally.
 *
 * @author Maryam Daneshi
 * @author George Politis
 */
public class SequenceNumberRewriter
{
    /**
     * The delta between what's been accepted and what's been received, mod 16.
     */
    int delta = 0;

    /**
     * The highest sequence number that got accepted, mod 16.
     */
    int highestSent = -1;

    /**
     * Rewrites the sequence number of the RTP packet in the byte buffer, hiding
     * any gaps caused by drops.
     *
     * @param accept true if the packet is accepted, false otherwise
     * @param buf the byte buffer that contains the RTP packet
     * @param off the offset in the byte buffer where the RTP packet starts
     * @param len the length of the RTP packet in the byte buffer
     */
    public void rewrite(boolean accept, byte[] buf, int off, int len)
    {
        if (buf == null || buf.length + off < len)
        {
            return;
        }

        int sequenceNumber = RawPacket.getSequenceNumber(buf, off, len);
        int newSequenceNumber = rewriteSequenceNumber(accept, sequenceNumber);

        if (sequenceNumber != newSequenceNumber)
        {
            RawPacket.setSequenceNumber(buf, off, newSequenceNumber);
        }
    }

    /**
     * Rewrites the sequence number passed as a parameter, hiding any gaps
     * caused by drops.
     *
     * @param accept true if the packet is accepted, false otherwise
     * @param sequenceNumber the sequence number to rewrite
     * @return a rewritten sequence number that hides any gaps caused by drops.
     */
    int rewriteSequenceNumber(boolean accept, int sequenceNumber)
    {
        if (accept)
        {
            // overwrite the sequence number (if needed)
            int newSequenceNumber
                = RTPUtils.subtractNumber(sequenceNumber, delta);

            // init or update the highest sent sequence number (if needed)
            if (highestSent == -1 ||
                RTPUtils.sequenceNumberDiff(newSequenceNumber, highestSent) > 0)
            {
                highestSent = newSequenceNumber;
            }

            return newSequenceNumber;
        }
        else
        {
            // update the delta (if needed)
            if (highestSent != -1)
            {
                final int newDelta
                    = RTPUtils.subtractNumber(sequenceNumber, highestSent);

                if (RTPUtils.sequenceNumberDiff(newDelta, delta) > 0)
                {
                    delta = newDelta;
                }
            }

            return sequenceNumber;
        }
    }
}
