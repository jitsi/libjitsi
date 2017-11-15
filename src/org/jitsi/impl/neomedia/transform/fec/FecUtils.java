package org.jitsi.impl.neomedia.transform.fec;

import java.util.*;

/**
 * Created by bbaldino on 11/9/17.
 */
public class FecUtils
{
    /**
     * A <tt>Comparator</tt> implementation for RTP sequence numbers.
     * Compares <tt>a</tt> and <tt>b</tt>, taking into account the wrap at 2^16.
     *
     * IMPORTANT: This is a valid <tt>Comparator</tt> implementation only if
     * used for subsets of [0, 2^16) which don't span more than 2^15 elements.
     *
     * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000])
     * Doesn't work for: [0, 2^15] and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
     */
    public static final Comparator<? super Integer> seqNumComparator
        = new Comparator<Integer>() {
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
    };
}
