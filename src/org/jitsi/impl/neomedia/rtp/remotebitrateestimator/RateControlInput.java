/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

/**
 * webrtc/webrtc/modules/remote_bitrate_estimator/include/bwe_defines.h
 *
 * @author Lyubomir Marinov
 */
public class RateControlInput
{
    public BandwidthUsage bwState;

    public long incomingBitRate;

    public double noiseVar;

    public RateControlInput(
            BandwidthUsage bwState,
            long incomingBitRate,
            double noiseVar)
    {
        this.bwState = bwState;
        this.incomingBitRate = incomingBitRate;
        this.noiseVar = noiseVar;
    }

    /**
     * Assigns the values of the fields of <tt>source</tt> to the respective
     * fields of this <tt>RateControlInput</tt>.
     *
     * @param source the <tt>RateControlInput</tt> the values of the fields of
     * which are to be assigned to the respective fields of this
     * <tt>RateControlInput</tt>
     */
    public void copy(RateControlInput source)
    {
        bwState = source.bwState;
        incomingBitRate = source.incomingBitRate;
        noiseVar = source.noiseVar;
    }
}
