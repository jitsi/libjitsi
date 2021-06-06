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
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * First-order allpass filter with
 * transfer function:
 *
 *         A + Z^(-1)
 * H(z) = ------------
 *        1 + A*Z^(-1)
 *
 * Implemented using minimum multiplier filter design.
 *
 * Reference: http://www.univ.trieste.it/~ramponi/teaching/
 * DSP/materiale/Ch6(2).pdf
 *
 * @author Dingxin Xu
 */
public class AllpassInt
{
    /**
     * First-order allpass filter.
     * @param in Q25 input signal [len]
     * @param in_offset offset of valid data.
     * @param S Q25 state [1]
     * @param S_offset offset of valid data.
     * @param A Q15 coefficient    (0 <= A < 32768)
     * @param out Q25 output signal [len]
     * @param out_offset offset of valid data.
     * @param len Number of samples
     */
    static void SKP_Silk_allpass_int(
        final int []in,    /* I:    Q25 input signal [len]               */
        int       in_offset,
        int       []S,     /* I/O:  Q25 state [1]                         */
        int       S_offset,
        int       A,      /* I:    Q15 coefficient    (0 <= A < 32768)  */
        int       []out,   /* O:    Q25 output signal [len]              */
        int       out_offset,
        final int len     /* I:    Number of samples                    */
    )
    {
        int    Y2, X2, S0;
        int        k;

        S0 = S[ S_offset ];
        for( k = len - 1; k >= 0; k-- ) {
            Y2         = in[in_offset] - S0;
            X2         = ( Y2 >> 15 ) * A + ( ( ( Y2 & 0x00007FFF ) * A ) >> 15 );
            out[out_offset++] = S0 + X2;
            S0         = in[in_offset++] + X2;
        }
        S[ S_offset ] = S0;
    }
}
