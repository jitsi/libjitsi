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

import org.jitsi.service.neomedia.device.*;

/**
 * Implementation of <tt>ScreenDevice</tt>.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class ScreenDeviceImpl implements ScreenDevice
{
    /**
     * An array with <tt>ScreenDevice</tt> element type which is empty.
     * Explicitly defined to reduce allocations, garbage collection.
     */
    private static final ScreenDevice[] EMPTY_SCREEN_DEVICE_ARRAY
        = new ScreenDevice[0];

    /**
     * Returns all available <tt>ScreenDevice</tt>s.
     *
     * @return an array of all available <tt>ScreenDevice</tt>s
     */
    public static ScreenDevice[] getAvailableScreenDevices()
    {
        GraphicsEnvironment ge;

        try
        {
            ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        }
        catch (Throwable t)
        {
            /*
             * We've seen NoClassDefFoundError at one time and InternalError at
             * another.
             */
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
                ge = null;
        }

        ScreenDevice[] screens = null;

        /*
         * Make sure the GraphicsEnvironment is not headless in order to avoid a
         * HeadlessException.
         */
        if ((ge != null) && !ge.isHeadlessInstance())
        {
            GraphicsDevice[] devices = ge.getScreenDevices();

            if ((devices != null) && (devices.length != 0))
            {
                screens = new ScreenDevice[devices.length];

                int i = 0;

                for (GraphicsDevice dev : devices)
                {
                    // We know that GraphicsDevice type is TYPE_RASTER_SCREEN.
                    screens[i] = new ScreenDeviceImpl(i, dev);
                    i++;
                }
            }
        }

        return (screens == null) ? EMPTY_SCREEN_DEVICE_ARRAY : screens;
    }

    /**
     * Gets the default <tt>ScreenDevice</tt>. The implementation attempts to
     * return the <tt>ScreenDevice</tt> with the highest resolution.
     *
     * @return the default <tt>ScreenDevice</tt>
     */
    public static ScreenDevice getDefaultScreenDevice()
    {
        int width = 0;
        int height = 0;
        ScreenDevice best = null;

        for (ScreenDevice screen : getAvailableScreenDevices())
        {
            Dimension size = screen.getSize();

            if ((size != null)
                    && ((width < size.width) || (height < size.height)))
            {
                width = size.width;
                height = size.height;
                best = screen;
            }
        }
        return best;
    }

    /**
     * Screen index.
     */
    private final int index;

    /**
     * AWT <tt>GraphicsDevice</tt>.
     */
    private final GraphicsDevice screen;

    /**
     * Constructor.
     *
     * @param index screen index
     * @param screen screen device
     */
    protected ScreenDeviceImpl(int index, GraphicsDevice screen)
    {
        this.index = index;
        this.screen = screen;
    }

    /**
     * If the screen contains specified point.
     *
     * @param p point coordinate
     * @return true if point belongs to screen, false otherwise
     */
    public boolean containsPoint(Point p)
    {
        return screen.getDefaultConfiguration().getBounds().contains(p);
    }

    /**
     * Get bounds of the screen.
     *
     * @return bounds of the screen
     */
    public Rectangle getBounds()
    {
        return screen.getDefaultConfiguration().getBounds();
    }

    /**
     * Get the screen index.
     *
     * @return screen index
     */
    public int getIndex()
    {
        return index;
    }

    /**
     * Get the identifier of the screen.
     *
     * @return ID of the screen
     */
    public String getName()
    {
        return screen.getIDstring();
    }

    /**
     * Gets the (current) size/resolution of this <tt>ScreenDevice</tt>.
     *
     * @return the (current) size/resolution of this <tt>ScreenDevice</tt>
     */
    public Dimension getSize()
    {
        DisplayMode displayMode = screen.getDisplayMode();

        return
            (displayMode == null)
                ? null
                : new Dimension(
                        displayMode.getWidth(),
                        displayMode.getHeight());
    }
}
