/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class NoiseShapeAnalysisFLP
{
    /**
     * Compute noise shaping coefficients and initial gain values.
     * @param psEnc Encoder state FLP
     * @param psEncCtrl Encoder control FLP
     * @param pitch_res LPC residual from pitch analysis
     * @param pitch_res_offset offset of valid data.
     * @param x Input signal [frame_length + la_shape]
     * @param x_offset offset of valid data.
     */
    static void SKP_Silk_noise_shape_analysis_FLP
    (
        SKP_Silk_encoder_state_FLP      psEnc,             /* I/O  Encoder state FLP                       */
        SKP_Silk_encoder_control_FLP    psEncCtrl,         /* I/O  Encoder control FLP                     */
        float[]                 pitch_res,         /* I    LPC residual from pitch analysis        */
        int                     pitch_res_offset,
        float[]                 x,                  /* I    Input signal [frame_length + la_shape]  */
        int                     x_offset
    )
    {
        SKP_Silk_shape_state_FLP psShapeSt = psEnc.sShape;
        int     k, nSamples;
        float   SNR_adj_dB, HarmBoost, HarmShapeGain, Tilt;
        float   nrg, pre_nrg=0, log_energy, log_energy_prev, energy_variation;
        float   delta, BWExp1, BWExp2, gain_mult, gain_add, strength, b;
        float[]   x_windowed = new float[ Define.SHAPE_LPC_WIN_MAX ];
        float[]   auto_corr = new float[ Define.SHAPE_LPC_ORDER_MAX + 1 ];
        float[] x_ptr, pitch_res_ptr;
        int x_ptr_offset, pitch_res_ptr_offset=0;

        /* Point to start of first LPC analysis block */
        x_ptr = x;
        x_ptr_offset = x_offset + psEnc.sCmn.la_shape - Define.SHAPE_LPC_WIN_MS * psEnc.sCmn.fs_kHz + psEnc.sCmn.subfr_length;

        /****************/
        /* CONTROL SNR  */
        /****************/
        /* Reduce SNR_dB values if recent bitstream has exceeded TargetRate */
        psEncCtrl.current_SNR_dB = psEnc.SNR_dB - 0.05f * psEnc.BufferedInChannel_ms;

        /* Reduce SNR_dB if inband FEC used */
        if( psEnc.speech_activity > DefineFLP.LBRR_SPEECH_ACTIVITY_THRES )
        {
            psEncCtrl.current_SNR_dB -= psEnc.inBandFEC_SNR_comp;
        }

        /****************/
        /* GAIN CONTROL */
        /****************/
        /* Input quality is the average of the quality in the lowest two VAD bands */
        psEncCtrl.input_quality = 0.5f * ( psEncCtrl.input_quality_bands[ 0 ] + psEncCtrl.input_quality_bands[ 1 ] );

        /* Coding quality level, between 0.0 and 1.0 */
        psEncCtrl.coding_quality = SigProcFLP.SKP_sigmoid( 0.25f * ( psEncCtrl.current_SNR_dB - 18.0f ) );

        /* Reduce coding SNR during low speech activity */
        b = 1.0f - psEnc.speech_activity;
        SNR_adj_dB = psEncCtrl.current_SNR_dB -
            PerceptualParametersFLP.BG_SNR_DECR_dB * psEncCtrl.coding_quality * ( 0.5f + 0.5f * psEncCtrl.input_quality ) * b * b;

        if( psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED )
        {
            /* Reduce gains for periodic signals */
            SNR_adj_dB += PerceptualParametersFLP.HARM_SNR_INCR_dB * psEnc.LTPCorr;
        }
        else
        {
            /* For unvoiced signals and low-quality input, adjust the quality slower than SNR_dB setting */
            SNR_adj_dB += ( -0.4f * psEncCtrl.current_SNR_dB + 6.0f ) * ( 1.0f - psEncCtrl.input_quality );
        }

        /*************************/
        /* SPARSENESS PROCESSING */
        /*************************/
        /* Set quantizer offset */
        if( psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED )
        {
            /* Initally set to 0; may be overruled in process_gains(..) */
            psEncCtrl.sCmn.QuantOffsetType = 0;
            psEncCtrl.sparseness = 0.0f;
        }
        else
        {
            /* Sparseness measure, based on relative fluctuations of energy per 2 milliseconds */
            nSamples = 2 * psEnc.sCmn.fs_kHz;
            energy_variation = 0.0f;
            log_energy_prev  = 0.0f;
            pitch_res_ptr = pitch_res;
            pitch_res_ptr_offset = pitch_res_offset;
            for( k = 0; k < Define.FRAME_LENGTH_MS / 2; k++ )
            {
                nrg = nSamples + ( float )EnergyFLP.SKP_Silk_energy_FLP( pitch_res_ptr,pitch_res_ptr_offset, nSamples );
                log_energy = MainFLP.SKP_Silk_log2( nrg );
                if( k > 0 )
                {
                    energy_variation += Math.abs( log_energy - log_energy_prev );
                }
                log_energy_prev = log_energy;
                pitch_res_ptr_offset += nSamples;
            }
            psEncCtrl.sparseness = SigProcFLP.SKP_sigmoid( 0.4f * ( energy_variation - 5.0f ) );

            /* Set quantization offset depending on sparseness measure */
            if( psEncCtrl.sparseness > PerceptualParametersFLP.SPARSENESS_THRESHOLD_QNT_OFFSET )
            {
                psEncCtrl.sCmn.QuantOffsetType = 0;
            }
            else
            {
                psEncCtrl.sCmn.QuantOffsetType = 1;
            }

            /* Increase coding SNR for sparse signals */
            SNR_adj_dB += PerceptualParametersFLP.SPARSE_SNR_INCR_dB * ( psEncCtrl.sparseness - 0.5f );
        }

        /*******************************/
        /* Control bandwidth expansion */
        /*******************************/
        delta  = PerceptualParametersFLP.LOW_RATE_BANDWIDTH_EXPANSION_DELTA * ( 1.0f - 0.75f * psEncCtrl.coding_quality );
        BWExp1 = PerceptualParametersFLP.BANDWIDTH_EXPANSION - delta;
        BWExp2 = PerceptualParametersFLP.BANDWIDTH_EXPANSION + delta;
        if( psEnc.sCmn.fs_kHz == 24 )
        {
            /* Less bandwidth expansion for super wideband */
            BWExp1 = 1.0f - ( 1.0f - BWExp1 ) * PerceptualParametersFLP.SWB_BANDWIDTH_EXPANSION_REDUCTION;
            BWExp2 = 1.0f - ( 1.0f - BWExp2 ) * PerceptualParametersFLP.SWB_BANDWIDTH_EXPANSION_REDUCTION;
        }
        /* BWExp1 will be applied after BWExp2, so make it relative */
        BWExp1 /= BWExp2;

        /********************************************/
        /* Compute noise shaping AR coefs and gains */
        /********************************************/
        for( k = 0; k < Define.NB_SUBFR; k++ )
        {
            /* Apply window */
            ApplySineWindowFLP.SKP_Silk_apply_sine_window_FLP( x_windowed,0, x_ptr,x_ptr_offset, 0, Define.SHAPE_LPC_WIN_MS * psEnc.sCmn.fs_kHz );

            /* Update pointer: next LPC analysis block */
            x_ptr_offset += psEnc.sCmn.subfr_length;

            /* Calculate auto correlation */
            AutocorrelationFLP.SKP_Silk_autocorrelation_FLP(auto_corr,0, x_windowed,0, Define.SHAPE_LPC_WIN_MS * psEnc.sCmn.fs_kHz, psEnc.sCmn.shapingLPCOrder + 1);

            /* Add white noise, as a fraction of energy */
            auto_corr[ 0 ] += auto_corr[ 0 ] * PerceptualParametersFLP.SHAPE_WHITE_NOISE_FRACTION;

            /* Convert correlations to prediction coefficients, and compute residual energy */
            nrg = LevinsondurbinFLP.SKP_Silk_levinsondurbin_FLP( psEncCtrl.AR2,k * Define.SHAPE_LPC_ORDER_MAX, auto_corr, psEnc.sCmn.shapingLPCOrder );

            /* Bandwidth expansion for synthesis filter shaping */
            BwexpanderFLP.SKP_Silk_bwexpander_FLP( psEncCtrl.AR2,k * Define.SHAPE_LPC_ORDER_MAX, psEnc.sCmn.shapingLPCOrder, BWExp2 );

            /* Make sure to fit in Q13 SKP_int16 */
            LPC_fit_int16( psEncCtrl.AR2,k * Define.SHAPE_LPC_ORDER_MAX, 1.0f, psEnc.sCmn.shapingLPCOrder, 3.999f );

            /* Compute noise shaping filter coefficients */
//            SKP_memcpy(
//                &psEncCtrl->AR1[ k * SHAPE_LPC_ORDER_MAX ],
//                &psEncCtrl->AR2[ k * SHAPE_LPC_ORDER_MAX ],
//                psEnc->sCmn.shapingLPCOrder * sizeof( SKP_float ) );
            for(int i_djinn=0; i_djinn<psEnc.sCmn.shapingLPCOrder; i_djinn++)
                psEncCtrl.AR1[ k * Define.SHAPE_LPC_ORDER_MAX + i_djinn ] = psEncCtrl.AR2[ k * Define.SHAPE_LPC_ORDER_MAX + i_djinn ];

            /* Bandwidth expansion for analysis filter shaping */
            BwexpanderFLP.SKP_Silk_bwexpander_FLP( psEncCtrl.AR1,k * Define.SHAPE_LPC_ORDER_MAX, psEnc.sCmn.shapingLPCOrder, BWExp1 );

            /* Increase residual energy */
            nrg += PerceptualParametersFLP.SHAPE_MIN_ENERGY_RATIO * auto_corr[ 0 ];
            psEncCtrl.Gains[ k ] = ( float )Math.sqrt( nrg );

            /* Ratio of prediction gains, in energy domain */
            float[] pre_nrg_djinnaddress = {pre_nrg};
            LPCInvPredGainFLP.SKP_Silk_LPC_inverse_pred_gain_FLP( pre_nrg_djinnaddress, psEncCtrl.AR2,k * Define.SHAPE_LPC_ORDER_MAX, psEnc.sCmn.shapingLPCOrder );
            pre_nrg = pre_nrg_djinnaddress[0];
            float[] nrg_djinnaddress = {nrg};
            LPCInvPredGainFLP.SKP_Silk_LPC_inverse_pred_gain_FLP( nrg_djinnaddress,     psEncCtrl.AR1,k * Define.SHAPE_LPC_ORDER_MAX, psEnc.sCmn.shapingLPCOrder );
            nrg = nrg_djinnaddress[0];
            psEncCtrl.GainsPre[ k ] = ( float )Math.sqrt( pre_nrg / nrg );
            //psEncCtrl->GainsPre[ k ] = 1.0f - 0.7f * ( 1.0f - pre_nrg / nrg );
        }

        /*****************/
        /* Gain tweaking */
        /*****************/
        /* Increase gains during low speech activity and put lower limit on gains */
        gain_mult = ( float )Math.pow( 2.0f, -0.16f * SNR_adj_dB );
        gain_add  = ( float )Math.pow( 2.0f,  0.16f * PerceptualParametersFLP.NOISE_FLOOR_dB ) +
                    ( float )Math.pow( 2.0f,  0.16f * PerceptualParametersFLP.RELATIVE_MIN_GAIN_dB ) * psEnc.avgGain;
        for( k = 0; k < Define.NB_SUBFR; k++ )
        {
            psEncCtrl.Gains[ k ] *= gain_mult;
            psEncCtrl.Gains[ k ] += gain_add;
            psEnc.avgGain += psEnc.speech_activity * PerceptualParametersFLP.GAIN_SMOOTHING_COEF * ( psEncCtrl.Gains[ k ] - psEnc.avgGain );
        }

        /************************************************/
        /* Decrease level during fricatives (de-essing) */
        /************************************************/
        gain_mult = 1.0f + PerceptualParametersFLP.INPUT_TILT + psEncCtrl.coding_quality * PerceptualParametersFLP.HIGH_RATE_INPUT_TILT;
        if( psEncCtrl.input_tilt <= 0.0f && psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_UNVOICED )
        {
            float essStrength = -psEncCtrl.input_tilt * psEnc.speech_activity * ( 1.0f - psEncCtrl.sparseness );
            if( psEnc.sCmn.fs_kHz == 24 )
            {
                gain_mult *= ( float )Math.pow( 2.0f, -0.16f * PerceptualParametersFLP.DE_ESSER_COEF_SWB_dB * essStrength );
            }
            else if( psEnc.sCmn.fs_kHz == 16 )
            {
                gain_mult *= (float)Math.pow( 2.0f, -0.16f * PerceptualParametersFLP.DE_ESSER_COEF_WB_dB * essStrength );
            }
            else
            {
                assert( psEnc.sCmn.fs_kHz == 12 || psEnc.sCmn.fs_kHz == 8 );
            }
        }

        for( k = 0; k < Define.NB_SUBFR; k++ )
        {
            psEncCtrl.GainsPre[ k ] *= gain_mult;
        }

        /************************************************/
        /* Control low-frequency shaping and noise tilt */
        /************************************************/
        /* Less low frequency shaping for noisy inputs */
        strength = PerceptualParametersFLP.LOW_FREQ_SHAPING * ( 1.0f + PerceptualParametersFLP.LOW_QUALITY_LOW_FREQ_SHAPING_DECR * ( psEncCtrl.input_quality_bands[ 0 ] - 1.0f ) );
        if( psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED )
        {
            /* Reduce low frequencies quantization noise for periodic signals, depending on pitch lag */
            /*f = 400; freqz([1, -0.98 + 2e-4 * f], [1, -0.97 + 7e-4 * f], 2^12, Fs); axis([0, 1000, -10, 1])*/
            for( k = 0; k < Define.NB_SUBFR; k++ )
            {
                b = 0.2f / psEnc.sCmn.fs_kHz + 3.0f / psEncCtrl.sCmn.pitchL[ k ];
                psEncCtrl.LF_MA_shp[ k ] = -1.0f + b;
                psEncCtrl.LF_AR_shp[ k ] =  1.0f - b - b * strength;
            }
            Tilt = - PerceptualParametersFLP.HP_NOISE_COEF -
                (1 - PerceptualParametersFLP.HP_NOISE_COEF) * PerceptualParametersFLP.HARM_HP_NOISE_COEF * psEnc.speech_activity;
        }
        else
        {
            b = 1.3f / psEnc.sCmn.fs_kHz;
            psEncCtrl.LF_MA_shp[ 0 ] = -1.0f + b;
            psEncCtrl.LF_AR_shp[ 0 ] =  1.0f - b - b * strength * 0.6f;
            for( k = 1; k < Define.NB_SUBFR; k++ )
            {
                psEncCtrl.LF_MA_shp[ k ] = psEncCtrl.LF_MA_shp[ k - 1 ];
                psEncCtrl.LF_AR_shp[ k ] = psEncCtrl.LF_AR_shp[ k - 1 ];
            }
            Tilt = -PerceptualParametersFLP.HP_NOISE_COEF;
        }

        /****************************/
        /* HARMONIC SHAPING CONTROL */
        /****************************/
        /* Control boosting of harmonic frequencies */
        HarmBoost = PerceptualParametersFLP.LOW_RATE_HARMONIC_BOOST * ( 1.0f - psEncCtrl.coding_quality ) * psEnc.LTPCorr;

        /* More harmonic boost for noisy input signals */
        HarmBoost += PerceptualParametersFLP.LOW_INPUT_QUALITY_HARMONIC_BOOST * ( 1.0f - psEncCtrl.input_quality );

        if( Define.USE_HARM_SHAPING!=0 && psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED )
        {
            /* Harmonic noise shaping */
            HarmShapeGain = PerceptualParametersFLP.HARMONIC_SHAPING;

            /* More harmonic noise shaping for high bitrates or noisy input */
            HarmShapeGain += PerceptualParametersFLP.HIGH_RATE_OR_LOW_QUALITY_HARMONIC_SHAPING *
                ( 1.0f - ( 1.0f - psEncCtrl.coding_quality ) * psEncCtrl.input_quality );

            /* Less harmonic noise shaping for less periodic signals */
            HarmShapeGain *= ( float )Math.sqrt( psEnc.LTPCorr );
        }
        else
        {
            HarmShapeGain = 0.0f;
        }

        /*************************/
        /* Smooth over subframes */
        /*************************/
        for( k = 0; k < Define.NB_SUBFR; k++ )
        {
            psShapeSt.HarmBoost_smth     += PerceptualParametersFLP.SUBFR_SMTH_COEF * ( HarmBoost - psShapeSt.HarmBoost_smth );
            psEncCtrl.HarmBoost[ k ]      = psShapeSt.HarmBoost_smth;
            psShapeSt.HarmShapeGain_smth += PerceptualParametersFLP.SUBFR_SMTH_COEF * ( HarmShapeGain - psShapeSt.HarmShapeGain_smth );
            psEncCtrl.HarmShapeGain[ k ]  = psShapeSt.HarmShapeGain_smth;
            psShapeSt.Tilt_smth          += PerceptualParametersFLP.SUBFR_SMTH_COEF * ( Tilt - psShapeSt.Tilt_smth );
            psEncCtrl.Tilt[ k ]           = psShapeSt.Tilt_smth;
        }
    }

    /**
     *
     * @param a Unstable/stabilized LPC vector [L].
     * @param a_offset offset of valid data.
     * @param bwe Bandwidth expansion factor.
     * @param L Number of LPC parameters in the input vector.
     * @param maxVal Maximum value allowed.
     */
    static void LPC_fit_int16(
              float[] a,                    /* I/O: Unstable/stabilized LPC vector [L]              */
              int a_offset,
        final float  bwe,                   /* I:   Bandwidth expansion factor                      */
        final int    L,                     /* I:   Number of LPC parameters in the input vector    */
        float       maxVal                  /* I    Maximum value allowed                           */
    )
    {
        float   maxabs, absval, sc;
        int     k, i, idx = 0;
        float[]   invGain = new float[1];

        BwexpanderFLP.SKP_Silk_bwexpander_FLP( a,a_offset, L, bwe );

        /***************************/
        /* Limit range of the LPCs */
        /***************************/
        /* Limit the maximum absolute value of the prediction coefficients */
        for( k = 0; k < 1000; k++ )
        {
            /* Find maximum absolute value and its index */
            maxabs = -1.0f;
            for( i = 0; i < L; i++ )
            {
                absval = Math.abs( a[ a_offset+i ] );
                if( absval > maxabs ) {
                    maxabs = absval;
                    idx    = i;
                }
            }

            if( maxabs >= maxVal )
            {
                /* Reduce magnitude of prediction coefficients */
                sc = 0.995f * ( 1.0f - ( 1.0f - maxVal / maxabs ) / ( idx + 1 ) );
                BwexpanderFLP.SKP_Silk_bwexpander_FLP( a,a_offset, L, sc );
            }
            else
            {
                break;
            }
        }
        /* Reached the last iteration */
        if( k == 1000 )
        {
            assert( false );
        }

        /**********************/
        /* Ensure stable LPCs */
        /**********************/
        for( k = 0; k < 1000; k++ )
        {
            if( LPCInvPredGainFLP.SKP_Silk_LPC_inverse_pred_gain_FLP( invGain, a,a_offset, L ) == 1 )
            {
                BwexpanderFLP.SKP_Silk_bwexpander_FLP( a,a_offset, L, 0.997f );
            }
            else
            {
                break;
            }
        }

        /* Reached the last iteration */
        if( k == 1000 )
        {
            assert( false );
            for( i = 0; i < L; i++ )
            {
                a[ i ] = 0.0f;
            }
        }
    }
}
