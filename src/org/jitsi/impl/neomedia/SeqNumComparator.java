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

import java.util.*;

/**
 * A <tt>Comparator</tt> implementation for RTP sequence numbers.
 * Compares <tt>a</tt> and <tt>b</tt>, taking into account the wrap at 2^16.
 *
 * IMPORTANT: This is a valid <tt>Comparator</tt> implementation only if
 * used for subsets of [0, 2^16) which don't span more than 2^15 elements.
 *
 * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000])
 * Doesn't work for: [0, 2^15] and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
 *
 * @author Boris Grozev
 * @author George Politis
 */
public class SeqNumComparator implements Comparator<Integer>
{
    @Override
    public int compare(Integer a, Integer b)
    {
        if (a.equals(b))
            return 0;
        else if (a > b)
        {
            if (a - b < 32768)
                return 1;
            else
                return -1;
        }
        else //a < b
        {
            if (b - a < 32768)
                return -1;
            else
                return 1;
        }
    }
}
