package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

class TimestampUtils
{
    static long subtractAsUnsignedInt32(long t1, long t2)
    {
        return (t1 - t2) & 0xFFFFFFFFL;
    }

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
