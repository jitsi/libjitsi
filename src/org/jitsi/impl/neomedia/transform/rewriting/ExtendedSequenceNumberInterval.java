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
package org.jitsi.impl.neomedia.transform.rewriting;

/**
 * Represents a (extended) sequence number interval.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
class ExtendedSequenceNumberInterval
{
    /**
     * The SSRC that this interval pertains to.
     */
    final long ssrc;

    /**
     * The delta (:= trackSeqNum - encodingTrackSeqNum) between and the
     * minimum sequence number of this interval and the corresponding point in
     * the main track.
     */
    final int delta;

    /**
     * The minimum of this interval.
     */
    final int min;

    /**
     * The maximum of this interval.
     */
    int max;

    /**
     *
     * @param ssrc The SSRC that this interval pertains to.
     * @param delta The delta (:= trackSeqNum - encodingTrackSeqNum) between and
     * the minimum sequence number of this interval and the corresponding point
     * in the main track.
     * @param min The minimum of this interval.
     */
    public ExtendedSequenceNumberInterval(long ssrc, int delta, int min)
    {
        this.ssrc = ssrc;
        this.min = min;
        this.max = min;
        this.delta = delta;
    }

    /**
     * Translates a sequence number in this interval to a sequence
     * number in the main track.
     *
     * @param extSeqNum the extended sequence number to translate/map.
     * @return the translated/mapped extended sequence number.
     */
    public int rewrite(int extSeqNum, boolean canExtend)
    {
        if (min <= extSeqNum && (extSeqNum <= max || canExtend))
        {
            int newExtSeqNum = extSeqNum + delta;

            if (canExtend && max < extSeqNum)
            {
                max = extSeqNum;
            }

            return newExtSeqNum;
        }
        else
        {
            return -1;
        }
    }

    /**
     * Returns the length of this interval.
     *
     * @return the length of this interval.
     */
    public int length()
    {
        return max - min;
    }
}
