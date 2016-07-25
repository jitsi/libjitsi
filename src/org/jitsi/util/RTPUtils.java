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
 * RTP-related static utility methods.
 * @author Boris Grozev
 */
public class RTPUtils
{
    /**
     * Returns the difference between two RTP sequence numbers (modulo 2^16).
     * @return the difference between two RTP sequence numbers (modulo 2^16).
     */
    public static int sequenceNumberDiff(int a, int b)
    {
        int diff = a - b;

        if (diff < -(1<<15))
            diff += 1<<16;
        else if (diff > 1<<15)
            diff -= 1<<16;

        return diff;
    }

    /**
     * Returns result of the subtraction of one RTP sequence number from another
     * (modulo 2^16).
     * @return result of the subtraction of one RTP sequence number from another
     * (modulo 2^16).
     */
    public static int subtractNumber(int a, int b)
    {
        return (a - b) & 0xFFFF;
    }
}
