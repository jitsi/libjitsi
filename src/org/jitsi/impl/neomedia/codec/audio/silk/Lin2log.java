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
 * Convert input to a log scale
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class Lin2log
{
    /**
     * Approximation of 128 * log2() (very close inverse of approx 2^() below)
     * Convert input to a log scale.
     *
     * @param inLin Input in linear scale
     * @return
     */
    static int SKP_Silk_lin2log( final int inLin )    /* I:    Input in linear scale */
    {
        int lz, frac_Q7;

        int[] lz_ptr = new int[1];
        int[] frac_Q7_ptr = new int[1];

        Inlines.SKP_Silk_CLZ_FRAC( inLin, lz_ptr, frac_Q7_ptr );
        lz = lz_ptr[0];
        frac_Q7 = frac_Q7_ptr[0];

        /* Piece-wise parabolic approximation */
        return( SigProcFIX.SKP_LSHIFT( 31 - lz, 7 ) + SKP_SMLAWB( frac_Q7, SigProcFIX.SKP_MUL( frac_Q7, 128 - frac_Q7 ), 179 ) );
    }
}
