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
 * Second order AR filter with single delay elements.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class ResamplerPrivateAR2
{
    /**
     * Second order AR filter with single delay elements.
     * @param S State vector [ 2 ].
     * @param S_offset offset of valid data.
     * @param out_Q8 Output signal.
     * @param out_Q8_offset offset of valid data.
     * @param in Input signal.
     * @param in_offset offset of valid data.
     * @param A_Q14 AR coefficients, Q14.
     * @param A_Q14_offset offset of valid data.
     * @param len Signal length.
     */
    static void SKP_Silk_resampler_private_AR2(
        int[]                        S,            /* I/O: State vector [ 2 ]                        */
        int S_offset,
        int[]                        out_Q8,        /* O:    Output signal                            */
        int out_Q8_offset,
        short[]                        in,            /* I:    Input signal                            */
        int in_offset,
        short[]                        A_Q14,        /* I:    AR coefficients, Q14                     */
        int A_Q14_offset,
        int                            len            /* I:    Signal length                            */
    )
    {
        int    k;
        int    out32;

        for( k = 0; k < len; k++ )
        {
            out32       = S[ S_offset ] + ( in[ in_offset+k ] << 8 );
            out_Q8[ out_Q8_offset+k ] = out32;
            out32       = out32 << 2;
            S[ S_offset   ]      = SKP_SMLAWB( S[ S_offset+1 ], out32, A_Q14[ A_Q14_offset ] );
            S[ S_offset+1 ]      = SKP_SMULWB( out32, A_Q14[ A_Q14_offset+1 ] );
        }
    }
}
