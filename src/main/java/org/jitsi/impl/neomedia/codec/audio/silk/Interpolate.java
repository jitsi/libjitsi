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
 * Interpolate two vectors.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class Interpolate
{
    /**
     * Interpolate two vectors.
     *
     * @param xi interpolated vector.
     * @param x0 first vector.
     * @param x1 second vector.
     * @param ifact_Q2 interp. factor, weight on 2nd vector.
     * @param d number of parameters.
     */
    static void SKP_Silk_interpolate(
        int[] xi,                                             /* O    interpolated vector                     */
        int[] x0,                                             /* I    first vector                            */
        int[] x1,                                             /* I    second vector                           */
        final int                   ifact_Q2,               /* I    interp. factor, weight on 2nd vector    */
        final int                   d                       /* I    number of parameters                    */
    )
    {
        int i;

        assert( ifact_Q2 >= 0 );
        assert( ifact_Q2 <= ( 1 << 2 ) );

        for( i = 0; i < d; i++ )
        {
            xi[ i ] = ( x0[ i ] + ( ( x1[ i ] - x0[ i ] ) * ifact_Q2 >> 2 ) );
        }
    }
}
