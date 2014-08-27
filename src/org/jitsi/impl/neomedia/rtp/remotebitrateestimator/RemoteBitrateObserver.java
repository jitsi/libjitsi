/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

/**
 * <tt>RemoteBitrateObserver</tt> is used to signal changes in bitrate estimates
 * for the incoming streams.
 */
public interface RemoteBitrateObserver
{
    /**
     * Called when a receive channel group has a new bitrate estimate for the
     * incoming streams.
     *
     * @param ssrcs
     * @param bitrate
     */
    void onReceiveBitrateChanged(int[] ssrcs, long bitrate);
}
