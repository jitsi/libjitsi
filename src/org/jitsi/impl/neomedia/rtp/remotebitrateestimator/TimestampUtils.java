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

/**
 * Helper class to determine timestamp order
 * @author Ethan Lin
 */
class TimestampUtils
{
    /**
     * Calculate the subtraction result of two long input as unsigned 32bit int.
     *
     * @param t1
     * @param t2
     * @return
     */
    static long subtractAsUnsignedInt32(long t1, long t2)
    {
        return (t1 - t2) & 0xFFFFFFFFL;
    }

    /**
     * webrtc/modules/include/module_common_types.h
     *
     * @param timestamp
     * @param prevTimestamp
     * @return true if timestamp is newer
     */
    static boolean isNewerTimestamp(long timestamp, long prevTimestamp)
    {
        // Distinguish between elements that are exactly 0x80000000 apart.
        // If t1>t2 and |t1-t2| = 0x80000000: IsNewer(t1,t2)=true,
        // IsNewer(t2,t1)=false
        // rather than having IsNewer(t1,t2) = IsNewer(t2,t1) = false.
        if (subtractAsUnsignedInt32(timestamp, prevTimestamp) == 0x80000000L)
        {
            return timestamp > prevTimestamp;
        }
        return
            timestamp != prevTimestamp
                && subtractAsUnsignedInt32(timestamp, prevTimestamp) < 0x80000000L;
    }

    /**
     * webrtc/modules/include/module_common_types.h
     *
     * @param timestamp1
     * @param timestamp2
     * @return
     */
    static long latestTimestamp(long timestamp1, long timestamp2)
    {
        return
            isNewerTimestamp(timestamp1, timestamp2) ? timestamp1 : timestamp2;
    }

}
