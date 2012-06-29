/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.control;

import javax.media.*;

/**
 * Defines the interface for controlling
 * <tt>CaptureDevice</tt>s/<tt>DataSource</tt>s associated with the
 * <tt>imgstreaming</tt> FMJ/JMF protocol.
 *
 * @author Lyubomir Marinov
 */
public interface ImgStreamingControl
    extends Control
{
    /**
     * Set the display index and the origin of the stream associated with a
     * specific index in the <tt>DataSource</tt> of this <tt>Control</tt>.
     *
     * @param streamIndex the index in the associated <tt>DataSource</tt> of the
     * stream to set the display index and the origin of
     * @param displayIndex the display index to set on the specified stream
     * @param x the x coordinate of the origin to set on the specified stream
     * @param y the y coordinate of the origin to set on the specified stream
     */
    public void setOrigin(int streamIndex, int displayIndex, int x, int y);
}
