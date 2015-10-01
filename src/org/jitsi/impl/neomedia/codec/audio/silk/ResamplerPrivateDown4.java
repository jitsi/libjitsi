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
 * Downsample by a factor 4.
 * Note: very low quality, only use with input sampling rates above 96 kHz.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class ResamplerPrivateDown4
{
    /**
     * Downsample by a factor 4.
     * Note: very low quality, only use with input sampling rates above 96 kHz.
     * @param S State vector [ 2 ].
     * @param S_offset offset of valid data.
     * @param out Output signal [ floor(len/2) ].
     * @param out_offset offset of valid data.
     * @param in Input signal [ len ].
     * @param in_offset offset of valid data.
     * @param inLen Number of input samples.
     */
    static void SKP_Silk_resampler_private_down4(
        int[]                        S,             /* I/O: State vector [ 2 ]                      */
        int S_offset,
        short[]                      out,           /* O:   Output signal [ floor(len/2) ]          */
        int out_offset,
        short[]                      in,            /* I:   Input signal [ len ]                    */
        int in_offset,
        int                          inLen           /* I:   Number of input samples                 */
    )
    {
        int k, len4 = inLen >> 2;
        int in32, out32, Y, X;

        assert( ResamplerRom.SKP_Silk_resampler_down2_0 > 0 );
        assert( ResamplerRom.SKP_Silk_resampler_down2_1 < 0 );

        /* Internal variables and state are in Q10 format */
        for( k = 0; k < len4; k++ )
        {
            /* Add two input samples and convert to Q10 */
            in32 = ( in[ in_offset + 4 * k ] + in[ in_offset + 4 * k + 1 ] ) << 9 ;

            /* All-pass section for even input sample */
            Y      = in32 - S[ S_offset ];
            X      = SKP_SMLAWB( Y, Y, ResamplerRom.SKP_Silk_resampler_down2_1 );
            out32  = S[ S_offset ] + X;
            S[ S_offset ] = in32 + X;

            /* Add two input samples and convert to Q10 */
            in32 = ( in[ in_offset + 4 * k + 2 ] + in[ in_offset + 4 * k + 3 ] ) << 9;

            /* All-pass section for odd input sample */
            Y      = in32 - S[ S_offset+1 ];
            X      = SKP_SMULWB( Y, ResamplerRom.SKP_Silk_resampler_down2_0 );
            out32  = out32 + S[ S_offset+1 ];
            out32  = out32 + X;
            S[ S_offset+1 ] = in32 + X;

            /* Add, convert back to int16 and store to output */
            out[ out_offset+k ] = (short)SigProcFIX.SKP_SAT16( SigProcFIX.SKP_RSHIFT_ROUND( out32, 11 ) );
        }
    }
}
