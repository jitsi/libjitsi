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
package org.jitsi.util;

/**
 * Provides utility methods for converting between different time formats.
 *
 * Two of the methods are taken from the Apache Commons Net package, and are
 * copied here to avoid pulling in the whole package as a dependency.
 *
 * @author Boris Grozev
 */
public class TimeUtils
{
    /**
     * Taken from org.apache.commons.net.ntp.TimeStamp.
     * baseline NTP time if bit-0=0 is 7-Feb-2036 @ 06:28:16 UTC
     */
    protected static final long msb0baseTime = 2085978496000L;

    /**
     * Taken from org.apache.commons.net.ntp.TimeStamp.
     *  baseline NTP time if bit-0=1 is 1-Jan-1900 @ 01:00:00 UTC
     */
    protected static final long msb1baseTime = -2208988800000L;

    /**
     * Taken from from org.apache.commons.net.ntp.TimeStamp#toNtpTime(long)
     *
     * Converts Java time to 64-bit NTP time representation.
     *
     * @param t Java time
     * @return NTP timestamp representation of Java time value.
     */
    public static long toNtpTime(long t)
    {
        boolean useBase1 = t < msb0baseTime;    // time < Feb-2036
        long baseTime;
        if (useBase1)
        {
            baseTime = t - msb1baseTime; // dates <= Feb-2036
        }
        else
        {
            // if base0 needed for dates >= Feb-2036
            baseTime = t - msb0baseTime;
        }

        long seconds = baseTime / 1000;
        long fraction = ((baseTime % 1000) * 0x100000000L) / 1000;

        if (useBase1)
        {
            seconds |= 0x80000000L; // set high-order bit if msb1baseTime 1900 used
        }

        long time = seconds << 32 | fraction;
        return time;
    }

    /**
     * Taken from from org.apache.commons.net.ntp.TimeStamp#toNtpTime(long)
     * Convert 64-bit NTP timestamp to Java standard time.
     *
     * Note that java time (milliseconds) by definition has less precision
     * then NTP time (picoseconds) so converting NTP timestamp to java time and back
     * to NTP timestamp loses precision. For example, Tue, Dec 17 2002 09:07:24.810 EST
     * is represented by a single Java-based time value of f22cd1fc8a, but its
     * NTP equivalent are all values ranging from c1a9ae1c.cf5c28f5 to c1a9ae1c.cf9db22c.
     *
     * @param ntpTimeValue the input time
     * @return the number of milliseconds since January 1, 1970, 00:00:00 GMT
     * represented by this NTP timestamp value.
     */
    public static long getTime(long ntpTimeValue)
    {
        long seconds = (ntpTimeValue >>> 32) & 0xffffffffL;     // high-order 32-bits
        long fraction = ntpTimeValue & 0xffffffffL;             // low-order 32-bits

        // Use round-off on fractional part to preserve going to lower precision
        fraction = Math.round(1000D * fraction / 0x100000000L);

        /*
         * If the most significant bit (MSB) on the seconds field is set we use
         * a different time base. The following text is a quote from RFC-2030
         * (SNTP v4):
         *
         *  If bit 0 is set, the UTC time is in the range 1968-2036 and UTC time
         *  is reckoned from 0h 0m 0s UTC on 1 January 1900. If bit 0 is not
         * set, the time is in the range 2036-2104 and UTC time is reckoned
         * from 6h 28m 16s UTC on 7 February 2036.
         */
        long msb = seconds & 0x80000000L;
        if (msb == 0)
        {
            // use base: 7-Feb-2036 @ 06:28:16 UTC
            return msb0baseTime + (seconds * 1000) + fraction;
        }
        else
        {
            // use base: 1-Jan-1900 @ 01:00:00 UTC
            return msb1baseTime + (seconds * 1000) + fraction;
        }
    }

    /**
     * Converts the given timestamp in NTP Timestamp Format into NTP Short
     * Format (see {@link "http://tools.ietf.org/html/rfc5905#section-6"}).
     *
     * @param ntpTime the timestamp to convert.
     * @return the NTP Short Format timestamp, represented as a long.
     */
    public static long toNtpShortFormat(long ntpTime)
    {
        return (ntpTime & 0x0000FFFFFFFF0000L) >>> 16;
    }

    /**
     * Converts a timestamp in NTP Short Format (Q16.16, see
     * {@link "http://tools.ietf.org/html/rfc5905#section-6"}) into
     * milliseconds.
     * @param ntpShortTime the timestamp in NTP Short Format to convert.
     * @return the number of milliseconds.
     */
    public static long ntpShortToMs(long ntpShortTime)
    {
        return (ntpShortTime * 1000L) >>> 16;
    }

    /**
     * Constructs a {@code long} representation of a timestamp in NTP Timestamp
     * Format (see {@link "http://tools.ietf.org/html/rfc5905#section-6"}).
     *
     * @param msw The most significant word (32bits) represented as a long.
     * @param lsw The least significant word (32bits) represented as a long.
     * @return the NTP timestamp constructed from {@code msw} and {@code lsw}.
     */
    public static long constuctNtp(long msw, long lsw)
    {
        return (msw << 32) | (lsw & 0xFFFFFFFFL);
    }

    /**
     * Gets the most significant word (32bits) from an NTP Timestamp represented
     * as a long.
     * @param ntpTime the timestamp in NTP Timestamp Format.
     * @return the MSW of {@code ntpTime}.
     */
    public static long getMsw(long ntpTime)
    {
        return (ntpTime >>> 32) & 0xFFFFFFFFL;
    }

    /**
     * Gets the least significant word (32bits) from an NTP Timestamp
     * represented as a long.
     *
     * @param ntpTime the timestamp in NTP Timestamp Format.
     * @return the LSW of {@code ntpTime}.
     */
    public static long getLsw(long ntpTime)
    {
        return ntpTime & 0xFFFFFFFFL;
    }

    /**
     * Returns the difference between two RTP timestamps.
     * @return the difference between two RTP timestamps.
     * @deprecated use {@link RTPUtils#rtpTimestampDiff(long, long)}
     */
    @Deprecated
    public static long rtpDiff(long a, long b)
    {
        return RTPUtils.rtpTimestampDiff(a, b);
    }
}
