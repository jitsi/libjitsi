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
 * step up function, converts reflection coefficients to
 * prediction coefficients.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class K2aFLP
{
    /**
     * step up function, converts reflection coefficients to prediction coefficients.
     *
     * @param A prediction coefficients [order].
     * @param rc reflection coefficients [order].
     * @param order prediction order.
     */
    static void SKP_Silk_k2a_FLP(
        float[]       A,                 /* O:   prediction coefficients [order]             */
        float[] rc,                /* I:   reflection coefficients [order]             */
        int       order               /* I:   prediction order                            */
    )
    {
        int   k, n;
        float[] Atmp = new float[SigProcFIX.SKP_Silk_MAX_ORDER_LPC];

        for( k = 0; k < order; k++ )
        {
            for( n = 0; n < k; n++ )
            {
                Atmp[ n ] = A[ n ];
            }
            for( n = 0; n < k; n++ )
            {
                A[ n ] += Atmp[ k - n - 1 ] * rc[ k ];
            }
            A[ k ] = -rc[ k ];
        }
    }
}
