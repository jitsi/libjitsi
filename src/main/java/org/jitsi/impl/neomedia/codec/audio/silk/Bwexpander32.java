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
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class Bwexpander32
{
    /**
     * Chirp (bandwidth expand) LP AR filter.
     * @param ar AR filter to be expanded (without leading 1).
     * @param d Length of ar.
     * @param chirp_Q16  Chirp factor in Q16.
     */
    static void SKP_Silk_bwexpander_32(
            int        []ar,      /* I/O    AR filter to be expanded (without leading 1)    */
            final int  d,        /* I    Length of ar                                      */
            int        chirp_Q16 /* I    Chirp factor in Q16                               */
        )
    {
        int   i;
        int tmp_chirp_Q16;

        tmp_chirp_Q16 = chirp_Q16;
        for( i = 0; i < d - 1; i++ ) {
            ar[ i ]       = SKP_SMULWW( ar[ i ],   tmp_chirp_Q16 );
            tmp_chirp_Q16 = SKP_SMULWW( chirp_Q16, tmp_chirp_Q16 );
        }
        ar[ d - 1 ] = SKP_SMULWW( ar[ d - 1 ], tmp_chirp_Q16 );
    }
}
