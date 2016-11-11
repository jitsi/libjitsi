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
package org.jitsi.impl.neomedia;

import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;

/**
 * Utility methods that extend the {@link MediaStream}.
 *
 * @author George Politis
 */
public class MediaStreamExtensions
{
    /**
     * Gets the {@code RTPEncodingResolver} associated to the
     * {@code MediaStream} passed in as a parameter.
     *
     * @param stream the {@code MediaStream}
     *
     * @return the {@code RTPEncodingResolver} that is associated to the
     * {@code MediaStream} passed in as a parameter, if that exists, otherwise
     * null.
     */
    public static RTPEncodingResolver getRTPEncodingResolver(MediaStream stream)
    {
        Object resolver = stream.getProperty(RTPEncodingResolver.class.getName());
        if (resolver != null)
        {
            return (RTPEncodingResolver) resolver;
        }

        return null;
    }

    /**
     * Gets the {@code RTPEncodingResolver} associated to the
     * {@code MediaStream} passed in as a parameter.
     *
     * @param stream the {@code MediaStream}.
     * @param receiveSSRC the encoding SSRC.
     *
     * @return the {@code RTPEncodingResolver} that is associated to the
     * {@code MediaStream} passed in as a parameter, if that exists, otherwise
     * null.
     */
    public static RTPEncodingResolver getRTPEncodingResolver(
        MediaStream stream, long receiveSSRC)
    {
        // Find the RTPEncoding that corresponds to this SSRC.
        StreamRTPManager receiveRTPManager = stream.getRTPTranslator()
            .findStreamRTPManagerByReceiveSSRC((int) receiveSSRC);

        if (receiveRTPManager != null)
        {
            MediaStream receiveStream = receiveRTPManager.getMediaStream();
            if (receiveStream != null)
            {
                return
                    MediaStreamExtensions.getRTPEncodingResolver(receiveStream);
            }
        }

        return null;
    }
}
