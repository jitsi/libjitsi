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

import static org.jitsi.impl.neomedia.codec.audio.silk.Macros.*;

/**
 * Second order ARMA filter
 * Can handle slowly varying filter coefficients
 *
 * @author Jing Dai
 */
public class Biquad
{
    /**
     * Second order ARMA filter
     * Can handle slowly varying filter coefficients
     * @param in input signal
     * @param in_offset offset of valid data.
     * @param B MA coefficients, Q13 [3]
     * @param A AR coefficients, Q13 [2]
     * @param S state vector [2]
     * @param out output signal
     * @param out_offset offset of valid data.
     * @param len signal length
     */
    static void SKP_Silk_biquad(
            short      []in,        /* I:    input signal               */
            int          in_offset,
            short      []B,         /* I:    MA coefficients, Q13 [3]   */
            short      []A,         /* I:    AR coefficients, Q13 [2]   */
            int        []S,         /* I/O:  state vector [2]           */
            short      []out,       /* O:    output signal              */
            int          out_offset,
            final int    len         /* I:    signal length              */
        )
    {
        int   k, in16;
        int A0_neg, A1_neg, S0, S1, out32, tmp32;

        S0 = S[ 0 ];
        S1 = S[ 1 ];
        A0_neg = -A[ 0 ];
        A1_neg = -A[ 1 ];
        for( k = 0; k < len; k++ ) {
            /* S[ 0 ], S[ 1 ]: Q13 */
            in16  = in[ in_offset + k ];
            out32 = SKP_SMLABB( S0, in16, B[ 0 ] );

            S0 = SKP_SMLABB( S1, in16, B[ 1 ] );
            S0 += ( SKP_SMULWB( out32, A0_neg ) << 3 );


            S1 = ( SKP_SMULWB( out32, A1_neg ) << 3 );
            S1 = SKP_SMLABB( S1, in16, B[ 2 ] );
            tmp32    = SigProcFIX.SKP_RSHIFT_ROUND( out32, 13 ) + 1;
            out[ out_offset + k ] = (short)SigProcFIX.SKP_SAT16( tmp32 );
        }
        S[ 0 ] = S0;
        S[ 1 ] = S1;
    }
}
