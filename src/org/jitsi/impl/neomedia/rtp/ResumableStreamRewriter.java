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

import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Rewrites sequence numbers for RTP streams by hiding any gaps caused by
 * dropped packets. Rewriters are not thread-safe. If multiple threads access a
 * rewriter concurrently, it must be synchronized externally.
 *
 * @author Maryam Daneshi
 * @author George Politis
 */
public class ResumableStreamRewriter
{
    /**
     * The sequence number delta between what's been accepted and what's been
     * received, mod 2^16.
     */
    int seqnumDelta = 0;

    /**
     * The timestamp delta between what's been accepted and what's been
     * received, mod 2^32.
     */
    private long timestampDelta = 0;

    /**
     * The highest sequence number that got accepted, mod 2^16.
     */
    int highestSequenceNumberSent = -1;

    /**
     * The highest timestamp that got accepted, mod 2^32.
     */
    private long highestTimestampSent = -1;

    /**
     * Rewrites the sequence number of the RTP packet in the byte buffer,
     * hiding any gaps caused by drops.
     *
     * @param accept true if the packet is accepted, false otherwise
     * @param buf the byte buffer that contains the RTP packet
     * @param off the offset in the byte buffer where the RTP packet starts
     * @param len the length of the RTP packet in the byte buffer
     * @return true if the packet was altered, false otherwise
     */
    public boolean rewriteRTP(boolean accept, byte[] buf, int off, int len)
    {
        if (buf == null || buf.length < off + len)
        {
            return false;
        }

        int sequenceNumber = RawPacket.getSequenceNumber(buf, off, len);
        int newSequenceNumber = rewriteSequenceNumber(accept, sequenceNumber);

        long timestamp = RawPacket.getTimestamp(buf, off, len);
        long newTimestamp = rewriteTimestamp(accept, timestamp);

        boolean modified = false;

        if (sequenceNumber != newSequenceNumber)
        {
            RawPacket.setSequenceNumber(buf, off, newSequenceNumber);
            modified = true;
        }

        if (timestamp != newTimestamp)
        {
            RawPacket.setTimestamp(buf, off, len, newTimestamp);
            modified = true;
        }

        return modified;
    }

    /**
     * Restores the RTP timestamp of the RTCP SR packet in the buffer.
     *
     * @param buf the byte buffer that contains the RTCP packet.
     * @param off the offset in the byte buffer where the RTCP packet starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return true if the SR is modified, false otherwise.
     */
    public boolean processRTCP(boolean rewrite, byte[] buf, int off, int len)
    {
        if (timestampDelta == 0)
        {
            return false;
        }

        long ts = RTCPSenderInfoUtils.getTimestamp(buf, off, len);

        if (ts == -1)
        {
            return false;
        }

        long newTs = rewrite
            ? (ts - timestampDelta) & 0xffffffffL
            : (ts + timestampDelta) & 0xffffffffL;

        int ret = RTCPSenderInfoUtils.setTimestamp(buf, off, len, (int) newTs);

        return ret > 0;
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
                = RTPUtils.subtractNumber(sequenceNumber, seqnumDelta);

            // init or update the highest sent sequence number (if needed)
            if (highestSequenceNumberSent == -1 || RTPUtils.sequenceNumberDiff(
                newSequenceNumber, highestSequenceNumberSent) > 0)
            {
                highestSequenceNumberSent = newSequenceNumber;
            }

            return newSequenceNumber;
        }
        else
        {
            // update the sequence number delta (if needed)
            if (highestSequenceNumberSent != -1)
            {
                final int newDelta = RTPUtils.subtractNumber(
                    sequenceNumber, highestSequenceNumberSent);

                if (RTPUtils.sequenceNumberDiff(newDelta, seqnumDelta) > 0)
                {
                    seqnumDelta = newDelta;
                }
            }

            return sequenceNumber;
        }
    }

    /**
     * Rewrites the timestamp passed as a parameter, hiding any gaps caused by
     * drops.
     *
     * @param accept true if the packet is accepted, false otherwise
     * @param timestamp the timestamp to rewrite
     * @return a rewritten timestamp that hides any gaps caused by drops.
     */
    private long rewriteTimestamp(boolean accept, long timestamp)
    {
        if (accept)
        {
            // overwrite the timestamp (if needed)
            long newTimestamp = (timestamp - timestampDelta) & 0xffffffffL;

            // init or update the highest sent timestamp (if needed)
            if (highestTimestampSent == -1 ||
                TimeUtils.rtpDiff(newTimestamp, highestTimestampSent) > 0)
            {
                highestTimestampSent = newTimestamp;
            }

            return newTimestamp;
        }
        else
        {
            // update the timestamp delta (if needed)
            if (highestTimestampSent != -1)
            {
                final long newDelta
                    = (timestamp - highestTimestampSent) & 0xffffffffL;

                if (TimeUtils.rtpDiff(newDelta, timestampDelta) > 0)
                {
                    timestampDelta = newDelta;
                }
            }

            return timestamp;
        }
    }
}
