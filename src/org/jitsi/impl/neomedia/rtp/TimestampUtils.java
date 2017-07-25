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
package org.jitsi.impl.neomedia.rtp;

import org.jitsi.util.RTPUtils;

/**
 * Helper class to perform various timestamp manipulations and comparisons
 * @author Ethan Lin
 * //FIXME(brian): perhaps this should be moved alongside RTPUtils (or maybe its
 * contents combined with RTPUtils?  This class deals with RTP timestamps)
 */
public class TimestampUtils
{
    // RTP timestamp is 32 bits, this represents the maximum value of an RTP timestamp
    public static final long MAX_TIMESTAMP_VALUE = Long.MAX_VALUE & 0xFFFFFFFFL;
    // (Roughly) half of the MAX_TIMESTAMP_VALUE.  Used when we're trying to compare
    // two timestamps to determine which came 'first'.
    public static final long ROLLOVER_DELTA_VALUE = 0x80000000L;
    /**
     * Calculate the subtraction result of two long input as unsigned 32bit int.
     *
     * @param t1 the first timestamp
     * @param t2 the second timestamp
     * @return
     */
    public static long subtractAsUnsignedInt32(long t1, long t2)
    {
        return RTPUtils.as32Bits(t1 - t2);
    }

    /**
     * Returns true if t1 is newer than t2,
     * taking into account rollover.  This is done by effectively
     * checking if the distance of going from 't2' to
     * 't1' (strictly incrementing) is shorter or if going from
     * 't1' to 't2' (i.e. rolling over) is shorter.
     * webrtc/modules/include/module_common_types.h
     *
     * @param t1
     * @param t2
     * @return true if t1 is newer
     */
    public static boolean isNewerTimestamp(long t1, long t2)
    {
        if (t1 == t2)
        {
            return false;
        }
        // Distinguish between elements that are exactly ROLLOVER_DELTA_VALUE apart.
        // If t1 > t2 and |t1-t2| == ROLLOVER_DELTA_VALUE:
        // isNewerTimestamp(t1,t2) = true,
        // isNewerTimestamp(t2,t1) = false
        // rather than having:
        // isNewerTimestamp(t1,t2) = isNewerTimestamp(t2,t1) = false.
        if (subtractAsUnsignedInt32(t1, t2) == ROLLOVER_DELTA_VALUE)
        {
            // The two timestamps are exactly ROLLOVER_DELTA_VALUE apart, so
            // we can't guess which is newer.  To break the tie, assume
            // the larger timestamp is newer.
            return t1 > t2;
        }
        return subtractAsUnsignedInt32(t1, t2) < ROLLOVER_DELTA_VALUE;
    }

    /**
     * webrtc/modules/include/module_common_types.h
     *
     * @param timestamp1
     * @param timestamp2
     * @return
     */
    public static long latestTimestamp(long timestamp1, long timestamp2)
    {
        return
            isNewerTimestamp(timestamp1, timestamp2) ? timestamp1 : timestamp2;
    }
}
