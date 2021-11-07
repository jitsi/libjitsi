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
package org.jitsi.service.neomedia.device;

import java.awt.*;

/**
 * Represents a physical screen display.
 *
 * @author Sebastien Vincent
 */
public interface ScreenDevice
{
    /**
     * Determines whether this screen contains a specified point.
     *
     * @param p point coordinate
     * @return <tt>true</tt> if <tt>point</tt> belongs to this screen;
     * <tt>false</tt>, otherwise
     */
    public boolean containsPoint(Point p);

    /**
     * Gets this screen's index.
     *
     * @return this screen's index
     */
    public int getIndex();

    /**
     * Gets the current resolution of this screen.
     *
     * @return the current resolution of this screen
     */
    public Dimension getSize();
}
