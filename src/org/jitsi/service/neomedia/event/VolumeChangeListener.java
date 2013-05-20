/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.event;

/**
 * Represents a listener (to be) notified about changes in the volume
 * level/value maintained by a <tt>VolumeControl</tt>.
 *
 * @author Damian Minkov
 */
public interface VolumeChangeListener
{
    /**
     * Notifies this instance that the volume level/value maintained by a source
     * <tt>VolumeControl</tt> (to which this instance has previously been added)
     * has changed.
     *
     * @param volumeChangeEvent a <tt>VolumeChangeEvent</tt> which details the
     * source <tt>VolumeControl</tt> which has fired the notification and the
     * volume level/value
     */
    public void volumeChange(VolumeChangeEvent volumeChangeEvent);
}
