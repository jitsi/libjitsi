/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * Downsample by a factor 2/3, low quality.
 *
 * @author Jing Dai.
 * @author Dingxin Xu
 */
public class ResamplerDown23
{
    static final int ORDER_FIR =                  4;

    /**
     * Downsample by a factor 2/3, low quality.
     * @param S State vector [ 6 ]
     * @param S_offset offset of valid data.
     * @param out Output signal [ floor(2*inLen/3) ]
     * @param out_offset offset of valid data.
     * @param in Input signal [ inLen ]
     * @param in_offset offset of valid data.
     * @param inLen Number of input samples
     */
    static void SKP_Silk_resampler_down2_3(
        int[]                           S,         /* I/O: State vector [ 6 ]                  */
        int S_offset,
        short[]                         out,       /* O:   Output signal [ floor(2*inLen/3) ]  */
        int out_offset,
        short[]                         in,        /* I:   Input signal [ inLen ]              */
        int in_offset,
        int                             inLen      /* I:   Number of input samples             */
    )
    {
        int nSamplesIn, counter, res_Q6;
        int[] buf = new int[ ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN + ORDER_FIR ];
        int buf_ptr;

        /* Copy buffered samples to start of buffer */
        for(int i_djinn=0; i_djinn<ORDER_FIR; i_djinn++)
            buf[i_djinn] = S[S_offset+i_djinn];

        /* Iterate over blocks of frameSizeIn input samples */
        while( true )
        {
            nSamplesIn = Math.min( inLen, ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN );

            /* Second-order AR filter (output in Q8) */
            ResamplerPrivateAR2.SKP_Silk_resampler_private_AR2( S,ORDER_FIR, buf,ORDER_FIR, in,in_offset,
                    ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ,0, nSamplesIn );

            /* Interpolate filtered signal */
            buf_ptr = 0;
            counter = nSamplesIn;
            while( counter > 2 )
            {
                /* Inner product */
                res_Q6 = Macros.SKP_SMULWB(         buf[ buf_ptr   ], ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ[ 2 ] );
                res_Q6 = Macros.SKP_SMLAWB( res_Q6, buf[ buf_ptr+1 ], ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ[ 3 ] );
                res_Q6 = Macros.SKP_SMLAWB( res_Q6, buf[ buf_ptr+2 ], ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ[ 5 ] );
                res_Q6 = Macros.SKP_SMLAWB( res_Q6, buf[ buf_ptr+3 ], ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ[ 4 ] );

                /* Scale down, saturate and store in output array */
                out[out_offset++] = (short)SigProcFIX.SKP_SAT16( SigProcFIX.SKP_RSHIFT_ROUND( res_Q6, 6 ) );

                res_Q6 = Macros.SKP_SMULWB(         buf[ buf_ptr+1 ], ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ[ 4 ] );
                res_Q6 = Macros.SKP_SMLAWB( res_Q6, buf[ buf_ptr+2 ], ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ[ 5 ] );
                res_Q6 = Macros.SKP_SMLAWB( res_Q6, buf[ buf_ptr+3 ], ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ[ 3 ] );
                res_Q6 = Macros.SKP_SMLAWB( res_Q6, buf[ buf_ptr+4 ], ResamplerRom.SKP_Silk_Resampler_2_3_COEFS_LQ[ 2 ] );

                /* Scale down, saturate and store in output array */
                out[out_offset++] = (short)SigProcFIX.SKP_SAT16( SigProcFIX.SKP_RSHIFT_ROUND( res_Q6, 6 ) );

                buf_ptr += 3;
                counter -= 3;
            }

            in_offset += nSamplesIn;
            inLen -= nSamplesIn;

            if( inLen > 0 )
            {
                /* More iterations to do; copy last part of filtered signal to beginning of buffer */
                for(int i_djinn=0; i_djinn<ORDER_FIR; i_djinn++)
                    buf[i_djinn] = buf[nSamplesIn+i_djinn];
            }
            else
            {
                break;
            }
        }

        /* Copy last part of filtered signal to the state for the next call */
        for(int i_djinn=0; i_djinn<ORDER_FIR; i_djinn++)
            S[S_offset+i_djinn] = buf[nSamplesIn+i_djinn];
    }
}
