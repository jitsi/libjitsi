/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.utils.*;

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
