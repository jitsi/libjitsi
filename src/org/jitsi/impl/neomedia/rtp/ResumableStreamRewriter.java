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

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
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
    long timestampDelta = 0;

    /**
     * The highest sequence number that got accepted, mod 2^16.
     */
    int highestSequenceNumberSent = -1;

    /**
     * The highest timestamp that got accepted, mod 2^32.
     */
    long highestTimestampSent = -1;

    /**
     * Keeps the 16 most significant bits of the extended sequence numbers. It
     * increases 0x10000 every time a wrap-around is detected in the sequence
     * numbers. This can be used to extend a given sequence number to 32 bits.
     */
    private int cycles = 0;

    /**
     * Ctor.
     */
    public ResumableStreamRewriter()
    {
        this(-1, 0, -1, 0);
    }

    /**
     * Ctor.
     *
     * @param highestSequenceNumberSent the highest sequence number that got
     * accepted, mod 2^16.
     * @param seqnumDelta the seqnumDelta between what's been accepted and
     * what's been received, mod 2^16.
     * @param highestTimestampSent The highest timestamp that got accepted,
     * mod 2^32.
     * @param timestampDelta The timestamp delta between what's been accepted
     * and what's been received, mod 2^32.
     */
    public ResumableStreamRewriter(
        int highestSequenceNumberSent, int seqnumDelta,
        long highestTimestampSent, long timestampDelta)
    {
        this.seqnumDelta = seqnumDelta;
        this.highestSequenceNumberSent = highestSequenceNumberSent;
        this.highestTimestampSent = highestTimestampSent;
        this.timestampDelta = timestampDelta;
    }

    /**
     * Sets the highest sequence number that got accepted, mod 2^16.
     *
     * @param highestSequenceNumberSent the highest sequence number that got
     * accepted, mod 2^16.
     */
    public void setHighestSequenceNumberSent(int highestSequenceNumberSent)
    {
        this.highestSequenceNumberSent = highestSequenceNumberSent;
    }

    /**
     * Sets the seqnumDelta between what's been accepted and
     * what's been received, mod 2^16.
     *
     * @param val the seqnumDelta between what's been accepted and
     * what's been received, mod 2^16.
     */
    public void setSeqnumDelta(int val)
    {
        this.seqnumDelta = val;
    }

    /**
     * Sets the highest timestamp that got accepted, mod 2^32.
     *
     * @param val The highest timestamp that got accepted, mod 2^32.
     */
    public void setHighestTimestampSent(long val)
    {
        this.highestTimestampSent = val;
    }

    /**
     * Sets the timestamp delta between what's been accepted and what's been
     * received, mod 2^32.
     *
     * @param val the timestamp delta between what's been accepted and what's
     * been received, mod 2^32.
     */
    public void setTimestampDelta(long val)
    {
        this.timestampDelta = val;
    }

    /**
     * Gets the highest timestamp that got accepted, mod 2^32.
     *
     * @return the highest timestamp that got accepted, mod 2^32.
     */
    public long getHighestTimestampSent()
    {
        return highestTimestampSent;
    }

    /**
     * Gets the timestamp delta between what's been accepted and what's been
     * received, mod 2^32.
     *
     * @return the timestamp delta between what's been accepted and what's been
     * received, mod 2^32.
     */
    public long getTimestampDelta()
    {
        return timestampDelta;
    }

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
     * Restores the RTP timestamp and sequence number of the RTP packet in the
     * buffer.
     *
     * @param buf the byte buffer that contains the RTP packet.
     * @param off the offset in the byte buffer where the RTP packet starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return true if the RTP packet is modified, false otherwise.
     */
    public boolean restoreRTP(byte[] buf, int off, int len)
    {
        boolean modified = false;

        if (timestampDelta != 0)
        {
            long ts = RawPacket.getTimestamp(buf, off, len);
            RawPacket.setTimestamp(
                buf, off, len, (ts + timestampDelta) & 0xffffffffL);

            modified = true;
        }

        if (seqnumDelta != 0)
        {
            int sn = RawPacket.getSequenceNumber(buf, off, len);
            RawPacket.setSequenceNumber(
                buf, off, (sn + seqnumDelta) & 0xffff);

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

        long ts = RTCPSenderInfoUtils.getTimestamp(
            buf, off + RTCPHeader.SIZE, len - RTCPHeader.SIZE);

        if (ts == -1)
        {
            return false;
        }

        long newTs = rewrite
            ? (ts - timestampDelta) & 0xffffffffL
            : (ts + timestampDelta) & 0xffffffffL;

        boolean ret = RTCPSenderInfoUtils.setTimestamp(
            buf, off + RTCPHeader.SIZE, len - RTCPHeader.SIZE, newTs);

        return ret;
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
                if (highestSequenceNumberSent != -1
                    && newSequenceNumber - highestSequenceNumberSent < 0)
                {
                    cycles += 0x10000;
                }

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
    long rewriteTimestamp(boolean accept, long timestamp)
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

    public int getSeqnumDelta()
    {
        return seqnumDelta;
    }

    public int getHighestSequenceNumberSent()
    {
        return highestSequenceNumberSent;
    }

    public int extendSequenceNumber(int seqnum) {
        int cycles = this.cycles;
        int maxseq = this.highestSequenceNumberSent;
        int delta = seqnum - maxseq;

        if (delta >= 0)
        {
            if (delta > 0x7FFF /* 2^15 - 1 =  32767 */)
            {
                cycles -= 0x10000 /* 2^16 = 65536 */;
            }
        }
        else if (delta < -0x7FFF)
        {
            cycles += 0x10000;
        }

        return seqnum + cycles;
    }
}
