/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

/**
 * Bandwidth over-use detector options.  These are used to drive experimentation
 * with bandwidth estimation parameters.
 *
 * webrtc/webrtc/common_types.h
 *
 * @author Lyubomir Marinov
 */
public class OverUseDetectorOptions
{
    public double initialAvgNoise = 0.0D;

    public final double[][] initialE
        = new double[][] { { 100, 0 }, { 0, 1e-1 } };

    public double initialOffset = 0.0D;

    public final double[] initialProcessNoise = new double[] { 1e-10, 1e-2 };

    public double initialSlope = 8.0D / 512.0D;

    public double initialThreshold = 25.0D;

    public double initialVarNoise = 50.0D;
}
