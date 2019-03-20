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
package org.jitsi.impl.neomedia.device;

import java.util.*;

import javax.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.directshow.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;

/**
 * Discovers and registers DirectShow video capture devices with JMF.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class DirectShowSystem
    extends DeviceSystem
{
    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying QuickTime/QTKit
     * capture devices.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_DIRECTSHOW;

    /**
     * The <tt>Logger</tt> used by the <tt>DirectShowSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(DirectShowSystem.class);

    /**
     * Constructor. Discover and register DirectShow capture devices
     * with JMF.
     *
     * @throws Exception if anything goes wrong while discovering and
     * registering DirectShow capture defines with JMF
     */
    public DirectShowSystem()
        throws Exception
    {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL);
    }

    @Override
    protected void doInitialize()
        throws Exception
    {
        DSManager manager = new DSManager();

        try
        {
            DSCaptureDevice devices[] = manager.getCaptureDevices();
            boolean captureDeviceInfoIsAdded = false;

            for(int i = 0, count = (devices == null) ? 0 : devices.length;
                    i < count;
                    i++)
            {
                DSCaptureDevice device = devices[i];
                DSFormat[] dsFormats = device.getSupportedFormats();
                String name = device.getName();

                if (dsFormats.length == 0)
                {
                    logger.warn(
                            "Camera '" + name
                                + "' reported no supported formats.");
                    continue;
                }

                List<Format> formats
                    = new ArrayList<Format>(dsFormats.length);

                for (DSFormat dsFormat : dsFormats)
                {
                    int pixelFormat = dsFormat.getPixelFormat();
                    int ffmpegPixFmt = DataSource.getFFmpegPixFmt(pixelFormat);

                    if (ffmpegPixFmt != FFmpeg.PIX_FMT_NONE)
                    {
                        Format format
                            = new AVFrameFormat(ffmpegPixFmt, pixelFormat);

                        if (!formats.contains(format))
                            formats.add(format);
                    }
                }
                if (formats.isEmpty())
                {
                    logger.warn(
                            "No support for the formats of camera '" + name
                                + "': " + Arrays.toString(dsFormats));
                    continue;
                }

                Format[] formatsArray
                    = formats.toArray(new Format[formats.size()]);

                if(logger.isInfoEnabled())
                {
                    logger.info(
                            "Support for the formats of camera '" + name
                                + "': " + Arrays.toString(formatsArray));
                }

                CaptureDeviceInfo cdi
                    = new CaptureDeviceInfo(
                            name,
                            new MediaLocator(LOCATOR_PROTOCOL + ':' + name),
                            formatsArray);

                CaptureDeviceManager.addDevice(cdi);
                captureDeviceInfoIsAdded = true;
            }

            if (captureDeviceInfoIsAdded
                    && !MediaServiceImpl.isJmfRegistryDisableLoad())
                CaptureDeviceManager.commit();
        }
        finally
        {
            manager.dispose();
        }
    }
}
