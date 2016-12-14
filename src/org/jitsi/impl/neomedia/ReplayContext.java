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
package org.jitsi.impl.neomedia;

/**
 * @author George Politis
 */
public class ReplayContext
{
    /**
     * The replay check windows size.
     */
    private static final long REPLAY_WINDOW_SIZE = 64;

    /**
     * Bit mask for replay check
     */
    private long replayWindow = 0;

    /**
     * RFC 3711: a 32-bit unsigned rollover counter (ROC), which records how
     * many times the 16-bit RTP sequence number has been reset to zero after
     * passing through 65,535.  Unlike the sequence number (SEQ), which SRTP
     * extracts from the RTP packet header, the ROC is maintained by SRTP as
     * described in Section 3.3.1.
     */
    private int roc = 0;

    /**
     * RFC 3711: for the receiver only, a 16-bit sequence number <tt>s_l</tt>,
     * which can be thought of as the highest received RTP sequence number (see
     * Section 3.3.1 for its handling), which SHOULD be authenticated since
     * message authentication is RECOMMENDED.
     */
    private int s_l = -1;

    /**
     * Returns a boolean that indicates whether or not a packet has already been
     * seen.
     *
     * @param pkt the {@code RawPacket} to determine whether or not it has
     * already been seen.
     * @return true if the packet has been seen, false otherwise.
     */
    public boolean isSeen(RawPacket pkt)
    {
        // Guess the RTP packet index.
        int seq = pkt.getSequenceNumber();

        // Initialize.
        if (s_l == -1)
        {
            s_l = seq;
        }

        // Guess the RTP packet index.
        int v;
        if (seq - s_l < -0x8000)
        {
            v = roc + 1;
        }
        else if (seq - s_l > 0x8000)
        {
            v = roc - 1;
        }
        else
        {
            v = roc;
        }

        long guessedIdx = (((long) v) << 16) | seq;

        // Determine whether the packet has already been received or not.
        long localIdx = (((long) roc) << 16) | s_l;
        long delta = guessedIdx - localIdx;

        boolean seen;
        if (delta > 0)
        {
            seen = false; // Packet not received yet.
        }
        else if (-delta > REPLAY_WINDOW_SIZE)
        {
            seen = true; // Packet too old.
        }
        else if (((replayWindow >> (-delta)) & 1) != 0)
        {
            seen = true; // Packet received already!
        }
        else
        {
            seen = false; // Packet not received yet.
        }

        // Update the replay bit mask.
        if (delta > 0)
        {
            replayWindow <<= delta;
            replayWindow |= 1;
        }
        else
        {
            replayWindow |= (1 << -delta);
        }

        if (v == roc)
        {
            if (seq > s_l)
                s_l = seq & 0xffff;
        }
        else if (v == (roc + 1))
        {
            s_l = seq & 0xffff;
            roc = v;
        }

        return seen;
    }
}
