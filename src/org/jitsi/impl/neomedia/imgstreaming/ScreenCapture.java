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

import org.jitsi.utils.*;
import org.jitsi.utils.logging.*;

/**
 * This class uses native code to capture desktop screen.
 *
 * It should work for Windows, Mac OS X and X11-based Unix such as Linux
 * and FreeBSD.
 *
 * @author Sebastien Vincent
 */
public class ScreenCapture
{
    private static final Logger logger = Logger.getLogger(ScreenCapture.class);

    static
    {
        String lib = "jnscreencapture";

        try
        {
            JNIUtils.loadLibrary(lib, ScreenCapture.class);
        }
        catch (Throwable t)
        {
            logger.error(
                    "Failed to load native library " + lib + ": "
                        + t.getMessage());
            throw new RuntimeException(t);
        }
    }

    /**
     * Grab desktop screen and get raw bytes.
     *
     * @param display index of display
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @param output output buffer to store screen bytes
     * @return true if grab success, false otherwise
     */
    public static native boolean grabScreen(
            int display,
            int x, int y, int width, int height,
            byte[] output);

    /**
     * Grab desktop screen and get raw bytes.
     *
     * @param display index of display
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @param output native output buffer to store screen bytes
     * @param outputLength native output length
     * @return true if grab success, false otherwise
     */
    public static native boolean grabScreen(
            int display,
            int x, int y, int width, int height,
            long output, int outputLength);
}
