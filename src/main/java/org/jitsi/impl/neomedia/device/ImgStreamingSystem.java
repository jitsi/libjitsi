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

import java.awt.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;

/**
 * Add ImageStreaming capture device.
 *
 * @author Sebastien Vincent
 */
public class ImgStreamingSystem
    extends DeviceSystem
{
    /**
     * The locator protocol used when creating or parsing
     * <tt>MediaLocator</tt>s.
     */
    private static final String LOCATOR_PROTOCOL
        = LOCATOR_PROTOCOL_IMGSTREAMING;

    /**
     * Add capture devices.
     *
     * @throws Exception if problem when adding capture devices
     */
    public ImgStreamingSystem()
        throws Exception
    {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL, FEATURE_REINITIALIZE);
    }

    @Override
    protected void doInitialize()
        throws Exception
    {
        /*
         * XXX The initialization of MediaServiceImpl is very complex so it is
         * wise to not reference it at the early stage of its initialization.
         */
        ScreenDevice[] screens = ScreenDeviceImpl.getAvailableScreenDevices();

        String name = "Desktop Streaming";
        int i = 0;
        boolean multipleMonitorsOneScreen = false;
        Dimension screenSize = null;

        /*
         * On Linux, multiple monitors may result in a single X display (:0.0)
         * which combines them.
         */
        if(OSUtils.IS_LINUX)
        {
            Dimension size = new Dimension(0, 0);

            for(ScreenDevice screen : screens)
            {
                Dimension s = screen.getSize();

                size.width += s.width;
                size.height += s.height;
            }

            try
            {
                screenSize = Toolkit.getDefaultToolkit().getScreenSize();

                if(screenSize.width == size.width ||
                        screenSize.height == size.height)
                {
                    multipleMonitorsOneScreen = true;
                }
            }
            catch(Exception e)
            {
            }
        }

        for(ScreenDevice screen : screens)
        {
            Dimension size = screenSize != null ? screenSize : screen.getSize();
            Format formats[]
                = new Format[]
                {
                    new AVFrameFormat(
                            size,
                            Format.NOT_SPECIFIED,
                            FFmpeg.PIX_FMT_ARGB,
                            Format.NOT_SPECIFIED),
                    new RGBFormat(
                            size, // size
                            Format.NOT_SPECIFIED, // maxDataLength
                            Format.byteArray, // dataType
                            Format.NOT_SPECIFIED, // frameRate
                            32, // bitsPerPixel
                            2 /* red */, 3 /* green */,  4 /* blue */)
                };
            CaptureDeviceInfo cdi
                = new CaptureDeviceInfo(
                        name + " " + i,
                        new MediaLocator(LOCATOR_PROTOCOL + ":" + i),
                        formats);

            CaptureDeviceManager.addDevice(cdi);
            i++;

            if(multipleMonitorsOneScreen)
                break;
        }

        if (!MediaServiceImpl.isJmfRegistryDisableLoad())
            CaptureDeviceManager.commit();
    }
}
