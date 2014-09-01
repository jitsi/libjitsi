/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;

/**
 * webrtc/webrtc/modules/interface/module.h
 *
 * @author Lyubomir Marinov
 */
public interface RecurringProcessible
{
    /**
     * Returns the number of milliseconds until this instance wants a worker
     * thread to call {@link #process()}.
     *
     * @return
     */
    long getTimeUntilNextProcess();

    /**
     * Process any pending tasks such as timeouts.
     *
     * @return
     */
    long process();
}
