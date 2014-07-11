/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.greyfading;

import javax.media.control.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

/**
 * Implements a <tt>CaptureDevice</tt> which provides a fading animation from
 * white to black to white... in form of video.
 *
 * @author Thomas Kuntz
 */
public class DataSource
    extends AbstractVideoPullBufferCaptureDevice
{
    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link AbstractPushBufferCaptureDevice#createStream(int, FormatControl)}.
     */
    protected VideoGreyFadingStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new VideoGreyFadingStream(this, formatControl);
    }
}
