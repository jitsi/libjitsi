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
 * First order low-pass filter, with input as SKP_int16, running at
 * 48 kHz
 *
 * @author Dingxin Xu
 */
public class LowpassShort
{
    /**
     * First order low-pass filter, with input as SKP_int16, running at 48 kHz.
     * @param in Q15 48 kHz signal; [len]
     * @param in_offset offset of valid data.
     * @param S Q25 state; length = 1
     * @param S_offset offset of valid data.
     * @param out Q25 48 kHz signal; [len]
     * @param out_offset offset of valid data.
     * @param len Signal length
     */
    static void SKP_Silk_lowpass_short(
        final short []in,        /* I:   Q15 48 kHz signal; [len]    */
        int         in_offset,
        int         []S,         /* I/O: Q25 state; length = 1       */
        int         S_offset,
        int         []out,       /* O:   Q25 48 kHz signal; [len]    */
        int         out_offset,
        final int   len         /* O:   Signal length               */
    )
    {
        int        k;
        int    in_tmp, out_tmp, state;

        state = S[ S_offset + 0 ];
        for( k = 0; k < len; k++ ) {
            in_tmp   = ( 768 * in[in_offset + k] );    /* multiply by 0.75, going from Q15 to Q25 */
            out_tmp  = state + in_tmp;                      /* zero at nyquist                         */
            state    = in_tmp - ( out_tmp >> 1 );   /* pole                                    */
            out[ out_offset + k ] = out_tmp;
        }
        S[ S_offset + 0 ] = state;
    }
}
