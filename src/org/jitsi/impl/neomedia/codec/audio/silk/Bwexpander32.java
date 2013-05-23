/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

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
            ar[ i ]       = Macros.SKP_SMULWW( ar[ i ],   tmp_chirp_Q16 );
            tmp_chirp_Q16 = Macros.SKP_SMULWW( chirp_Q16, tmp_chirp_Q16 );
        }
        ar[ d - 1 ] = Macros.SKP_SMULWW( ar[ d - 1 ], tmp_chirp_Q16 );
    }
}
