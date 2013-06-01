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
public class MainFLP
{
    /**
     * using log2() helps the fixed-point conversion.
     * @param x
     * @return
     */
    static float SKP_Silk_log2( double x )
    {
        return ( float )( 3.32192809488736 * Math.log10( x ) );
    }
}
