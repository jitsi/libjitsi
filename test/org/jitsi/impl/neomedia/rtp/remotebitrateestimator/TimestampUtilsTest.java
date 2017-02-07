/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

import org.junit.*;

import static org.jitsi.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateEstimatorSingleStream.k24BitTimestampToMs;
import static org.junit.Assert.*;

/**
 * @author Ethan Lin
 */
public class TimestampUtilsTest
{
    private final static long kBoundary = 0x80000000L;

    private final double timestampToMsCoeff = k24BitTimestampToMs;
    private final int kBurstDeltaThresholdMs = 5;


    @Test
    public void testTimestampIsNewer()
        throws Exception
    {
        assertEquals(true, TimestampUtils.isNewerTimestamp(1L, 0L));
        assertEquals(true, TimestampUtils.isNewerTimestamp(0L, kBoundary + 1L));
        assertEquals(true, TimestampUtils.isNewerTimestamp(kBoundary, 0L));
    }

    @Test
    public void testTimestampIsOlder()
        throws Exception
    {
        assertEquals(false, TimestampUtils.isNewerTimestamp(0L, 1L));
        assertEquals(false, TimestampUtils.isNewerTimestamp(0L, kBoundary));
    }

    @Test
    public void returnLatestTimestamp()
        throws Exception
    {
        assertEquals(1L, TimestampUtils.latestTimestamp(1L, 0L));
        assertEquals(0L, TimestampUtils.latestTimestamp(0L, kBoundary + 1));
        assertEquals(kBoundary, TimestampUtils.latestTimestamp(1L, kBoundary));
    }

    @Test
    public void testPacketBelongsToBurst()
        throws Exception
    {
        assertEquals(
            false,
            TimestampUtils.belongsToBurst(2L, 0x28F5CL, 0L, 0L, timestampToMsCoeff, kBurstDeltaThresholdMs));
        // arrivalTimeDeltaMs = 2 - 0 = 2
        // tsDeltaMs = (long)((0x28F5C - 0) * k24BitTimestampToMs + 0.5) = 2ms
        // propagationDeltaMs = arrivalTimeDeltaMs - tsDeltaMs = 0, not belong to burst

        assertEquals(
            false,
            TimestampUtils.belongsToBurst(100L, 0x100000L, 0L, 0L, timestampToMsCoeff, kBurstDeltaThresholdMs));
        // arrivalTimeDeltaMs = 100 - 0 = 100ms > 5ms, not belong to burst

        assertEquals(
            true,
            TimestampUtils.belongsToBurst(2L, 0x28F5DL, 0L, 0L, timestampToMsCoeff, kBurstDeltaThresholdMs));
        // arrivalTimeDeltaMs = 3 - 0 = 3
        // tsDeltaMs = (long)(0x28F5C - 0) * k24BitTimestampToMs + 0.5 > 2ms
        // propagationDeltaMs = arrivalTimeDeltaMs - tsDeltaMs = 1 > 0, belong to burst
    }

    @Test
    public void test24BitTimestampToMsConversion()
        throws Exception
    {
        // according to https://webrtc.org/experiments/rtp-hdrext/abs-send-time,
        // the 24 bit timestamp has resolution of 64seconds
        long timestamp = 0xFFFFFF00L;
        assertEquals(true, Math.abs(timestamp * timestampToMsCoeff - 64000) < 0.01);
    }
}
