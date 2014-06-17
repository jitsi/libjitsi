/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.recording;

/**
 * An interface that allows handling of <tt>RecorderEvent</tt> instances, such
 * as writing them to disk in some format.
 *
 * @author Boris Grozev
 */
public interface RecorderEventHandler
{
    /**
     * Handle a specific <tt>RecorderEvent</tt>
     * @param ev the event to handle.
     * @return
     */
    public boolean handleEvent(RecorderEvent ev);

    /**
     * Closes the <tt>RecorderEventHandler</tt>.
     */
    public void close();
}
