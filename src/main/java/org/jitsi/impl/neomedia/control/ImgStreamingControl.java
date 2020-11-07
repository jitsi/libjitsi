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
package org.jitsi.impl.neomedia.control;

import javax.media.*;

/**
 * Defines the interface for controlling
 * <tt>CaptureDevice</tt>s/<tt>DataSource</tt>s associated with the
 * <tt>imgstreaming</tt> FMJ/JMF protocol.
 *
 * @author Lyubomir Marinov
 */
public interface ImgStreamingControl
    extends Control
{
    /**
     * Set the display index and the origin of the stream associated with a
     * specific index in the <tt>DataSource</tt> of this <tt>Control</tt>.
     *
     * @param streamIndex the index in the associated <tt>DataSource</tt> of the
     * stream to set the display index and the origin of
     * @param displayIndex the display index to set on the specified stream
     * @param x the x coordinate of the origin to set on the specified stream
     * @param y the y coordinate of the origin to set on the specified stream
     */
    public void setOrigin(int streamIndex, int displayIndex, int x, int y);
}
