/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;


import javax.media.control.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

/**
 * Implements the <tt>CaptureDevice</tt> and <tt>DataSource</tt> for the
 *  purpose of rtpdump file streaming.
 *
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
    protected RtpdumpStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new RtpdumpStream(this, formatControl);
    }
}
