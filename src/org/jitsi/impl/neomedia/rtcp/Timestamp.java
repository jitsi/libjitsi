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
package org.jitsi.impl.neomedia.rtcp;

/**
 * Represents a timestamp in multiple formats such as RTP timestamp, system time
 * in the context of an RTP stream.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class Timestamp
{
    /**
     * The RTP timestamp associated with {@link #systemTimeMs}.
     */
    private final int rtpTimestamp;

    /**
     * The system time corresponding to {@link #rtpTimestamp} expressed in
     * milliseconds since the epoch.
     */
    private final long systemTimeMs;

    /**
     * Initializes a new {@code Timestamp} instance from a specific system time
     * in milliseconds since the epoch and a corresponding RTP timestamp.
     *
     * @param systemTimeMs the system time corresponding to {@code rtpTimestamp}
     * expressed in milliseconds since the epoch
     * @param rtpTimestamp the RTP timestamp associated with
     * {@code systemTimeMs}
     */
    public Timestamp(long systemTimeMs, int rtpTimestamp)
    {
        this.systemTimeMs = systemTimeMs;
        this.rtpTimestamp = rtpTimestamp;
    }

    /**
     * Gets the RTP timestamp associated with {@link #systemTimeMs}.
     *
     * @return the RTP timestamp associated with {@code systemTimeMs}
     */
    public int getRtpTimestamp()
    {
        return rtpTimestamp;
    }

    /**
     * Gets the RTP timestamp associated with {@link #systemTimeMs} as a
     * {@code long} value.
     *
     * @returnthe RTP timestamp associated with {@code systemTimeMs} as a
     * {@code long} value
     */
    public long getRtpTimestampAsLong()
    {
        return getRtpTimestamp() & 0xffffffffL;
    }

    /**
     * Gets the system time corresponding to {@link #rtpTimestamp} expressed in
     * milliseconds since epoch.
     *
     * @return the system time corresponding to {@code rtpTimestamp} expressed
     * in milliseconds since epoch
     */
    public long getSystemTimeMs()
    {
        return systemTimeMs;
    }
}
