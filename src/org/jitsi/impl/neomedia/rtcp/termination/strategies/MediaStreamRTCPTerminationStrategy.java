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
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;

/**
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public abstract class MediaStreamRTCPTerminationStrategy
    implements RTCPTerminationStrategy
{
    /**
     * The <tt>MediaStream</tt> who owns this
     * <tt>BasicRTCPTerminationStrategy</tt> and whose RTCP packets this
     * <tt>RTCPTerminationStrategy</tt> terminates.
     */
    private MediaStream stream;

    /**
     * The view of {@link #stream} as a {@code MediaStreamImpl}. Explicitly
     * defined in order to reduce the number of casts.
     */
    private MediaStreamImpl mediaStreamImpl;

    /**
     * Gets the {@code MediaStreamImpl} which owns this
     * {@code RTCPTerminationStrategy}.
     *
     * @return the {@code MediaStreamImpl} which owns this
     * {@code RTCPTerminationStrategy} or {@code null} of the
     * {@code MediaStream} which owns this instance is not a
     * {@code MediaStreamImpl} instance (which should not happen at the time of
     * this writing)
     */
    protected MediaStreamImpl getMediaStreamImpl()
    {
        return mediaStreamImpl;
    }

    /**
     * Gets the <tt>MediaStream</tt> that owns this RTCP termination strategy.
     *
     * @return the <tt>MediaStream</tt> that owns this RTCP termination strategy
     */
    public MediaStream getStream()
    {
        return stream;
    }

    /**
     * Initializes the RTCP termination.
     *
     * @param stream The <tt>MediaStream</tt> who owns this
     * <tt>BasicRTCPTerminationStrategy</tt> and whose RTCP traffic this
     * <tt>BasicRTCPTerminationStrategy</tt> is terminating.
     */
    public void initialize(MediaStream stream)
    {
        this.stream = stream;

        mediaStreamImpl
            = (stream instanceof MediaStreamImpl)
                ? (MediaStreamImpl) stream
                : null;
    }
}
