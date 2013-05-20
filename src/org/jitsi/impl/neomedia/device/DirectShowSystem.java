/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import javax.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.directshow.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.directshow.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Discovers and registers DirectShow video capture devices with JMF.
 *
 * @author Sebastien Vincent
 */
public class DirectShowSystem
    extends DeviceSystem
{
    /**
     * The <tt>Logger</tt> used by the <tt>DirectShowSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(DirectShowSystem.class);

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying QuickTime/QTKit
     * capture devices.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_DIRECTSHOW;

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

    protected void doInitialize()
        throws Exception
    {
        DSCaptureDevice devices[] = DSManager.getInstance().getCaptureDevices();
        boolean captureDeviceInfoIsAdded = false;

        for(int i = 0, count = (devices == null) ? 0 : devices.length;
                i < count;
                i++)
        {
            long pixelFormat = devices[i].getFormat().getPixelFormat();
            int ffmpegPixFmt = (int) DataSource.getFFmpegPixFmt(pixelFormat);
            Format format = null;

            if(ffmpegPixFmt != FFmpeg.PIX_FMT_NONE)
            {
                format = new AVFrameFormat(ffmpegPixFmt, (int) pixelFormat);
            }
            else
            {
                logger.warn("No support for this webcam: " +
                        devices[i].getName() + "(format " + pixelFormat +
                        " not supported)");
                continue;
            }

            if(logger.isInfoEnabled())
            {
                for(DSFormat f : devices[i].getSupportedFormats())
                {
                    if(f.getWidth() != 0 && f.getHeight() != 0)
                        logger.info(
                                "Webcam available resolution for "
                                    + devices[i].getName()
                                    + ":"
                                    + f.getWidth() + "x" + f.getHeight());
                }
            }

            CaptureDeviceInfo device
                = new CaptureDeviceInfo(
                        devices[i].getName(),
                        new MediaLocator(
                                LOCATOR_PROTOCOL + ':' + devices[i].getName()),
                        new Format[] { format });

            if(logger.isInfoEnabled())
                logger.info("Found[" + i + "]: " + device.getName());

            CaptureDeviceManager.addDevice(device);
            captureDeviceInfoIsAdded = true;
        }

        if (captureDeviceInfoIsAdded
                && !MediaServiceImpl.isJmfRegistryDisableLoad())
            CaptureDeviceManager.commit();

        DSManager.dispose();
    }
}
