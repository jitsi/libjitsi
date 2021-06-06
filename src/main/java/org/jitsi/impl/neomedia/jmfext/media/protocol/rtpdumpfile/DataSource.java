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

package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import javax.media.control.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

/**
 * Implements the <tt>CaptureDevice</tt> and <tt>DataSource</tt> for the
 * purpose of rtpdump file streaming.
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
    protected RtpdumpStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new RtpdumpStream(this, formatControl);
    }
}
