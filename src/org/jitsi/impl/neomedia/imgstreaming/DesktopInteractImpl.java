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
package org.jitsi.impl.neomedia.imgstreaming;

import java.awt.*;
import java.awt.image.*;

import org.jitsi.util.*;
import org.jitsi.utils.logging.*;

/**
 * Capture desktop screen either via native code (JNI) if available or by using
 * <tt>java.awt.Robot</tt>.
 *
 * @see java.awt.Robot
 * @author Sebastien Vincent
 */
public class DesktopInteractImpl
    implements DesktopInteract
{
    /**
     * The <tt>Logger</tt> used by the <tt>DesktopInteractImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(DesktopInteractImpl.class);

    /**
     * Screen capture robot.
     */
    private Robot robot = null;

    /**
     * Constructor.
     *
     * @throws AWTException if platform configuration does not allow low-level
     * input control
     * @throws SecurityException if Robot creation is not permitted
     */
    public DesktopInteractImpl() throws AWTException, SecurityException
    {
        robot = new Robot();
    }

    /**
     * Capture the full desktop screen using native grabber.
     *
     * Contrary to other captureScreen method, it only returns raw bytes
     * and not <tt>BufferedImage</tt>. It is done in order to limit
     * slow operation such as converting ARGB images (uint32_t) to bytes
     * especially for big big screen. For example a 1920x1200 desktop consumes
     * 9 MB of memory for grabbing and another 9 MB array for conversion
     * operation.
     *
     * @param display index of display
     * @param output output buffer to store bytes in.
     * Be sure that output length is sufficient
     * @return true if success, false if JNI error or output length too short
     */
    public boolean captureScreen(int display, byte output[])
    {
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        return captureScreen(display, 0, 0, dim.width, dim.height, output);
    }

    /**
     * Capture the full desktop screen using native grabber.
     *
     * Contrary to other captureScreen method, it only returns raw bytes
     * and not <tt>BufferedImage</tt>. It is done in order to limit
     * slow operation such as converting ARGB images (uint32_t) to bytes
     * especially for big big screen. For example a 1920x1200 desktop consumes
     * 9 MB of memory for grabbing and another 9 MB array for conversion
     * operation.
     *
     * @param display index of display
     * @param buffer native output buffer to store bytes in.
     * Be sure that output length is sufficient
     * @param bufferLength length of native buffer
     * @return true if success, false if JNI error or output length too short
     */
    public boolean captureScreen(int display, long buffer, int bufferLength)
    {
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        return
            captureScreen(
                    display,
                    0, 0, dim.width, dim.height,
                    buffer, bufferLength);
    }

    /**
     * Capture a part of the desktop screen using native grabber.
     *
     * Contrary to other captureScreen method, it only returns raw bytes
     * and not <tt>BufferedImage</tt>. It is done in order to limit
     * slow operation such as converting ARGB images (uint32_t) to bytes
     * especially for big big screen. For example a 1920x1200 desktop consumes
     * 9 MB of memory for grabbing and another 9 MB array for conversion
     * operation.
     *
     * @param display index of display
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @param output output buffer to store bytes in.
     * Be sure that output length is sufficient
     * @return true if success, false if JNI error or output length too short
     */
    public boolean captureScreen(
            int display,
            int x, int y, int width, int height,
            byte[] output)
    {
        return
            (OSUtils.IS_LINUX || OSUtils.IS_MAC || OSUtils.IS_WINDOWS)
                && ScreenCapture.grabScreen(
                        display,
                        x, y, width, height,
                        output);
    }

    /**
     * Capture a part of the desktop screen using native grabber.
     *
     * Contrary to other captureScreen method, it only returns raw bytes
     * and not <tt>BufferedImage</tt>. It is done in order to limit
     * slow operation such as converting ARGB images (uint32_t) to bytes
     * especially for big big screen. For example a 1920x1200 desktop consumes
     * 9 MB of memory for grabbing and another 9 MB array for conversion
     * operation.
     *
     * @param display index of display
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @param buffer native output buffer to store bytes in.
     * Be sure that output length is sufficient
     * @param bufferLength length of native buffer
     * @return true if success, false if JNI error or output length too short
     */
    public boolean captureScreen(
            int display,
            int x, int y, int width, int height,
            long buffer, int bufferLength)
    {
        return
            (OSUtils.IS_LINUX || OSUtils.IS_MAC || OSUtils.IS_WINDOWS)
                && ScreenCapture.grabScreen(
                        display,
                        x, y, width, height,
                        buffer, bufferLength);
    }

    /**
     * Capture the full desktop screen using <tt>java.awt.Robot</tt>.
     *
     * @return <tt>BufferedImage</tt> of the desktop screen
     */
    public BufferedImage captureScreen()
    {
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        return captureScreen(0, 0, dim.width, dim.height);
    }

    /**
     * Capture a part of the desktop screen using <tt>java.awt.Robot</tt>.
     *
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @return <tt>BufferedImage</tt> of a part of the desktop screen
     * or null if Robot problem
     */
    public BufferedImage captureScreen(int x, int y, int width, int height)
    {
        BufferedImage img = null;
        Rectangle rect = null;

        if(robot == null)
        {
            /* Robot has not been created so abort */
            return null;
        }

        if (logger.isInfoEnabled())
            logger.info("Begin capture: " + System.nanoTime());
        rect = new Rectangle(x, y, width, height);
        img = robot.createScreenCapture(rect);
        if (logger.isInfoEnabled())
            logger.info("End capture: " + System.nanoTime());

        return img;
    }
}
