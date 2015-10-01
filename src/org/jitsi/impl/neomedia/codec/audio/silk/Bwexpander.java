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
 * Chirp (bandwidth expand) LP AR filter
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class Bwexpander
{
    /**
     * Chirp (bandwidth expand) LP AR filter.
     * @param ar AR filter to be expanded (without leading 1).
     * @param d Length of ar.
     * @param chirp_Q16 Chirp factor (typically in the range 0 to 1).
     */
    static void SKP_Silk_bwexpander(
            short            []ar,        /* I/O  AR filter to be expanded (without leading 1)    */
            final int        d,          /* I    Length of ar                                    */
            int              chirp_Q16   /* I    Chirp factor (typically in the range 0 to 1)    */
    )
    {
        int   i;
        int chirp_minus_one_Q16;

        chirp_minus_one_Q16 = chirp_Q16 - 65536;

        /* NB: Dont use SKP_SMULWB, instead of SKP_RSHIFT_ROUND( SKP_MUL() , 16 ), below. */
        /* Bias in SKP_SMULWB can lead to unstable filters                                */
        for( i = 0; i < d - 1; i++ ) {
            ar[ i ]    = (short)SigProcFIX.SKP_RSHIFT_ROUND( ( chirp_Q16 * ar[ i ]), 16 );
            chirp_Q16 +=            SigProcFIX.SKP_RSHIFT_ROUND( ( chirp_Q16 * chirp_minus_one_Q16 ), 16 );
        }
        ar[ d - 1 ] = (short)SigProcFIX.SKP_RSHIFT_ROUND( ( chirp_Q16 * ar[ d - 1 ] ), 16 );
    }
}
