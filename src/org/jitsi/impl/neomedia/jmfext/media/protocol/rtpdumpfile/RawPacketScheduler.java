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

package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import org.jitsi.impl.neomedia.RTPPacketPredicate;
import org.jitsi.impl.neomedia.RawPacket;

/**
 * Suggests a throttle method that puts the current thread to sleep for X milis,
 * where X is such that RTP timestamps and a given clock are respected.
 *
 * @author George Politis
 */
public class RawPacketScheduler
{
    /**
     * The RTP clock rate, used to interpret the RTP timestamps read from the
     * file.
     */
    private final long clockRate;

    /**
     * Ctor.
     *
     * @param clockRate
     */
    public RawPacketScheduler(long clockRate)
    {
        this.clockRate = clockRate;
    }

    /**
     * The timestamp of the last rtp packet (the timestamp change only when
     * a marked packet has been sent).
     */
    private long lastRtpTimestamp = -1;

    /**
     * puts the current thread to sleep for X milis, where X is such that RTP
     * timestamps and a given clock are respected.
     *
     * @param rtpPacket the <tt>RTPPacket</tt> to throttle.
     */
    public void throttle(RawPacket rtpPacket)
    {
        if (!RTPPacketPredicate.INSTANCE.test(rtpPacket))
        {
            return;
        }

        if (lastRtpTimestamp == -1)
        {
            lastRtpTimestamp = rtpPacket.getTimestamp();
            return;
        }

        long previous = lastRtpTimestamp;
        lastRtpTimestamp = rtpPacket.getTimestamp();

        long rtpDiff = lastRtpTimestamp - previous;

        // rtpDiff < 0 can happen when the timestamps wrap at 2^32, or when
        // the rtpdump file loops. In the latter case, we don't want to sleep.
        //if (rtpDiff < 0)
        //    rtpDiff += 1L << 32; //rtp timestamps wrap at 2^32

        long nanos = (rtpDiff * 1000 * 1000 * 1000) / clockRate;
        if (nanos > 0)
        {
            try
            {
                Thread.sleep(
                        nanos / 1000000,
                        (int) (nanos % 1000000));
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
}
