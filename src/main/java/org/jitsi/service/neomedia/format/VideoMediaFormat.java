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
package org.jitsi.service.neomedia.format;

import java.awt.*;

/**
 * The interface represents a video format. Video formats characterize video
 * streams and the <tt>VideoMediaFormat</tt> interface gives access to some of
 * their properties such as encoding and clock rate.
 *
 * @author Emil Ivov
 */
public interface VideoMediaFormat
    extends MediaFormat
{
    /**
     * Returns the size of the image that this <tt>VideoMediaFormat</tt>
     * describes.
     *
     * @return a <tt>java.awt.Dimension</tt> instance indicating the image size
     * (in pixels) of this <tt>VideoMediaFormat</tt>.
     */
    public Dimension getSize();

    /**
     * Returns the frame rate associated with this <tt>MediaFormat</tt>.
     *
     * @return The frame rate associated with this format.
     */
    public float getFrameRate();
}
