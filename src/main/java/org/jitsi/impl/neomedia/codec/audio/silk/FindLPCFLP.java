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
 * @author Dingxin Xu
 */
public class FindLPCFLP
{
    /**
     *
     * @param NLSF NLSFs.
     * @param interpIndex NLSF interp. index for NLSF interp.
     * @param prev_NLSFq Previous NLSFs, for NLSF interpolation.
     * @param useInterpNLSFs Flag.
     * @param LPC_order LPC order.
     * @param x Input signal.
     * @param subfr_length Subframe length incl preceeding samples.
     */
    static void SKP_Silk_find_LPC_FLP(
              float                 NLSF[],             /* O    NLSFs                                   */
              int                   []interpIndex,      /* O    NLSF interp. index for NLSF interp.     */
        final float                 prev_NLSFq[],       /* I    Previous NLSFs, for NLSF interpolation  */
        final int                   useInterpNLSFs,     /* I    Flag                                    */
        final int                   LPC_order,          /* I    LPC order                               */
        final float                 x[],                /* I    Input signal                            */
        final int                   subfr_length        /* I    Subframe length incl preceeding samples */
    )
    {
        int     k;
        float[]   a = new float[ MAX_LPC_ORDER ];

        /* Used only for NLSF interpolation */
        double      res_nrg, res_nrg_2nd, res_nrg_interp;
        float   a_tmp[] = new float[ MAX_LPC_ORDER ], NLSF0[] = new float[ MAX_LPC_ORDER ];
        float   LPC_res[] = new float[ ( MAX_FRAME_LENGTH + NB_SUBFR * MAX_LPC_ORDER ) / 2 ];

        /* Default: No interpolation */
        interpIndex[0] = 4;

        /* Burg AR analysis for the full frame */
        res_nrg = BurgModifiedFLP.SKP_Silk_burg_modified_FLP( a, x, 0, subfr_length, NB_SUBFR,
                DefineFLP.FIND_LPC_COND_FAC, LPC_order );

        if( useInterpNLSFs == 1 ) {

            /* Optimal solution for last 10 ms; subtract residual energy here, as that's easier than        */
            /* adding it to the residual energy of the first 10 ms in each iteration of the search below    */
            res_nrg -= BurgModifiedFLP.SKP_Silk_burg_modified_FLP( a_tmp, x, ( NB_SUBFR / 2 ) * subfr_length,
                subfr_length, NB_SUBFR / 2, DefineFLP.FIND_LPC_COND_FAC, LPC_order );

            /* Convert to NLSFs */
            WrappersFLP.SKP_Silk_A2NLSF_FLP( NLSF, a_tmp, LPC_order );

            /* Search over interpolation indices to find the one with lowest residual energy */
            res_nrg_2nd = Float.MAX_VALUE;
            for( k = 3; k >= 0; k-- ) {
                /* Interpolate NLSFs for first half */
                WrappersFLP.SKP_Silk_interpolate_wrapper_FLP( NLSF0, prev_NLSFq, NLSF, 0.25f * k, LPC_order );

                /* Convert to LPC for residual energy evaluation */
                WrappersFLP.SKP_Silk_NLSF2A_stable_FLP( a_tmp, NLSF0, LPC_order );

                /* Calculate residual energy with LSF interpolation */
                LPCAnalysisFilterFLP.SKP_Silk_LPC_analysis_filter_FLP( LPC_res, a_tmp, x, 0, 2 * subfr_length, LPC_order );
                res_nrg_interp =
                    EnergyFLP.SKP_Silk_energy_FLP( LPC_res, LPC_order,                subfr_length - LPC_order ) +
                    EnergyFLP.SKP_Silk_energy_FLP( LPC_res, LPC_order + subfr_length, subfr_length - LPC_order );

                /* Determine whether current interpolated NLSFs are best so far */
                if( res_nrg_interp < res_nrg ) {
                    /* Interpolation has lower residual energy */
                    res_nrg = res_nrg_interp;
                    interpIndex[0] = k;
                } else if( res_nrg_interp > res_nrg_2nd ) {
                    /* No reason to continue iterating - residual energies will continue to climb */
                    break;
                }
                res_nrg_2nd = res_nrg_interp;
            }
        }

        if( interpIndex[0] == 4 ) {
            /* NLSF interpolation is currently inactive, calculate NLSFs from full frame AR coefficients */
            WrappersFLP.SKP_Silk_A2NLSF_FLP( NLSF, a, LPC_order );
        }
    }
}
