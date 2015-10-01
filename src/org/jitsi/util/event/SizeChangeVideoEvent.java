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
package org.jitsi.util.event;

import java.awt.*;

/**
 * Represents a <tt>VideoEvent</tt> which notifies about an update to the size
 * of a specific visual <tt>Component</tt> depicting video.
 *
 * @author Lyubomir Marinov
 */
public class SizeChangeVideoEvent
    extends VideoEvent
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The type of a <tt>VideoEvent</tt> which notifies about an update to the
     * size of a specific visual <tt>Component</tt> depicting video.
     */
    public static final int VIDEO_SIZE_CHANGE = 3;

    /**
     * The new height of the associated visual <tt>Component</tt>.
     */
    private final int height;

    /**
     * The new width of the associated visual <tt>Component</tt>.
     */
    private final int width;

    /**
     * Initializes a new <tt>SizeChangeVideoEvent</tt> which is to notify about
     * an update to the size of a specific visual <tt>Component</tt> depicting
     * video.
     *
     * @param source the source of the new <tt>SizeChangeVideoEvent</tt>
     * @param visualComponent the visual <tt>Component</tt> depicting video
     * with the updated size
     * @param origin the origin of the video the new
     * <tt>SizeChangeVideoEvent</tt> is to notify about
     * @param width the new width of <tt>visualComponent</tt>
     * @param height the new height of <tt>visualComponent</tt>
     */
    public SizeChangeVideoEvent(
            Object source,
            Component visualComponent,
            int origin,
            int width,
            int height)
    {
        super(source, VIDEO_SIZE_CHANGE, visualComponent, origin);

        this.width = width;
        this.height = height;
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that the cloning of this instance initializes a new
     * <tt>SizeChangeVideoEvent</tt> instance.
     */
    @Override
    public VideoEvent clone(Object source)
    {
        return
            new SizeChangeVideoEvent(
                    source,
                    getVisualComponent(),
                    getOrigin(),
                    getWidth(), getHeight());
    }

    /**
     * Gets the new height of the associated visual <tt>Component</tt>.
     *
     * @return the new height of the associated visual <tt>Component</tt>
     */
    public int getHeight()
    {
        return height;
    }

    /**
     * Gets the new width of the associated visual <tt>Component</tt>.
     *
     * @return the new width of the associated visual <tt>Component</tt>
     */
    public int getWidth()
    {
        return width;
    }
}
