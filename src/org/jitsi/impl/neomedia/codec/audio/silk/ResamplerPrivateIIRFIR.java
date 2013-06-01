/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * Upsample using a combination of allpass-based 2x upsampling and FIR interpolation.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class ResamplerPrivateIIRFIR
{
    /**
     * Upsample using a combination of allpass-based 2x upsampling and FIR interpolation.
     * @param SS Resampler state.
     * @param out Output signal.
     * @param out_offset offset of valid data.
     * @param in Input signal.
     * @param in_offset offset of valid data.
     * @param inLen Number of input samples.
     */
    static void SKP_Silk_resampler_private_IIR_FIR(
        Object                            SS,              /* I/O: Resampler state                         */
        short[]                            out,            /* O:    Output signal                             */
        int out_offset,
        short[]                         in,             /* I:    Input signal                            */
        int in_offset,
        int                                inLen            /* I:    Number of input samples                    */
    )
    {
        SKP_Silk_resampler_state_struct S = (SKP_Silk_resampler_state_struct )SS;

        int nSamplesIn, table_index;
        int max_index_Q16, index_Q16, index_increment_Q16, res_Q15;
        short[] buf = new short[ 2 * ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN + ResamplerRom.RESAMPLER_ORDER_FIR_144 ];
        int buf_ptr;

        /* Copy buffered samples to start of buffer */
//TODO:litter-endian or big-endian???
//        SKP_memcpy( buf, S->sFIR, RESAMPLER_ORDER_FIR_144 * sizeof( SKP_int32 ) );
        for(int i_djinn=0; i_djinn<ResamplerRom.RESAMPLER_ORDER_FIR_144; i_djinn++)
        {
//            buf[2*i_djinn] = (short)(S.sFIR[i_djinn]>>>16);
//            buf[2*i_djinn+1] = (short)(S.sFIR[i_djinn]&0x0000FFFF);
//littel-endian
            buf[2*i_djinn] = (short)(S.sFIR[i_djinn]&0x0000FFFF);
            buf[2*i_djinn+1] = (short)(S.sFIR[i_djinn]>>>16);
        }

        /* Iterate over blocks of frameSizeIn input samples */
        index_increment_Q16 = S.invRatio_Q16;
        while( true )
        {
            nSamplesIn = Math.min( inLen, S.batchSize );

            if( S.input2x == 1 )
            {
                /* Upsample 2x */
                S.up2_function(S.sIIR, buf, ResamplerRom.RESAMPLER_ORDER_FIR_144, in, in_offset, nSamplesIn);
            }
            else
            {
                /* Fourth-order ARMA filter */
                ResamplerPrivateARMA4.SKP_Silk_resampler_private_ARMA4( S.sIIR,0, buf,ResamplerRom.RESAMPLER_ORDER_FIR_144, in,in_offset, S.Coefs,0, nSamplesIn );
            }

            max_index_Q16 = nSamplesIn << ( 16 + S.input2x );         /* +1 if 2x upsampling */

            /* Interpolate upsampled signal and store in output array */
            for( index_Q16 = 0; index_Q16 < max_index_Q16; index_Q16 += index_increment_Q16 )
            {
                table_index = Macros.SKP_SMULWB( index_Q16 & 0xFFFF, 144 );
                buf_ptr = index_Q16 >> 16;
                res_Q15 = Macros.SKP_SMULBB(          buf[ buf_ptr   ], ResamplerRom.SKP_Silk_resampler_frac_FIR_144[       table_index ][ 0 ] );
                res_Q15 = Macros.SKP_SMLABB( res_Q15, buf[ buf_ptr+1 ], ResamplerRom.SKP_Silk_resampler_frac_FIR_144[       table_index ][ 1 ] );
                res_Q15 = Macros.SKP_SMLABB( res_Q15, buf[ buf_ptr+2 ], ResamplerRom.SKP_Silk_resampler_frac_FIR_144[       table_index ][ 2 ] );
                res_Q15 = Macros.SKP_SMLABB( res_Q15, buf[ buf_ptr+3 ], ResamplerRom.SKP_Silk_resampler_frac_FIR_144[ 143 - table_index ][ 2 ] );
                res_Q15 = Macros.SKP_SMLABB( res_Q15, buf[ buf_ptr+4 ], ResamplerRom.SKP_Silk_resampler_frac_FIR_144[ 143 - table_index ][ 1 ] );
                res_Q15 = Macros.SKP_SMLABB( res_Q15, buf[ buf_ptr+5 ], ResamplerRom.SKP_Silk_resampler_frac_FIR_144[ 143 - table_index ][ 0 ] );
                out[out_offset++] = (short)SigProcFIX.SKP_SAT16( SigProcFIX.SKP_RSHIFT_ROUND( res_Q15, 15 ) );
            }
            in_offset += nSamplesIn;
            inLen -= nSamplesIn;

            if( inLen > 0 )
            {
                /* More iterations to do; copy last part of filtered signal to beginning of buffer */
//TODO:litter-endian or big-endian???
//                SKP_memcpy( buf, &buf[ nSamplesIn << S->input2x ], RESAMPLER_ORDER_FIR_144 * sizeof( SKP_int32 ) );
                for(int i_djinn=0; i_djinn<ResamplerRom.RESAMPLER_ORDER_FIR_144; i_djinn++)
                    buf[i_djinn] = buf[(nSamplesIn << S.input2x) + i_djinn];
            }
            else
            {
                break;
            }
        }

        /* Copy last part of filtered signal to the state for the next call */
//TODO:litter-endian or big-endian???
//        SKP_memcpy( S->sFIR, &buf[nSamplesIn << S->input2x ], RESAMPLER_ORDER_FIR_144 * sizeof( SKP_int32 ) );
        for(int i_djinn=0; i_djinn<ResamplerRom.RESAMPLER_ORDER_FIR_144; i_djinn++)
        {
//            S.sFIR[i_djinn] = (int)buf[(nSamplesIn << S.input2x) + 2*i_djinn] << 16;
//            S.sFIR[i_djinn] |= (int)buf[(nSamplesIn << S.input2x) + 2*i_djinn+1] & 0x0000FFFF;
//little-endian
            S.sFIR[i_djinn] = (buf[(nSamplesIn << S.input2x) + 2*i_djinn]&0xFF);
            S.sFIR[i_djinn] |= (((buf[(nSamplesIn << S.input2x) + 2*i_djinn]>>8)&0xFF)<<8)&0x0000FF00;
            S.sFIR[i_djinn] |= (((buf[(nSamplesIn << S.input2x) + 2*i_djinn + 1]>>0)&0xFF)<<16)&0x00FF0000;
            S.sFIR[i_djinn] |= (((buf[(nSamplesIn << S.input2x) + 2*i_djinn + 1 ]>>8)&0xFF)<<24)&0xFF000000;
        }
    }
}
