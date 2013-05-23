/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * Definitions For FLP pitch estimator.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class PitchEstDefinesFLP
{
    static final float PITCH_EST_FLP_SHORTLAG_BIAS =            0.2f;    /* for logarithmic weighting    */
    static final float PITCH_EST_FLP_PREVLAG_BIAS =             0.2f;    /* for logarithmic weighting    */
    static final float PITCH_EST_FLP_FLATCONTOUR_BIAS =         0.05f;
}
