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
public class LBRRReset
{
    /**
     * Resets LBRR buffer, used if packet size changes.
     *
     * @param psEncC state
     */
    static void SKP_Silk_LBRR_reset(
        SKP_Silk_encoder_state      psEncC             /* I/O  state                                       */
    )
    {
        int i;

        for( i = 0; i < Define.MAX_LBRR_DELAY; i++ ) {
            psEncC.LBRR_buffer[ i ].usage = Define.SKP_SILK_NO_LBRR;
        }
    }
}
