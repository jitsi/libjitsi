/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements a <tt>MediaDevice</tt> which provides a fading animation from
 * white to black to white... in form of video.
 *
 * @author Thomas Kuntz
 */
public class IVFMediaDevice
    extends MediaDeviceImpl
{
    /**
     * The list of <tt>Format</tt>s supported by the
     * <tt>IVFCaptureDevice</tt> instances.
     */
    protected static final Format[] SUPPORTED_FORMATS
        = new Format[]
                {
                    new VideoFormat(Constants.VP8)
                };

    /**
     * Initializes a new <tt>IVFMediaDevice</tt> instance which will read
     * the IVF file located at <tt>filename</tt>.
     * 
     * @param filename the location of the IVF the <tt>IVFStream<tt>
     * will read.
     */
    public IVFMediaDevice(String filename)
    {
        super(new CaptureDeviceInfo(
                  filename,
                  new MediaLocator("ivffile:"+filename),
                  IVFMediaDevice.SUPPORTED_FORMATS),
              MediaType.VIDEO);
    }
}
