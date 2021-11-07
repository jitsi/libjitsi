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
public class CodeSigns
{
    /* shifting avoids if-statement */
//    #define SKP_enc_map(a)                  ( SKP_RSHIFT( (a), 15 ) + 1 )
    static int SKP_enc_map(int a)
    {
        return (a>>15)+1;
    }

//    #define SKP_dec_map(a)                  ( SKP_LSHIFT( (a),  1 ) - 1 )
    static int SKP_dec_map(int a)
    {
        return (a<<1)-1;
    }

    /**
     * Encodes signs of excitation.
     * @param sRC Range coder state.
     * @param q Pulse signal.
     * @param length Length of input.
     * @param sigtype Signal type.
     * @param QuantOffsetType QuantOffsetType.
     * @param RateLevelIndex Rate level index.
     */
    static void SKP_Silk_encode_signs(
        SKP_Silk_range_coder_state      sRC,               /* I/O  Range coder state                       */
        byte[]                      q,                  /* I    Pulse signal                            */
        final int                   length,             /* I    Length of input                         */
        final int                   sigtype,            /* I    Signal type                             */
        final int                   QuantOffsetType,    /* I    Quantization offset type                */
        final int                   RateLevelIndex      /* I    Rate level index                        */
    )
    {
        int i;
        int inData;
        int[] cdf = new int[3];

        i = SKP_SMULBB( N_RATE_LEVELS - 1, ( sigtype << 1 ) + QuantOffsetType ) + RateLevelIndex;
        cdf[ 0 ] = 0;
        cdf[ 1 ] = TablesSign.SKP_Silk_sign_CDF[ i ];
        cdf[ 2 ] = 65535;

        for( i = 0; i < length; i++ )
        {
            if( q[ i ] != 0 )
            {
//                inData = SKP_enc_map( q[ i ] ); /* - = 0, + = 1 */
                inData = (q[i] >>15) + 1; /* - = 0, + = 1 */
                RangeCoder.SKP_Silk_range_encoder( sRC, inData, cdf, 0 );
            }
        }
    }

    /**
     * Decodes signs of excitation.
     * @param sRC Range coder state.
     * @param q pulse signal.
     * @param length length of output.
     * @param sigtype Signal type.
     * @param QuantOffsetType Quantization offset type.
     * @param RateLevelIndex Rate Level Index.
     */
    static void SKP_Silk_decode_signs(
            SKP_Silk_range_coder_state      sRC,               /* I/O  Range coder state                           */
            int                         q[],                /* I/O  pulse signal                                */
            final int                   length,             /* I    length of output                            */
            final int                   sigtype,            /* I    Signal type                                 */
            final int                   QuantOffsetType,    /* I    Quantization offset type                    */
            final int                   RateLevelIndex      /* I    Rate Level Index                            */
        )
    {
        int i;
        int data;
        int data_ptr[] = new int[1];
        int[] cdf = new int[3];

        i = SKP_SMULBB( N_RATE_LEVELS - 1, ( sigtype << 1 ) + QuantOffsetType ) + RateLevelIndex;
        cdf[ 0 ] = 0;
        cdf[ 1 ] = TablesSign.SKP_Silk_sign_CDF[ i ];
        cdf[ 2 ] = 65535;

        for( i = 0; i < length; i++ ) {
            if( q[ i ] > 0 ) {
                RangeCoder.SKP_Silk_range_decoder( data_ptr, 0, sRC, cdf, 0, 1 );
                data = data_ptr[0];
                /* attach sign */
                /* implementation with shift, subtraction, multiplication */
//                q[ i ] *= SKP_dec_map( data );
                q[ i ] *= (data<<1) - 1;
            }
        }
    }
}
