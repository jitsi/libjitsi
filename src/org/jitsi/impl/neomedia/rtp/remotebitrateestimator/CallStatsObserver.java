/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

/**
 * webrtc/webrtc/modules/interface/module_common_types.h
 *
 * @author Lyubomir Marinov
 */
public interface CallStatsObserver
{
    void onRttUpdate(long rttMs);
}
