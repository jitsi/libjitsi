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

import static org.jitsi.impl.neomedia.codec.audio.silk.Macros.*;

/**
 * approximate sigmoid function.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class SigmQ15
{
    /* fprintf(1, '%d, ', round(1024 * ([1 ./ (1 + exp(-(1:5))), 1] - 1 ./ (1 + exp(-(0:5)))))); */
    static int[] sigm_LUT_slope_Q10 =
    {
        237, 153, 73, 30, 12, 7
    };
    /* fprintf(1, '%d, ', round(32767 * 1 ./ (1 + exp(-(0:5))))); */
    static int[] sigm_LUT_pos_Q15 =
    {
        16384, 23955, 28861, 31213, 32178, 32548
    };
    /* fprintf(1, '%d, ', round(32767 * 1 ./ (1 + exp((0:5))))); */
    static int[] sigm_LUT_neg_Q15 =
    {
        16384, 8812, 3906, 1554, 589, 219
    };

    static int SKP_Silk_sigm_Q15( int in_Q5 )
    {
        int ind;

        if( in_Q5 < 0 )
        {
            /* Negative input */
            in_Q5 = -in_Q5;
            if( in_Q5 >= 6 * 32 )
            {
                return 0;        /* Clip */
            }
            else
            {
                /* Linear interpolation of look up table */
                ind = in_Q5 >> 5;
                return( sigm_LUT_neg_Q15[ ind ] - SKP_SMULBB( sigm_LUT_slope_Q10[ ind ], in_Q5 & 0x1F ) );
            }
        }
        else
        {
            /* Positive input */
            if( in_Q5 >= 6 * 32 )
            {
                return 32767;        /* clip */
            }
            else
            {
                /* Linear interpolation of look up table */
                ind = in_Q5 >> 5;
                return( sigm_LUT_pos_Q15[ ind ] + SKP_SMULBB( sigm_LUT_slope_Q10[ ind ], in_Q5 & 0x1F ) );
            }
        }
    }
}
