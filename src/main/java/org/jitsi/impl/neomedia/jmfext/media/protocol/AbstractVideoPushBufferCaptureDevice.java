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
package org.jitsi.impl.neomedia.jmfext.media.protocol;

import javax.media.*;
import javax.media.control.*;

/**
 * Provides a base implementation of <tt>PushBufferDataSource</tt> and
 * <tt>CaptureDevice</tt> for the purposes of video in order to facilitate
 * implementers by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractVideoPushBufferCaptureDevice
    extends AbstractPushBufferCaptureDevice
{

    /**
     * Initializes a new <tt>AbstractVideoPushBufferCaptureDevice</tt> instance.
     */
    protected AbstractVideoPushBufferCaptureDevice()
    {
        this(null);
    }

    /**
     * Initializes a new <tt>AbstractVideoPushBufferCaptureDevice</tt> instance
     * from a specific <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> to create the new instance from
     */
    protected AbstractVideoPushBufferCaptureDevice(MediaLocator locator)
    {
        super(locator);
    }

    /**
     * Creates a new <tt>FrameRateControl</tt> instance which is to allow the
     * getting and setting of the frame rate of this
     * <tt>AbstractVideoPushBufferCaptureDevice</tt>.
     *
     * @return a new <tt>FrameRateControl</tt> instance which is to allow the
     * getting and setting of the frame rate of this
     * <tt>AbstractVideoPushBufferCaptureDevice</tt>
     * @see AbstractPushBufferCaptureDevice#createFrameRateControl()
     */
    @Override
    protected FrameRateControl createFrameRateControl()
    {
        return null;
    }
}
