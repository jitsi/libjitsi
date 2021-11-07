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

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class FindLTPFLP
{
    /**
     *
     * @param b LTP coefs.
     * @param WLTP Weight for LTP quantization.
     * @param LTPredCodGain LTP coding gain.
     * @param r_first LPC residual, signal + state for 10 ms.
     * @param r_last LPC residual, signal + state for 10 ms.
     * @param r_last_offset offset of valid data.
     * @param lag LTP lags.
     * @param Wght Weights.
     * @param subfr_length Subframe length.
     * @param mem_offset Number of samples in LTP memory.
     */
    static void SKP_Silk_find_LTP_FLP(
        float b[],                      /* O    LTP coefs                               */
        float WLTP[],                   /* O    Weight for LTP quantization             */
        float LTPredCodGain[],          /* O    LTP coding gain                         */
        final float r_first[],          /* I    LPC residual, signal + state for 10 ms  */
        final float r_last[],           /* I    LPC residual, signal + state for 10 ms  */
        int   r_last_offset,
        final int   lag[   ],           /* I    LTP lags                                */
        final float Wght[  ],           /* I    Weights                                 */
        final int   subfr_length,       /* I    Subframe length                         */
        final int   mem_offset          /* I    Number of samples in LTP memory         */
    )
    {
        int i,k;
        float b_ptr[], temp, WLTP_ptr[];
        float LPC_res_nrg, LPC_LTP_res_nrg;
        float d[] = new float[NB_SUBFR], m, g, delta_b[]=new float[LTP_ORDER];
        float w[] = new float[NB_SUBFR], nrg[] = new float[NB_SUBFR], regu;
        float Rr[] = new float[LTP_ORDER], rr[] = new float[NB_SUBFR];
        float r_ptr[], lag_ptr[];
        int r_ptr_offset, lag_ptr_offset;

        b_ptr    = b;
        int b_ptr_offset = 0;
        WLTP_ptr = WLTP;
        int WLTP_ptr_offset = 0;
        r_ptr    = r_first;
        r_ptr_offset = mem_offset;

        for( k = 0; k < NB_SUBFR; k++ ) {
            if( k == ( NB_SUBFR >> 1 ) ) { /* Shift residual for last 10 ms */
                r_ptr = r_last;
                r_ptr_offset = r_last_offset + mem_offset;
            }
            lag_ptr = r_ptr;
            lag_ptr_offset = r_ptr_offset - (lag[k] + LTP_ORDER/2);

            CorrMatrixFLP.SKP_Silk_corrMatrix_FLP(lag_ptr, lag_ptr_offset, subfr_length, LTP_ORDER, WLTP_ptr, WLTP_ptr_offset);
            CorrMatrixFLP.SKP_Silk_corrVector_FLP(lag_ptr, lag_ptr_offset, r_ptr, r_ptr_offset, subfr_length, LTP_ORDER, Rr);


            rr[k] = (float) EnergyFLP.SKP_Silk_energy_FLP(r_ptr, r_ptr_offset, subfr_length);

            regu = DefineFLP.LTP_DAMPING * ( rr[ k ] + 1.0f );

            RegularizeCorrelationsFLP.SKP_Silk_regularize_correlations_FLP(WLTP_ptr, WLTP_ptr_offset, rr, k, regu, LTP_ORDER);
            SolveLSFLP.SKP_Silk_solve_LDL_FLP( WLTP_ptr, WLTP_ptr_offset, LTP_ORDER, Rr, b_ptr, b_ptr_offset );

            /* Calculate residual energy */
            nrg[ k ] = ResidualEnergyFLP.SKP_Silk_residual_energy_covar_FLP( b_ptr, b_ptr_offset,
                WLTP_ptr, WLTP_ptr_offset, Rr, rr[ k ], LTP_ORDER );

            temp = Wght[ k ] / ( nrg[ k ] * Wght[ k ] + 0.01f * subfr_length );
            ScaleVectorFLP.SKP_Silk_scale_vector_FLP( WLTP_ptr, WLTP_ptr_offset, temp, LTP_ORDER * LTP_ORDER );
//            w[ k ] = matrix_ptr( WLTP_ptr, LTP_ORDER / 2, LTP_ORDER / 2, LTP_ORDER );
            w[k] = WLTP_ptr[WLTP_ptr_offset + ((LTP_ORDER/2) * LTP_ORDER + LTP_ORDER/2)];

            r_ptr_offset    += subfr_length;
            b_ptr_offset    += LTP_ORDER;
            WLTP_ptr_offset += LTP_ORDER * LTP_ORDER;
        }

        /* Compute LTP coding gain */
        if( LTPredCodGain != null ) {
            LPC_LTP_res_nrg = 1e-6f;
            LPC_res_nrg     = 0.0f;
            for( k = 0; k < NB_SUBFR; k++ ) {
                LPC_res_nrg     += rr[  k ] * Wght[ k ];
                LPC_LTP_res_nrg += nrg[ k ] * Wght[ k ];
            }

            assert( LPC_LTP_res_nrg > 0 );
            LTPredCodGain[0] = 3.0f * MainFLP.SKP_Silk_log2( LPC_res_nrg / LPC_LTP_res_nrg );
        }

        /* Smoothing */
        /* d = sum( B, 1 ); */
        b_ptr = b;
        b_ptr_offset = 0;
        for( k = 0; k < NB_SUBFR; k++ ) {
            d[ k ] = 0;
            for( i = 0; i < LTP_ORDER; i++ ) {
                d[ k ] += b_ptr[ b_ptr_offset + i ];
            }
            b_ptr_offset += LTP_ORDER;
        }
        /* m = ( w * d' ) / ( sum( w ) + 1e-3 ); */
        temp = 1e-3f;
        for( k = 0; k < NB_SUBFR; k++ ) {
            temp += w[ k ];
        }
        m = 0;
        for( k = 0; k < NB_SUBFR; k++ ) {
            m += d[ k ] * w[ k ];
        }
        m = m / temp;

        b_ptr = b;
        b_ptr_offset = 0;
        for( k = 0; k < NB_SUBFR; k++ ) {
            g = DefineFLP.LTP_SMOOTHING / ( DefineFLP.LTP_SMOOTHING + w[ k ] ) * ( m - d[ k ] );
            temp = 0;
            for( i = 0; i < LTP_ORDER; i++ ) {
                delta_b[ i ] = Math.max( b_ptr[ i ], 0.1f );
                temp += delta_b[ i ];
            }
            temp = g / temp;
            for( i = 0; i < LTP_ORDER; i++ ) {
                b_ptr[ i ] = b_ptr[ i ] + delta_b[ i ] * temp;
            }
            b_ptr_offset += LTP_ORDER;
        }
    }
}
