/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

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

    public void copy(RateControlInput source)
    {
        bwState = source.bwState;
        incomingBitRate = source.incomingBitRate;
        noiseVar = source.noiseVar;
    }
}
