/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

import java.util.*;

/**
 * Set decoder sampling rate.
 * 
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class DecoderSetFs
{
    /**
     * Set decoder sampling rate.
     * @param psDec the decoder state.
     * @param fs_kHz the sampling frequency(kHz).
     */
    static void SKP_Silk_decoder_set_fs(
        SKP_Silk_decoder_state          psDec,             /* I/O  Decoder state pointer                       */
        int                             fs_kHz              /* I    Sampling frequency (kHz)                    */
    )
    {
        if( psDec.fs_kHz != fs_kHz ) {
            psDec.fs_kHz  = fs_kHz;
            psDec.frame_length = Define.FRAME_LENGTH_MS*fs_kHz;
            
            psDec.subfr_length = (Define.FRAME_LENGTH_MS/Define.NB_SUBFR)*fs_kHz;
            
            if( psDec.fs_kHz == 8 ) {
                psDec.LPC_order = Define.MIN_LPC_ORDER;
                psDec.psNLSF_CB[0]   = TablesNLSFCB010.SKP_Silk_NLSF_CB0_10;
                psDec.psNLSF_CB[1]   = TablesNLSFCB110.SKP_Silk_NLSF_CB1_10;
            } else {
                psDec.LPC_order = Define.MAX_LPC_ORDER;
                psDec.psNLSF_CB[0]   = TablesNLSFCB016.SKP_Silk_NLSF_CB0_16;
                psDec.psNLSF_CB[1]   = TablesNLSFCB116.SKP_Silk_NLSF_CB1_16;
            }
            /* Reset part of the decoder state */
            Arrays.fill(psDec.sLPC_Q14, 0, Define.MAX_LPC_ORDER, 0);
            
            Arrays.fill(psDec.outBuf, 0, Define.MAX_FRAME_LENGTH, (short)0);
            
            Arrays.fill(psDec.prevNLSF_Q15, 0, Define.MAX_LPC_ORDER, 0);
            

            psDec.sLTP_buf_idx            = 0;
            psDec.lagPrev                 = 100;
            psDec.LastGainIndex           = 1;
            psDec.prev_sigtype            = 0;
            psDec.first_frame_after_reset = 1;

            if( fs_kHz == 24 ) {
                psDec.HP_A = TablesOther.SKP_Silk_Dec_A_HP_24;
                psDec.HP_B = TablesOther.SKP_Silk_Dec_B_HP_24;
            } else if( fs_kHz == 16 ) {
                psDec.HP_A = TablesOther.SKP_Silk_Dec_A_HP_16;
                psDec.HP_B = TablesOther.SKP_Silk_Dec_B_HP_16;
            } else if( fs_kHz == 12 ) {
                psDec.HP_A = TablesOther.SKP_Silk_Dec_A_HP_12;
                psDec.HP_B = TablesOther.SKP_Silk_Dec_B_HP_12;
            } else if( fs_kHz == 8 ) {
                psDec.HP_A = TablesOther.SKP_Silk_Dec_A_HP_8;
                psDec.HP_B = TablesOther.SKP_Silk_Dec_B_HP_8;
            } else {
                /* unsupported sampling rate */
                Typedef.SKP_assert( false );
            }
        } 
        /* Check that settings are valid */
        Typedef.SKP_assert( psDec.frame_length > 0 && psDec.frame_length <= Define.MAX_FRAME_LENGTH );
    }
}
