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
 * compute number of bits to right shift the sum of squares of a vector
 * of int16s to make it fit in an int32
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class SumSqrShift
{
    /**
     * Compute number of bits to right shift the sum of squares of a vector
     * of int16s to make it fit in an int32.
     *
     * @param energy Energy of x, after shifting to the right.
     * @param shift Number of bits right shift applied to energy.
     * @param x Input vector.
     * @param x_offset offset of valid data.
     * @param len Length of input vector.
     */
    static void SKP_Silk_sum_sqr_shift(
            int[]     energy,            /* O    Energy of x, after shifting to the right            */
            int[]     shift,             /* O    Number of bits right shift applied to energy        */
            short[]   x,                 /* I    Input vector                                        */
            int       x_offset,
            int       len                /* I    Length of input vector                              */
        )
    {
        int   i, shft;
        int   in32, nrg_tmp, nrg;

        if( len % 2 != 0 ) {
            /* Input is not 4-byte aligned */
            nrg = SKP_SMULBB( x[ x_offset ], x[ x_offset ] );
            i = 1;
        } else {
            nrg = 0;
            i   = 0;
        }
        shft = 0;
        len--;
        while( i < len ) {
            /* Load two values at once */
            in32 = ((x[ x_offset + i ] & 0xFFFF) << 16) | (x[ x_offset + i + 1 ] & 0xFFFF);
            nrg = SigProcFIX.SKP_SMLABB_ovflw( nrg, in32, in32 );
            nrg = SigProcFIX.SKP_SMLATT_ovflw( nrg, in32, in32 );
            i += 2;
            if( nrg < 0 ) {
                /* Scale down */
                nrg = ( nrg >>> 2 );
                shft = 2;
                break;
            }
        }
        for( ; i < len; i += 2 ) {
            /* Load two values at once */
            in32 = ((x[ x_offset + i ] & 0xFFFF) << 16) | (x[ x_offset + i + 1 ] & 0xFFFF);	
            nrg_tmp = SKP_SMULBB( in32, in32 );
            nrg_tmp = SigProcFIX.SKP_SMLATT_ovflw( nrg_tmp, in32, in32 );
            nrg = ( nrg + (nrg_tmp >>> shft) );
            if( nrg < 0 ) {
                /* Scale down */
                nrg = ( nrg >>> 2 );
                shft += 2;
            }
        }
        if( i == len ) {
            /* One sample left to process */
            nrg_tmp = SKP_SMULBB( x[ x_offset + i ], x[ x_offset + i ] );
            nrg = ( nrg + (nrg_tmp >>> shft) );
        }

        /* Make sure to have at least one extra leading zero (two leading zeros in total) */
        if( (nrg & 0xC0000000) != 0 ) {
            nrg = ( nrg >>> 2 );
            shft += 2;
        }

        /* Output arguments */
        shift[0]  = shft;
        energy[0] = nrg;
    }
}
