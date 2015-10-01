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
