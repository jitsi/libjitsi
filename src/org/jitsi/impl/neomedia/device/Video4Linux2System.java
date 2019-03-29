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

import javax.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.video4linux2.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;

/**
 * Discovers and registers <tt>CaptureDevice</tt>s which implement the Video for
 * Linux Two API Specification with JMF.
 *
 * @author Lyubomir Marinov
 */
public class Video4Linux2System
    extends DeviceSystem
{
    /**
     * The <tt>Logger</tt> used by the <tt>Video4Linux2System</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(Video4Linux2System.class);

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying
     * <tt>CaptureDevice</tt> which implement the Video for Linux Two API
     * Specification.
     */
    private static final String LOCATOR_PROTOCOL
        = LOCATOR_PROTOCOL_VIDEO4LINUX2;

    /**
     * Initializes a new <tt>Video4Linux2System</tt> instance which discovers and
     * registers <tt>CaptureDevice</tt>s which implement the Video for Linux Two
     * API Specification with JMF.
     *
     * @throws Exception if anything goes wrong while discovering and
     * registering <tt>CaptureDevice</tt>s which implement the Video for Linux
     * Two API Specification with JMF
     */
    public Video4Linux2System()
        throws Exception
    {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL);
    }

    /**
     * Discovers and registers a <tt>CaptureDevice</tt> implementing the Video
     * for Linux Two API Specification with a specific device name with JMF.
     *
     * @param deviceName the device name of a candidate for a
     * <tt>CaptureDevice</tt> implementing the Video for Linux Two API
     * Specification to be discovered and registered with JMF
     * @return <tt>true</tt> if a <tt>CaptureDeviceInfo</tt> for the specified
     * <tt>CaptureDevice</tt> has been added to <tt>CaptureDeviceManager</tt>;
     * otherwise, <tt>false</tt>
     * @throws Exception if anything goes wrong while discovering and
     * registering the specified <tt>CaptureDevice</tt> with JMF
     */
    private boolean discoverAndRegister(String deviceName)
        throws Exception
    {
        int fd = Video4Linux2.open(deviceName, Video4Linux2.O_RDWR);
        boolean captureDeviceInfoIsAdded = false;

        if (-1 != fd)
        {
            try
            {
                long v4l2_capability = Video4Linux2.v4l2_capability_alloc();

                if (0 != v4l2_capability)
                {
                    try
                    {
                        if ((Video4Linux2.ioctl(
                                        fd,
                                        Video4Linux2.VIDIOC_QUERYCAP,
                                        v4l2_capability)
                                    != -1)
                                && ((Video4Linux2
                                            .v4l2_capability_getCapabilities(
                                                v4l2_capability)
                                        & Video4Linux2.V4L2_CAP_VIDEO_CAPTURE)
                                    == Video4Linux2.V4L2_CAP_VIDEO_CAPTURE))
                        {
                            captureDeviceInfoIsAdded
                                = register(deviceName, fd, v4l2_capability);
                        }
                    }
                    finally
                    {
                        Video4Linux2.free(v4l2_capability);
                    }
                }
            }
            finally
            {
                Video4Linux2.close(fd);
            }
        }
        return captureDeviceInfoIsAdded;
    }

    @Override
    protected void doInitialize()
        throws Exception
    {
        String baseDeviceName = "/dev/video";
        boolean captureDeviceInfoIsAdded = discoverAndRegister(baseDeviceName);

        for (int deviceMinorNumber = 0;
                deviceMinorNumber <= 63;
                deviceMinorNumber++)
        {
            captureDeviceInfoIsAdded
                = discoverAndRegister(baseDeviceName + deviceMinorNumber)
                    || captureDeviceInfoIsAdded;
        }
        if (captureDeviceInfoIsAdded
                && !MediaServiceImpl.isJmfRegistryDisableLoad())
            CaptureDeviceManager.commit();
    }

    /**
     * Registers a <tt>CaptureDevice</tt> implementing the Video for Linux Two
     * API Specification with a specific device name, a specific <tt>open()</tt>
     * file descriptor and a specific <tt>v4l2_capability</tt> with JMF.
     *
     * @param deviceName name of the device (i.e. /dev/videoX)
     * @param fd file descriptor of the device
     * @param v4l2_capability device V4L2 capability
     * @return <tt>true</tt> if a <tt>CaptureDeviceInfo</tt> for the specified
     * <tt>CaptureDevice</tt> has been added to <tt>CaptureDeviceManager</tt>;
     * otherwise, <tt>false</tt>
     * @throws Exception if anything goes wrong while registering the specified
     * <tt>CaptureDevice</tt> with JMF
     */
    private boolean register(String deviceName, int fd, long v4l2_capability)
        throws Exception
    {
        long v4l2_format
            = Video4Linux2.v4l2_format_alloc(
                    Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE);
        int pixelformat = 0;
        String supportedRes = null;

        if (0 != v4l2_format)
        {
            try
            {
                if (Video4Linux2.ioctl(
                            fd,
                            Video4Linux2.VIDIOC_G_FMT,
                            v4l2_format)
                        != -1)
                {
                    long fmtPix
                        = Video4Linux2.v4l2_format_getFmtPix(v4l2_format);

                    pixelformat
                        = Video4Linux2.v4l2_pix_format_getPixelformat(fmtPix);

                    if (FFmpeg.PIX_FMT_NONE
                            == DataSource.getFFmpegPixFmt(pixelformat))
                    {
                        Video4Linux2.v4l2_pix_format_setPixelformat(
                                fmtPix,
                                Video4Linux2.V4L2_PIX_FMT_RGB24);
                        if (Video4Linux2.ioctl(
                                    fd,
                                    Video4Linux2.VIDIOC_S_FMT,
                                    v4l2_format)
                                != -1)
                        {
                            pixelformat
                                = Video4Linux2.v4l2_pix_format_getPixelformat(
                                        fmtPix);
                        }
                    }

                    if(logger.isInfoEnabled())
                    {
                        supportedRes =
                            Video4Linux2.v4l2_pix_format_getWidth(fmtPix)
                            + "x"
                            + Video4Linux2.v4l2_pix_format_getHeight(fmtPix);
                    }
                }
            }
            finally
            {
                Video4Linux2.free(v4l2_format);
            }
        }

        Format format;
        int ffmpegPixFmt = DataSource.getFFmpegPixFmt(pixelformat);

        if (FFmpeg.PIX_FMT_NONE != ffmpegPixFmt)
            format = new AVFrameFormat(ffmpegPixFmt, pixelformat);
        else
            return false;

        String name = Video4Linux2.v4l2_capability_getCard(v4l2_capability);

        if ((name == null) || (name.length() == 0))
            name = deviceName;
        else
            name += " (" + deviceName + ")";

        if(logger.isInfoEnabled() && supportedRes != null)
        {
            logger.info("Webcam available resolution for " + name
                    + ":" + supportedRes);
        }

        CaptureDeviceManager.addDevice(
                new CaptureDeviceInfo(
                        name,
                        new MediaLocator(LOCATOR_PROTOCOL + ":" + deviceName),
                        new Format[] { format }));
        return true;
    }
}
