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
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.greyfading;

import javax.media.control.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

/**
 * Implements a <tt>CaptureDevice</tt> which provides a fading animation from
 * white to black to white... in form of video.
 *
 * @author Thomas Kuntz
 */
public class DataSource
    extends AbstractVideoPullBufferCaptureDevice
{
    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link AbstractPushBufferCaptureDevice#createStream(int, FormatControl)}.
     */
    protected VideoGreyFadingStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new VideoGreyFadingStream(this, formatControl);
    }
}
