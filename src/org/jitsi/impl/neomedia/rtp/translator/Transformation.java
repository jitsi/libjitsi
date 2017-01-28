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
package org.jitsi.impl.neomedia.rtp.translator;

/**
 * A 2D translation in the RTP sequence number and the RTP timestamp space. It
 * keeps a sequence number delta (mod 2^16) and an RTP timestamp delta
 * (mod 2^32) and applies it to sequence numbers or RTP timestamps.
 *
 * @author George Politis
 *
 * NOTE(gp) Once we move to Java 8, I want this to be implemented as a
 * Function.
 */
public class Transformation
{
    public Transformation(long tsDelta, int seqNumDelta)
    {
        this.seqNumDelta = seqNumDelta;
        this.tsDelta = tsDelta;
    }
    /**
     * The sequence number delta (mod 2^16) to apply to the RTP packets of the
     * forwarded RTP stream.
     */
    private final long tsDelta;

    /**
     * The timestamp delta (mod 2^32) to apply to the RTP packets of the
     * forwarded RTP stream.
     */
    private final int seqNumDelta;

    /**
     *
     * @param ts
     * @return
     */
    public long rewriteTimestamp(long ts)
    {
        return tsDelta == 0 ? ts : (ts + tsDelta) & 0xFFFFFFFFL;
    }

    /**
     *
     * @param seqNum
     * @return
     */
    public int rewriteSeqNum(int seqNum)
    {
        return seqNumDelta == 0 ? seqNum : (seqNum + seqNumDelta) & 0xFFFF;
    }
}
