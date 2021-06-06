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

import static org.jitsi.impl.neomedia.codec.audio.silk.Define.*;
import static org.jitsi.impl.neomedia.codec.audio.silk.Macros.*;

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class GainQuant
{

    static final int OFFSET =         ( ( MIN_QGAIN_DB * 128 ) / 6 + 16 * 128 );
    static final int SCALE_Q16 =      ( ( 65536 * ( N_LEVELS_QGAIN - 1 ) ) / ( ( ( MAX_QGAIN_DB - MIN_QGAIN_DB ) * 128 ) / 6 ) );
    static final int INV_SCALE_Q16 =   ( ( 65536 * ( ( ( MAX_QGAIN_DB - MIN_QGAIN_DB ) * 128 ) / 6 ) ) / ( N_LEVELS_QGAIN - 1 ) );

    /**
     * Gain scalar quantization with hysteresis, uniform on log scale.
     * @param ind gain indices
     * @param gain_Q16 gains (quantized out)
     * @param prev_ind last index in previous frame
     * @param conditional first gain is delta coded if 1
     */
    static void SKP_Silk_gains_quant(
            int                       ind[],        /* O    gain indices                            */
            int                       gain_Q16[],   /* I/O  gains (quantized out)                   */
            int                       []prev_ind,              /* I/O  last index in previous frame            */
            final int                 conditional             /* I    first gain is delta coded if 1          */
    )
    {
        int k;

        for( k = 0; k < NB_SUBFR; k++ ) {
            /* Add half of previous quantization error, convert to log scale, scale, floor() */
            ind[ k ] = SKP_SMULWB( SCALE_Q16, Lin2log.SKP_Silk_lin2log( gain_Q16[ k ] ) - OFFSET );

            /* Round towards previous quantized gain (hysteresis) */
            if( ind[ k ] < prev_ind[0] ) {
                ind[ k ]++;
            }

            /* Compute delta indices and limit */
            if( k == 0 && conditional == 0 ) {
                /* Full index */
                ind[ k ] = SigProcFIX.SKP_LIMIT_int( ind[ k ], 0, N_LEVELS_QGAIN - 1 );
                ind[ k ] = Math.max( ind[ k ], prev_ind[0] + MIN_DELTA_GAIN_QUANT );
                prev_ind[0] = ind[ k ];
            } else {
                /* Delta index */
                ind[ k ] = SigProcFIX.SKP_LIMIT_int( ind[ k ] - prev_ind[0], MIN_DELTA_GAIN_QUANT, MAX_DELTA_GAIN_QUANT );
                /* Accumulate deltas */
                prev_ind[0] += ind[ k ];
                /* Shift to make non-negative */
                ind[ k ] -= MIN_DELTA_GAIN_QUANT;
            }

            /* Convert to linear scale and scale */
            gain_Q16[ k ] = Log2lin.SKP_Silk_log2lin( Math.min( SKP_SMULWB( INV_SCALE_Q16, prev_ind[0] ) + OFFSET, 3967 ) ); /* 3967 = 31 in Q7 */
        }
    }

    /**
     * Gains scalar dequantization, uniform on log scale.
     * @param gain_Q16 quantized gains.
     * @param ind gain indices.
     * @param prev_ind last index in previous frame.
     * @param conditional first gain is delta coded if 1.
     */
    static void SKP_Silk_gains_dequant(
            int                         gain_Q16[ ],   /* O    quantized gains                         */
            int                         ind[  ],        /* I    gain indices                            */
            int                         []prev_ind,              /* I/O  last index in previous frame            */
            final int                   conditional             /* I    first gain is delta coded if 1          */
        )
    {
        int   k;

        for( k = 0; k < NB_SUBFR; k++ ) {
            if( k == 0 && conditional == 0 ) {
                prev_ind[0] = ind[ k ];
            } else {
                /* Delta index */
                prev_ind[0] += ind[ k ] + MIN_DELTA_GAIN_QUANT;
            }

            /* Convert to linear scale and scale */
            gain_Q16[ k ] = Log2lin.SKP_Silk_log2lin( Math.min( SKP_SMULWB( INV_SCALE_Q16, prev_ind[0] ) + OFFSET, 3967 ) ); /* 3967 = 31 in Q7 */
        }
    }
}
