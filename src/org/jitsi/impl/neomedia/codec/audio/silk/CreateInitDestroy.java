/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * Initialize decoder state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class CreateInitDestroy
{
    /**
     * Initialize decoder state.
     * @param psDec the decoder state.
     * @return
     */
    static int SKP_Silk_init_decoder(
        SKP_Silk_decoder_state      psDec              /* I/O  Decoder state pointer                       */
    )
    {
        //psDec = new SKP_Silk_decoder_state();

        /* Set sampling rate to 24 kHz, and init non-zero values */
        DecoderSetFs.SKP_Silk_decoder_set_fs( psDec, 24 );

        /* Used to deactivate e.g. LSF interpolation and fluctuation reduction */
        psDec.first_frame_after_reset = 1;
        psDec.prev_inv_gain_Q16 = 65536;

        /* Reset CNG state */
        CNG.SKP_Silk_CNG_Reset( psDec );

        PLC.SKP_Silk_PLC_Reset(psDec);
        return(0);
    }
}
