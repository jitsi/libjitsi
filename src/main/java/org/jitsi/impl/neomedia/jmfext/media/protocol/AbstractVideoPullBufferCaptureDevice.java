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

import org.jitsi.impl.neomedia.control.*;

/**
 * Provides a base implementation of <tt>PullBufferDataSource</tt> and
 * <tt>CaptureDevice</tt> for the purposes of video in order to facilitate
 * implementers by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractVideoPullBufferCaptureDevice
    extends AbstractPullBufferCaptureDevice
{

    /**
     * Initializes a new <tt>AbstractVideoPullBufferCaptureDevice</tt> instance.
     */
    protected AbstractVideoPullBufferCaptureDevice()
    {
    }

    /**
     * Initializes a new <tt>AbstractVideoPullBufferCaptureDevice</tt> instance
     * from a specific <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> to create the new instance from
     */
    protected AbstractVideoPullBufferCaptureDevice(MediaLocator locator)
    {
        super(locator);
    }

    /**
     * Creates a new <tt>FrameRateControl</tt> instance which is to allow the
     * getting and setting of the frame rate of this
     * <tt>AbstractVideoPullBufferCaptureDevice</tt>.
     *
     * @return a new <tt>FrameRateControl</tt> instance which is to allow the
     * getting and setting of the frame rate of this
     * <tt>AbstractVideoPullBufferCaptureDevice</tt>
     * @see AbstractPullBufferCaptureDevice#createFrameRateControl()
     */
    @Override
    protected FrameRateControl createFrameRateControl()
    {
        return
            new FrameRateControlAdapter()
            {
                /**
                 * The output frame rate of this
                 * <tt>AbstractVideoPullBufferCaptureDevice</tt>.
                 */
                private float frameRate = -1;

                @Override
                public float getFrameRate()
                {
                    return frameRate;
                }

                @Override
                public float setFrameRate(float frameRate)
                {
                    this.frameRate = frameRate;
                    return this.frameRate;
                }
            };
    }
}
